package io.github.lzdev42.catalyticui.integration

import com.catalytic.grpc.*
import io.github.lzdev42.catalyticui.data.grpc.GrpcClientManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * 事件订阅集成测试
 * 验证 Subscribe 流能正确接收状态变更事件
 */
class EventSubscriptionTest {
    
    private val testHost = "127.0.0.1"
    private val testPort = 5000
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /**
     * 测试订阅后能收到初始状态事件
     * Subscribe 会在首次轮询时推送当前状态
     */
    @Test
    fun testSubscribeReceivesInitialStatus() {
        runBlocking {
            println("📡 Testing Subscribe receives initial status...")
            val clientManager = GrpcClientManager(testScope)
            
            try {
                withTimeout(15000) {
                    val result = clientManager.connect(testHost, testPort)
                    assertTrue(result.isSuccess, "Connection should succeed")
                    val client = result.getOrThrow()
                    
                    // 订阅事件 - Wire 使用 GrpcStreamingCall
                    val streamingCall = client.Subscribe()
                    val (sendChannel, receiveChannel) = streamingCall.executeIn(testScope)
                    
                    // 发送订阅请求
                    val request = SubscribeRequest(topics = listOf("slot_update"))
                    sendChannel.send(request)
                    sendChannel.close()  // 必须关闭以通知服务器请求完成
                    
                    println("   Subscribed, waiting for first event...")
                    
                    // 等待第一个事件 (最多5秒)
                    val firstEvent = withTimeout(5000) {
                        receiveChannel.receive()
                    }
                    
                    println("   Received event: type=${firstEvent.type}")
                    assertNotNull(firstEvent, "Should receive at least one event")
                    assertTrue(firstEvent.type == "slot_update", "Event type should be slot_update")
                    
                    val slotUpdate = firstEvent.slot_update
                    assertNotNull(slotUpdate, "SlotUpdate payload should exist")
                    println("   slot_id: ${slotUpdate.slot_id}")
                    println("   status: ${slotUpdate.status?.status}")
                    println("   total_steps: ${slotUpdate.status?.total_steps}")
                    
                    // 关闭订阅
                    sendChannel.close()
                    
                    println("✅ Subscribe receives initial status - PASSED")
                }
            } catch (e: Exception) {
                println("❌ Test failed: ${e.message}")
                throw e
            } finally {
                clientManager.disconnect()
            }
        }
    }
    
    /**
     * 测试状态变更时收到事件
     */
    @Test
    fun testSubscribeReceivesStatusChange() {
        runBlocking {
            println("📡 Testing Subscribe receives status change...")
            val clientManager = GrpcClientManager(testScope)
            
            try {
                withTimeout(15000) {
                    val result = clientManager.connect(testHost, testPort)
                    val client = result.getOrThrow()
                    
                    // 先停止测试确保干净状态
                    client.StopTest().execute(SlotId(id = 0))
                    delay(500)
                    
                    // 添加一个测试步骤
                    val stepId = (System.currentTimeMillis() % 100000).toInt()
                    val stepJson = """{"step_id":$stepId,"step_name":"Test Step","execution_mode":"engine_controlled","skip":false,"engine_task":{"target_device":"dmm","action_type":"query","payload":"*IDN?","timeout_ms":5000},"check_type":"none"}"""
                    client.AddTestStep().execute(TestStepPayload(json_content = stepJson))
                    println("   Added test step: $stepId")
                    
                    // 订阅事件
                    val streamingCall = client.Subscribe()
                    val (sendChannel, receiveChannel) = streamingCall.executeIn(testScope)
                    sendChannel.send(SubscribeRequest(topics = listOf("slot_update")))
                    sendChannel.close()  // 必须关闭以通知服务器请求完成
                    
                    // 收集事件
                    val events = mutableListOf<Event>()
                    val collectJob = launch {
                        repeat(5) {
                            try {
                                val event = withTimeout(2000) { receiveChannel.receive() }
                                events.add(event)
                            } catch (e: Exception) {
                                // 超时或取消
                            }
                        }
                    }
                    
                    // 等待初始事件
                    delay(1000)
                    
                    // 触发状态变更
                    println("   Starting test...")
                    client.StartTest().execute(StartTestRequest(slot_id = 0, loop = false))
                    
                    // 等待事件收集
                    delay(2000)
                    collectJob.cancel()
                    sendChannel.close()
                    
                    println("   Collected ${events.size} events")
                    events.forEachIndexed { idx, e ->
                        println("   Event[$idx]: type=${e.type}, status=${e.slot_update?.status?.status}")
                    }
                    
                    assertTrue(events.isNotEmpty(), "Should receive events")
                    
                    // 清理
                    client.StopTest().execute(SlotId(id = 0))
                    
                    println("✅ Subscribe receives status change - PASSED")
                }
            } catch (e: Exception) {
                println("❌ Test failed: ${e.message}")
                throw e
            } finally {
                clientManager.disconnect()
            }
        }
    }
}
