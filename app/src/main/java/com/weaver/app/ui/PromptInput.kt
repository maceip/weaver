package com.weaver.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weaver.app.bridge.AttachmentKind
import com.weaver.app.bridge.Preset
import com.weaver.app.bridge.SlashCommand

data class ModelChoice(val id: String, val label: String)

val DefaultModels = listOf(
    ModelChoice("gemini-3.1-pro", "3.1 Pro"),
    ModelChoice("gemini-3.1-flash", "3.1 Flash"),
    ModelChoice("gemini-2.5-pro", "2.5 Pro"),
)

data class PromptInputState(
    val text: TextFieldValue = TextFieldValue(""),
    val activeSlash: SlashCommand? = null,
    val selectedPresetId: String? = null,
    val selectedModelId: String = DefaultModels.first().id,
) {
    companion object {
        /** Keeps a half-typed prompt across rotation / process death. */
        val Saver: Saver<PromptInputState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.text.text,
                    state.text.selection.start.toString(),
                    state.text.selection.end.toString(),
                    state.activeSlash?.name.orEmpty(),
                    state.selectedPresetId.orEmpty(),
                    state.selectedModelId,
                )
            },
            restore = { saved ->
                PromptInputState(
                    text = TextFieldValue(
                        text = saved[0],
                        selection = TextRange(saved[1].toInt(), saved[2].toInt()),
                    ),
                    activeSlash = saved[3].takeIf { it.isNotEmpty() }
                        ?.let { SlashCommand.valueOf(it) },
                    selectedPresetId = saved[4].takeIf { it.isNotEmpty() },
                    selectedModelId = saved[5],
                )
            },
        )
    }
}

@Composable
fun PromptInput(
    state: PromptInputState,
    presets: List<Preset>,
    models: List<ModelChoice> = DefaultModels,
    placeholder: String = "What would you like to change or create?",
    onStateChange: (PromptInputState) -> Unit,
    onSubmit: () -> Unit,
    onAttach: (AttachmentKind) -> Unit,
    onVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val outline = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(surface)
            .border(1.dp, outline, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SlashChipRow(state.activeSlash, onClear = {
            onStateChange(state.copy(activeSlash = null))
        })

        PromptField(
            value = state.text,
            placeholder = placeholder,
            onChange = { onStateChange(state.copy(text = it)) },
            onSubmit = onSubmit,
        )

        Spacer(Modifier.height(8.dp))

        ActionsRow(
            state = state,
            presets = presets,
            models = models,
            onStateChange = onStateChange,
            onSubmit = onSubmit,
            onAttach = onAttach,
            onVoice = onVoice,
        )
    }
}

@Composable
private fun SlashChipRow(active: SlashCommand?, onClear: () -> Unit) {
    if (active == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "/" + active.name.lowercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "tap to clear",
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onClear, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Filled.ExpandMore, contentDescription = "clear slash command")
        }
    }
}

