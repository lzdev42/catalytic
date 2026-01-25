using System.Collections.Concurrent;
using System.Reflection;
using System.Text.Json;
using CatalyticKit;

namespace Catalytic.Plugin;

/// <summary>
/// 插件事件参数
/// </summary>
/// <param name="EventType">事件类型</param>
/// <param name="Data">事件数据</param>
public record PluginEventArgs(string EventType, byte[] Data);

/// <summary>
/// 已加载插件信息
/// 包含插件的清单、实例和程序集信息
/// </summary>
public sealed class LoadedPlugin
{
    /// <summary>插件清单</summary>
    public PluginManifest Manifest { get; }
    
    /// <summary>插件目录路径</summary>
    public string Directory { get; }
    
    /// <summary>插件实例</summary>
    public IPlugin Instance { get; }
    
    /// <summary>插件程序集</summary>
    public Assembly Assembly { get; }
    
    internal LoadedPlugin(PluginManifest manifest, string directory, IPlugin instance, Assembly assembly)
    {
        Manifest = manifest;
        Directory = directory;
        Instance = instance;
        Assembly = assembly;
    }
}

/// <summary>
/// 插件上下文实现
/// 提供插件与 Catalytic 交互的能力
/// </summary>
internal sealed class PluginContext : IPluginContext
{
    /// <summary>插件目录路径</summary>
    public string PluginDirectory { get; }
    
    private readonly PluginManager _manager;
    private readonly string _pluginId;
    
    public PluginContext(string pluginId, string directory, PluginManager manager)
    {
        _pluginId = pluginId;
        PluginDirectory = directory;
        _manager = manager;
    }
    
    /// <summary>
    /// 通过 Host 日志系统输出日志
    /// </summary>
    public void Log(LogLevel level, string message)
    {
        _manager.OnLog?.Invoke(level, _pluginId, message);
    }

    /// <summary>
    /// 获取指定协议或 ID 的通讯器
    /// </summary>
    public ICommunicator? GetCommunicator(string protocolOrId)
    {
        // 先尝试按协议名查找，再尝试按 ID 查找
        return _manager.GetCommunicator(protocolOrId) ?? _manager.GetCommunicatorById(protocolOrId);
    }

    /// <summary>
    /// 推送事件到 Host
    /// </summary>
    public void PushEvent(string eventType, byte[] data)
    {
        _manager.OnPluginEvent?.Invoke(new PluginEventArgs(eventType, data));
    }

    /// <summary>
    /// 获取设备缓冲的数据
    /// </summary>
    public byte[] GetDeviceData(string deviceId)
    {
        return _manager.DataManager?.GetData(deviceId) ?? Array.Empty<byte>();
    }
}

/// <summary>
/// 插件管理器
/// 负责发现、加载、激活和管理插件
/// </summary>
public sealed class PluginManager : IAsyncDisposable
{
    private readonly string _pluginsDirectory;
    public DataManager? DataManager { get; set; } // Property injection or Constructor injection logic
    
    // 使用并发字典保证线程安全
    private readonly ConcurrentDictionary<string, LoadedPlugin> _pluginsById = new();
    private readonly ConcurrentDictionary<string, ICommunicator> _communicatorsByProtocol = new();
    private readonly ConcurrentDictionary<string, IProcessor> _processorsByName = new();
    
    /// <summary>
    /// 日志处理器
    /// 参数: (日志级别, 插件ID, 消息内容)
    /// </summary>
    public Action<LogLevel, string, string>? OnLog { get; set; }

    /// <summary>
    /// 插件事件处理器
    /// 当插件调用 PushEvent 时触发
    /// </summary>
    public Action<PluginEventArgs>? OnPluginEvent { get; set; }

    /// <summary>
    /// 创建插件管理器
    /// </summary>
    /// <param name="dataManager">数据管理器</param>
    /// <param name="pluginsDirectory">插件目录路径，默认为应用目录下的 plugins 文件夹</param>
    public PluginManager(DataManager dataManager, string? pluginsDirectory = null)
    {
        DataManager = dataManager;
        _pluginsDirectory = pluginsDirectory ?? Path.Combine(AppContext.BaseDirectory, "plugins");
    }

