package com.weaver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.weaver.app.bridge.ExportKind

private data class ExportOption(
    val kind: ExportKind,
    val icon: ImageVector,
    val label: String,
    val subtitle: String,
)

// "What next" copy is the whole point — each row tells the user what they get,
// so the decision is made in the sheet rather than after a mystery download.
private val ExportOptions =
    listOf(
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
 * Bottom-sheet export picker. The recommended target floats to the top with a
 * badge: Figma when the Figma app is installed, otherwise the code download.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    figmaInstalled: Boolean,
    onPick: (ExportKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val recommended = if (figmaInstalled) ExportKind.Figma else ExportKind.Zip

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = "Export design",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 2.dp),
            )
            Text(
                text = "Pick where this screen should go next",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )
            ExportOptions
                .sortedByDescending { it.kind == recommended }
                .forEach { option ->
                    ExportRow(
                        option = option,
                        recommended = option.kind == recommended,
                        onClick = { onPick(option.kind) },
                    )
                }
        }
    }
}

@Composable
private fun ExportRow(
    option: ExportOption,
    recommended: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(option.icon, contentDescription = null)
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(option.label, style = MaterialTheme.typography.titleMedium)
                if (recommended) {
                    Spacer(Modifier.width(8.dp))
                    RecommendedBadge()
                }
            }
            Text(
                text = option.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecommendedBadge() {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "RECOMMENDED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
