package io.github.lzdev42.catalyticui.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * 截图工具类
 * 用于保存 Compose UI 测试截图
 */
object ScreenshotUtil {
    
    private const val DEFAULT_SCREENSHOT_DIR = "screenshots"
    
    /**
     * 保存 ImageBitmap 为 PNG 文件
     * 
     * @param image Compose ImageBitmap
     * @param filename 文件名 (不含路径)
     * @param directory 保存目录 (默认 screenshots/)
     * @return 保存的文件路径
     */
    fun saveScreenshot(
        image: ImageBitmap,
        filename: String,
        directory: String = DEFAULT_SCREENSHOT_DIR
    ): String {
        val skiaBitmap = image.asSkiaBitmap()
        val skiaImage = Image.makeFromBitmap(skiaBitmap)
        val pngData = skiaImage.encodeToData(EncodedImageFormat.PNG)
            ?: throw IllegalStateException("Failed to encode image to PNG")
        
        val file = File(directory, filename)
        file.parentFile?.mkdirs()
        file.writeBytes(pngData.bytes)
        
        println("Screenshot saved: ${file.absolutePath}")
        return file.absolutePath
    }
    
    /**
     * 获取截图保存目录
     */
    fun getScreenshotDir(): File {
        return File(DEFAULT_SCREENSHOT_DIR).also {
            if (!it.exists()) it.mkdirs()
        }
    }
}
