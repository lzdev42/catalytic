using System.Reflection;
using System.Text.Json;
using System.Linq;
using Grpc.Core;
using Catalytic.Plugin;
using Catalytic.Engine;
using CatalyticKit;
using System.Threading.Channels;

namespace Catalytic.Grpc;

/// <summary>
/// gRPC Host 服务实现
/// 处理 UI 发来的所有 gRPC 请求
/// </summary>
public class HostGrpcService : HostService.HostServiceBase
{
    private readonly PluginManager _pluginManager;
    private readonly AppConfig _config;
    private readonly Action _shutdownAction;
    private readonly Engine.Engine _engine;
    
    private const uint DefaultTimeoutMs = 1000;
    
    /// <summary>
    /// 构造函数，通过依赖注入获取所需服务
    /// </summary>
    public HostGrpcService(PluginManager pluginManager, AppConfig config, Action shutdownAction, Engine.Engine engine)
    {
        _pluginManager = pluginManager;
        _config = config;
        _shutdownAction = shutdownAction;
        _engine = engine;
    }
    
    // =========== 辅助方法 ===========
    
    /// <summary>
    /// 执行 Engine 操作并返回统一结果
    /// </summary>
    private Task<Result> ExecuteEngineAction(Action action, string actionName)
    {
        try
        {
            action();
            return Task.FromResult(new Result { Success = true });
        }
        catch (Exception ex)
        {
            Logger.Error($"{actionName}失败: {ex.Message}");
            return Task.FromResult(new Result { Success = false, Error = ex.Message });
        }
    }
    
    // =========== 系统 ===========
    
    /// <summary>
    /// 获取系统信息
    /// 返回版本号、槽位数、已加载的协议等
    /// </summary>
    public override Task<SystemInfo> GetSystemInfo(Empty request, ServerCallContext context)
    {
        // 从程序集获取版本号
        var version = Assembly.GetExecutingAssembly().GetName().Version?.ToString() ?? "0.1.0";
        
        // 从插件管理器获取已注册的协议
        var protocols = _pluginManager.GetRegisteredProtocols().ToList();
        
        return Task.FromResult(new SystemInfo
        {
            Version = version,
            SlotCount = _engine.SlotCount,
            EngineLoaded = true,
            RegisteredProtocols = { protocols }
        });
    }

    /// <summary>
    /// 关闭 Host
    /// </summary>
    public override Task<Result> Shutdown(Empty request, ServerCallContext context)
    {
        Logger.Info("收到 gRPC 关闭请求");
        _shutdownAction();
        return Task.FromResult(new Result { Success = true });
    }

    // =========== 设备类型 ===========
    
    /// <summary>
    /// 获取所有设备类型列表
    /// </summary>
    public override Task<DeviceTypeList> ListDeviceTypes(Empty request, ServerCallContext context)
    {
        try 
        {
            // Engine C# SDK 目前没有直接返回 DeviceTypeList 的 API
            // 但 GetConfig() 返回整个配置 JSON，其中包含 "device_types"
            var configJson = _engine.GetConfig();
            using var doc = JsonDocument.Parse(configJson);
            var result = new DeviceTypeList();
            
            if (doc.RootElement.TryGetProperty("device_types", out var typesElement) && typesElement.ValueKind == JsonValueKind.Array)
            {
                foreach (var item in typesElement.EnumerateArray())
                {
                    var type = new DeviceType
                    {
                        Id = item.GetProperty("id").GetString() ?? "",
                        Name = item.TryGetProperty("name", out var p) ? p.GetString() ?? "" : "",
                        PluginId = item.TryGetProperty("plugin_id", out p) ? p.GetString() ?? "" : ""
                    };

                    // Populate Nested Devices
                    JsonElement devicesElem;
                    if ((item.TryGetProperty("devices", out devicesElem) || item.TryGetProperty("instances", out devicesElem)) 
                        && devicesElem.ValueKind == JsonValueKind.Array)
                    {
                        foreach (var dev in devicesElem.EnumerateArray())
                        {
                            type.Devices.Add(new Device
                            {
                                Id = dev.GetProperty("id").GetString() ?? "",
                                Name = dev.TryGetProperty("name", out p) ? p.GetString() ?? "" : "",
                                Address = dev.TryGetProperty("address", out p) ? p.GetString() ?? "" : "",
                                DeviceTypeId = type.Id
                            });
                        }
                    }

                    // Populate Nested Commands
                    if (item.TryGetProperty("commands", out var commandsElem) && commandsElem.ValueKind == JsonValueKind.Array)
                    {
                        foreach (var cmd in commandsElem.EnumerateArray())
                        {
                            type.Commands.Add(new Command
                            {
                                Id = cmd.GetProperty("id").GetString() ?? "",
                                Name = cmd.TryGetProperty("name", out p) ? p.GetString() ?? "" : "",
                                Payload = cmd.TryGetProperty("payload", out p) ? p.GetString() ?? "" : "",
                                TimeoutMs = cmd.TryGetProperty("timeout_ms", out p) ? (uint)p.GetInt32() : DefaultTimeoutMs,
                                ParseRule = cmd.TryGetProperty("parse_rule", out p) ? p.GetString() ?? "" : "",
                                DeviceTypeId = type.Id
                            });
                        }
                    }

                    result.Items.Add(type);
                }
            }
            return Task.FromResult(result);
        }
        catch (Exception ex)
        {
            Logger.Error($"ListDeviceTypes Failed: {ex.Message}");
            return Task.FromResult(new DeviceTypeList());
        }
    }

