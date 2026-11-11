package com.meshscope.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshscope.android.data.AnalyzerNode
import com.meshscope.android.data.AnalyzerPacket
import com.meshscope.android.data.AnalyzerPreset
import com.meshscope.android.data.AnalyzerPresets
import com.meshscope.android.data.CoreScopeClient
import com.meshscope.android.data.LiveTrafficEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MeshScopeUiState(
    val baseUrl: String = AnalyzerPresets.all.first().baseUrl,
    val presetName: String = AnalyzerPresets.all.first().name,
    val nodes: List<AnalyzerNode> = emptyList(),
    val events: List<LiveTrafficEvent> = emptyList(),
    val selectedEventKey: String? = null,
    val status: String = "Idle",
    val connected: Boolean = false,
    val error: String? = null,
    val showSettings: Boolean = false,
    val customUrlDraft: String = AnalyzerPresets.all.first().baseUrl,
)

class MeshScopeViewModel(
    private val client: CoreScopeClient = CoreScopeClient(),
) : ViewModel() {

    private val _ui = MutableStateFlow(MeshScopeUiState())
    val ui: StateFlow<MeshScopeUiState> = _ui.asStateFlow()

    private var liveJob: Job? = null
    private var pollJob: Job? = null

    init {
        connect(_ui.value.baseUrl, AnalyzerPresets.all.first().name)
    }

    fun openSettings() {
        _ui.update { it.copy(showSettings = true, customUrlDraft = it.baseUrl) }
    }

    fun closeSettings() {
        _ui.update { it.copy(showSettings = false) }
    }

    fun updateCustomUrlDraft(value: String) {
        _ui.update { it.copy(customUrlDraft = value) }
    }

    fun applyPreset(preset: AnalyzerPreset) {
        connect(preset.baseUrl, preset.name)
    }

    fun applyCustomUrl() {
        val url = _ui.value.customUrlDraft.trim()
        if (url.isBlank()) return
        connect(url, "Custom")
    }

    fun selectEvent(key: String?) {
        _ui.update { it.copy(selectedEventKey = key) }
    }

    fun refresh() {
        connect(_ui.value.baseUrl, _ui.value.presetName)
    }

    private fun connect(baseUrl: String, presetName: String) {
        liveJob?.cancel()
        pollJob?.cancel()

        _ui.update {
            it.copy(
                baseUrl = baseUrl,
                presetName = presetName,
                showSettings = false,
                status = "Connecting…",
                connected = false,
                error = null,
                events = emptyList(),
                selectedEventKey = null,
            )
        }

        viewModelScope.launch {
            try {
                val nodes = client.fetchNodes(baseUrl)
                _ui.update {
                    it.copy(
                        nodes = nodes.filter { n ->
                            n.resolvedLat() != null && n.resolvedLon() != null
                        },
                        status = "Loaded ${nodes.size} nodes",
                    )
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(error = e.message ?: "Node fetch failed", status = "Node fetch failed")
                }
            }

            try {
                val recent = client.fetchRecentPackets(baseUrl)
                recent.asReversed().forEach { ingest(it) }
                _ui.update { it.copy(status = "Synced recent packets") }
            } catch (_: Exception) {
                // Live WS can still work without history.
            }
        }

        liveJob = viewModelScope.launch {
            client.observeLivePackets(baseUrl)
                .catch { e ->
                    _ui.update {
                        it.copy(
                            connected = false,
                            error = e.message ?: "Live feed disconnected",
                            status = "Live feed offline — polling",
                        )
                    }
                    startPolling(baseUrl)
                }
                .collect { packet ->
                    _ui.update {
                        it.copy(
                            connected = true,
                            error = null,
                            status = "Live",
                        )
                    }
                    ingest(packet)
                }
        }

        // Fallback poller always runs lightly in case WS is silent.
        startPolling(baseUrl)
    }

    private fun startPolling(baseUrl: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(8_000)
                try {
                    val packets = client.fetchRecentPackets(baseUrl, limit = 20)
                    packets.forEach { ingest(it) }
                    _ui.update {
                        if (!it.connected) {
                            it.copy(status = "Polling analyzer")
                        } else {
                            it
                        }
                    }
                } catch (_: Exception) {
                    // Keep trying.
                }
            }
        }
    }

    private fun ingest(packet: AnalyzerPacket) {
        val hops = packet.hopChain()
        if (hops.isEmpty() && packet.originId == null && packet.destId == null) return
        val event = LiveTrafficEvent(
            key = packet.eventKey(),
            packet = packet,
            hops = hops,
        )
        _ui.update { state ->
            val exists = state.events.any { it.key == event.key }
            if (exists) return@update state
            val next = (listOf(event) + state.events).take(MAX_EVENTS)
            state.copy(
                events = next,
                selectedEventKey = state.selectedEventKey ?: event.key,
            )
        }
    }

    companion object {
        private const val MAX_EVENTS = 80
    }
}
