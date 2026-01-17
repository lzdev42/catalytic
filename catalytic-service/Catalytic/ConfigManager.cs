using System.Text.Json;
using System.Text.Json.Serialization;

namespace Catalytic;

/// <summary>
/// 工作区配置 (Level 1)
/// 存储在平台配置目录，只包含工作目录路径
/// </summary>
public sealed class WorkspaceConfig
{
    [JsonPropertyName("working_directory")]
    public string? WorkingDirectory { get; set; }
}

/// <summary>
/// 应用程序配置类 (Level 2)
/// 存储在工作目录的 config.json
/// </summary>
public sealed class AppConfig
{
    /// <summary>
    /// gRPC 服务器监听端口
    /// </summary>
    [JsonPropertyName("grpc_port")]
    public int GrpcPort { get; set; } = 5000;
    
    /// <summary>
    /// 测试槽位数量
    /// </summary>
    [JsonPropertyName("slot_count")]
    public int SlotCount { get; set; } = 4;
    
    /// <summary>
    /// 测试报告目录路径（相对于工作目录或绝对路径）
    /// </summary>
    [JsonPropertyName("reports_path")]
    public string ReportsPath { get; set; } = "reports";
    
    /// <summary>
    /// 日志目录路径（相对于工作目录或绝对路径）
    /// </summary>
    [JsonPropertyName("logs_path")]
    public string LogsPath { get; set; } = "logs";
    
    /// <summary>
    /// 运行时数据目录路径（相对于工作目录或绝对路径）
    /// </summary>
    [JsonPropertyName("data_path")]
    public string DataPath { get; set; } = "data";
}

/// <summary>
/// 配置管理器
/// 实现两级配置加载机制：
/// Level 1: 平台配置目录存储工作目录路径 (workspace.json)
/// Level 2: 工作目录存储实际配置 (config.json)
/// </summary>
public static class ConfigManager
{
    private const string WorkspaceFileName = "workspace.json";
    private const string ConfigFileName = "config.json";
    private const string AppName = "Catalytic";
    
    /// <summary>
    /// 当前工作目录（运行时确定）
    /// </summary>
    public static string? WorkingDirectory { get; private set; }
    
    /// <summary>
    /// 获取平台配置目录
    /// macOS: ~/Library/Application Support/Catalytic/
    /// Windows: %APPDATA%\Catalytic\
    /// Linux: ~/.config/Catalytic/
    /// </summary>
    public static string GetPlatformConfigDirectory()
    {
        string baseDir;
        
        if (OperatingSystem.IsMacOS())
        {
            var home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            baseDir = Path.Combine(home, "Library", "Application Support");
        }
        else if (OperatingSystem.IsWindows())
        {
            baseDir = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        }
        else // Linux
        {
            var home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            baseDir = Path.Combine(home, ".config");
        }
        
        return Path.Combine(baseDir, AppName);
    }
    
    /// <summary>
    /// 获取程序所在目录（备选工作目录）
    /// </summary>
    public static string GetAppDirectory()
    {
        return AppContext.BaseDirectory.TrimEnd(Path.DirectorySeparatorChar);
    }
    

    

    
    /// <summary>
    /// 配置文件 DTO (仅内部使用)
    /// </summary>
    private class ConfigFileDto 
    {
        [JsonPropertyName("working_dir")]
        public string? WorkingDirectory { get; set; }
        
        [JsonPropertyName("grpc_port")]
        public int? GrpcPort { get; set; }
        
        [JsonPropertyName("slot_count")]
        public int? SlotCount { get; set; }
    }

    /// <summary>
    /// 从 UI 传入的 config.json 读取完整配置
    /// 返回 (工作目录, 配置对象)
    /// </summary>
    public static (string WorkingDir, AppConfig Config)? LoadFromConfigFile(string configFilePath)
    {
        if (!File.Exists(configFilePath))
        {
            Logger.Error($"配置文件不存在: {configFilePath}");
            return null;
        }
        
        try
        {
            var json = File.ReadAllText(configFilePath);
            var options = new JsonSerializerOptions 
            {
                PropertyNameCaseInsensitive = true,
                NumberHandling = JsonNumberHandling.AllowReadingFromString, // Match legacy manual parsing leniency
                AllowTrailingCommas = true
            };
            
            var dto = JsonSerializer.Deserialize<ConfigFileDto>(json, options);
            
            if (dto == null) return null;
            
            // 验证 working_dir
            var workingDir = dto.WorkingDirectory;
            if (string.IsNullOrEmpty(workingDir) || !Directory.Exists(workingDir))
            {
                Logger.Error($"工作目录不存在或未指定: {workingDir}");
                return null;
            }
            
            // 构造 AppConfig
            var config = new AppConfig();
            if (dto.GrpcPort.HasValue) config.GrpcPort = dto.GrpcPort.Value;
            if (dto.SlotCount.HasValue) config.SlotCount = dto.SlotCount.Value;
            
            return (workingDir, config);
        }
        catch (Exception ex)
        {
            Logger.Error($"读取配置文件失败: {ex.Message}");
            return null;
        }
    }
    
    /// <summary>
    /// 直接设置工作目录
    /// </summary>
    public static void SetWorkingDirectory(string path)
    {
        WorkingDirectory = path;
    }
    
    /// <summary>
    /// 获取完整路径（处理相对路径）
    /// </summary>
    public static string GetFullPath(string relativePath)
    {
        if (Path.IsPathRooted(relativePath))
        {
            return relativePath;
        }
        return Path.Combine(WorkingDirectory ?? "", relativePath);
    }
}
