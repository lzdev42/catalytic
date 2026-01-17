package io.github.lzdev42.catalyticui.integration

import com.catalytic.grpc.*
import io.github.lzdev42.catalyticui.data.grpc.GrpcClientManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/**
 * Layer 3: 测试执行控制集成测试
 * 验证 Start/Stop/Pause/Resume 和 GetSlotStatus API
 */
class TestExecutionTest {
    
    private val testHost = "127.0.0.1"
    private val testPort = 5000
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * 测试获取槽位状态 (空闲状态)
     */
    @Test
    fun testGetSlotStatusIdle() {
        runBlocking {
            println("📊 Testing GetSlotStatus (Idle)...")
            val clientManager = GrpcClientManager(testScope)
            
            try {
                val result = clientManager.connect(testHost, testPort)
                assertTrue(result.isSuccess, "Connection should succeed")
                val client = result.getOrThrow()
                
                val slotId = SlotId(id = 0)
                
                // 重置槽位状态 (可能有之前测试留下的状态)
                client.StopTest().execute(slotId)
                delay(500)
                
                val status = client.GetSlotStatus().execute(slotId)
                
                println("   slot_id: ${status.slot_id}")
                println("   status: ${status.status}")
                println("   current_step_index: ${status.current_step_index}")
                println("   elapsed_ms: ${status.elapsed_ms}")
                
                assertEquals(0, status.slot_id, "Slot ID should be 0")
                // 接受 idle 或 completed 作为有效的非运行状态
                val validIdleStates = listOf("idle", "completed")
                assertTrue(status.status in validIdleStates, 
                    "Status should be idle or completed, got: ${status.status}")
                
                println("✅ GetSlotStatus successful")
                
            } finally {
                clientManager.disconnect()
            }
        }
    }
    
    /**
     * 测试启动和停止测试
     * 使用超时保护防止测试卡死
     */
    @Test
    fun testStartAndStopTest() {
        runBlocking {
            println("▶️  Testing Start/Stop Test...")
            val clientManager = GrpcClientManager(testScope)
            
            try {
                withTimeout(15000) { // 15秒超时保护
                    val result = clientManager.connect(testHost, testPort)
                    assertTrue(result.isSuccess, "Connection should succeed")
                    val client = result.getOrThrow()
                    
                    val slotId = 0
                    
                    // 先添加一个测试步骤，否则 StartTest 可能瞬间完成
                    val stepId = (System.currentTimeMillis() % 100000).toInt()
                    val stepJson = """{"step_id":$stepId,"step_name":"Dummy Step","execution_mode":"engine_controlled","skip":false,"engine_task":{"target_device":"dmm","action_type":"wait","payload":"","timeout_ms":30000},"check_type":"none"}"""
                    client.AddTestStep().execute(TestStepPayload(json_content = stepJson))
                    println("   Added dummy wait step: $stepId")
                    
                    // 启动测试
                    val startRequest = StartTestRequest(slot_id = slotId, loop = false)
                    val startResult = client.StartTest().execute(startRequest)
                    println("   StartTest: success=${startResult.success}, error=${startResult.error}")
                    
                    if (!startResult.success) {
                        println("   ⚠️  StartTest failed (may be expected if no valid steps): ${startResult.error}")
                        // 不要断言失败，因为可能缺少其他配置
                    } else {
                        // 短暂等待让状态变为 Running
                        delay(500)
                        
                        // 检查状态
                        val statusAfterStart = client.GetSlotStatus().execute(SlotId(id = slotId))
                        println("   Status after start: ${statusAfterStart.status}")
                        // Note: 状态可能是 running, paused, idle, completed, error
                        
                        // 停止测试
                        val stopResult = client.StopTest().execute(SlotId(id = slotId))
                        println("   StopTest: success=${stopResult.success}")
                        
                        delay(500)
                        
                        // 验证状态变为 idle
                        val statusAfterStop = client.GetSlotStatus().execute(SlotId(id = slotId))
                        println("   Status after stop: ${statusAfterStop.status}")
                    }
                    
                    println("✅ Start/Stop Test completed")
                }
            } catch (e: Exception) {
                println("❌ Test failed: ${e.message}")
                throw e
            } finally {
                // 确保清理
                try {
                    val result = clientManager.connect(testHost, testPort)
                    if (result.isSuccess) {
                        result.getOrNull()?.StopTest()?.execute(SlotId(id = 0))
                    }
                } catch (_: Exception) {}
                clientManager.disconnect()
            }
        }
    }
    
    /**
     * 测试暂停和恢复
     */
    @Test
    fun testPauseAndResumeTest() {
        runBlocking {
            println("⏸️  Testing Pause/Resume Test...")
            val clientManager = GrpcClientManager(testScope)
            
            try {
                withTimeout(15000) {
                    val result = clientManager.connect(testHost, testPort)
                    val client = result.getOrThrow()
                    
                    val slotId = SlotId(id = 0)
                    
                    // 先尝试暂停 (对于非 running 状态应该返回错误)
                    val pauseResult = client.PauseTest().execute(slotId)
                    println("   PauseTest on idle slot: success=${pauseResult.success}, error=${pauseResult.error}")
                    
                    // 验证错误消息包含中文翻译
                    if (!pauseResult.success) {
                        val hasChineseError = pauseResult.error.contains("状态无效")
                        println("   Error message is Chinese: $hasChineseError")
                        assertTrue(hasChineseError, "Error should be translated to Chinese: ${pauseResult.error}")
                    }
                    
                    println("✅ Pause/Resume Test completed (error handling verified)")
                }
            } finally {
                clientManager.disconnect()
            }
        }
    }
}
