package io.github.lzdev42.catalyticui.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun getCurrentTimeString(): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "HH:mm:ss"
    return formatter.stringFromDate(NSDate())
}
