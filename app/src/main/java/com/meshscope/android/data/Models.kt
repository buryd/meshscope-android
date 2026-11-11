package com.meshscope.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AnalyzerNode(
    val id: String? = null,
    @SerialName("public_key") val publicKey: String? = null,
    val name: String? = null,
    val role: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("last_seen") val lastSeen: String? = null,
    val snr: Double? = null,
    val rssi: Double? = null,
) {
    fun resolvedId(): String =
        id?.takeIf { it.isNotBlank() }
            ?: publicKey?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: "unknown"

    fun resolvedLat(): Double? = lat ?: latitude
    fun resolvedLon(): Double? = lon ?: longitude

    fun isRepeater(): Boolean {
        val r = role?.lowercase().orEmpty()
        return r.contains("repeat") || r.contains("router") || r == "repeater"
    }
}

@Serializable
data class PathHop(
    val id: String? = null,
    @SerialName("public_key") val publicKey: String? = null,
    val name: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val snr: Double? = null,
    val rssi: Double? = null,
) {
    fun resolvedId(): String =
        id?.takeIf { it.isNotBlank() }
            ?: publicKey?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: "hop"
}

@Serializable
data class AnalyzerPacket(
    val id: String? = null,
    @SerialName("packet_hash") val packetHash: String? = null,
    val timestamp: String? = null,
    @SerialName("received_at") val receivedAt: String? = null,
    @SerialName("msg_type") val msgType: String? = null,
    val type: String? = null,
    @SerialName("origin_id") val originId: String? = null,
    @SerialName("dest_id") val destId: String? = null,
    @SerialName("path_json") val pathJson: List<PathHop>? = null,
    @SerialName("resolved_path") val resolvedPath: List<PathHop>? = null,
    val snr: Double? = null,
    val rssi: Double? = null,
    val raw: JsonElement? = null,
) {
    fun hopChain(): List<PathHop> =
        when {
            !resolvedPath.isNullOrEmpty() -> resolvedPath
            !pathJson.isNullOrEmpty() -> pathJson
            else -> emptyList()
        }

    fun displayType(): String = msgType ?: type ?: "packet"

    fun eventKey(): String =
        id?.takeIf { it.isNotBlank() }
            ?: packetHash?.takeIf { it.isNotBlank() }
            ?: "${timestamp ?: receivedAt}-${originId}-${destId}-${displayType()}"
}

data class AnalyzerPreset(
    val name: String,
    val baseUrl: String,
    val description: String,
)

object AnalyzerPresets {
    val all = listOf(
        AnalyzerPreset(
            name = "Warner Robins (LAN)",
            baseUrl = "http://10.0.2.2:3000",
            description = "Local CoreScope-WarnerRobins on this PC (emulator). On a phone use http://<PC-LAN-IP>:3000",
        ),
        AnalyzerPreset(
            name = "Chicagoland CoreScope",
            baseUrl = "https://corescope.chicagolandmesh.org",
            description = "Public demo feed — Chicago traffic only",
        ),
        AnalyzerPreset(
            name = "Custom / LAN",
            baseUrl = "http://10.0.2.2:3000",
            description = "Any CoreScope/Beacon base URL",
        ),
    )
}

data class LiveTrafficEvent(
    val key: String,
    val packet: AnalyzerPacket,
    val hops: List<PathHop>,
    val receivedMs: Long = System.currentTimeMillis(),
)