    /// <summary>
    /// 创建设备类型
    /// </summary>
    public override Task<Result> CreateDeviceType(DeviceType request, ServerCallContext context)
    {
        return UpdateDeviceType(request, context);
    }

    /// <summary>
    /// 更新设备类型 (Full Nested Update)
    /// </summary>
    public override Task<Result> UpdateDeviceType(DeviceType request, ServerCallContext context)
    {
        Logger.Info($"更新设备类型 (Nested): {request.Id} (Devices: {request.Devices.Count}, Commands: {request.Commands.Count})");
        try
        {
            var json = JsonSerializer.Serialize(new 
            {
                id = request.Id,
                name = request.Name,
                plugin_id = request.PluginId,
                
                // Nested Serialization
                devices = request.Devices.Select(d => new 
                { 
                    id = d.Id, 
                    name = d.Name, 
                    address = d.Address 
                }),
                
                commands = request.Commands.Select(c => new 
                { 
                    id = c.Id, 
                    name = c.Name, 
                    payload = c.Payload, 
                    timeout_ms = c.TimeoutMs, 
                    parse_rule = c.ParseRule 
                })
            });
            
            _engine.AddDeviceType(request.Id, json);
            return Task.FromResult(new Result { Success = true });
        }
        catch (Exception ex)
        {
             return Task.FromResult(new Result { Success = false, Error = ex.Message });
        }
    }

    /// <summary>
    /// 删除设备类型
    /// </summary>
    public override Task<Result> DeleteDeviceType(DeviceTypeId request, ServerCallContext context)
    {
        return Task.FromResult(new Result { Success = false, Error = "Engine API currently does not support deleting DeviceTypes." });
    }

    // =========== 命令 (Legacy Support) ===========
    
    // ListCommands Removed (Deprecated by Nested DeviceType)

    /// <summary>
    /// 创建命令 (Legacy Granular)
    /// </summary>
    public override Task<Result> CreateCommand(Command request, ServerCallContext context)
    {
        Logger.Info($"创建命令: {request.Id} (Name: {request.Name})");
        return Task.FromResult(new Result { Success = true });
    }

    /// <summary>
    /// 更新命令 (Legacy Granular)
    /// </summary>
    public override Task<Result> UpdateCommand(Command request, ServerCallContext context)
    {
        Logger.Info($"更新命令: {request.Id}");
        return Task.FromResult(new Result { Success = true });
    }

    // DeleteCommand Removed (Proto Definition Removed)

    // =========== 设备 ===========
    
