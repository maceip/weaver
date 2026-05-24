package com.weaver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.weaver.app.bridge.CanvasTool

/**
 * Vertical tool palette mirroring Stitch's left rail (cursor / marquee /
 * edit / pan / image / palette / favorite). Layout on mobile is still TBD —
 * keeping this isolated so we can move it to an overflow sheet or a bottom
 * rail without churn elsewhere.
 */
@Composable
fun CanvasToolPalette(
    activeTool: CanvasTool,
    onSelect: (CanvasTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(48.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
                .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ToolButton(CanvasTool.Cursor, Icons.Filled.NearMe, activeTool, onSelect)
        ToolButton(CanvasTool.Marquee, Icons.Filled.SelectAll, activeTool, onSelect)
        ToolButton(CanvasTool.Edit, Icons.Filled.Edit, activeTool, onSelect)
        ToolButton(CanvasTool.Hand, Icons.Filled.PanTool, activeTool, onSelect)
        ToolButton(CanvasTool.InsertImage, Icons.Filled.Image, activeTool, onSelect)
        Spacer(Modifier.height(2.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
        Spacer(Modifier.height(2.dp))
        ToolButton(CanvasTool.Palette, Icons.Filled.Palette, activeTool, onSelect)
        ToolButton(CanvasTool.Favorite, Icons.Filled.Star, activeTool, onSelect)
    }
}

@Composable
private fun ToolButton(
    tool: CanvasTool,
    icon: ImageVector,
    activeTool: CanvasTool,
    onSelect: (CanvasTool) -> Unit,
) {
    val active = tool == activeTool
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                ),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = { onSelect(tool) }, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = tool.name,
                tint = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
