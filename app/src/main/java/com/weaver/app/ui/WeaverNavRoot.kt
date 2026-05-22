package com.weaver.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.weaver.app.agent.TaskTracker
import com.weaver.app.assets.BitmapCache
import com.weaver.app.auth.AuthController
import com.weaver.app.auth.AuthState
import com.weaver.app.bridge.AttachmentKind
import com.weaver.app.bridge.Bridge
import com.weaver.app.bridge.CanvasTool
import com.weaver.app.bridge.Inbound
import com.weaver.app.bridge.Outbound
import com.weaver.app.bridge.Preset
import com.weaver.app.bridge.StitchNode
import com.weaver.app.data.ProjectRepository
import com.weaver.app.fold.FoldObserver
import com.weaver.app.offline.AnnotationStore
import com.weaver.app.offline.NodeCache

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun WeaverNavRoot(
    bridge: Bridge,
    presets: List<Preset>,
    authController: AuthController,
    projectRepository: ProjectRepository,
    onRequestUpload: () -> Unit,
    annotationStore: AnnotationStore,
    nodeCache: NodeCache,
    taskTracker: TaskTracker,
    bitmapCache: BitmapCache? = null,
    foldObserver: FoldObserver? = null,
) {
    val authState by authController.state.collectAsState()
    val nodes by bridge.nodes.collectAsState()
    val selection by bridge.selection.collectAsState()
    val sessions by bridge.sessions.collectAsState()
    val online by bridge.online.collectAsState()
    val annotations by annotationStore.state.collectAsState()
    val tasks by taskTracker.tasks.collectAsState()
    val foldState = foldObserver?.state?.collectAsState()?.value
    var promptState by rememberSaveable(stateSaver = PromptInputState.Saver) {
        mutableStateOf(PromptInputState())
    }
    var activeTool by rememberSaveable { mutableStateOf(CanvasTool.Cursor) }

    // Honest signal for ranking the export sheet: we can detect the Figma app
    // is installed, but not whether the user is signed in — Figma exposes no
    // such API and its cookies are cross-origin to our WebView.
    val context = LocalContext.current
    val figmaInstalled = remember { isFigmaInstalled(context) }

    val backStack = rememberNavBackStack(Login)

    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp, verticalPartitionSpacerSize = 0.dp)
    }
    // One adaptive signal: room for two panes also means room for wide chrome.
    val isWide = directive.maxHorizontalPartitions > 1
    val isTabletop = foldState?.isTabletop == true
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
        when (val op = reconcileSelection(backStack, selection)) {
            is NavReconcile.Push -> backStack.add(op.key)
            NavReconcile.PopTop -> backStack.removeLastOrNull()
            null -> Unit
        }
    }

    // Persist the live canvas so a cold, offline relaunch isn't a blank screen.
    LaunchedEffect(nodes) {
        val projectId = backStack.currentProjectId()
        if (projectId != null && nodes.isNotEmpty()) nodeCache.save(projectId, nodes)
    }

    // Buffered prompts have left the outbox once a transport is back.
    LaunchedEffect(online) {
        if (online) taskTracker.promoteQueued()
    }

    // Settle the oldest in-flight task whenever a streaming session finishes.
    var settledFinished by remember { mutableStateOf(0) }
    LaunchedEffect(sessions) {
        val finished = sessions.values.count { it.finished }
        repeat((finished - settledFinished).coerceAtLeast(0)) {
            taskTracker.settleOldestRunning()
        }
        settledFinished = finished
    }
    LaunchedEffect(Unit) {
        bridge.events.collect { event ->
            if (event is Outbound.Error) taskTracker.settleOldestRunning(failed = true)
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
            },
            sceneStrategies = listOf(supportingPaneStrategy),
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
                            backStack.add(Overview(project.id))
                        },
                        onNewProject = { seedPrompt ->
                            val project = projectRepository.newProject(
                                title = seedPrompt.take(40).ifBlank { "Untitled" },
                            )
                            backStack.add(Overview(project.id))
                            bridge.send(
                                Inbound.SubmitPrompt(
                                    text = seedPrompt,
                                    presetId = null,
                                    modelId = null,
                                ),
                            )
                            taskTracker.submit(seedPrompt, queued = !online)
                        },
                    )
                }
                entry<Overview>(metadata = SupportingPaneSceneStrategy.mainPane()) { route ->
                    LaunchedEffect(route.projectId) {
                        bridge.seedNodes(nodeCache.load(route.projectId))
                    }
                    OverviewPane(
                        nodes = nodes,
                        primary = selection.firstOrNull(),
                        bridge = bridge,
                        bitmapCache = bitmapCache,
                        onFocus = { id ->
                            bridge.send(Inbound.SelectNode(id))
                            backStack.add(Focused(route.projectId, id))
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
                            isWide = isWide,
                            bridge = bridge,
                            bitmapCache = bitmapCache,
                            selection = selection,
                            figmaInstalled = figmaInstalled,
                            isFavorite = node.id in annotations.favorites,
                            noteCount = annotations.notes.count { it.targetId == node.id },
                            onToggleFavorite = {
                                annotationStore.toggleFavorite(node.id)
                                bridge.send(Inbound.ToggleFavorite(node.id))
                            },
                            onAddNote = { text ->
                                annotationStore.addNote(node.id, text)
                                bridge.send(Inbound.AddNote(node.id, text))
                            },
                        )
                    }
                }
                entry<MultiSelect>(metadata = SupportingPaneSceneStrategy.mainPane()) {
                    MultiSelectPane(
                        nodes = nodes.filter { it.id in selection },
                        selection = selection,
                        bridge = bridge,
                        bitmapCache = bitmapCache,
                        figmaInstalled = figmaInstalled,
                    )
                }
            },
        )

        val top = backStack.lastOrNull()

        // Floating agent orb — visible app-wide once past the login gate.
        if (top !is Login) {
            AgentOrb(
                tasks = tasks,
                online = online,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 12.dp, end = 12.dp),
            )
        }

        // Chrome — tool palette + prompt dock — only visible inside a project.
        if (top !is Login && top !is Home) {
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
                scopeId = (top as? Focused)?.nodeId,
                onRequestUpload = onRequestUpload,
                isWide = isWide,
                isTabletop = isTabletop,
                online = online,
                onPromptSubmitted = { text -> taskTracker.submit(text, queued = !online) },
            )
        }
    }
}

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