    /// <summary>
    /// 发现并加载所有插件
    /// </summary>
    /// <returns>加载成功的数量和错误列表</returns>
    public async Task<(int Loaded, List<string> Errors)> LoadAllAsync()
    {
        var errors = new List<string>();
        var loaded = 0;

        // 如果插件目录不存在则创建
        if (!Directory.Exists(_pluginsDirectory))
        {
            Directory.CreateDirectory(_pluginsDirectory);
            return (0, errors);
        }

        // 遍历每个插件子目录
        foreach (var pluginDir in Directory.GetDirectories(_pluginsDirectory))
        {
            var result = await LoadPluginAsync(pluginDir);
            if (result.Error != null)
            {
                errors.Add(result.Error);
            }
            else
            {
                loaded++;
            }
        }

        return (loaded, errors);
    }

    private class PluginLoadException : Exception
    {
        public PluginLoadException(string message) : base(message) { }
    }

    /// <summary>
    /// 加载单个插件
    /// </summary>
    /// <param name="pluginDir">插件目录路径</param>
    /// <returns>加载结果</returns>
    private async Task<(LoadedPlugin? Plugin, string? Error)> LoadPluginAsync(string pluginDir)
    {
        try
        {
            var manifest = await LoadManifestAsync(pluginDir);
            var assembly = LoadAssembly(pluginDir, manifest);
            var instance = CreatePluginInstance(assembly, manifest);
            var loaded = await ActivatePluginAsync(pluginDir, instance, manifest);
            
            RegisterPlugin(loaded);
            
            Log(LogLevel.Info, "PluginManager", $"已加载插件: {manifest.Id} v{manifest.Version}");
            return (loaded, null);
        }
        catch (PluginLoadException ex)
        {
            return (null, ex.Message);
        }
    }

    private async Task<PluginManifest> LoadManifestAsync(string pluginDir)
    {
        var manifestPath = Path.Combine(pluginDir, "manifest.json");
        if (!File.Exists(manifestPath))
            throw new PluginLoadException($"{Path.GetFileName(pluginDir)} 目录中没有 manifest.json");

        try
        {
            var json = await File.ReadAllTextAsync(manifestPath);
            return JsonSerializer.Deserialize<PluginManifest>(json) 
                ?? throw new PluginLoadException("反序列化返回 null");
        }
        catch (Exception ex) when (ex is not PluginLoadException)
        {
            throw new PluginLoadException($"解析 {Path.GetFileName(pluginDir)} 的清单失败: {ex.Message}");
        }
    }

    private Assembly LoadAssembly(string pluginDir, PluginManifest manifest)
    {
        if (string.IsNullOrEmpty(manifest.Id) || string.IsNullOrEmpty(manifest.Entry))
            throw new PluginLoadException($"{Path.GetFileName(pluginDir)} 的清单无效: 缺少 id 或 entry");

        if (_pluginsById.ContainsKey(manifest.Id))
            throw new PluginLoadException($"插件 ID 重复: {manifest.Id}");

        var entryPath = Path.Combine(pluginDir, manifest.Entry);
        if (!File.Exists(entryPath))
            throw new PluginLoadException($"入口 DLL 不存在: {manifest.Id} 中的 {manifest.Entry}");

        try
        {
            return Assembly.LoadFrom(entryPath);
        }
        catch (Exception ex)
        {
            throw new PluginLoadException($"加载程序集 {manifest.Entry} 失败: {ex.Message}");
        }
    }

    private IPlugin CreatePluginInstance(Assembly assembly, PluginManifest manifest)
    {
        var pluginType = assembly.GetTypes()
            .FirstOrDefault(t => typeof(IPlugin).IsAssignableFrom(t) && !t.IsInterface && !t.IsAbstract)
            ?? throw new PluginLoadException($"{manifest.Id} 中没有找到 IPlugin 实现");

        try
        {
            return (IPlugin)(Activator.CreateInstance(pluginType) 
                ?? throw new InvalidOperationException("Activator 返回 null"));
        }
        catch (Exception ex)
        {
            throw new PluginLoadException($"创建 {manifest.Id} 的插件实例失败: {ex.Message}");
        }
    }

    private async Task<LoadedPlugin> ActivatePluginAsync(string pluginDir, IPlugin instance, PluginManifest manifest)
    {
        var context = new PluginContext(manifest.Id, pluginDir, this);
        
        try
        {
            await instance.ActivateAsync(context);
            return new LoadedPlugin(manifest, pluginDir, instance, instance.GetType().Assembly);
        }
        catch (Exception ex)
        {
            throw new PluginLoadException($"{manifest.Id} 插件激活失败: {ex.Message}");
        }
    }

