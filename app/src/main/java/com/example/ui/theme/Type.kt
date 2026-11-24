package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val FrauncesDisplayFamily = FontFamily(
    Font(R.font.fraunces, FontWeight.SemiBold),
    Font(R.font.fraunces, FontWeight.Bold),
    Font(R.font.fraunces, FontWeight.ExtraBold)
)

// High-contrast editorial italic serif inspired by Material 3 Expressive hero typography
val EditorialItalicFamily = FontFamily(
    Font(R.font.fraunces, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.fraunces, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.fraunces, FontWeight.ExtraBold, FontStyle.Italic)
)

// Tall ultra-condensed display numerals inspired by Material 3 Expressive clock cards
val AntonioCondensedFamily = FontFamily(
    Font(R.font.antonio, FontWeight.Normal),
    Font(R.font.antonio, FontWeight.Medium),
    Font(R.font.antonio, FontWeight.Bold)
)

val FredokaPlayfulFamily = FontFamily(
    Font(R.font.fredoka, FontWeight.Medium),
    Font(R.font.fredoka, FontWeight.SemiBold),
    Font(R.font.fredoka, FontWeight.Bold)
)

val JakartaBodyFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val CountdownCondensedLargeStyle = TextStyle(
    fontFamily = AntonioCondensedFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 54.sp,
    lineHeight = 58.sp,
    letterSpacing = (-0.5).sp,
    fontFeatureSettings = "tnum"
)

val CountdownCondensedMediumStyle = TextStyle(
    fontFamily = AntonioCondensedFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.3).sp,
    fontFeatureSettings = "tnum"
)

val CondensedStatStyle = TextStyle(
    fontFamily = AntonioCondensedFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 28.sp,
    fontFeatureSettings = "tnum"
)

val MonoBadgeStyle = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontFeatureSettings = "tnum"
)

val TaskPulseTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = EditorialItalicFamily,
        fontWeight = FontWeight.ExtraBold,
        fontStyle = FontStyle.Italic,
        fontSize = 44.sp,
        lineHeight = 50.sp,
        letterSpacing = (-0.8).sp
    ),
    displaySmall = TextStyle(
        fontFamily = EditorialItalicFamily,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = EditorialItalicFamily,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = EditorialItalicFamily,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = EditorialItalicFamily,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FredokaPlayfulFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = JakartaBodyFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = JakartaBodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = JakartaBodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = JakartaBodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = JakartaBodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FredokaPlayfulFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JakartaBodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JakartaBodyFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)