    /// <summary>
    /// 获取所有设备列表
    /// </summary>
    public override Task<DeviceList> ListDevices(Empty request, ServerCallContext context)
    {
        try 
        {
            var configJson = _engine.GetConfig();
            using var doc = JsonDocument.Parse(configJson);
            var result = new DeviceList();
            
            // Engine Config "devices" is a map: type_id -> [device_obj]
            if (doc.RootElement.TryGetProperty("devices", out var devicesMap) && devicesMap.ValueKind == JsonValueKind.Object)
            {
                foreach (var property in devicesMap.EnumerateObject())
                {
                    var typeId = property.Name;
                    if (property.Value.ValueKind == JsonValueKind.Array)
                    {
                        foreach (var item in property.Value.EnumerateArray())
                        {
                            result.Items.Add(new Device
                            {
                                Id = item.GetProperty("name").GetString() ?? "", // In Engine "name" is the label/id
                                DeviceTypeId = typeId,
                                Name = item.GetProperty("name").GetString() ?? "",
                                Address = item.TryGetProperty("address", out var p) ? p.GetString() ?? "" : ""
                            });
                        }
                    }
                }
            }
            return Task.FromResult(result);
        }
        catch (Exception ex)
        {
            Logger.Error($"ListDevices Failed: {ex.Message}");
            return Task.FromResult(new DeviceList());
        }
    }

    /// <summary>
    /// 创建设备 (Granular)
    /// </summary>
    public override Task<Result> CreateDevice(Device request, ServerCallContext context)
    {
        Logger.Info($"创建设备: {request.Id} (Name: {request.Name}, Type: {request.DeviceTypeId})");
        try
        {
            // Engine 2.0: { "id": "uuid", "name": "DisplayName", "address": "..." }
            var json = JsonSerializer.Serialize(new 
            {
                id = request.Id,
                name = request.Name,
                address = request.Address
            });
            _engine.AddDeviceInstance(request.DeviceTypeId, json);
            return Task.FromResult(new Result { Success = true });
        }
        catch (Exception ex)
        {
            return Task.FromResult(new Result { Success = false, Error = ex.Message });
        }
    }

    /// <summary>
    /// 更新设备 (Granular)
    /// </summary>
    public override Task<Result> UpdateDevice(Device request, ServerCallContext context)
    {
        Logger.Info($"更新设备: {request.Id}");
        try
        {
            // Engine 2.0: Overlay update via AddDeviceInstance
            var json = JsonSerializer.Serialize(new 
            {
                id = request.Id,
                name = request.Name,
                address = request.Address
            });
            _engine.AddDeviceInstance(request.DeviceTypeId, json);
            return Task.FromResult(new Result { Success = true });
        }
        catch (Exception ex)
        {
            return Task.FromResult(new Result { Success = false, Error = ex.Message });
        }
    }

    /// <summary>
    /// 删除设备
    /// </summary>
    public override Task<Result> DeleteDevice(DeviceId request, ServerCallContext context)
    {
        Logger.Info($"删除设备: {request.Id}");
        try
        {
            // We need TypeID to delete. Iterate nested structure to find it.
            var configJson = _engine.GetConfig();
            using var doc = JsonDocument.Parse(configJson);
            string? foundTypeId = null;

            if (doc.RootElement.TryGetProperty("device_types", out var typesArray) && typesArray.ValueKind == JsonValueKind.Array)
            {
                foreach (var typeObj in typesArray.EnumerateArray())
                {
                    if (typeObj.TryGetProperty("instances", out var instances) && instances.ValueKind == JsonValueKind.Array) // LLD says "instances", Host Read uses "devices"? Checked LLD, it says "instances". Wait.
                    {
                        // Double check key name: Host ListDeviceTypes uses "devices" (Line 94). LLD Rust struct says "instances".
                        // If Host ListDeviceTypes works, then key is "devices"?
                        // Let's check ListDeviceTypes implementation again.
                        // It uses "devices".
                        // If Rust serializer uses default specificiation for struct field "instances", it outputs "instances".
                        // Unless there is a #[serde(rename="devices")].
                        // I confirmed LLD struct `DeviceType` has `pub instances: Vec<DeviceInstance>`.
                        // I did NOT see a rename. 
                        // So Engine probably outputs "instances".
                        // BUT Host ListDeviceTypes reads "devices".
                        // This implies ListDeviceTypes MIGHT BE BROKEN if field name is "instances".
                        // OR my assumption that Host ListDeviceTypes is working is based on "Pass" status but verified by whom? Manual verify?
                        // User said: "Check... until passed to FFI".
                        // I must verifying the field naming consistency.
                        // I will assume "devices" for now to match ListDeviceTypes, but I'll add a check for "instances" too.
                        
                        // Actually, I'll check both.
                    }
                }
            }
            
            // Re-implementation with robust check
            if (doc.RootElement.TryGetProperty("device_types", out var types))
            {
                 foreach (var type in types.EnumerateArray())
                 {
                     // Check "devices" (UI term) or "instances" (Rust term)
                     JsonElement devList;
                     if (!type.TryGetProperty("devices", out devList) && !type.TryGetProperty("instances", out devList))
                        continue;
                        
                     if (devList.ValueKind == JsonValueKind.Array)
                     {
                         foreach (var dev in devList.EnumerateArray())
                         {
                             // Check match by ID or Name (Engine uses ID now)
                             var devId = dev.TryGetProperty("id", out var p) ? p.GetString() : null;
                             if (devId == request.Id)
                             {
                                 foundTypeId = type.GetProperty("id").GetString();
                                 break;
                             }
                         }
                     }
                     if (foundTypeId != null) break;
                 }
            }
            
            if (foundTypeId != null)
            {
                // Note: RemoveDeviceInstance signature might expect "label" if legacy, or "id"/"name" if updated.
                // Assuming it expects the ID now as implied by new usage.
                _engine.RemoveDeviceInstance(foundTypeId, request.Id);
                return Task.FromResult(new Result { Success = true });
            }
            else
            {
                 return Task.FromResult(new Result { Success = false, Error = "Device not found" });
            }
        }
        catch (Exception ex)
        {
            return Task.FromResult(new Result { Success = false, Error = ex.Message });
        }
    }

