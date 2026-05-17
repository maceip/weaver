package com.weaver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.weaver.app.bridge.StitchNode
import kotlin.math.roundToInt

/**
 * Pannable / zoomable grid that mirrors Stitch's canvas. Renders one tile per node
 * laid out at the coordinates Stitch reports; we honor those positions so the
 * overview "feels like Stitch" rather than reflowing it as a list.
 *
 * UI is placeholder until Stitch screenshots inform the visual design.
 */
@Composable
fun OverviewCanvas(
    nodes: List<StitchNode>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTransformGestures { _, panDelta, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.25f, 4f)
                    pan += panDelta
                }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { applyOverviewTransform(scale, pan) },
        ) {
            nodes.forEach { node ->
                NodeTile(
                    node = node,
                    selected = node.id == selectedId,
                    onClick = { onSelect(node.id) },
                )
            }
            if (nodes.isEmpty()) {
                Text(
                    text = "No designs yet. Type a prompt below.",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private fun GraphicsLayerScope.applyOverviewTransform(scale: Float, pan: Offset) {
    scaleX = scale
    scaleY = scale
    translationX = pan.x
    translationY = pan.y
}

@Composable
private fun NodeTile(node: StitchNode, selected: Boolean, onClick: () -> Unit) {
    val widthDp = (node.w.coerceAtLeast(160f)).dp
    val heightDp = (node.h.coerceAtLeast(220f)).dp
    Box(
        modifier = Modifier
            .offset { IntOffset(node.x.roundToInt(), node.y.roundToInt()) }
            .size(widthDp, heightDp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .pointerInput(node.id) { detectTransformGestures { _, _, _, _ -> } },
    ) {
        Text(
            text = node.id.takeLast(6),
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
