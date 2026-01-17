package io.github.lzdev42.catalyticui

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform