package io.github.lzdev42.catalyticui.i18n

/**
 * JVM implementation of readResourceAsText.
 * Reads resource files from the classpath.
 */
actual fun readResourceAsText(fileName: String): String {
    val classLoader = Thread.currentThread().contextClassLoader
        ?: StringsLoader::class.java.classLoader
    
    val inputStream = classLoader.getResourceAsStream(fileName)
        ?: throw IllegalArgumentException("Resource not found: $fileName")
    
    return inputStream.bufferedReader().use { it.readText() }
}
