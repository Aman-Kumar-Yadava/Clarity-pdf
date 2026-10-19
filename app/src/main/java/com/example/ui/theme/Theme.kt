package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PixelNavyPrimaryDark,
    onPrimary = PixelNavyOnPrimaryDark,
    primaryContainer = PixelPrimaryContainerDark,
    onPrimaryContainer = PixelOnPrimaryContainerDark,
    secondary = PixelSecondaryDark,
    onSecondary = PixelOnSecondaryDark,
    secondaryContainer = PixelSecondaryContainerDark,
    onSecondaryContainer = PixelOnSecondaryContainerDark,
    tertiary = PixelTertiaryDark,
    onTertiary = PixelOnTertiaryDark,
    tertiaryContainer = PixelTertiaryContainerDark,
    onTertiaryContainer = PixelOnTertiaryContainerDark,
    background = PixelBackgroundDark,
    onBackground = PixelOnBackgroundDark,
    surface = PixelSurfaceDark,
    onSurface = PixelOnSurfaceDark,
    surfaceVariant = PixelSurfaceVariantDark,
    onSurfaceVariant = PixelOnSurfaceVariantDark,
    outline = PixelOutlineDark,
    outlineVariant = PixelOutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = PixelNavyPrimaryLight,
    onPrimary = PixelNavyOnPrimaryLight,
    primaryContainer = PixelPrimaryContainerLight,
    onPrimaryContainer = PixelOnPrimaryContainerLight,
    secondary = PixelSecondaryLight,
    onSecondary = PixelOnSecondaryLight,
    secondaryContainer = PixelSecondaryContainerLight,
    onSecondaryContainer = PixelOnSecondaryContainerLight,
    tertiary = PixelTertiaryLight,
    onTertiary = PixelOnTertiaryLight,
    tertiaryContainer = PixelTertiaryContainerLight,
    onTertiaryContainer = PixelOnTertiaryContainerLight,
    background = PixelBackgroundLight,
    onBackground = PixelOnBackgroundLight,
    surface = PixelSurfaceLight,
    onSurface = PixelOnSurfaceLight,
    surfaceVariant = PixelSurfaceVariantLight,
    onSurfaceVariant = PixelOnSurfaceVariantLight,
    outline = PixelOutlineLight,
    outlineVariant = PixelOutlineVariantLight
)

@Composable
fun ClarityPdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
