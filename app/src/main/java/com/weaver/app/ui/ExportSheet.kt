package com.weaver.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.weaver.app.bridge.ExportKind

private data class ExportOption(
    val kind: ExportKind,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val subtitle: String,
)

// "What next" copy is the whole point — each row tells the user what they get,
// so the decision is made in the sheet rather than after a mystery download.
private val ExportOptions = listOf(
    ExportOption(ExportKind.Figma, Icons.Filled.DesignServices, "Copy to Figma", "Paste the design into Figma to keep editing"),
    ExportOption(ExportKind.Zip, Icons.Filled.FolderZip, "Download code", "Get the HTML/CSS bundle as a .zip"),
    ExportOption(ExportKind.CopyCode, Icons.Filled.ContentCopy, "Copy code", "Copy the generated markup to the clipboard"),
    ExportOption(ExportKind.AiStudio, Icons.Filled.AutoAwesome, "Google AI Studio", "Keep building with Gemini in AI Studio"),
    ExportOption(ExportKind.Firebase, Icons.Filled.LocalFireDepartment, "Firebase", "Deploy straight to Firebase Hosting"),
    ExportOption(ExportKind.Jules, Icons.Filled.SmartToy, "Jules", "Hand off to Jules, Google's coding agent"),
    ExportOption(ExportKind.Lovable, Icons.Filled.Favorite, "Lovable", "Open the project in Lovable"),
    ExportOption(ExportKind.Bolt, Icons.Filled.Bolt, "Bolt", "Open the project in Bolt.new"),
)

/**
 * Export picker anchored to the canvas toolbar. The recommended target is listed
 * first with a badge: Figma when the Figma app is installed, otherwise the code download.
 */
@Composable
fun ExportSheet(
    expanded: Boolean,
    figmaInstalled: Boolean,
    onPick: (ExportKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val recommended = if (figmaInstalled) ExportKind.Figma else ExportKind.Zip

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("exportSheet"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(
                text = "Export design",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Pick where this screen should go next",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        HorizontalDivider()
        ExportOptions
            .sortedByDescending { it.kind == recommended }
            .forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = option.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (option.kind == recommended) {
                                Text(
                                    text = "RECOMMENDED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                    leadingIcon = { Icon(option.icon, contentDescription = null) },
                    onClick = { onPick(option.kind) },
                )
            }
    }
}
