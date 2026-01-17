package io.github.lzdev42.catalyticui.host

import org.junit.Test
import kotlin.test.assertTrue

class HostLauncherTest {
    @Test
    fun testPathsAndLogs() {
        println("=== START HostLauncherTest ===")
        
        // 1. Check if running
        val isRunning = HostLauncher.isPortInUse(5000)
        println("isPortInUse(5000): $isRunning")
        
        // 2. Force find path to exercise logging
        val path = HostLauncher.findHostPath()
        println("findHostPath result: $path")
        
        println("=== END HostLauncherTest ===")
    }
}
