package com.weaver.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.weaver.app.assets.BitmapCache
import com.weaver.app.auth.AuthController
import com.weaver.app.auth.AuthState
import com.weaver.app.bridge.AttachmentKind
import com.weaver.app.bridge.Bridge
import com.weaver.app.bridge.CanvasTool
import com.weaver.app.bridge.Inbound
import com.weaver.app.bridge.Preset
import com.weaver.app.bridge.StitchNode
import com.weaver.app.data.ProjectRepository
import com.weaver.app.fold.FoldObserver
import com.weaver.app.fold.FoldState

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun WeaverNavRoot(
    bridge: Bridge,
    presets: List<Preset>,
    authController: AuthController,
    projectRepository: ProjectRepository,
    bitmapCache: BitmapCache? = null,
    foldObserver: FoldObserver? = null,
) {
    val authState by authController.state.collectAsState()
    val nodes by bridge.nodes.collectAsState()
    val selection by bridge.selection.collectAsState()
    val foldState = foldObserver?.state?.collectAsState()?.value
    var promptState by remember { mutableStateOf(PromptInputState()) }
    var activeTool by remember { mutableStateOf(CanvasTool.Cursor) }
    var currentProjectId by remember { mutableStateOf<String?>(null) }

    val backStack = rememberNavBackStack(Login)

    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp, verticalPartitionSpacerSize = 0.dp)
    }
    val supportingPaneStrategy = rememberSupportingPaneSceneStrategy<NavKey>(
        backNavigationBehavior = BackNavigationBehavior.PopUntilCurrentDestinationChange,
        directive = directive,
    )

    // Once authenticated, jump straight to Home.
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated && backStack.lastOrNull() is Login) {
            backStack.clear()
            backStack.add(Home)
        }
    }

    // Sync bridge selection into the back stack while inside a project.
    LaunchedEffect(selection) {
        val top = backStack.lastOrNull()
        if (top is Login || top is Home) return@LaunchedEffect
        when {
            selection.size > 1 && top !is MultiSelect && top !is Focused -> backStack.add(MultiSelect)
            selection.isEmpty() && top is MultiSelect -> backStack.removeLastOrNull()
            selection.size == 1 && top is Focused && top.nodeId != selection.first() -> {
                backStack.removeLastOrNull()
                backStack.add(Focused(selection.first()))
            }
        }
    }

    BackHandler(enabled = promptState.activeSlash != null) {
        promptState = promptState.copy(activeSlash = null)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = {
                val top = backStack.lastOrNull()
                backStack.removeLastOrNull()
                if (top is MultiSelect) bridge.send(Inbound.ClearSelection)
                // Leaving the project root drops the cached project id.
                if (top is Overview && backStack.lastOrNull() is Home) currentProjectId = null
            },
            sceneStrategy = supportingPaneStrategy,
            entryProvider = entryProvider {
                entry<Login> {
                    LoginScreen(
                        authController = authController,
                        state = authState,
                        onAuthenticated = {
                            // No-op: the LaunchedEffect above advances the stack.
                        },
                    )
                }
                entry<Home>(metadata = SupportingPaneSceneStrategy.mainPane()) {
                    HomeScreen(
                        repository = projectRepository,
                        onOpen = { project ->
                            projectRepository.touch(project.id)
                            currentProjectId = project.id
                            backStack.add(Overview)
                            // TODO: tell the bridge which Stitch project to navigate to;
                            // for now Stitch's own UI handles project selection.
                        },
                        onNewProject = { seedPrompt ->
                            val project = projectRepository.newProject(
                                title = seedPrompt.take(40).ifBlank { "Untitled" },
                            )
                            currentProjectId = project.id
                            backStack.add(Overview)
                            bridge.send(
                                Inbound.SubmitPrompt(
                                    text = seedPrompt,
                                    presetId = null,
                                    modelId = null,
                                ),
                            )
                        },
                    )
                }
                entry<Overview>(metadata = SupportingPaneSceneStrategy.mainPane()) {
                    OverviewPane(
                        nodes = nodes,
                        primary = selection.firstOrNull(),
                        bridge = bridge,
                        bitmapCache = bitmapCache,
                        onFocus = { id ->
                            bridge.send(Inbound.SelectNode(id))
                            backStack.add(Focused(id))
                        },
                    )
                }
                entry<Focused>(metadata = SupportingPaneSceneStrategy.supportingPane()) { route ->
                    val node = nodes.firstOrNull { it.id == route.nodeId }
                    if (node != null) {
                        FocusedPane(
                            node = node,
                            neighbour = nodes.getOrNull(nodes.indexOf(node) + 1)
                                ?: nodes.getOrNull(nodes.indexOf(node) - 1),
                            isWide = foldIsWide(foldState),
                            bridge = bridge,
                            bitmapCache = bitmapCache,
                            selection = selection,
                        )
                    }
                }
                entry<MultiSelect>(metadata = SupportingPaneSceneStrategy.mainPane()) {
                    MultiSelectPane(
                        nodes = nodes.filter { it.id in selection },
                        selection = selection,
                        bridge = bridge,
                        bitmapCache = bitmapCache,
                    )
                }
            },
        )

        // Chrome — tool palette + prompt dock — only visible inside a project.
        if (backStack.lastOrNull() !is Login && backStack.lastOrNull() !is Home) {
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

            PromptDock(
                bridge = bridge,
                presets = presets,
                state = promptState,
                onStateChange = { promptState = it },
                scopeId = (backStack.lastOrNull() as? Focused)?.nodeId,
            )
        }
    }
}

private fun foldIsWide(state: FoldState?): Boolean =
    (state?.widthPx ?: 0) >= 1600

@Composable
private fun OverviewPane(
    nodes: List<StitchNode>,
    primary: String?,
    bridge: Bridge,
    bitmapCache: BitmapCache?,
    onFocus: (String) -> Unit,
) {
    OverviewCanvas(
        nodes = nodes,
        selectedId = primary,
        onSelect = { id -> bridge.send(Inbound.SelectNode(id)) },
        onFocus = onFocus,
        bitmapCache = bitmapCache,
        pagesPerView = 1,
    )
}

@Composable
private fun FocusedPane(
    node: StitchNode,
    neighbour: StitchNode?,
    isWide: Boolean,
    bridge: Bridge,
    bitmapCache: BitmapCache?,
    selection: List<String>,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isWide && neighbour != null) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    FocusedDesignView(node = node, bitmapCache = bitmapCache)
                }
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    FocusedDesignView(node = neighbour, bitmapCache = bitmapCache)
                }
            }
        } else {
            FocusedDesignView(node = node, bitmapCache = bitmapCache)
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

        if (!isWide) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 64.dp, bottom = 96.dp),
            ) {
                SizeBadge(widthPx = node.w.toInt(), heightPx = node.h.toInt())
            }
        }
    }
}

@Composable
private fun MultiSelectPane(
    nodes: List<StitchNode>,
    selection: List<String>,
    bridge: Bridge,
    bitmapCache: BitmapCache?,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        OverviewCanvas(
            nodes = nodes,
            selectedId = nodes.firstOrNull()?.id,
            onSelect = { id -> bridge.send(Inbound.SelectNode(id)) },
            onFocus = { /* no-op while multi-select toolbar is up */ },
            bitmapCache = bitmapCache,
        )
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
}

@Composable
private fun PromptDock(
    bridge: Bridge,
    presets: List<Preset>,
    state: PromptInputState,
    onStateChange: (PromptInputState) -> Unit,
    scopeId: String?,
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