    private void RegisterPlugin(LoadedPlugin loaded)
    {
        _pluginsById[loaded.Manifest.Id] = loaded;
        var instance = loaded.Instance;
        var manifest = loaded.Manifest;

        // 注册通讯器能力
        if (instance is ICommunicator communicator)
        {
            foreach (var protocol in manifest.Capabilities.Protocols)
            {
                if (_communicatorsByProtocol.ContainsKey(protocol))
                    throw new PluginLoadException($"协议冲突: {protocol} 已被其他插件注册");
                    
                _communicatorsByProtocol[protocol] = communicator;
            }
        }

        // 注册处理器能力
        if (instance is IProcessor processor)
        {
            foreach (var taskName in manifest.Capabilities.Tasks)
            {
                if (_processorsByName.ContainsKey(taskName))
                    throw new PluginLoadException($"处理器名称冲突: {taskName} 已被其他插件注册");
                    
                _processorsByName[taskName] = processor;
            }
        }
    }

    /// <summary>
    /// 手动注册通讯器（主要用于测试或内置）
    /// </summary>
    public void RegisterCommunicator(string protocol, ICommunicator communicator)
    {
        _communicatorsByProtocol[protocol] = communicator;
    }

    /// <summary>
    /// 手动按 ID 注册通讯器（主要用于测试）
    /// 这允许 GetCommunicatorById 能找到 mock 插件
    /// </summary>
    public void RegisterCommunicatorById(string pluginId, ICommunicator communicator)
    {
        // 创建一个虚拟的 LoadedPlugin 以支持 GetCommunicatorById
        var fakeManifest = new PluginManifest { Id = pluginId, Name = pluginId, Version = "test" };
        var fakeLoaded = new LoadedPlugin(fakeManifest, "", communicator, typeof(PluginManager).Assembly);
        _pluginsById[pluginId] = fakeLoaded;
        
        // 同时注册到协议字典
        _communicatorsByProtocol[communicator.Protocol] = communicator;
    }

    /// <summary>
    /// 根据协议名获取通讯器
    /// </summary>
    public ICommunicator? GetCommunicator(string protocol)
    {
        return _communicatorsByProtocol.TryGetValue(protocol, out var communicator) ? communicator : null;
    }

    /// <summary>
    /// 根据插件 ID 获取通讯器（用于显式绑定）
    /// </summary>
    public ICommunicator? GetCommunicatorById(string pluginId)
    {
        return _pluginsById.TryGetValue(pluginId, out var loaded) && loaded.Instance is ICommunicator communicator 
            ? communicator : null;
    }

    /// <summary>
    /// 根据名称获取处理器
    /// </summary>
    public IProcessor? GetProcessor(string name)
    {
        return _processorsByName.TryGetValue(name, out var processor) ? processor : null;
    }

    /// <summary>
    /// 手动注册处理器（主要用于测试）
    /// </summary>
    public void RegisterProcessor(string taskName, IProcessor processor)
    {
        _processorsByName[taskName] = processor;
    }

    /// <summary>
    /// 获取所有已加载的插件
    /// </summary>
    public IEnumerable<LoadedPlugin> GetAllPlugins() => _pluginsById.Values;

    /// <summary>
    /// 获取所有已注册的协议
    /// </summary>
    public IEnumerable<string> GetRegisteredProtocols() => _communicatorsByProtocol.Keys;

    /// <summary>
    /// 获取所有已注册的处理器名称
    /// </summary>
    public IEnumerable<string> GetRegisteredProcessors() => _processorsByName.Keys;

    private void Log(LogLevel level, string source, string message)
    {
        OnLog?.Invoke(level, source, message);
    }

    /// <summary>
    /// 异步释放资源
    /// 停用所有已加载的插件
    /// </summary>
    public async ValueTask DisposeAsync()
    {
        foreach (var loaded in _pluginsById.Values)
        {
            try
            {
                await loaded.Instance.DeactivateAsync();
            }
            catch (Exception ex)
            {
                Log(LogLevel.Error, "PluginManager", $"停用 {loaded.Manifest.Id} 时出错: {ex.Message}");
            }
        }

        _pluginsById.Clear();
        _communicatorsByProtocol.Clear();
        _processorsByName.Clear();
    }
}