@Composable
private fun PromptField(
    value: TextFieldValue,
    placeholder: String,
    onChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp, max = 200.dp)) {
        if (value.text.isEmpty()) {
            Text(
                text = placeholder,
                color = placeholderColor,
                style = LocalTextStyle.current.copy(fontSize = 16.sp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = 16.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActionsRow(
    state: PromptInputState,
    presets: List<Preset>,
    models: List<ModelChoice>,
    onStateChange: (PromptInputState) -> Unit,
    onSubmit: () -> Unit,
    onAttach: (AttachmentKind) -> Unit,
    onVoice: () -> Unit,
) {
    var addOpen by remember { mutableStateOf(false) }
    var slashOpen by remember { mutableStateOf(false) }
    var paletteOpen by remember { mutableStateOf(false) }
    var modelOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            CircleIconButton(Icons.Filled.Add, "Add") { addOpen = true }
            AttachmentMenu(addOpen, onDismiss = { addOpen = false }) { kind ->
                addOpen = false
                onAttach(kind)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box {
            SlashTrigger(
                active = state.activeSlash,
                onClick = { slashOpen = true },
            )
            SlashMenu(slashOpen, onDismiss = { slashOpen = false }) { cmd ->
                slashOpen = false
                onStateChange(state.copy(activeSlash = cmd))
            }
        }

        Spacer(Modifier.weight(1f))

        CircleIconButton(Icons.Filled.LibraryAdd, "Variations") {
            onAttach(AttachmentKind.Variations)
        }
        Spacer(Modifier.width(6.dp))
        Box {
            PaletteButton(
                selected = presets.firstOrNull { it.id == state.selectedPresetId },
                onClick = { paletteOpen = true },
            )
            PaletteMenu(
                expanded = paletteOpen,
                presets = presets,
                onDismiss = { paletteOpen = false },
            ) { p ->
                paletteOpen = false
                onStateChange(state.copy(selectedPresetId = p?.id))
            }
        }
        Spacer(Modifier.width(6.dp))
        Box {
            ModelButton(
                label = models.firstOrNull { it.id == state.selectedModelId }?.label ?: "—",
                onClick = { modelOpen = true },
            )
            ModelMenu(modelOpen, models, onDismiss = { modelOpen = false }) { m ->
                modelOpen = false
                onStateChange(state.copy(selectedModelId = m.id))
            }
        }
        Spacer(Modifier.width(6.dp))
        CircleIconButton(Icons.Filled.Mic, "Voice", trailing = Icons.Filled.AutoAwesome, onClick = onVoice)
        Spacer(Modifier.width(6.dp))
        SendButton(enabled = state.text.text.isNotBlank(), onClick = onSubmit)
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    trailing: ImageVector? = null,
    onClick: () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = contentDescription)
                if (trailing != null) {
                    Icon(
                        trailing,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun SlashTrigger(active: SlashCommand?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = active?.let { "/" + it.name.lowercase() } ?: "/",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onClick, modifier = Modifier.size(18.dp)) {
                Icon(Icons.Filled.ExpandMore, contentDescription = "open slash menu", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PaletteButton(selected: Preset?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 6.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaletteSwatch(selected)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onClick, modifier = Modifier.size(18.dp)) {
            Icon(Icons.Filled.ExpandMore, contentDescription = "open palette menu", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PaletteSwatch(preset: Preset?) {
    val colors = preset?.palette?.mapNotNull { hex -> parseHexColor(hex) }
        ?: listOf(Color(0xFF1E88E5), Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFF111111))
    val safe = if (colors.isEmpty()) listOf(Color.Gray) else colors
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Brush.sweepGradient(safe + safe.first())),
    ) {
        if (preset == null) {
            Icon(
                Icons.Filled.Palette,
                contentDescription = "palette",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(12.dp),
            )
        }
    }
}

@Composable
private fun ModelButton(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onClick, modifier = Modifier.size(18.dp)) {
            Icon(Icons.Filled.ExpandMore, contentDescription = "open model menu", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = { if (enabled) onClick() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "send", tint = fg)
        }
    }
}

@Composable
private fun AttachmentMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (AttachmentKind) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Upload Files") },
            leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) },
            onClick = { onPick(AttachmentKind.UploadFile) },
        )
        DropdownMenuItem(
            text = { Text("Website URL") },
            leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
            onClick = { onPick(AttachmentKind.WebsiteUrl) },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Variations") },
            leadingIcon = { Icon(Icons.Filled.LibraryAdd, contentDescription = null) },
            onClick = { onPick(AttachmentKind.Variations) },
        )
    }
}

@Composable
private fun SlashMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (SlashCommand) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Image") },
            leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
            onClick = { onPick(SlashCommand.Image) },
        )
        DropdownMenuItem(
            text = { Text("Logo") },
            leadingIcon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
            onClick = { onPick(SlashCommand.Logo) },
        )
        DropdownMenuItem(
            text = { Text("Diagram") },
            leadingIcon = { Icon(Icons.Filled.InsertChart, contentDescription = null) },
            onClick = { onPick(SlashCommand.Diagram) },
        )
        DropdownMenuItem(
            text = { Text("Animate") },
            leadingIcon = { Icon(Icons.Filled.Movie, contentDescription = null) },
            onClick = { onPick(SlashCommand.Animate) },
        )
    }
}

@Composable
private fun PaletteMenu(
    expanded: Boolean,
    presets: List<Preset>,
    onDismiss: () -> Unit,
    onPick: (Preset?) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("DESIGN.md") },
            leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
            onClick = { onPick(null) },
        )
        DropdownMenuItem(
            text = { Text("Start with your design") },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            onClick = { onPick(null) },
        )
        DropdownMenuItem(
            text = { Text("Create new") },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            onClick = { onPick(null) },
        )
        val (user, builtin) = presets.partition { !it.isBuiltin }
        user.forEach { preset ->
            DropdownMenuItem(
                text = { Text(preset.name) },
                leadingIcon = { PaletteSwatch(preset) },
                onClick = { onPick(preset) },
            )
        }
        if (builtin.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = "Stitch Presets",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            builtin.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.name) },
                    leadingIcon = { PaletteSwatch(preset) },
                    onClick = { onPick(preset) },
                )
            }
        }
    }
}

@Composable
private fun ModelMenu(
    expanded: Boolean,
    models: List<ModelChoice>,
    onDismiss: () -> Unit,
    onPick: (ModelChoice) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        models.forEach { model ->
            DropdownMenuItem(text = { Text(model.label) }, onClick = { onPick(model) })
        }
    }
}

private fun parseHexColor(hex: String): Color? = runCatching {
    val cleaned = hex.removePrefix("#")
    val long = cleaned.toLong(16)
    when (cleaned.length) {
        6 -> Color(0xFF000000 or long)
        8 -> Color(long)
        else -> null
    }
}.getOrNull()
