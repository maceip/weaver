package com.weaver.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.weaver.app.agent.TaskEntry
import com.weaver.app.agent.TaskStatus
import com.weaver.app.ui.theme.Voltage
import com.weaver.app.ui.theme.VoltageDim

private val OrbDone = Color(0xFF5BD6A0)
private val OrbFailed = Color(0xFFFF6B6B)
private val OrbQueued = Color(0xFFF4C95D)

/**
 * Floating task-execution orb. A glowing dot that pulses while work is in
 * flight; one tap expands the three most recent prompts with their status.
 */
@Composable
fun AgentOrb(
    tasks: List<TaskEntry>,
    online: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val recent = tasks.takeLast(3).reversed()
    val busy = tasks.any { it.status == TaskStatus.Running || it.status == TaskStatus.Queued }
    val queued = tasks.count { it.status == TaskStatus.Queued }

    BackHandler(enabled = expanded) { expanded = false }

    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Orb(busy = busy, queued = queued, onClick = { expanded = !expanded })

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            TaskPanel(recent = recent, online = online, queued = queued)
        }
    }
}

@Composable
private fun Orb(
    busy: Boolean,
    queued: Int,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = if (busy) 0.82f else 0.95f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(if (busy) 900 else 2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse",
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer glow halo.
        Box(
            modifier =
                Modifier
                    .size(60.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(Voltage.copy(alpha = 0.45f), Color.Transparent)),
                    ),
        )
        // Core.
        Box(
            modifier =
                Modifier
                    .testTag("agentOrb")
                    .size(34.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White, Voltage, VoltageDim)))
                    .clickable(onClick = onClick),
        )
        if (queued > 0) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(OrbQueued),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = queued.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black,
                )
            }
        }
    }
}

@Composable
private fun TaskPanel(
    recent: List<TaskEntry>,
    online: Boolean,
    queued: Int,
) {
    Column(
        modifier =
            Modifier
                .padding(top = 8.dp)
                .widthIn(min = 220.dp, max = 300.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Recent prompts",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (online) OrbDone else OrbQueued),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (online) "online" else "offline · $queued queued",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))

        if (recent.isEmpty()) {
            Text(
                text = "No prompts yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            recent.forEach { task ->
                TaskRow(task)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntry) {
    val (dot, label) =
        when (task.status) {
            TaskStatus.Queued -> OrbQueued to "queued"
            TaskStatus.Running -> Voltage to "running"
            TaskStatus.Done -> OrbDone to "done"
            TaskStatus.Failed -> OrbFailed to "failed"
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dot),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = task.prompt,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = dot,
        )
    }
}
