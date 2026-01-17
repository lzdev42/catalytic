package io.github.lzdev42.catalyticui.integration

import com.catalytic.grpc.Empty
import io.github.lzdev42.catalyticui.data.grpc.GrpcClientManager
import io.github.lzdev42.catalyticui.data.grpc.GrpcRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Layer 1: gRPC 连接测试
 * 
 * 前置条件：Host 应用需运行在 localhost:50051
 * 
 * 运行命令：./gradlew jvmTest
 */
class GrpcConnectionTest {
    
    private val testHost = "127.0.0.1"
    private val testPort = 5001  // Matches running Host port (5000 occupied by ControlCenter)
    private val testScope = CoroutineScope(Dispatchers.IO)
    

    /**
     * 测试能否连接到 Host gRPC 服务
     */
    @Test
    fun testHostConnection() {
        runBlocking {
            println("🔗 Testing gRPC connection to $testHost:$testPort...")
            
            val clientManager = GrpcClientManager(testScope)
            
            try {
                val result = clientManager.connect(testHost, testPort)
                
                if (result.isFailure) {
                    println("❌ Connect returned failure: ${result.exceptionOrNull()}")
                    result.exceptionOrNull()?.printStackTrace()
                }
                
                assertTrue(result.isSuccess, "Connection should succeed: ${result.exceptionOrNull()?.message}")
                println("✅ Connected successfully!")
                
                val client = result.getOrNull()
                assertNotNull(client, "Client should not be null")
                
            } catch (e: Exception) {
                println("❌ Connection failed: ${e.message}")
                println("⚠️  Make sure Host is running on $testHost:$testPort")
                throw e
            } finally {
                clientManager.disconnect()
            }
        }
    }
    
    /**
     * 测试获取系统信息
     */
    @Test
    fun testGetSystemInfo() {
        runBlocking {
            println("📋 Testing GetSystemInfo RPC...")
            
            val clientManager = GrpcClientManager(testScope)
            
            try {
                val result = clientManager.connect(testHost, testPort)
                assertTrue(result.isSuccess, "Connection should succeed")
                
                val client = result.getOrThrow()
                val systemInfo = client.GetSystemInfo().execute(Empty())
                
                assertNotNull(systemInfo, "System info should not be null")
                assertNotNull(systemInfo.version, "Version should not be null")
                
                println("✅ GetSystemInfo successful")
                println("   Version: ${systemInfo.version}")
                println("   Slot Count: ${systemInfo.slot_count}")
                println("   Engine Loaded: ${systemInfo.engine_loaded}")
                println("   Registered Protocols: ${systemInfo.registered_protocols.joinToString(", ")}")
                
            } catch (e: Exception) {
                println("❌ GetSystemInfo failed: ${e.message}")
                throw e
            } finally {
                clientManager.disconnect()
            }
        }
    }
    
    /**
     * 测试列出槽位
     */
    @Test
    fun testListSlots() {
        runBlocking {
            println("🎰 Testing ListSlots RPC...")
            
            val clientManager = GrpcClientManager(testScope)
            
            try {
                val result = clientManager.connect(testHost, testPort)
                assertTrue(result.isSuccess, "Connection should succeed")
                
                val client = result.getOrThrow()
                val slotsResponse = client.ListSlots().execute(Empty())
                
                assertNotNull(slotsResponse, "Slots response should not be null")
                println("✅ ListSlots successful")
                println("   Found ${slotsResponse.items.size} slots")
            
                slotsResponse.items.forEach { slot ->
                    println("   - Slot ${slot.slot_id}: bound")
                }
                
            } catch (e: Exception) {
                println("❌ ListSlots failed: ${e.message}")
                throw e
            } finally {
                clientManager.disconnect()
            }
        }
    }
    
    /**
     * 测试获取设备类型
     */
    @Test
    fun testGetDeviceTypes() {
        runBlocking {
            println("📱 Testing ListDeviceTypes RPC...")
            
            val clientManager = GrpcClientManager(testScope)
            
            try {
                val result = clientManager.connect(testHost, testPort)
                assertTrue(result.isSuccess, "Connection should succeed")
                
                val client = result.getOrThrow()
                val typesResponse = client.ListDeviceTypes().execute(Empty())
                
                assertNotNull(typesResponse, "Device types response should not be null")
                println("✅ ListDeviceTypes successful")
                println("   Found ${typesResponse.items.size} device types")
                
                typesResponse.items.forEach { type ->
                    println("   - ${type.name} (ID: ${type.id}, Transport: ${type.transport})")
                }
                
            } catch (e: Exception) {
                println("❌ ListDeviceTypes failed: ${e.message}")
                throw e
            } finally {
                clientManager.disconnect()
            }
        }
    }
    
    /**
     * 测试通过 GrpcRepository 获取设备类型（集成测试）
     */
    @Test
    fun testRepositoryGetDeviceTypes() {
        runBlocking {
            println("🏗️  Testing GrpcRepository.getDeviceTypes()...")
            
            val clientManager = GrpcClientManager(testScope)
            val repository = GrpcRepository(clientManager, testScope)
            
            try {
                val result = clientManager.connect(testHost, testPort)
                assertTrue(result.isSuccess, "Connection should succeed")
                
                val deviceTypes = repository.getDeviceTypes()
                
                assertNotNull(deviceTypes, "Device types should not be null")
                println("✅ Repository.getDeviceTypes successful")
                println("   Found ${deviceTypes.size} device types")
                
                deviceTypes.forEach { type ->
                    println("   - ${type.name} (${type.devices.size} devices)")
                    type.devices.forEach { device ->
                        println("       └─ ${device.name} @ ${device.address}")
                    }
                }
                
            } catch (e: Exception) {
                println("❌ Repository.getDeviceTypes failed: ${e.message}")
                throw e
            } finally {
                clientManager.disconnect()
            }
        }
    }
}
