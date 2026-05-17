package com.weaver.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.weaver.app.bridge.AttachmentKind
import com.weaver.app.bridge.Bridge
import com.weaver.app.bridge.CanvasTool
import com.weaver.app.bridge.Inbound
import com.weaver.app.bridge.Preset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeaverNavRoot(
    bridge: Bridge,
    presets: List<Preset>,
) {
    val nodes by bridge.nodes.collectAsState()
    val selection by bridge.selection.collectAsState()
    var promptState by remember { mutableStateOf(PromptInputState()) }
    var activeTool by remember { mutableStateOf(CanvasTool.Cursor) }

    val primary = selection.firstOrNull()
    val focused = nodes.firstOrNull { it.id == primary }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            selection.size > 1 -> "${selection.size} selected"
                            focused != null -> focused.id
                            else -> "Weaver"
                        },
                    )
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (focused == null && selection.size <= 1) {
                OverviewCanvas(
                    nodes = nodes,
                    selectedId = primary,
                    onSelect = { id -> bridge.send(Inbound.SelectNode(id)) },
                )
            } else if (focused != null) {
                FocusedDesignView(node = focused)
            }

            // Vertical tool palette on the left edge; small enough to live alongside
            // the canvas content on the Pixel 10 Pro Fold's inner display.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
            ) {
                CanvasToolPalette(
                    activeTool = activeTool,
                    onSelect = { tool ->
                        activeTool = tool
                        bridge.send(Inbound.SelectTool(tool))
                    },
                )
            }

            // Canvas toolbar floats over the top of the design when something is selected.
            if (selection.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                ) {
                    CanvasToolbar(
                        selectedIds = selection,
                        onAction = { action, ids ->
                            bridge.send(Inbound.Canvas(action, ids))
                        },
                    )
                }
            }

            // Size badge anchored to the bottom-left of the focused design.
            if (focused != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 64.dp, bottom = 96.dp),
                ) {
                    SizeBadge(widthPx = focused.w.toInt(), heightPx = focused.h.toInt())
                }
            }

            PromptDock(
                bridge = bridge,
                presets = presets,
                state = promptState,
                onStateChange = { promptState = it },
                scopeId = focused?.id,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun PromptDock(
    bridge: Bridge,
    presets: List<Preset>,
    state: PromptInputState,
    onStateChange: (PromptInputState) -> Unit,
    scopeId: String?,
    @Suppress("UNUSED_PARAMETER") contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp, start = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        PromptInput(
            state = state,
            presets = presets,
            onStateChange = onStateChange,
            onSubmit = {
                val text = state.text.text.trim()
                if (text.isEmpty()) return@PromptInput
                bridge.send(
                    Inbound.SubmitPrompt(
                        text = text,
                        scopeId = scopeId,
                        slash = state.activeSlash,
                        presetId = state.selectedPresetId,
                        modelId = state.selectedModelId,
                    ),
                )
                onStateChange(state.copy(text = TextFieldValue("")))
            },
            onAttach = { kind ->
                bridge.send(Inbound.Attach(kind))
                if (kind == AttachmentKind.WebsiteUrl) {
                    // TODO: surface URL entry dialog
                }
            },
            onVoice = {
                // TODO: launch speech recognizer, then dispatch Inbound.VoiceInput
            },
        )
    }
}
