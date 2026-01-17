package io.github.lzdev42.catalyticui.config

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Repository for managing application configuration.
 * 
 * Config file location:
 * - macOS: ~/Library/Application Support/Catalytic/config.json
 * - Windows: %APPDATA%\Catalytic\config.json
 * - Linux: ~/.config/Catalytic/config.json
 */
object ConfigRepository {
    
    private const val APP_NAME = "Catalytic"
    private const val CONFIG_FILE = "config.json"
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private var cachedConfig: AppConfig? = null
    
    /**
     * Get the application data directory path based on the OS.
     */
    fun getAppDataDir(): File {
        val os = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")
        
        val appDataPath = when {
            os.contains("mac") -> "$userHome/Library/Application Support/$APP_NAME"
            os.contains("win") -> "${System.getenv("APPDATA")}/$APP_NAME"
            else -> "$userHome/.config/$APP_NAME" // Linux
        }
        
        return File(appDataPath)
    }
    
    /**
     * Get the config file path.
     */
    fun getConfigFile(): File {
        return File(getAppDataDir(), CONFIG_FILE)
    }
    
    /**
     * Load configuration from disk.
     * Returns default config if file doesn't exist or is invalid.
     */
    fun load(): AppConfig {
        cachedConfig?.let { return it }
        
        val configFile = getConfigFile()
        
        val config = if (configFile.exists()) {
            try {
                val content = configFile.readText()
                json.decodeFromString<AppConfig>(content)
            } catch (e: Exception) {
                println("Failed to load config: ${e.message}, using defaults")
                AppConfig()
            }
        } else {
            // First launch - return default config with null workingDir
            AppConfig()
        }
        
        cachedConfig = config
        return config
    }
    
    /**
     * Save configuration to disk.
     */
    fun save(config: AppConfig) {
        val configFile = getConfigFile()
        
        // Ensure directory exists
        configFile.parentFile?.mkdirs()
        
        try {
            val content = json.encodeToString(AppConfig.serializer(), config)
            configFile.writeText(content)
            cachedConfig = config
        } catch (e: Exception) {
            println("Failed to save config: ${e.message}")
        }
    }
    
    /**
     * Update configuration with a transform function.
     */
    fun update(transform: (AppConfig) -> AppConfig) {
        val current = load()
        val updated = transform(current)
        save(updated)
    }
    
    /**
     * Check if this is the first launch (no working directory configured).
     */
    fun isFirstLaunch(): Boolean {
        return load().workingDir == null
    }
    
    /**
     * Clear the cache (for testing).
     */
    fun clearCache() {
        cachedConfig = null
    }
}