private fun isFigmaInstalled(context: Context): Boolean = runCatching {
    context.packageManager.getLaunchIntentForPackage("com.figma.mirror") != null
}.getOrDefault(false)

@Composable
private fun FocusedPane(
    node: StitchNode,
    neighbour: StitchNode?,
    isWide: Boolean,
    bridge: Bridge,
    bitmapCache: BitmapCache?,
    selection: List<String>,
    figmaInstalled: Boolean,
    isFavorite: Boolean,
    noteCount: Int,
    onToggleFavorite: () -> Unit,
    onAddNote: (String) -> Unit,
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

        FocusedAnnotations(
            isFavorite = isFavorite,
            noteCount = noteCount,
            onToggleFavorite = onToggleFavorite,
            onAddNote = onAddNote,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 12.dp),
        )

        if (selection.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            ) {
                CanvasToolbar(
                    selectedIds = selection,
                    onAction = { action, ids -> bridge.send(Inbound.Canvas(action, ids)) },
                    onExport = { kind, ids ->
                        bridge.send(Inbound.RequestExport(kind, ids.firstOrNull()))
                    },
                    figmaInstalled = figmaInstalled,
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
    figmaInstalled: Boolean,
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
                onExport = { kind, ids ->
                    bridge.send(Inbound.RequestExport(kind, ids.firstOrNull()))
                },
                figmaInstalled = figmaInstalled,
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
    onRequestUpload: () -> Unit,
    isWide: Boolean,
    isTabletop: Boolean,
    online: Boolean,
    onPromptSubmitted: (String) -> Unit,
) {
    // On a large screen the dock is capped and centred rather than stretched
    // edge-to-edge; on the tabletop posture it lifts into the lower panel.
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        PromptInput(
            state = state,
            presets = presets,
            placeholder = if (online) {
                "What would you like to change or create?"
            } else {
                "Offline — your request will be sent when you reconnect"
            },
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
                onPromptSubmitted(text)
                onStateChange(state.copy(text = TextFieldValue("")))
            },
            onAttach = { kind ->
                when (kind) {
                    // Upload runs natively: a headless WebView can't satisfy
                    // Chromium's user-activation gate for a scripted file-input
                    // click, so the picker is launched from here instead.
                    AttachmentKind.UploadFile -> onRequestUpload()
                    else -> bridge.send(Inbound.Attach(kind))
                }
            },
            onVoice = {
                // TODO: launch speech recognizer, then dispatch Inbound.VoiceInput
            },
            modifier = Modifier
                .widthIn(max = if (isWide) 760.dp else Dp.Unspecified)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = if (isTabletop) 48.dp else 16.dp,
                ),
        )
    }
}
