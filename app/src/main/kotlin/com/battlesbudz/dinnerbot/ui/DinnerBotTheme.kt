package com.battlesbudz.dinnerbot.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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

/**
 * The Dinner Bot look (v0.6.0 redesign, Battles's approved mockups):
 * clean white surfaces, large plain bold type, rounded cards, pill-shaped
 * chips, buttons, and toggles, and prices set prominently at the right of
 * a row. Near-black is the working color (buttons, the selected tab's
 * pill); a muted green is the secondary accent for prices and checks.
 *
 * Dark mode (v0.6.1, Battles's direct ask after the redesign): a second
 * scheme in the same family, chosen automatically from the phone's own
 * dark setting (uiMode night) — no in-app toggle. Dark surfaces stay
 * warm near-black like the light scheme's warm whites; the working
 * color inverts (near-white buttons and tab pill with near-black
 * content, so the selected tab stays unmistakable), and the green
 * accent is brightened until prices and checks carry the same contrast
 * on dark that they have on light. Every screen draws from these two
 * schemes only — nothing in the features hardcodes a color.
 */
private val DinnerBotLightColors = lightColorScheme(
    primary = Color(0xFF141414),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2B2B2B),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF47795A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEBDF),
    onSecondaryContainer = Color(0xFF1E3A28),
    tertiary = Color(0xFF47795A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF141414),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141414),
    surfaceVariant = Color(0xFFF1F1ED),
    onSurfaceVariant = Color(0xFF4A4A45),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F4),
    surfaceContainer = Color(0xFFF1F1ED),
    surfaceContainerHigh = Color(0xFFE9E9E4),
    outline = Color(0xFFD8D8D1),
    outlineVariant = Color(0xFFE7E7E0),
)

private val DinnerBotDarkColors = darkColorScheme(
    primary = Color(0xFFF2F1EC),
    onPrimary = Color(0xFF141414),
    primaryContainer = Color(0xFF3B3B37),
    onPrimaryContainer = Color(0xFFF2F1EC),
    secondary = Color(0xFF93C6A4),
    onSecondary = Color(0xFF0F2416),
    secondaryContainer = Color(0xFF24402F),
    onSecondaryContainer = Color(0xFFC9E7D2),
    tertiary = Color(0xFF93C6A4),
    background = Color(0xFF121211),
    onBackground = Color(0xFFF2F1EC),
    surface = Color(0xFF121211),
    onSurface = Color(0xFFF2F1EC),
    surfaceVariant = Color(0xFF242422),
    onSurfaceVariant = Color(0xFFC6C5BC),
    surfaceContainerLowest = Color(0xFF0C0C0B),
    surfaceContainerLow = Color(0xFF1A1A19),
    surfaceContainer = Color(0xFF222220),
    surfaceContainerHigh = Color(0xFF2C2C2A),
    outline = Color(0xFF454540),
    outlineVariant = Color(0xFF30302D),
)

/** Big plain headings, bold; body stays the platform's readable default. */
private val DinnerBotTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp,
    ),
)

/** Rounded everything: small rows 14dp, cards 20dp, hero surfaces 28dp. */
private val DinnerBotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DinnerBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DinnerBotDarkColors else DinnerBotLightColors,
        typography = DinnerBotTypography,
        shapes = DinnerBotShapes,
        content = content,
    )
}
