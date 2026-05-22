package com.weaver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.weaver.app.bridge.CanvasAction
import com.weaver.app.bridge.ExportKind

/**
 * Floating toolbar shown over the focused-design surface. Swaps between
 * single-selection (Generate / Modify / Preview / More) and multi-selection
 * (Generate / DESIGN.md / Align / Distribute / More) based on how many ids
 * Stitch reports as selected.
 */
@Composable
fun CanvasToolbar(
    selectedIds: List<String>,
    onAction: (CanvasAction, List<String>) -> Unit,
    onExport: (ExportKind, List<String>) -> Unit,
    figmaInstalled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (selectedIds.isEmpty()) return
    val multi = selectedIds.size > 1
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        if (multi) {
            MultiSelectActions(selectedIds, onAction)
        } else {
            SingleSelectActions(selectedIds.first(), onAction, onExport, figmaInstalled)
        }
    }
}

@Composable
private fun SingleSelectActions(
    id: String,
    onAction: (CanvasAction, List<String>) -> Unit,
    onExport: (ExportKind, List<String>) -> Unit,
    figmaInstalled: Boolean,
) {
    var generateOpen by remember { mutableStateOf(false) }
    var modifyOpen by remember { mutableStateOf(false) }
    var previewOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    val ids = listOf(id)

    Row(verticalAlignment = Alignment.CenterVertically) {
        ToolbarButton(Icons.Filled.AutoAwesome, "Generate", onClick = { generateOpen = true })
        GenerateMenuSingle(generateOpen, onDismiss = { generateOpen = false }) {
            generateOpen = false
            onAction(it, ids)
        }
        Spacer(Modifier.width(4.dp))

        ToolbarButton(Icons.Filled.Edit, "Modify", onClick = { modifyOpen = true })
        ModifyMenu(modifyOpen, onDismiss = { modifyOpen = false }) {
            modifyOpen = false
            onAction(it, ids)
        }
        Spacer(Modifier.width(4.dp))

        ToolbarButton(Icons.Filled.RemoveRedEye, "Preview", onClick = { previewOpen = true })
        PreviewMenu(previewOpen, onDismiss = { previewOpen = false }) {
            previewOpen = false
            onAction(it, ids)
        }
        Spacer(Modifier.width(4.dp))

        IconButton(onClick = { exportOpen = true }) {
            Icon(Icons.Filled.Upload, contentDescription = "Export")
        }
        Spacer(Modifier.width(2.dp))
        Text("Export", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(4.dp))

        IconButton(onClick = { onAction(CanvasAction.More, ids) }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More")
        }
        Spacer(Modifier.width(2.dp))
        Text("More", style = MaterialTheme.typography.labelLarge)
    }

    if (exportOpen) {
        ExportSheet(
            figmaInstalled = figmaInstalled,
            onPick = { kind ->
                exportOpen = false
                onExport(kind, ids)
            },
            onDismiss = { exportOpen = false },
        )
    }
}

@Composable
private fun MultiSelectActions(
    ids: List<String>,
    onAction: (CanvasAction, List<String>) -> Unit,
) {
    var generateOpen by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        ToolbarButton(Icons.Filled.AutoAwesome, "Generate", onClick = { generateOpen = true })
        GenerateMenuMulti(generateOpen, onDismiss = { generateOpen = false }) {
            generateOpen = false
            onAction(it, ids)
        }
        Spacer(Modifier.width(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("DESIGN.md", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(8.dp))

        IconButton(onClick = { onAction(CanvasAction.AlignLeft, ids) }) {
            Icon(Icons.Filled.AlignHorizontalLeft, contentDescription = "Align left")
        }
        IconButton(onClick = { onAction(CanvasAction.DistributeHorizontal, ids) }) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = "Distribute horizontally")
        }
        IconButton(onClick = { onAction(CanvasAction.More, ids) }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More")
        }
        Spacer(Modifier.width(2.dp))
        Text("More", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = onClick, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.ExpandMore, contentDescription = "open menu", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun GenerateMenuSingle(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPick: (CanvasAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        menuItem(Icons.Filled.PlayCircleOutline, "Instant Prototype", badge = "NEW") { onPick(CanvasAction.InstantPrototype) }
        menuItem(Icons.Filled.LibraryAdd, "Variations", shortcut = "⇧V") { onPick(CanvasAction.Variations) }
        menuItem(Icons.Filled.History, "Regenerate", shortcut = "⇧R") { onPick(CanvasAction.Regenerate) }
        menuItem(Icons.Filled.Whatshot, "Predictive Heatmap") { onPick(CanvasAction.PredictiveHeatmap) }
        menuItem(Icons.Filled.PhoneAndroid, "Mobile App Version") { onPick(CanvasAction.MobileAppVersion) }
        menuItem(Icons.Filled.Layers, "Missing States", badge = "NEW") { onPick(CanvasAction.MissingStates) }
        menuItem(Icons.Filled.Movie, "Animate", badge = "NEW") { onPick(CanvasAction.Animate) }
    }
}

@Composable
private fun GenerateMenuMulti(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPick: (CanvasAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        menuItem(Icons.Filled.PlayCircleOutline, "Instant Prototype", badge = "NEW") { onPick(CanvasAction.InstantPrototype) }
        menuItem(Icons.Filled.PhoneAndroid, "Mobile App Versions") { onPick(CanvasAction.MobileAppVersion) }
    }
}

@Composable
private fun ModifyMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPick: (CanvasAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        menuItem(Icons.Filled.Edit, "Edit", shortcut = "E") { onPick(CanvasAction.Edit) }
        menuItem(Icons.Filled.RateReview, "Annotate", shortcut = "A") { onPick(CanvasAction.Annotate) }
        menuItem(Icons.Filled.Palette, "DESIGN.md") { onPick(CanvasAction.OpenDesignDoc) }
    }
}

@Composable
private fun PreviewMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onPick: (CanvasAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        menuItem(Icons.Filled.OpenInNew, "New Tab", shortcut = "⇧P") { onPick(CanvasAction.PreviewNewTab) }
        menuItem(Icons.Filled.QrCode, "Show QR Code") { onPick(CanvasAction.PreviewQrCode) }
        menuItem(Icons.Filled.PhoneAndroid, "Mobile", shortcut = "390×884") { onPick(CanvasAction.PreviewMobile) }
        menuItem(Icons.Filled.TabletAndroid, "Tablet", shortcut = "768×1024") { onPick(CanvasAction.PreviewTablet) }
        menuItem(Icons.Filled.DesktopWindows, "Desktop", shortcut = "1280×1024") { onPick(CanvasAction.PreviewDesktop) }
    }
}

@Composable
private fun menuItem(
    icon: ImageVector,
    label: String,
    shortcut: String? = null,
    badge: String? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = { Icon(icon, contentDescription = null) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        trailingIcon =
            shortcut?.let {
                { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        onClick = onClick,
    )
}
