package io.github.lzdev42.catalyticui.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

actual fun getCurrentTimeString(): String {
    return LocalTime.now().format(formatter)
}
