// ==============================================================================
// PLAIN-ENGLISH GUIDE
//
// WHAT THIS FILE IS: the app's look: the 5 pastel color palettes, the rounded
// corner sizes, and the text styles. Dark mode follows your phone's setting.
//
// COLORS: Color(0xFF7CC6AE) is a hex color. Ignore "0xFF" (it means fully
// opaque); the rest is the usual web color #7CC6AE. To add a palette, copy one
// Palette(...) block, give it a new id, and add its name to both strings.xml files.
// ==============================================================================
package com.gymnam.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// One theme: the accent color (calorie ring, buttons), the page background and
// the card color, each in a light and a dark version.
/**
 * A pastel palette. "accent" is the ring/primary colour; protein/carbs/fat get their own
 * soft colours so the macro bars are readable at a glance. Each palette has light + dark.
 */
data class Palette(
    val id: String,
    val nameRes: Int,
    val accentLight: Color, val accentDark: Color,
    val bgLight: Color, val bgDark: Color,
    val cardLight: Color, val cardDark: Color,
)

// Colors for protein, carbs, fat bars, and the "over goal" warning color.
data class MacroColors(val protein: Color, val carbs: Color, val fat: Color, val over: Color)

// The 5 palettes in Settings: mint (default), peach, lavender, sky, rose.
val Palettes = listOf(
    Palette("mint", com.gymnam.app.R.string.palette_mint,
        Color(0xFF7CC6AE), Color(0xFF8FD4BD),
        Color(0xFFF4FAF7), Color(0xFF141917),
        Color(0xFFFFFFFF), Color(0xFF1E2522)),
    Palette("peach", com.gymnam.app.R.string.palette_peach,
        Color(0xFFF2A983), Color(0xFFF5B896),
        Color(0xFFFFF7F2), Color(0xFF1B1613),
        Color(0xFFFFFFFF), Color(0xFF27201C)),
    Palette("lavender", com.gymnam.app.R.string.palette_lavender,
        Color(0xFFA99BE0), Color(0xFFB9ADEA),
        Color(0xFFF8F6FD), Color(0xFF16141C),
        Color(0xFFFFFFFF), Color(0xFF211E29)),
    Palette("sky", com.gymnam.app.R.string.palette_sky,
        Color(0xFF86B9E6), Color(0xFF99C6EE),
        Color(0xFFF4F8FD), Color(0xFF12161B),
        Color(0xFFFFFFFF), Color(0xFF1C2229)),
    Palette("rose", com.gymnam.app.R.string.palette_rose,
        Color(0xFFE8A0B4), Color(0xFFEFB3C3),
        Color(0xFFFDF5F7), Color(0xFF1B1416),
        Color(0xFFFFFFFF), Color(0xFF281E21)),
)

// Find a palette by its id; if not found, use the first one (mint).
fun paletteById(id: String) = Palettes.firstOrNull { it.id == id } ?: Palettes.first()

// Turns a palette into Material Design's full list of color roles.
// ink = main text color, soft = secondary (grey) text.
// alpha = transparency: 0.25f means 25% strength (used for the ring's track).
private fun scheme(p: Palette, dark: Boolean): ColorScheme {
    val ink = if (dark) Color(0xFFECEAE8) else Color(0xFF2B2A33)
    val soft = if (dark) Color(0xFFA9A6A3) else Color(0xFF77757F)
    val accent = if (dark) p.accentDark else p.accentLight
    val onAccent = Color(0xFF1F1F24)
    return if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.25f), onPrimaryContainer = ink,
        secondary = Color(0xFFB7A6D9), tertiary = Color(0xFFF3B88E),
        background = p.bgDark, onBackground = ink,
        surface = p.cardDark, onSurface = ink,
        surfaceVariant = p.cardDark, onSurfaceVariant = soft,
        surfaceContainer = p.cardDark, surfaceContainerLow = p.cardDark,
        surfaceContainerHigh = p.cardDark, outlineVariant = Color(0x22FFFFFF)
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.22f), onPrimaryContainer = ink,
        secondary = Color(0xFF9E8CCB), tertiary = Color(0xFFE59A6A),
        background = p.bgLight, onBackground = ink,
        surface = p.cardLight, onSurface = ink,
        surfaceVariant = Color(0xFFF1EFF3), onSurfaceVariant = soft,
        surfaceContainer = p.cardLight, surfaceContainerLow = p.cardLight,
        surfaceContainerHigh = p.cardLight, outlineVariant = Color(0x14000000)
    )
}

// Protein = blue, carbs = yellow, fat = pink, over = soft red (lighter in dark mode).
fun macroColors(dark: Boolean) = if (dark)
    MacroColors(Color(0xFF9FC8F0), Color(0xFFF2D58C), Color(0xFFF0A9BE), Color(0xFFF08C8C))
else
    MacroColors(Color(0xFF7FB2E5), Color(0xFFEBC56A), Color(0xFFE590A8), Color(0xFFE07070))

// Corner roundness: small 12 dp, medium 20 dp, large 28 dp (cards), extra-large 32 dp.
private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val base = Typography()
// Text styles: the default ones, with some titles made bold/semi-bold.
private val AppType = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.4.sp, fontWeight = FontWeight.Medium)
)

// Wraps the whole app: everything inside uses these colors, shapes and fonts.
// isSystemInDarkTheme() = is the phone in dark mode right now?
@Composable
fun GymNamTheme(paletteId: String, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = scheme(paletteById(paletteId), dark),
        shapes = AppShapes,
        typography = AppType,
        content = content
    )
}
