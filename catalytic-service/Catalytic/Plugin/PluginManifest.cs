using System.Text.Json.Serialization;

namespace Catalytic.Plugin;

/// <summary>
/// 插件清单类
/// 对应插件目录下的 manifest.json 文件
/// </summary>
public sealed class PluginManifest
{
    /// <summary>插件唯一标识</summary>
    [JsonPropertyName("id")]
    public string Id { get; set; } = "";
    
    /// <summary>插件显示名称</summary>
    [JsonPropertyName("name")]
    public string Name { get; set; } = "";
    
    /// <summary>插件版本号</summary>
    [JsonPropertyName("version")]
    public string Version { get; set; } = "1.0.0";
    
    /// <summary>插件作者（可选）</summary>
    [JsonPropertyName("author")]
    public string? Author { get; set; }
    
    /// <summary>入口 DLL 文件名</summary>
    [JsonPropertyName("entry")]
    public string Entry { get; set; } = "";
    
    /// <summary>插件能力声明</summary>
    [JsonPropertyName("capabilities")]
    public PluginCapabilities Capabilities { get; set; } = new();
}

/// <summary>
/// 插件能力声明
/// 描述插件支持的协议和任务
/// </summary>
public sealed class PluginCapabilities
{
    /// <summary>支持的协议列表，如 ["scpi", "modbus"]</summary>
    [JsonPropertyName("protocols")]
    public List<string> Protocols { get; set; } = new();
    
    /// <summary>支持的任务列表，如 ["burn_firmware", "calibrate"]</summary>
    [JsonPropertyName("tasks")]
    public List<string> Tasks { get; set; } = new();
}
