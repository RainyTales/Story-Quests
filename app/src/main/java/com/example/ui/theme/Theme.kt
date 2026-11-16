package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StoryQuestDarkColorScheme = darkColorScheme(
    primary = ArcaneGold,
    onPrimary = Color(0xFF1A1100),
    primaryContainer = ArcaneGoldDark,
    onPrimaryContainer = ArcaneGoldBright,
    secondary = ElectricCyan,
    onSecondary = Color(0xFF002026),
    secondaryContainer = ElectricCyanContainer,
    onSecondaryContainer = Color(0xFF8FF3FF),
    tertiary = MysticViolet,
    onTertiary = Color(0xFF1E0B3B),
    tertiaryContainer = MysticVioletContainer,
    onTertiaryContainer = Color(0xFFE6D6FF),
    background = VoidBackground,
    onBackground = TextPrimary,
    surface = VoidSurface,
    onSurface = TextPrimary,
    surfaceVariant = VoidSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = VoidBorder,
    error = ErrorCrimson,
    onError = Color.White,
    errorContainer = ErrorCrimsonContainer,
    onErrorContainer = Color(0xFFFFD9DF),
)

private val StoryQuestShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun StoryQuestTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = StoryQuestDarkColorScheme,
        typography = StoryQuestTypography,
        shapes = StoryQuestShapes,
        content = content,
    )
}
