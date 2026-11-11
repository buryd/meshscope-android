package com.meshscope.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshscope.android.MeshScopeUiState
import com.meshscope.android.data.AnalyzerPresets
import com.meshscope.android.data.LiveTrafficEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshScopeScreen(
    state: MeshScopeUiState,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
    onRefresh: () -> Unit,
    onSelectEvent: (String) -> Unit,
    onCustomUrlChange: (String) -> Unit,
    onApplyCustomUrl: () -> Unit,
    onApplyPreset: (com.meshscope.android.data.AnalyzerPreset) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MeshScope",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.presetName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Analyzer settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            StatusBar(
                connected = state.connected,
                status = state.status,
                nodeCount = state.nodes.size,
                eventCount = state.events.size,
                error = state.error,
            )

            Box(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxWidth(),
            ) {
                TrafficMap(
                    nodes = state.nodes,
                    events = state.events,
                    selectedEventKey = state.selectedEventKey,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            TrafficFeed(
                events = state.events,
                selectedKey = state.selectedEventKey,
                onSelect = onSelectEvent,
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxWidth(),
            )
        }
    }

    if (state.showSettings) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onCloseSettings,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Analyzer source",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Point MeshScope at a CoreScope or Beacon analyzer API — same feed NodeScope uses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )

                AnalyzerPresets.all.forEach { preset ->
                    Button(
                        onClick = { onApplyPreset(preset) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(preset.name)
                            Text(
                                text = preset.description,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.customUrlDraft,
                    onValueChange = onCustomUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom analyzer base URL") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCloseSettings) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onApplyCustomUrl) { Text("Connect") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatusBar(
    connected: Boolean,
    status: String,
    nodeCount: Int,
    eventCount: Int,
    error: String?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(
                    if (connected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                ),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(status, style = MaterialTheme.typography.bodyMedium)
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = "$nodeCount nodes · $eventCount pkts",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun TrafficFeed(
    events: List<LiveTrafficEvent>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Live traffic",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Waiting for MeshCore packets…",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(events, key = { it.key }) { event ->
                    TrafficRow(
                        event = event,
                        selected = event.key == selectedKey,
                        onClick = { onSelect(event.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrafficRow(
    event: LiveTrafficEvent,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val hopNames = event.hops.mapNotNull { it.name ?: it.resolvedId().take(8) }
    val pathLabel = when {
        hopNames.isNotEmpty() -> hopNames.joinToString(" → ")
        else -> listOfNotNull(
            event.packet.originId?.take(8),
            event.packet.destId?.take(8),
        ).joinToString(" → ").ifBlank { "unknown path" }
    }
    val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(event.receivedMs))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = event.packet.displayType(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = pathLabel,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val metrics = buildList {
            event.packet.snr?.let { add("SNR ${"%.1f".format(it)} dB") }
            event.packet.rssi?.let { add("RSSI ${"%.0f".format(it)}") }
            if (event.hops.isNotEmpty()) add("${event.hops.size} hops")
        }.joinToString(" · ")
        if (metrics.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = metrics,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}
