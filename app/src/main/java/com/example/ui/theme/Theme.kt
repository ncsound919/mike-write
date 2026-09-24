package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MikeWriteColorScheme = darkColorScheme(
    primary = AmberGold,
    onPrimary = DeepNavy,
    primaryContainer = AmberGoldLight,
    onPrimaryContainer = DeepNavy,
    secondary = SkyBlue,
    onSecondary = DeepNavy,
    tertiary = EmeraldVoice,
    onTertiary = DeepNavy,
    background = DeepNavy,
    onBackground = OffWhiteText,
    surface = DarkNavySurface,
    onSurface = OffWhiteText,
    surfaceVariant = MidnightCard,
    onSurfaceVariant = LightGrayMuted,
    error = CrimsonRecord,
    onError = Color.White
)

@Composable
fun MikeWriteTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MikeWriteColorScheme,
        typography = Typography,
        content = content
    )
}
