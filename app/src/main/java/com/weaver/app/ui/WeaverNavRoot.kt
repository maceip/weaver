package com.weaver.app.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.CancellationException

/**
 * Top-level navigation states. The bridge owns the Stitch selection; this state
 * captures whether the user has explicitly "entered" the focused view (tile tap)
 * so we can drive a back-stack with predictive-back animations. Will be replaced
 * by Nav3 + adaptive scenes in a follow-up.
 */
enum class ViewMode { Overview, Focused }

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
    var viewMode by remember { mutableStateOf(ViewMode.Overview) }

    val primary = selection.firstOrNull()
    val focused = nodes.firstOrNull { it.id == primary }

    val isWide = (foldState?.widthPx ?: 0) >= 1600
    val pagesPerView = if (isWide) 2 else 1

    // If selection clears from outside (e.g. Stitch fires selection_changed:[]), drop
    // out of focused mode too so the back stack stays coherent.
    LaunchedEffect(focused) {
        if (focused == null && viewMode == ViewMode.Focused) viewMode = ViewMode.Overview
    }

    val backProgress = remember { Animatable(0f) }
    var swipeFromRight by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = viewMode == ViewMode.Focused) { progress ->
        try {
            progress.collect { event ->
                swipeFromRight = event.swipeEdge == BackEventCompat.EDGE_RIGHT
                backProgress.snapTo(event.progress)
            }
            backProgress.animateTo(1f, animationSpec = tween(150))
            viewMode = ViewMode.Overview
            backProgress.snapTo(0f)
        } catch (_: CancellationException) {
            backProgress.animateTo(0f, animationSpec = tween(180))
        }
    }

    BackHandler(enabled = viewMode == ViewMode.Overview && selection.size > 1) {
        bridge.send(Inbound.ClearSelection)
    }

    BackHandler(enabled = promptState.activeSlash != null) {
        promptState = promptState.copy(activeSlash = null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            viewMode == ViewMode.Focused && focused != null -> focused.id
                            selection.size > 1 -> "${selection.size} selected"
                            else -> "Weaver"
                        },
                    )
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Overview is always present underneath so predictive back reveals it.
            OverviewCanvas(
                nodes = nodes,
                selectedId = primary,
                onSelect = { id -> bridge.send(Inbound.SelectNode(id)) },
                onFocus = { id ->
                    bridge.send(Inbound.SelectNode(id))
                    viewMode = ViewMode.Focused
                },
                bitmapCache = bitmapCache,
                pagesPerView = pagesPerView,
                modifier = Modifier.fillMaxSize(),
            )

            if (viewMode == ViewMode.Focused && focused != null) {
                FocusedLayer(
                    progress = backProgress.value,
                    swipeFromRight = swipeFromRight,
                ) {
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

            if (selection.isNotEmpty() && viewMode == ViewMode.Focused) {
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

            if (focused != null && !isWide && viewMode == ViewMode.Focused) {
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
                scopeId = focused?.id?.takeIf { viewMode == ViewMode.Focused },
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun FocusedLayer(
    progress: Float,
    swipeFromRight: Boolean,
    content: @Composable () -> Unit,
) {
    val inv = 1f - progress
    val scale = 0.85f + 0.15f * inv
    val alpha = inv.coerceIn(0f, 1f)
    val slidePx = if (swipeFromRight) -200f * progress else 200f * progress

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                translationX = slidePx
            },
    ) {
        content()
    }
}

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
