package com.weaver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weaver.app.bridge.Preset
import com.weaver.app.data.Project
import com.weaver.app.data.ProjectRepository
import com.weaver.app.ui.common.Artboard
import com.weaver.app.ui.common.DottedCanvas
import com.weaver.app.ui.common.PillBar
import com.weaver.app.ui.common.PillItem
import com.weaver.app.ui.common.WireBlock
import com.weaver.app.ui.theme.Block
import com.weaver.app.ui.theme.BlockHi
import com.weaver.app.ui.theme.Line
import com.weaver.app.ui.theme.Voltage
import com.weaver.app.ui.theme.WeaverType

@Composable
fun HomeScreen(
    repository: ProjectRepository,
    onOpen: (Project) -> Unit,
    onNewProject: (seedPrompt: String) -> Unit,
) {
    val projects by repository.projects.collectAsState()
    var prompt by remember { mutableStateOf(TextFieldValue("")) }

    val active = projects.filter { !it.isDraft }
    val drafts = projects.filter { it.isDraft }

    DottedCanvas {
        Column(modifier = Modifier.fillMaxSize()) {
            PillBar(
                items =
                    listOf(
                        PillItem("Projects", Icons.Rounded.FolderOpen, active = true),
                        PillItem("Drafts", Icons.Rounded.Layers),
                        PillItem("Shared", Icons.Rounded.IosShare),
                    ),
            )

            if (active.isEmpty() && drafts.isEmpty()) {
                EmptyState()
            } else {
                if (active.isNotEmpty()) {
                    RowLabel("Active threads")
                    CardRow {
                        active.forEach { p ->
                            ProjectCard(p, hero = p == active.first(), width = 250.dp, height = 310.dp) {
                                onOpen(p)
                            }
                        }
                    }
                }
                if (drafts.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    RowLabel("Drafts · ${drafts.size}")
                    CardRow {
                        drafts.forEach { p ->
                            ProjectCard(p, hero = false, width = 220.dp, height = 250.dp) {
                                onOpen(p)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Home composer — typing here spawns a new project and forwards the
            // seed prompt to the bridge inside that project's session.
            PromptInput(
                state = PromptInputState(text = prompt),
                presets = emptyList<Preset>(),
                placeholder = "Start a new thread…",
                onStateChange = { prompt = it.text },
                onSubmit = {
                    val text = prompt.text.trim()
                    if (text.isNotEmpty()) {
                        onNewProject(text)
                        prompt = TextFieldValue("")
                    }
                },
                onAttach = { /* TODO: attach from Home composer */ },
                onVoice = { /* TODO: voice input */ },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text("No projects yet.", style = WeaverType.Title)
        Spacer(Modifier.height(6.dp))
        Text("Type a seed prompt below to start one.", style = WeaverType.BodyDim)
    }
}

@Composable
private fun RowLabel(text: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text, style = WeaverType.CardTitle.copy(fontSize = 12.sp))
        Box(Modifier.weight(1f).height(1.dp).background(Line))
    }
}

@Composable
private fun CardRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun ProjectCard(
    p: Project,
    hero: Boolean,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.clickable(onClick = onClick)) {
        Artboard(
            title = p.title,
            meta = formatMeta(p.lastModified),
            active = !p.isDraft,
            peek = false,
            width = width,
            height = height,
        ) {
            if (hero) ProjectHero() else ProjectMockup()
        }
    }
}

@Composable
private fun ColumnScope.ProjectHero() {
    WireBlock(Modifier.fillMaxWidth(0.78f), height = 24.dp, radius = 8.dp, tint = BlockHi)
    WireBlock(Modifier.fillMaxWidth(0.55f), height = 12.dp, radius = 6.dp)
    Box(
        modifier =
            Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Block),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF78B4F0).copy(alpha = 0.22f), Color.Transparent)),
            ),
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Voltage))
        Text("drafting · ready", style = WeaverType.Caption.copy(fontSize = 11.sp))
    }
}

@Composable
private fun ColumnScope.ProjectMockup() {
    WireBlock(Modifier.fillMaxWidth(0.6f), height = 18.dp, radius = 6.dp, tint = BlockHi)
    WireBlock(Modifier.fillMaxWidth(0.4f), height = 9.dp, radius = 5.dp)
    Box(
        modifier =
            Modifier
                .padding(top = 2.dp)
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Block)
                .border(1.dp, Line, RoundedCornerShape(8.dp)),
    )
}

private fun formatMeta(timestampMs: Long): String {
    val deltaMs = System.currentTimeMillis() - timestampMs
    val mins = deltaMs / 60_000
    val hours = deltaMs / 3_600_000
    val days = deltaMs / 86_400_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> "${days}d"
    }
}
