package com.meshscope.android.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CoreScopeClient(
    private val http: OkHttpClient = defaultClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun fetchNodes(baseUrl: String): List<AnalyzerNode> {
        val roots = listOf(
            "${trimSlash(baseUrl)}/api/nodes",
            "${trimSlash(baseUrl)}/api/v1/nodes",
        )
        var lastError: Exception? = null
        for (url in roots) {
            try {
                val body = getText(url)
                return parseNodeList(body)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Unable to fetch nodes")
    }

    suspend fun fetchRecentPackets(baseUrl: String, limit: Int = 40): List<AnalyzerPacket> {
        val roots = listOf(
            "${trimSlash(baseUrl)}/api/packets?limit=$limit",
            "${trimSlash(baseUrl)}/api/v1/packets?limit=$limit",
        )
        var lastError: Exception? = null
        for (url in roots) {
            try {
                val body = getText(url)
                return parsePacketList(body)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Unable to fetch packets")
    }

    fun observeLivePackets(baseUrl: String): Flow<AnalyzerPacket> = callbackFlow {
        val wsUrls = listOf(
            httpToWs("${trimSlash(baseUrl)}/ws"),
            httpToWs("${trimSlash(baseUrl)}/api/ws"),
            httpToWs("${trimSlash(baseUrl)}/api/v1/ws"),
        )

        var socket: WebSocket? = null
        var index = 0

        fun connectNext() {
            if (index >= wsUrls.size) {
                close(IOException("WebSocket endpoints unavailable"))
                return
            }
            val url = wsUrls[index++]
            val request = Request.Builder().url(url).build()
            socket = http.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    // Connected.
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parseLiveMessage(text)?.let { trySend(it) }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    parseLiveMessage(bytes.utf8())?.let { trySend(it) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    webSocket.cancel()
                    connectNext()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    // no-op; flow will close via awaitClose
                }
            })
        }

        connectNext()

        awaitClose {
            socket?.close(1000, "client closed")
            socket?.cancel()
        }
    }

    private fun parseLiveMessage(text: String): AnalyzerPacket? {
        return try {
            val element = json.parseToJsonElement(text)
            when (element) {
                is JsonObject -> {
                    val nested = element["packet"] ?: element["data"] ?: element["payload"]
                    when {
                        nested is JsonObject -> json.decodeFromJsonElement<AnalyzerPacket>(nested)
                        element.containsKey("path_json") ||
                            element.containsKey("resolved_path") ||
                            element.containsKey("msg_type") ||
                            element.containsKey("packet_hash") ->
                            json.decodeFromJsonElement<AnalyzerPacket>(element)
                        else -> null
                    }
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseNodeList(body: String): List<AnalyzerNode> {
        val element = json.parseToJsonElement(body)
        val array = extractArray(element, "nodes", "data", "results")
        return array.mapNotNull {
            runCatching { json.decodeFromJsonElement<AnalyzerNode>(it) }.getOrNull()
        }
    }

    private fun parsePacketList(body: String): List<AnalyzerPacket> {
        val element = json.parseToJsonElement(body)
        val array = extractArray(element, "packets", "data", "results")
        return array.mapNotNull {
            runCatching { json.decodeFromJsonElement<AnalyzerPacket>(it) }.getOrNull()
        }
    }

    private fun extractArray(element: JsonElement, vararg keys: String): List<JsonElement> {
        return when (element) {
            is JsonArray -> element
            is JsonObject -> {
                keys.asSequence()
                    .mapNotNull { key -> element[key] }
                    .firstOrNull { it is JsonArray }
                    ?.jsonArray
                    ?: emptyList()
            }
            else -> emptyList()
        }
    }

    private suspend fun getText(url: String): String =
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url(url).get().build()
            val call = http.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            if (cont.isActive) {
                                cont.resumeWithException(
                                    IOException("HTTP ${it.code} for $url"),
                                )
                            }
                            return
                        }
                        val body = it.body?.string().orEmpty()
                        if (cont.isActive) cont.resume(body)
                    }
                }
            })
        }

    companion object {
        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

        fun trimSlash(url: String): String = url.trim().trimEnd('/')

        fun httpToWs(url: String): String =
            when {
                url.startsWith("https://") -> "wss://" + url.removePrefix("https://")
                url.startsWith("http://") -> "ws://" + url.removePrefix("http://")
                else -> url
            }
    }
}
