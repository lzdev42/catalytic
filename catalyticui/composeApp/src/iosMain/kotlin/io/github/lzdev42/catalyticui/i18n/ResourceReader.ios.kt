package io.github.lzdev42.catalyticui.i18n

import platform.Foundation.*
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * iOS implementation of readResourceAsText.
 * Reads resource files from the main bundle using NSBundle.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun readResourceAsText(fileName: String): String {
    val name = fileName.substringBeforeLast(".")
    val ext = fileName.substringAfterLast(".")
    
    val path = NSBundle.mainBundle.pathForResource(name, ofType = ext)
        ?: throw IllegalArgumentException("Resource not found: $fileName")
    
    val content = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null)
        ?: throw IllegalArgumentException("Failed to load resource: $fileName")
        
    return content as String
}
