package com.weaver.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.weaver.app.R

// Geist + Geist Mono via Google's downloadable-fonts provider. First launch
// hits the network; subsequent launches use the on-device cache.
private val provider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs,
    )

private val geist = GoogleFont("Geist")
private val geistMono = GoogleFont("Geist Mono")

val GeistFamily =
    FontFamily(
        Font(geist, provider, weight = FontWeight.Normal),
        Font(geist, provider, weight = FontWeight.Medium),
        Font(geist, provider, weight = FontWeight.SemiBold),
        Font(geist, provider, weight = FontWeight.Bold),
    )

val GeistMonoFamily =
    FontFamily(
        Font(geistMono, provider, weight = FontWeight.Normal),
        Font(geistMono, provider, weight = FontWeight.Medium),
        Font(geistMono, provider, weight = FontWeight.SemiBold),
    )

// Designer-supplied semantic ramp. Use for atoms; MaterialTheme.typography
// below covers the rest of the surface.
object WeaverType {
    val Display =
        TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp)
    val Title = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.3).sp)
    val CardTitle = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, letterSpacing = (-0.1).sp)
    val Body = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.sp)
    val BodyDim = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, color = TextDim)
    val Caption = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, color = TextDim)
    val Mono = TextStyle(fontFamily = GeistMonoFamily, fontSize = 11.sp, letterSpacing = 0.3.sp, color = TextDim)
    val MonoSmall = TextStyle(fontFamily = GeistMonoFamily, fontSize = 10.5.sp, letterSpacing = 0.4.sp, color = TextDim)
    val Pill = TextStyle(fontFamily = GeistFamily, fontWeight = FontWeight.Medium, fontSize = 13.5.sp)
}

val Typography =
    Typography(
        displayLarge = WeaverType.Display,
        titleLarge = WeaverType.Title,
        titleMedium = WeaverType.CardTitle,
        bodyLarge = WeaverType.Body,
        bodyMedium = WeaverType.Body.copy(fontSize = 12.sp),
        bodySmall = WeaverType.BodyDim,
        labelLarge = WeaverType.Pill,
        labelMedium = WeaverType.Caption,
        labelSmall = WeaverType.MonoSmall,
    )
