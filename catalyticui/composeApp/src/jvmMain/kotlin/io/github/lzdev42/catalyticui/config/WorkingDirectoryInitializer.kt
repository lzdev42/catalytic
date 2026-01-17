package io.github.lzdev42.catalyticui.config

import java.io.File

/**
 * Initializes the working directory structure.
 * Creates required subdirectories: plugins/, data/, logs/
 */
object WorkingDirectoryInitializer {
    
    private val REQUIRED_DIRS = listOf("plugins", "data", "logs")
    
    /**
     * Initialize the working directory by creating required subdirectories.
     * @param path The working directory path
     * @return true if all directories were created successfully
     */
    fun initialize(path: String): Boolean {
        val workingDir = File(path)
        if (!workingDir.exists()) {
            workingDir.mkdirs()
        }
        
        var allSuccess = true
        REQUIRED_DIRS.forEach { dir ->
            val subDir = File(workingDir, dir)
            if (!subDir.exists()) {
                val created = subDir.mkdirs()
                if (!created) {
                    println("Failed to create directory: ${subDir.absolutePath}")
                    allSuccess = false
                }
            }
        }
        
        return allSuccess
    }
    
    /**
     * Check if the working directory is properly initialized.
     */
    fun isInitialized(path: String): Boolean {
        val workingDir = File(path)
        return REQUIRED_DIRS.all { File(workingDir, it).exists() }
    }
}
