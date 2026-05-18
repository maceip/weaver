package com.weaver.app.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Voltage dark scheme. Single dark theme matches the design spec —
// no dynamic / light variant. Accent is Voltage; alternates live in Color.kt.
private val VoltageScheme = darkColorScheme(
    primary = Voltage,
    onPrimary = VoltageInk,
    primaryContainer = VoltageDim,
    onPrimaryContainer = Voltage,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim,
    outline = LineStrong,
    outlineVariant = Line,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WeaverTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = VoltageScheme,
        typography = Typography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