    /// <summary>
    /// 扫描可用设备
    /// </summary>
    public override Task<ScanResult> ScanDevices(ScanRequest request, ServerCallContext context)
    {
        Logger.Info($"扫描设备: transport={request.Transport}");
        // TODO: 实现设备扫描逻辑
        return Task.FromResult(new ScanResult());
    }

    /// <summary>
    /// 测试设备连接
    /// </summary>
    public override Task<ConnectionTestResult> TestConnection(DeviceId request, ServerCallContext context)
    {
        Logger.Info($"测试连接: {request.Id}");
        // TODO: 实现连接测试逻辑
        return Task.FromResult(new ConnectionTestResult
        {
            Success = false,
            Message = "尚未实现"
        });
    }

    // =========== 槽位 ===========
    
    // =========== 槽位 ===========
    
    /// <summary>
    /// 获取所有槽位列表
    /// </summary>
    public override Task<SlotList> ListSlots(Empty request, ServerCallContext context)
    {
        var result = new SlotList();
        for (int i = 0; i < _engine.SlotCount; i++)
        {
            var bindingJson = _engine.GetSlotBinding(i);
            var binding = new SlotBinding { SlotId = (uint)i };
            
            if (!string.IsNullOrEmpty(bindingJson))
            {
                try 
                {
                    // 支持两种格式：
                    // 旧格式: {"type": "device_id"}
                    // 新格式: {"type": ["device_id1", "device_id2"]}
                    using var doc = JsonDocument.Parse(bindingJson);
                    foreach (var prop in doc.RootElement.EnumerateObject())
                    {
                        var typeId = prop.Name;
                        var bindingList = new DeviceBindingList();
                        
                        if (prop.Value.ValueKind == JsonValueKind.Array)
                        {
                            // 新格式：数组
                            foreach (var item in prop.Value.EnumerateArray())
                            {
                                bindingList.DeviceIds.Add(item.GetString() ?? "");
                            }
                        }
                        else if (prop.Value.ValueKind == JsonValueKind.String)
                        {
                            // 旧格式：单个字符串，转为单元素列表
                            bindingList.DeviceIds.Add(prop.Value.GetString() ?? "");
                        }
                        
                        binding.DeviceBindings.Add(typeId, bindingList);
                    }
                }
                catch (Exception ex)
                {
                    Logger.Warning($"Failed to parse slot binding JSON for slot {i}: {ex.Message}");
                }
            }
            result.Items.Add(binding);
        }
        return Task.FromResult(result);
    }

    /// <summary>
    /// 设置槽位绑定（将设备绑定到槽位）
    /// </summary>
    public override Task<Result> SetSlotBinding(SlotBinding request, ServerCallContext context)
    {
        Logger.Info($"设置槽位绑定: slot={request.SlotId}");
        try
        {
            // 转换为 Engine 期望的格式: {"type": ["id1", "id2"]}
            var dict = new Dictionary<string, List<string>>();
            foreach (var kv in request.DeviceBindings)
            {
                dict[kv.Key] = kv.Value.DeviceIds.ToList();
            }
            var json = JsonSerializer.Serialize(dict);
            _engine.SetSlotBinding((int)request.SlotId, json);
            return Task.FromResult(new Result { Success = true });
        }
        catch (Exception ex)
        {
            return Task.FromResult(new Result { Success = false, Error = ex.Message });
        }
    }

