package io.github.lzdev42.catalyticui.i18n

import kotlinx.serialization.json.Json

/**
 * Loads language strings from JSON resource files.
 * 
 * Supported languages:
 * - en: English (default)
 * - zh_CN: Simplified Chinese
 * - zh_TW: Traditional Chinese
 */
object StringsLoader {
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val cache = mutableMapOf<String, AppStrings>()
    
    /**
     * Load strings for the specified language.
     * Falls back to English if the language file is not found.
     * 
     * @param lang Language code (e.g., "en", "zh_CN", "zh_TW")
     * @return AppStrings instance
     */
    fun load(lang: String): AppStrings {
        // Return cached if available
        cache[lang]?.let { return it }
        
        val content = try {
            readResourceAsText("strings_$lang.json")
        } catch (e: Exception) {
            // Fallback to English
            if (lang != "en") {
                println("Language file for '$lang' not found, falling back to English")
                return load("en")
            }
            // If even English fails, return default
            println("Failed to load English strings: ${e.message}")
            return AppStrings()
        }
        
        val strings = try {
            json.decodeFromString<AppStrings>(content)
        } catch (e: Exception) {
            println("Failed to parse strings_$lang.json: ${e.message}")
            AppStrings()
        }
        
        cache[lang] = strings
        return strings
    }
    
    /**
     * Clear the cache (useful for hot-reload during development)
     */
    fun clearCache() {
        cache.clear()
    }
    
    /**
     * Get list of supported languages
     */
    fun supportedLanguages(): List<LanguageOption> = listOf(
        LanguageOption("en", "English"),
        LanguageOption("zh_CN", "简体中文"),
        LanguageOption("zh_TW", "繁體中文")
    )
}

/**
 * Represents a language option for UI display
 */
data class LanguageOption(
    val code: String,
    val displayName: String
)

/**
 * Read resource file as text.
 * This function should be implemented per platform.
 */
expect fun readResourceAsText(fileName: String): String
