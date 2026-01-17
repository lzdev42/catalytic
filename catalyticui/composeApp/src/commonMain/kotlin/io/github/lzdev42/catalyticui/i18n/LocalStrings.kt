package io.github.lzdev42.catalyticui.i18n

import androidx.compose.runtime.compositionLocalOf

/**
 * CompositionLocal for providing AppStrings throughout the UI tree.
 * 
 * Usage:
 * ```kotlin
 * val strings = LocalStrings.current
 * Text(strings.startTest)
 * ```
 */
val LocalStrings = compositionLocalOf { AppStrings() }
