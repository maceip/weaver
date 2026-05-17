package com.weaver.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.weaver.app.assets.BitmapCache
import com.weaver.app.bridge.AttachmentKind
import com.weaver.app.bridge.Bridge
import com.weaver.app.bridge.CanvasTool
import com.weaver.app.bridge.Inbound
import com.weaver.app.bridge.Preset
import com.weaver.app.bridge.StitchNode
import com.weaver.app.fold.FoldObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeaverNavRoot(
    bridge: Bridge,
    presets: List<Preset>,
    bitmapCache: BitmapCache? = null,
    foldObserver: FoldObserver? = null,
) {
    val nodes by bridge.nodes.collectAsState()
    val selection by bridge.selection.collectAsState()
    val foldState = foldObserver?.state?.collectAsState()?.value
    var promptState by remember { mutableStateOf(PromptInputState()) }
    var activeTool by remember { mutableStateOf(CanvasTool.Cursor) }

    val primary = selection.firstOrNull()
    val focused = nodes.firstOrNull { it.id == primary }

    // Inner display of the fold is wide enough to show two designs side-by-side.
    val isWide = (foldState?.widthPx ?: 0) >= 1600
    val pagesPerView = if (isWide) 2 else 1

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
                    bitmapCache = bitmapCache,
                    pagesPerView = pagesPerView,
                )
            } else if (focused != null) {
                if (isWide) {
                    SplitFocusedView(
                        nodes = nodes,
                        focusedId = focused.id,
                        bitmapCache = bitmapCache,
                    )
                } else {
                    FocusedDesignView(node = focused, bitmapCache = bitmapCache)
                }
            }

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

            if (selection.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                ) {
                    CanvasToolbar(
                        selectedIds = selection,
                        onAction = { action, ids -> bridge.send(Inbound.Canvas(action, ids)) },
                    )
                }
            }

            if (focused != null && !isWide) {
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

/**
 * Inner-display two-up view: the focused design on the left, its right-hand
 * neighbour on the right. Either tile pinch-zooms independently.
 */
@Composable
private fun SplitFocusedView(
    nodes: List<StitchNode>,
    focusedId: String,
    bitmapCache: BitmapCache?,
) {
    val focusedIndex = nodes.indexOfFirst { it.id == focusedId }
    if (focusedIndex < 0) return
    val left = nodes[focusedIndex]
    val right = nodes.getOrNull(focusedIndex + 1) ?: nodes.getOrNull(focusedIndex - 1)
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            FocusedDesignView(node = left, bitmapCache = bitmapCache)
        }
        if (right != null) {
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                FocusedDesignView(node = right, bitmapCache = bitmapCache)
            }
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
