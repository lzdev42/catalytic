package io.github.lzdev42.catalyticui.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Application configuration stored in the user's app data directory.
 * Location: ~/Library/Application Support/Catalytic/config.json (macOS)
 *           %APPDATA%\Catalytic\config.json (Windows)
 * 
 * If workingDir is null, it indicates first launch and triggers the setup wizard.
 */
@Serializable
data class AppConfig(
    /**
     * User-selected working directory for plugins, data, and logs.
     * null = first launch, UI should show directory picker.
     */
    @SerialName("working_dir")
    val workingDir: String? = null,
    
    /**
     * UI language code (en, zh_CN, zh_TW).
     */
    @SerialName("language")
    val language: String = "en",
    
    /**
     * Dark theme preference.
     */
    @SerialName("is_dark_theme")
    val isDarkTheme: Boolean = true,
    
    /**
     * gRPC port for Host connection.
     */
    @SerialName("grpc_port")
    val grpcPort: Int = 5000
)