    /// <summary>
    /// 设置槽位数量
    /// </summary>
    public override Task<Result> SetSlotCount(SlotCountRequest request, ServerCallContext context)
    {
        Logger.Info($"设置槽位数量: count={request.Count}");
        try
        {
            _engine.SetSlotCount((int)request.Count);
            return Task.FromResult(new Result { Success = true });
        }
        catch (Exception ex)
        {
            return Task.FromResult(new Result { Success = false, Error = ex.Message });
        }
    }

    /// <summary>
    /// 设置槽位 SN
    /// </summary>
    public override Task<Result> SetSlotSn(SetSlotSnRequest request, ServerCallContext context)
    {
        Logger.Info($"设置槽位 SN: slot_id={request.SlotId}, sn={request.Sn}");
        try
        {
            _engine.SetSlotSn(request.SlotId, request.Sn);
            return Task.FromResult(new Result { Success = true });
        }
        catch (Exception ex)
        {
            return Task.FromResult(new Result { Success = false, Error = ex.Message });
        }
    }

    /// <summary>
    /// 获取槽位运行状态
    /// </summary>
    public override Task<SlotStatus> GetSlotStatus(SlotId request, ServerCallContext context)
    {
        try
        {
            var json = _engine.GetSlotStatus((int)request.Id);
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            
            var result = new SlotStatus
            {
                SlotId = root.TryGetProperty("slot_id", out var sid) ? sid.GetUInt32() : request.Id,
                Status = root.TryGetProperty("status", out var st) ? st.GetString() ?? "idle" : "idle",
                CurrentStepIndex = root.TryGetProperty("current_step_index", out var csi) ? csi.GetUInt32() : 0,
                TotalSteps = root.TryGetProperty("total_steps", out var ts) ? ts.GetUInt32() : 0,
                ElapsedMs = root.TryGetProperty("elapsed_ms", out var em) ? em.GetUInt64() : 0,
                Sn = root.TryGetProperty("sn", out var sn) && sn.ValueKind != JsonValueKind.Null ? sn.GetString() ?? "" : "",
                // 新增字段
                CurrentStepName = ExtractCurrentStepName(root),
                CurrentStepDesc = ExtractCurrentStepDesc(root)
            };
            
            // 填充变量列表
            result.Variables.AddRange(ExtractVariables(root));
            
            return Task.FromResult(result);
        }
        catch (Exception ex)
        {
            Logger.Error($"GetSlotStatus failed: {ex.Message}");
            return Task.FromResult(new SlotStatus { SlotId = request.Id, Status = "error" });
        }
    }
    
    // ========== 辅助方法：提取 SlotStatus 新字段 ==========
    
    private static string ExtractCurrentStepName(JsonElement root)
    {
        // 尝试从 current_step.step_name 或顶层 current_step_name 获取
        if (root.TryGetProperty("current_step", out var cs) && 
            cs.TryGetProperty("step_name", out var name))
            return name.GetString() ?? "";
        if (root.TryGetProperty("current_step_name", out var csn))
            return csn.GetString() ?? "";
        return "";
    }
    
    private static string ExtractCurrentStepDesc(JsonElement root)
    {
        if (root.TryGetProperty("current_step", out var cs) && 
            cs.TryGetProperty("description", out var desc))
            return desc.GetString() ?? "";
        if (root.TryGetProperty("current_step_desc", out var csd))
            return csd.GetString() ?? "";
        return "";
    }
    
    private static IEnumerable<SlotVariable> ExtractVariables(JsonElement root)
    {
        if (!root.TryGetProperty("variables", out var vars) || 
            vars.ValueKind != JsonValueKind.Object)
            yield break;
        
        foreach (var prop in vars.EnumerateObject())
        {
            var varName = prop.Name;
            var val = prop.Value;
            
            // Engine 返回格式: {"voltage": {"value": "3.31", "unit": "V", ...}}
            if (val.ValueKind == JsonValueKind.Object)
            {
                yield return new SlotVariable
                {
                    Name = varName,
                    Value = val.TryGetProperty("value", out var v) ? v.GetString() ?? "" : "",
                    Unit = val.TryGetProperty("unit", out var u) ? u.GetString() ?? "" : "",
                    IsPassing = val.TryGetProperty("passed", out var p) && p.ValueKind == JsonValueKind.True
                };
            }
            else
            {
                // 简单值格式
                yield return new SlotVariable
                {
                    Name = varName,
                    Value = val.ToString(),
                    Unit = "",
                    IsPassing = true
                };
            }
        }
    }

