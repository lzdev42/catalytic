package io.github.lzdev42.catalyticui.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ========================================
// Extended Colors (not in Material3)
// ========================================

data class ExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccess: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarning: Color,
    val surfaceDim: Color,
    val surfaceContainerHigh: Color,
    val onSurfaceMuted: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        success = Success,
        successContainer = SuccessContainer,
        onSuccess = Color.White,
        warning = Warning,
        warningContainer = WarningContainer,
        onWarning = Color.White,
        surfaceDim = SurfaceDimLight,
        surfaceContainerHigh = SurfaceContainerHighLight,
        onSurfaceMuted = OnSurfaceMutedLight,
    )
}

// ========================================
// Light Color Scheme
// ========================================

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = PrimaryDark,
    
    secondary = Primary,
    onSecondary = OnPrimary,
    
    error = Error,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = Error,
    
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
)

private val LightExtendedColors = ExtendedColors(
    success = Success,
    successContainer = SuccessContainer,
    onSuccess = Color.White,
    warning = Warning,
    warningContainer = WarningContainer,
    onWarning = Color.White,
    surfaceDim = SurfaceDimLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    onSurfaceMuted = OnSurfaceMutedLight,
)

// ========================================
// Dark Color Scheme
// ========================================

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDarkTheme,
    onPrimary = PrimaryContainerDarkTheme,
    primaryContainer = PrimaryContainerDarkTheme,
    onPrimaryContainer = PrimaryLightDarkTheme,
    
    secondary = PrimaryDarkTheme,
    onSecondary = PrimaryContainerDarkTheme,
    
    error = ErrorDark,
    onError = Color.White,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorDark,
    
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
)

private val DarkExtendedColors = ExtendedColors(
    success = SuccessDark,
    successContainer = SuccessContainerDark,
    onSuccess = Color.White,
    warning = WarningDark,
    warningContainer = WarningContainerDark,
    onWarning = Color.White,
    surfaceDim = SurfaceDimDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    onSurfaceMuted = OnSurfaceMutedDark,
)

// ========================================
// Theme Composable
// ========================================

@Composable
fun CatalyticTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    
    CompositionLocalProvider(
        LocalExtendedColors provides extendedColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CatalyticTypography,
            shapes = CatalyticShapes,
            content = content
        )
    }
}

// ========================================
// Extension for easy access
// ========================================

object CatalyticTheme {
    val extendedColors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current
}
