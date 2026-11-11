package com.meshscope.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MeshGreen = Color(0xFF7CFFB2)
private val MeshTeal = Color(0xFF1FA97A)
private val MeshBg = Color(0xFF071A14)
private val MeshSurface = Color(0xFF0E2A21)
private val MeshOn = Color(0xFFE7FFF3)

private val scheme = darkColorScheme(
    primary = MeshGreen,
    onPrimary = Color(0xFF042218),
    secondary = MeshTeal,
    background = MeshBg,
    surface = MeshSurface,
    onBackground = MeshOn,
    onSurface = MeshOn,
    error = Color(0xFFFF8A80),
)

@Composable
fun MeshScopeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        content = content,
    )
}