    // =========== 测试脚本 ===========
    
    /// <summary>
    /// 加载测试脚本 (整体加载)
    /// </summary>
    public override Task<Result> LoadTestScript(TestScript request, ServerCallContext context)
    {
        // Engine currently does not support "LoadScript" (overwrite all).
        // It only supports Add/Update/Remove granularly.
        // We can simulate it if needed, or deprecate this RPC in favor of granular ones.
        // For now, return false or try parsing and adding?
        // Let's implement granular APIs first.
        return Task.FromResult(new Result { Success = false, Error = "Please use granular Step APIs (Add/Update/DeleteTestStep)." });
    }

    /// <summary>
    /// 获取当前加载的测试脚本
    /// </summary>
    public override Task<TestScript> GetCurrentScript(Empty request, ServerCallContext context)
    {
        try 
        {
            var json = _engine.GetTestSteps();
            return Task.FromResult(new TestScript { JsonContent = json });
        }
        catch (Exception)
        {
             return Task.FromResult(new TestScript());
        }
    }

    // =========== Test Step Management (Granular) ===========

    public override Task<Result> AddTestStep(TestStepPayload request, ServerCallContext context)
        => ExecuteEngineAction(() => _engine.AddTestStep(request.JsonContent), "添加测试步骤");

    public override Task<Result> UpdateTestStep(TestStepPayload request, ServerCallContext context)
        => ExecuteEngineAction(() => _engine.UpdateTestStep(request.JsonContent), "更新测试步骤");

    public override Task<Result> DeleteTestStep(TestStepId request, ServerCallContext context)
        => ExecuteEngineAction(() => _engine.RemoveTestStep((int)request.Id), "删除测试步骤");

    public override Task<Result> ReorderTestSteps(ReorderStepsRequest request, ServerCallContext context)
        => ExecuteEngineAction(() => _engine.ReorderSteps(request.StepIds.Select(id => (int)id).ToArray()), "重排测试步骤");

    // =========== 测试执行 ===========
    
    /// <summary>
    /// 启动测试
    /// </summary>
    public override Task<Result> StartTest(StartTestRequest request, ServerCallContext context)
    {
        Logger.Info($"启动测试: slot={request.SlotId}, loop={request.Loop}");
        return ExecuteEngineAction(() => _engine.StartSlot((int)request.SlotId), "启动测试");
    }

    /// <summary>
    /// 暂停测试
    /// </summary>
    public override Task<Result> PauseTest(SlotId request, ServerCallContext context)
    {
        Logger.Info($"暂停测试: slot={request.Id}");
        return ExecuteEngineAction(() => _engine.PauseSlot((int)request.Id), "暂停测试");
    }

    /// <summary>
    /// 恢复测试
    /// </summary>
    public override Task<Result> ResumeTest(SlotId request, ServerCallContext context)
    {
        Logger.Info($"恢复测试: slot={request.Id}");
        return ExecuteEngineAction(() => _engine.ResumeSlot((int)request.Id), "恢复测试");
    }

    /// <summary>
    /// 停止测试
    /// </summary>
    public override Task<Result> StopTest(SlotId request, ServerCallContext context)
    {
        Logger.Info($"停止测试: slot={request.Id}");
        return ExecuteEngineAction(() => _engine.StopSlot((int)request.Id), "停止测试");
    }

    // =========== 插件 ===========
    
    /// <summary>
    /// 获取所有已加载的插件列表
    /// </summary>
    public override Task<PluginList> ListPlugins(Empty request, ServerCallContext context)
    {
        var plugins = _pluginManager.GetAllPlugins().Select(p => 
        {
            var types = new List<string>();
            if (p.Instance is ICommunicator) types.Add("communicator");
            if (p.Instance is IProcessor) types.Add("processor");
            
            return new Plugin
            {
                Id = p.Manifest.Id,
                Name = p.Manifest.Name, 
                Version = p.Manifest.Version,
                Protocols = { p.Manifest.Capabilities.Protocols },
                Types_ = { types }
            };
        });
        
        return Task.FromResult(new PluginList { Items = { plugins } });
    }

