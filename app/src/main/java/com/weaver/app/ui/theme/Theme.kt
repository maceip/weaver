package com.weaver.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Voltage dark scheme. Single dark theme matches the design spec —
// no dynamic / light variant. Accent is Voltage; alternates live in Color.kt.
//
// We use plain MaterialTheme for now; MaterialExpressiveTheme + MotionScheme
// are still `internal` in material3 1.4.0 stable. Swap when public.
private val VoltageScheme =
    darkColorScheme(
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

@Composable
fun WeaverTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = VoltageScheme,
        typography = Typography,
        content = content,
    )
}
