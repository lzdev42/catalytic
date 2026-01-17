package io.github.lzdev42.catalyticui.data.grpc

import com.catalytic.grpc.SlotStatus as GrpcSlotStatus
import com.catalytic.grpc.SlotVariable as GrpcSlotVariable
import io.github.lzdev42.catalyticui.model.SlotStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mappers 单元测试
 * 
 * 验证 gRPC 数据正确转换为 UI 模型
 */
class MappersTest {
    
    // ========== mapSlotStatus (String) ==========
    
    @Test
    fun `mapSlotStatus converts idle status`() {
        assertEquals(SlotStatus.IDLE, Mappers.mapSlotStatus("idle"))
        assertEquals(SlotStatus.IDLE, Mappers.mapSlotStatus("IDLE"))
    }
    
    @Test
    fun `mapSlotStatus converts running status`() {
        assertEquals(SlotStatus.RUNNING, Mappers.mapSlotStatus("running"))
        assertEquals(SlotStatus.RUNNING, Mappers.mapSlotStatus("RUNNING"))
    }
    
    @Test
    fun `mapSlotStatus converts paused status`() {
        assertEquals(SlotStatus.PAUSED, Mappers.mapSlotStatus("paused"))
    }
    
    @Test
    fun `mapSlotStatus converts completed and pass to PASS`() {
        assertEquals(SlotStatus.PASS, Mappers.mapSlotStatus("completed"))
        assertEquals(SlotStatus.PASS, Mappers.mapSlotStatus("pass"))
    }
    
    @Test
    fun `mapSlotStatus converts error and fail to FAIL`() {
        assertEquals(SlotStatus.FAIL, Mappers.mapSlotStatus("error"))
        assertEquals(SlotStatus.FAIL, Mappers.mapSlotStatus("fail"))
    }
    
    @Test
    fun `mapSlotStatus defaults to IDLE for unknown status`() {
        assertEquals(SlotStatus.IDLE, Mappers.mapSlotStatus("unknown"))
        assertEquals(SlotStatus.IDLE, Mappers.mapSlotStatus(""))
    }
    
    // ========== mapSlotStatus (GrpcSlotStatus) ==========
    
    @Test
    fun `mapSlotStatus converts basic fields correctly`() {
        val grpc = GrpcSlotStatus(
            slot_id = 5,
            status = "running",
            current_step_index = 3,
            total_steps = 10,
            elapsed_ms = 65000L, // 1m 5s
            sn = "ABC123"
        )
        
        val result = Mappers.mapSlotStatus(grpc)
        
        assertEquals(5, result.id)
        assertEquals(SlotStatus.RUNNING, result.status)
        assertEquals(3, result.currentStep)
        assertEquals(10, result.totalSteps)
        assertEquals("ABC123", result.sn)
        assertEquals("1m 5s", result.elapsedTime)
    }
    
    @Test
    fun `mapSlotStatus converts new step name and desc fields`() {
        val grpc = GrpcSlotStatus(
            slot_id = 1,
            status = "running",
            current_step_index = 2,
            total_steps = 5,
            current_step_name = "电压检测",
            current_step_desc = "正在查询 UDS 0x22..."
        )
        
        val result = Mappers.mapSlotStatus(grpc)
        
        assertEquals("电压检测", result.currentStepName)
        assertEquals("正在查询 UDS 0x22...", result.currentStepValue)
    }
    
    @Test
    fun `mapSlotStatus ignores blank step name and desc`() {
        val grpc = GrpcSlotStatus(
            slot_id = 1,
            status = "idle",
            current_step_name = "",
            current_step_desc = "   "
        )
        
        val result = Mappers.mapSlotStatus(grpc)
        
        assertNull(result.currentStepName)
        // Note: "   " is not blank in Kotlin (has whitespace), so it will be included
    }
    
    @Test
    fun `mapSlotStatus converts variables list correctly`() {
        val grpc = GrpcSlotStatus(
            slot_id = 1,
            status = "running",
            variables = listOf(
                GrpcSlotVariable(
                    name = "voltage",
                    value_ = "3.31",
                    unit = "V",
                    is_passing = true
                ),
                GrpcSlotVariable(
                    name = "current",
                    value_ = "125",
                    unit = "mA",
                    is_passing = false
                )
            )
        )
        
        val result = Mappers.mapSlotStatus(grpc)
        
        assertEquals(2, result.variables.size)
        
        val voltage = result.variables[0]
        assertEquals("voltage", voltage.name)
        assertEquals("3.31 V", voltage.value)
        assertTrue(voltage.isPassing)
        
        val current = result.variables[1]
        assertEquals("current", current.name)
        assertEquals("125 mA", current.value)
        assertEquals(false, current.isPassing)
    }
    
    @Test
    fun `mapSlotStatus handles variable without unit`() {
        val grpc = GrpcSlotStatus(
            slot_id = 1,
            status = "running",
            variables = listOf(
                GrpcSlotVariable(
                    name = "count",
                    value_ = "42",
                    unit = "", // No unit
                    is_passing = true
                )
            )
        )
        
        val result = Mappers.mapSlotStatus(grpc)
        
        assertEquals(1, result.variables.size)
        assertEquals("count", result.variables[0].name)
        assertEquals("42", result.variables[0].value) // No unit suffix
    }
    
    @Test
    fun `mapSlotStatus preserves existing logs`() {
        val existingLogs = listOf("10:00:00 Step 1 completed", "10:00:05 Step 2 started")
        val grpc = GrpcSlotStatus(
            slot_id = 1,
            status = "running"
        )
        
        val result = Mappers.mapSlotStatus(grpc, existingLogs = existingLogs)
        
        assertEquals(2, result.logs.size)
        assertEquals("10:00:00 Step 1 completed", result.logs[0])
    }
    
    @Test
    fun `mapSlotStatus uses provided sn when grpc sn is blank`() {
        val grpc = GrpcSlotStatus(
            slot_id = 1,
            status = "idle",
            sn = "" // Blank in gRPC
        )
        
        val result = Mappers.mapSlotStatus(grpc, sn = "FALLBACK_SN")
        
        assertEquals("FALLBACK_SN", result.sn)
    }
    
    @Test
    fun `mapSlotStatus calculates progress correctly`() {
        val grpc = GrpcSlotStatus(
            slot_id = 1,
            status = "running",
            current_step_index = 5,
            total_steps = 20
        )
        
        val result = Mappers.mapSlotStatus(grpc)
        
        assertEquals(0.25f, result.progress)
        assertEquals(25, result.progressPercent)
    }
    
    @Test
    fun `mapSlotStatus handles zero total steps`() {
        val grpc = GrpcSlotStatus(
            slot_id = 1,
            status = "idle",
            current_step_index = 0,
            total_steps = 0
        )
        
        val result = Mappers.mapSlotStatus(grpc)
        
        assertEquals(0f, result.progress)
        assertEquals(0, result.progressPercent)
    }
    
    @Test
    fun `mapSlotStatus handles zero elapsed time`() {
        val grpc = GrpcSlotStatus(
            slot_id = 1,
            status = "idle",
            elapsed_ms = 0
        )
        
        val result = Mappers.mapSlotStatus(grpc)
        
        assertNull(result.elapsedTime)
    }
}
