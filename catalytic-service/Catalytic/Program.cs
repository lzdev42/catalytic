using Catalytic;
using Catalytic.Engine;
using Catalytic.Grpc;
using Catalytic.Plugin;
using CatalyticKit;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using Microsoft.Extensions.DependencyInjection;

// ========== 单实例检查 ==========
// 使用全局互斥锁确保只有一个 Host 实例在运行
const string mutexName = "Global\\CatalyticHost";
using var mutex = new Mutex(true, mutexName, out var createdNew);

if (!createdNew)
{
    Logger.Error("另一个 Catalytic Host 实例已在运行。");
    Logger.Info("请先关闭现有实例。");
    return;
}

// ========== 启动 ==========
Logger.Info("Catalytic Host 正在启动...");

// ========== 解析命令行参数 ==========
string? cmdConfigPath = null;
int? cmdPort = null;

for (int i = 0; i < args.Length; i++)
{
    if (args[i] == "--config" && i + 1 < args.Length)
    {
        cmdConfigPath = args[i + 1];
        Logger.Info($"命令行参数: 配置文件 = {cmdConfigPath}");
    }
    else if (args[i] == "--port" && i + 1 < args.Length)
    {
        if (int.TryParse(args[i + 1], out var p))
        {
            cmdPort = p;
            Logger.Info($"命令行参数: 端口 = {p}");
        }
    }
}

// ========== 验证必需参数 ==========
if (string.IsNullOrEmpty(cmdConfigPath) || !cmdPort.HasValue)
{
    Logger.Error("缺少必需参数。用法: Catalytic --config <path> --port <port>");
    return;
}

// ========== 加载配置 ==========
var loadResult = ConfigManager.LoadFromConfigFile(cmdConfigPath);
if (loadResult == null)
{
    Logger.Error($"配置文件不存在或格式错误: {cmdConfigPath}");
    return;
}

var (workingDir, config) = loadResult.Value;
config.GrpcPort = cmdPort.Value;
ConfigManager.SetWorkingDirectory(workingDir);

Logger.Info($"配置已加载: {cmdConfigPath}");
Logger.Info($"工作目录: {workingDir}");
Logger.Info($"gRPC 端口: {config.GrpcPort}, 槽位数: {config.SlotCount}");

// ========== 初始化 Engine ==========
Logger.Info("正在初始化 Engine...");
Catalytic.Engine.Engine engine;
try
{
    engine = new Catalytic.Engine.Engine(config.SlotCount);
    Logger.Info($"✓ Engine 创建成功，槽位数: {engine.SlotCount}");
    
    // 设置数据存储路径
    var dataPath = ConfigManager.GetFullPath(config.DataPath);
    engine.SetDataPath(dataPath);
    Logger.Info($"✓ 数据路径: {dataPath}");
    
    // 注册 UI 回调（后续会通过 gRPC 推送给 UI）
    engine.OnUIUpdate(json => {
        Logger.Info($"[Engine] UI 更新: {json.Length} 字节");
    });
    
    Logger.Info("✓ Engine 初始化完成");
}
catch (Exception ex)
{
    Logger.Error($"Engine 初始化失败: {ex.Message}");
    Logger.Error("请确保 libcatalytic.dylib 在正确的位置");
    return;
}

// 加载插件
var pluginManager = new PluginManager();

// 设置插件日志处理器
pluginManager.OnLog += (level, pluginId, message) =>
{
    var levelStr = level switch
    {
        LogLevel.Debug => "DEBUG",
        LogLevel.Info => "INFO",
        LogLevel.Warning => "WARN",
        LogLevel.Error => "ERROR",
        _ => "UNKNOWN"
    };
    Logger.Log(levelStr, pluginId, message);
};

Logger.Info("正在加载插件...");
var (loadedCount, loadErrors) = await pluginManager.LoadAllAsync();

// 报告加载失败的插件
if (loadErrors.Count > 0)
{
    Logger.Warning($"{loadErrors.Count} 个插件加载失败:");
    foreach (var error in loadErrors)
    {
        Logger.Info($"  - {error}");
    }
}

Logger.Info($"已加载 {loadedCount} 个插件");
Logger.Info($"已注册协议: {string.Join(", ", pluginManager.GetRegisteredProtocols())}");

// ========== 创建任务分发器 ==========
// 这里的变量需要保持引用，虽然在这个简单的 Program.cs 中 engine 和 pluginManager 会一直存活，
// 但为了语义明确，我们显式持有它们。
var engineTaskDispatcher = new EngineTaskDispatcher(engine, pluginManager);
var hostTaskDispatcher = new HostTaskDispatcher(engine, pluginManager);
Logger.Info("✓ 任务分发器已创建，等待 Engine 任务...");

// ========== MOCK 自测 ==========
if (args.Contains("--mock"))
{
    try 
    {
        // await MockIntegrationTest.Run(engine, pluginManager, config);
        Logger.Warning("Mock test is disabled due to refactoring. Please use 'dotnet test'.");
    }
    catch (Exception ex)
    {
        Logger.Error($"Mock test failed: {ex}");
    }
    // 测试完成后退出，或者让它继续运行？Mock通常跑完就停。
    Logger.Info("Mock 测试完成，按 Ctrl+C 退出...");
    // return; // 如果想跑完即停，取消注释。但用户可能想看日志，暂不 return，让它继续跑 WebHost 但已经没用了
}

// 设置优雅关闭处理
var cts = new CancellationTokenSource();
Console.CancelKeyPress += (s, e) =>
{
    e.Cancel = true;  // 阻止立即终止
    cts.Cancel();     // 触发取消信号
};

// 构建 gRPC 服务器
var builder = WebApplication.CreateBuilder();

// 配置 Kestrel 监听 HTTP/2（gRPC 需要）
builder.WebHost.ConfigureKestrel(options =>
{
    options.ListenLocalhost(config.GrpcPort, o => o.Protocols = HttpProtocols.Http2);
});

// 注册服务
builder.Services.AddGrpc();
builder.Services.AddSingleton(pluginManager);
builder.Services.AddSingleton(config);
builder.Services.AddSingleton(engine);  // 注入 Engine
builder.Services.AddSingleton<Action>(() => cts.Cancel());  // 注入关闭回调

var app = builder.Build();

// 映射 gRPC 服务
app.MapGrpcService<HostGrpcService>();

Logger.Info($"gRPC 服务器正在监听端口 {config.GrpcPort}");
Logger.Info("Host 已就绪");

// 运行直到收到关闭信号
try
{
    await app.StartAsync(cts.Token);
    await Task.Delay(Timeout.Infinite, cts.Token);
}
catch (OperationCanceledException)
{
    Logger.Info("正在关闭...");
}
finally
{
    await app.StopAsync();
}

// 清理资源
await pluginManager.DisposeAsync();

Logger.Info("Host 已停止");
