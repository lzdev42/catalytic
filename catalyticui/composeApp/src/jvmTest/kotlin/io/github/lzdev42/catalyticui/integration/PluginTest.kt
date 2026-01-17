package io.github.lzdev42.catalyticui.integration

import com.catalytic.grpc.*
import io.github.lzdev42.catalyticui.data.grpc.GrpcClientManager
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 插件系统集成测试
 */
class PluginTest {
    
    private val testHost = "127.0.0.1"
    private val testPort = 5000
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * 测试 ListPlugins 返回 SerialPlugin
     */
    @Test
    fun testListPluginsReturnsSerialPlugin() {
        runBlocking {
            println("🔌 Testing ListPlugins...")
            val clientManager = GrpcClientManager(testScope)
            
            try {
                val result = clientManager.connect(testHost, testPort)
                assertTrue(result.isSuccess, "Connection should succeed")
                val client = result.getOrThrow()
                
                val plugins = client.ListPlugins().execute(Empty())
                
                println("   Found ${plugins.items.size} plugins:")
                plugins.items.forEach { p ->
                    println("   - ${p.id}: ${p.name} v${p.version} (protocols: ${p.protocols})")
                }
                
                // 验证 SerialPlugin 存在
                val serialPlugin = plugins.items.find { it.id == "catalytic.serial" }
                assertTrue(serialPlugin != null, "SerialPlugin should be loaded")
                assertTrue(serialPlugin.protocols.contains("serial"), "SerialPlugin should support 'serial' protocol")
                
                println("✅ ListPlugins test passed")
                
            } finally {
                clientManager.disconnect()
            }
        }
    }
}
