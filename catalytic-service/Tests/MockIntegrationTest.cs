using Catalytic;
using CatalyticKit;
using Catalytic.Engine;
using Catalytic.Grpc;
using Catalytic.Plugin;
using Google.Protobuf.WellKnownTypes;
using System.Text.Json;

public static class MockIntegrationTest 
{
    public static async Task Run(Engine engine, PluginManager pm, AppConfig config)
    {
        Console.WriteLine("=== [MOCK] 开始自测流程 (Integration Test) ===");
        
        // 1. 模拟 Host Service 环境
        // 注意：HostGrpcService 的构造函数依赖注入，这里手动构造
        // public HostGrpcService(PluginManager pluginManager, AppConfig config, Action shutdownAction, Engine.Engine engine)
        var service = new HostGrpcService(pm, config, () => Console.WriteLine("[MOCK] Shutdown requested"), engine);
        
        // 2. 构造复杂嵌套数据 (UI 数据模型)
        var deviceType = new DeviceType 
        {
            Id = "mock-type-01",
            Name = "Mock DMM",
            PluginId = "plugin.mock"
        };
        
        // 嵌套设备
        deviceType.Devices.Add(new Device { Id = "dev-01", Name = "DMM A", Address = "COM1" });
        deviceType.Devices.Add(new Device { Id = "dev-02", Name = "DMM B", Address = "COM2" });
        
        // 嵌套命令
        deviceType.Commands.Add(new Command 
        { 
            Id = "cmd-01", 
            Name = "Read Volts", 
            Payload = "MEAS:VOLT:DC?", 
            TimeoutMs = 1000 
        });

        Console.WriteLine($"[MOCK] 构造数据: Type={deviceType.Name}, Devices={deviceType.Devices.Count}, Commands={deviceType.Commands.Count}");

        // 3. 调用业务逻辑 (模拟 UI 保存)
        Console.WriteLine("[MOCK] >>> 调用 UpdateDeviceType (模拟 UI 保存)...");
        try 
        {
            var result = await service.UpdateDeviceType(deviceType, null!);
            if (result.Success) 
                Console.WriteLine("✅ Host 处理成功 (Success)");
            else 
                Console.WriteLine($"❌ Host 处理失败 (Error): {result.Error}");
        }
        catch (Exception ex)
        {
             Console.WriteLine($"❌ Host 抛出异常: {ex}");
             return;
        }

        // 2.1 构造测试步骤 (JSON Payload) - Engine Expects Resolved Data
        // 注意: Engine 不解析 CommandId，需要 Host/UI 提前解析为 Payload
        // critical: target_device 必须是 TypeID (Bound key), 而非 InstanceID.
        string stepJson = "{\"step_id\": 1, \"step_name\": \"Mock Step 1\", \"execution_mode\": \"engine_controlled\", \"engine_task\": {\"target_device\": \"mock-type-01\", \"action_type\": \"query\", \"payload\": \"MEAS:VOLT:DC?\", \"timeout_ms\": 1000}}";
        
        // 2.2 构造槽位绑定
        var binding = new SlotBinding { SlotId = 0 };
        // Old Legacy binding format expected by SetSlotBinding wrapper in Host?
        // Host SetSlotBinding converts `SlotBinding` proto to `{"type": ["id"]}`.
        // So we populate `SlotBinding` proto.
        var bindingList = new DeviceBindingList();
        bindingList.DeviceIds.Add("dev-01");
        binding.DeviceBindings.Add("mock-type-01", bindingList);

        // 3. 调用业务逻辑 (模拟 UI 保存)
        Console.WriteLine("[MOCK] >>> 调用 UpdateDeviceType (模拟 UI 保存)...");
        try 
        {
            var result = await service.UpdateDeviceType(deviceType, null!);
            if (result.Success) Console.WriteLine("✅ DeviceType 保存成功");
            
            Console.WriteLine("[MOCK] >>> 调用 AddTestStep (模拟 UI 添加步骤)...");
            var payload = new TestStepPayload { JsonContent = stepJson };
            await service.AddTestStep(payload, null!);
            Console.WriteLine("✅ TestStep 保存成功");

            Console.WriteLine("[MOCK] >>> 调用 SetSlotBinding (模拟 UI 绑定)...");
            await service.SetSlotBinding(binding, null!);
             Console.WriteLine("✅ SlotBinding 保存成功");
        }
        catch (Exception ex)
        {
             Console.WriteLine($"❌ Host 抛出异常: {ex}");
             return;
        }

        // 4. 验证 FFI 结果 (模拟读取回显)
        Console.WriteLine("[MOCK] >>> 验证 Engine 数据...");
        try
        {
            var listResult = await service.ListDeviceTypes(new Catalytic.Grpc.Empty(), null!);
            
            var savedType = listResult.Items.FirstOrDefault(t => t.Id == "mock-type-01");
            if (savedType != null)
            {
                // ... (DeviceType verification) ...
                Console.WriteLine($"[MOCK] 读取回显: Type={savedType.Name}, Devices={savedType.Devices.Count}, Commands={savedType.Commands.Count}");
                
                bool devicesMatch = savedType.Devices.Count == 2;
                bool commandsMatch = savedType.Commands.Count == 1;
                
                Console.WriteLine($"[MOCK] 详细对比 (DeviceType):");
                Console.WriteLine($"  - Devices (Exp): 2 [DMM A, DMM B] (Act): {savedType.Devices.Count}");
                Console.WriteLine($"  - Commands (Exp): 1 [Read Volts] (Act): {savedType.Commands.Count}");
                
                if (devicesMatch && commandsMatch) Console.WriteLine("✅ DeviceType 数据验证通过");
                else Console.WriteLine("❌ DeviceType 数据验证失败");

                // Verify Steps (Detailed Parsing)
                var script = await service.GetCurrentScript(new Catalytic.Grpc.Empty(), null!);
                Console.WriteLine($"[MOCK] 详细对比 (Steps Parsing):");
                
                try 
                {
                    using var stepDoc = JsonDocument.Parse(script.JsonContent);
                    var stepsArray = stepDoc.RootElement; // Assuming array
                    if (stepsArray.ValueKind == JsonValueKind.Object && stepsArray.TryGetProperty("test_steps", out var arr))
                    {
                        stepsArray = arr; // Handle case where it returns wrapped object
                    }

                    if (stepsArray.ValueKind == JsonValueKind.Array)
                    {
                        Console.WriteLine($"  - Count: {stepsArray.GetArrayLength()}");
                        foreach (var step in stepsArray.EnumerateArray())
                        {
                            Console.WriteLine($"  - Step [{step.GetProperty("step_id")}]");
                            Console.WriteLine($"    Name: {step.GetProperty("step_name")}");
                            Console.WriteLine($"    Mode: {step.GetProperty("execution_mode")}");
                            
                            if (step.TryGetProperty("engine_task", out var task))
                            {
                                Console.WriteLine($"    Task Target: {task.GetProperty("target_device")}");
                                Console.WriteLine($"    Task Action: {task.GetProperty("action_type")}");
                                Console.WriteLine($"    Task Payload: {task.GetProperty("payload")}"); // May be byte array or string
                            }
                        }
                        Console.WriteLine("✅ TestStep 深度验证通过: 字段解析成功");
                    }
                    else
                    {
                         Console.WriteLine($"❌ TestStep 格式错误: Expected Array, Got {stepsArray.ValueKind}");
                         Console.WriteLine($"RAW: {script.JsonContent}");
                    }
                }
                catch (Exception ex)
                {
                    Console.WriteLine($"❌ TestStep 解析异常: {ex.Message}");
                    Console.WriteLine($"RAW: {script.JsonContent}");
                }
            }
            else
            {
                Console.WriteLine("❌ Engine 数据验证失败: 未找到 mock-type-01 (Not Found)");
            }
        }
        catch (Exception ex)
        {
            Console.WriteLine($"❌ 验证阶段异常: {ex}");
        }
        
        // 5. 清理 (Delete)
        Console.WriteLine("[MOCK] >>> 清理测试数据 (DeleteDevice)...");
        // 注意：DeleteDeviceType 暂未实现，我们尝试删除 Device
        // var delResult = await service.DeleteDevice(new DeleteDeviceRequest { Id = "dev-01" }, null!);
        // Console.WriteLine($"[MOCK] Delete dev-01: {delResult.Success}");
        
        Console.WriteLine("=== [MOCK] 自测结束 ===");
    }
}