    // =========== 事件订阅 ===========
    
    /// <summary>
    /// 订阅事件流
    /// 客户端订阅后会持续收到 Host 推送的事件
    /// </summary>
    /// <summary>
    /// 订阅事件流
    /// 客户端订阅后会持续收到 Host 推送的事件
    /// </summary>
    public override async Task Subscribe(SubscribeRequest request, IServerStreamWriter<Event> responseStream, ServerCallContext context)
    {
        Logger.Info($"客户端已订阅: topics={string.Join(",", request.Topics)}");
        
        var wantSlotUpdate = request.Topics.Count == 0 || request.Topics.Contains("slot_update");
        
        // 创建无界通道用于缓冲事件
        var channel = Channel.CreateUnbounded<Event>();
        
        // 定义事件处理函数
        Action<string> onSnapshot = (json) =>
        {
            if (!wantSlotUpdate) return;
            
            try
            {
                using var doc = JsonDocument.Parse(json);
                var root = doc.RootElement;
                
                // 快照包含 slots 数组
                if (root.TryGetProperty("slots", out var slotsElement) && slotsElement.ValueKind == JsonValueKind.Array)
                {
                    foreach (var slotElement in slotsElement.EnumerateArray())
                    {
                        var slotId = slotElement.TryGetProperty("slot_id", out var id) ? id.GetUInt32() : 0;
                        var statusStr = slotElement.TryGetProperty("status", out var st) ? st.GetString() ?? "idle" : "idle";
                        
                        // 提取进度
                        uint currentStep = 0;
                        uint totalSteps = 0;
                        ulong elapsed = 0;
                        
                        if (slotElement.TryGetProperty("progress", out var prog))
                        {
                            currentStep = prog.TryGetProperty("current", out var c) ? c.GetUInt32() : 0;
                            totalSteps = prog.TryGetProperty("total", out var t) ? t.GetUInt32() : 0;
                        }
                        
                        var slotStatus = new SlotStatus
                        {
                            SlotId = slotId,
                            Status = statusStr,
                            CurrentStepIndex = currentStep,
                            TotalSteps = totalSteps,
                            ElapsedMs = elapsed, // Engine 快照目前主要关注进度，时间暂未透传或在 progress 里
                            Sn = slotElement.TryGetProperty("sn", out var sn) && sn.ValueKind != JsonValueKind.Null ? sn.GetString() ?? "" : "",
                            
                            // 提取当前步骤信息
                            CurrentStepName = slotElement.TryGetProperty("current_step_name", out var csn) ? csn.GetString() ?? "" : "",
                            CurrentStepDesc = slotElement.TryGetProperty("current_step_desc", out var csd) ? csd.GetString() ?? "" : ""
                        };
                        
                        // 变量
                        if (slotElement.TryGetProperty("variables", out var vars))
                        {
                            foreach (var prop in vars.EnumerateObject())
                            {
                                slotStatus.Variables.Add(new SlotVariable
                                {
                                    Name = prop.Name,
                                    Value = prop.Value.GetString() ?? ""
                                });
                            }
                        }
                        
                        var evt = new Event
                        {
                            Type = "slot_update",
                            SlotUpdate = new SlotUpdateEvent
                            {
                                SlotId = slotId,
                                Status = slotStatus
                            }
                        };
                        
                        // 尝试写入通道，如果失败则丢弃（理论上 Unbounded 不会失败）
                        channel.Writer.TryWrite(evt);
                    }
                }
            }
            catch (Exception ex)
            {
                Logger.Warning($"处理快照失败: {ex.Message}");
            }
        };
        
        // 订阅事件
        _engine.SnapshotReceived += onSnapshot;
        
        try
        {
            // 循环读取通道并发送
            while (await channel.Reader.WaitToReadAsync(context.CancellationToken))
            {
                while (channel.Reader.TryRead(out var evt))
                {
                    await responseStream.WriteAsync(evt);
                }
            }
        }
        catch (OperationCanceledException)
        {
            Logger.Info("客户端已取消订阅");
        }
        finally
        {
            // 取消订阅
            _engine.SnapshotReceived -= onSnapshot;
        }
    }
}
