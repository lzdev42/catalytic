package io.github.lzdev42.catalyticui

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lzdev42.catalyticui.config.AppConfig
import io.github.lzdev42.catalyticui.data.EngineRepository
import io.github.lzdev42.catalyticui.i18n.LocalStrings
import io.github.lzdev42.catalyticui.i18n.StringsLoader
import io.github.lzdev42.catalyticui.ui.screens.MainScreen
import io.github.lzdev42.catalyticui.ui.screens.SetupWizard
import io.github.lzdev42.catalyticui.ui.theme.CatalyticTheme
import io.github.lzdev42.catalyticui.viewmodel.MainViewModel
import io.github.lzdev42.catalyticui.viewmodel.SettingsViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App(
    repository: EngineRepository? = null,
    initialConfig: AppConfig = AppConfig(),
    onConfigChange: (AppConfig) -> Unit = {}
) {
    // Config state
    var config by remember { mutableStateOf(initialConfig) }
    
    // Theme from config
    var isDarkTheme by remember(config.isDarkTheme) { mutableStateOf(config.isDarkTheme) }
    
    // Language from config
    var currentLanguage by remember(config.language) { mutableStateOf(config.language) }
    val strings = remember(currentLanguage) { StringsLoader.load(currentLanguage) }
    
    // ViewModel
    val viewModel: MainViewModel = viewModel { MainViewModel(repository) }
    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(repository) }
    
    // Data is loaded from gRPC repository - no mock data
    
    CompositionLocalProvider(LocalStrings provides strings) {
        CatalyticTheme(darkTheme = isDarkTheme) {
            // Check if first launch (no working directory)
            if (config.workingDir == null) {
                SetupWizard(
                    onDirectorySelected = { path ->
                        val newConfig = config.copy(workingDir = path)
                        config = newConfig
                        onConfigChange(newConfig)
                    }
                )
            } else {
                MainScreen(
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel,
                    isDarkTheme = isDarkTheme,
                    currentLanguage = currentLanguage,
                    onToggleTheme = {
                        isDarkTheme = !isDarkTheme
                        val newConfig = config.copy(isDarkTheme = isDarkTheme)
                        config = newConfig
                        onConfigChange(newConfig)
                    },
                    onLanguageChange = { lang ->
                        currentLanguage = lang
                        val newConfig = config.copy(language = lang)
                        config = newConfig
                        onConfigChange(newConfig)
                    }
                )
            }
        }
    }
}