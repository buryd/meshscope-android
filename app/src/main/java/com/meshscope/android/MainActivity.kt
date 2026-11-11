package com.meshscope.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshscope.android.ui.MeshScopeScreen
import com.meshscope.android.ui.theme.MeshScopeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MeshScopeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeshScopeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.ui.collectAsStateWithLifecycle()
                    MeshScopeScreen(
                        state = state,
                        onOpenSettings = viewModel::openSettings,
                        onCloseSettings = viewModel::closeSettings,
                        onRefresh = viewModel::refresh,
                        onSelectEvent = viewModel::selectEvent,
                        onCustomUrlChange = viewModel::updateCustomUrlDraft,
                        onApplyCustomUrl = viewModel::applyCustomUrl,
                        onApplyPreset = viewModel::applyPreset,
                    )
                }
            }
        }
    }
}
