package com.example.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.Typography
import androidx.wear.compose.material3.dynamicColorScheme
import com.example.wear.R

val WatchAntonioFamily = FontFamily(
    Font(R.font.antonio, FontWeight.Medium),
    Font(R.font.antonio, FontWeight.Bold)
)

val WatchEditorialItalicFamily = FontFamily(
    Font(R.font.fraunces, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.fraunces, FontWeight.Bold, FontStyle.Italic)
)

val WatchFredokaFamily = FontFamily(
    Font(R.font.fredoka, FontWeight.Medium),
    Font(R.font.fredoka, FontWeight.SemiBold),
    Font(R.font.fredoka, FontWeight.Bold)
)

val WatchJakartaFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val WatchCountdownLargeStyle = TextStyle(
    fontFamily = WatchAntonioFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 38.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum"
)

val WatchHeroSerifStyle = TextStyle(
    fontFamily = WatchEditorialItalicFamily,
    fontWeight = FontWeight.Bold,
    fontStyle = FontStyle.Italic,
    fontSize = 18.sp,
    lineHeight = 22.sp
)

val WatchUrgentCriticalColor = Color(0xFFFF6B5B)
val WatchUrgentSoonColor = Color(0xFFFBBF24)
val WatchBreakMintColor = Color(0xFF6EE7B7)

private val FallbackWatchColorScheme = ColorScheme(
    primary = Color(0xFFFF896F),
    primaryDim = Color(0xFFE0694E),
    primaryContainer = Color(0xFF7B240E),
    onPrimary = Color(0xFF4A1100),
    onPrimaryContainer = Color(0xFFFFDAD2),
    secondary = Color(0xFFBEC2FF),
    secondaryDim = Color(0xFF9399EE),
    secondaryContainer = Color(0xFF282A8E),
    onSecondary = Color(0xFF15176E),
    onSecondaryContainer = Color(0xFFE0E0FF),
    tertiary = Color(0xFF6EE7B7),
    tertiaryDim = Color(0xFF34D399),
    tertiaryContainer = Color(0xFF005235),
    onTertiary = Color(0xFF003823),
    onTertiaryContainer = Color(0xFFBFF5DF),
    surfaceContainerLow = Color(0xFF13111C),
    surfaceContainer = Color(0xFF1C1928),
    surfaceContainerHigh = Color(0xFF272336),
    onSurface = Color(0xFFF4EFFA),
    onSurfaceVariant = Color(0xFFB5ADC8),
    outline = Color(0xFF5D5670),
    outlineVariant = Color(0xFF332E42),
    background = Color.Black,
    onBackground = Color.White,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val WatchTypography = Typography(
    defaultFontFamily = WatchJakartaFamily,
    numeralExtraLarge = WatchCountdownLargeStyle,
    numeralLarge = WatchCountdownLargeStyle.copy(fontSize = 32.sp, lineHeight = 34.sp),
    displaySmall = TextStyle(
        fontFamily = WatchEditorialItalicFamily,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontSize = 22.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = WatchFredokaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = WatchFredokaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 19.sp
    ),
    titleSmall = TextStyle(
        fontFamily = WatchJakartaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = WatchJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    bodySmall = TextStyle(
        fontFamily = WatchJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp
    ),
    labelLarge = TextStyle(
        fontFamily = WatchFredokaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = WatchJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = WatchJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 13.sp
    )
)

class WatchHaptics(private val feedback: HapticFeedback) {
    fun select() {
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun confirm() {
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberWatchHaptics(): WatchHaptics {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { WatchHaptics(haptic) }
}

@Composable
fun TaskPulseWearTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicScheme = remember(context) { dynamicColorScheme(context) }
    val colorScheme = (dynamicScheme ?: FallbackWatchColorScheme).copy(
        background = Color.Black,
        onBackground = Color.White
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WatchTypography,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
