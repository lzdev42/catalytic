package io.github.lzdev42.catalyticui.i18n

/**
 * Android implementation of readResourceAsText.
 * Reads resource files from the classpath/assets using ClassLoader.
 */
actual fun readResourceAsText(fileName: String): String {
    val classLoader = StringsLoader::class.java.classLoader
    val inputStream = classLoader?.getResourceAsStream(fileName)
        ?: throw IllegalArgumentException("Resource not found: $fileName")
    
    return inputStream.bufferedReader().use { it.readText() }
}
