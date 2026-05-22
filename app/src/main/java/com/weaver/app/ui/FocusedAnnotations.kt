package com.weaver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Per-design star + note controls. Both are on-device state, so they work
 * offline and logged out; the sync to Stitch is buffered separately.
 */
@Composable
fun FocusedAnnotations(
    isFavorite: Boolean,
    noteCount: Int,
    onToggleFavorite: () -> Unit,
    onAddNote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var noteOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Box {
            IconButton(onClick = { noteOpen = true }) {
                Icon(Icons.AutoMirrored.Filled.StickyNote2, contentDescription = "Add note")
            }
            if (noteCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = noteCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }

    if (noteOpen) {
        NoteDialog(
            onDismiss = { noteOpen = false },
            onSave = { text ->
                onAddNote(text)
                noteOpen = false
            },
        )
    }
}

@Composable
private fun NoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a note") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Saved on-device, synced when online") },
                modifier = Modifier.testTag("noteField"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onSave(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
