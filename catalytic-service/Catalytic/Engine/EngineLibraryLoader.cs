using System.Runtime.InteropServices;

namespace Catalytic.Engine;

/// <summary>
/// Engine 动态库加载器
/// 负责根据平台加载正确的 libcatalytic 动态库
/// </summary>
public static class EngineLibraryLoader
{
    /// <summary>
    /// 获取当前平台对应的 Engine 动态库文件名
    /// </summary>
    public static string GetLibraryFileName()
    {
        if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
        {
            return "catalytic.dll";
        }
        else if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
        {
            return "libcatalytic.dylib";
        }
        else if (RuntimeInformation.IsOSPlatform(OSPlatform.Linux))
        {
            return "libcatalytic.so";
        }
        else
        {
            throw new PlatformNotSupportedException($"不支持的操作系统: {RuntimeInformation.OSDescription}");
        }
    }
    
    /// <summary>
    /// 获取 Engine 动态库的完整路径
    /// 动态库位于程序所在目录
    /// </summary>
    public static string GetLibraryPath()
    {
        var appDir = ConfigManager.GetAppDirectory();
        var fileName = GetLibraryFileName();
        return Path.Combine(appDir, fileName);
    }
    
    /// <summary>
    /// 检查 Engine 动态库是否存在
    /// </summary>
    public static bool LibraryExists()
    {
        return File.Exists(GetLibraryPath());
    }
    
    /// <summary>
    /// 获取用于 DllImport 的库名称
    /// 注意：DllImport 会自动添加平台前缀和后缀
    /// 所以只需返回 "catalytic"
    /// </summary>
    public const string LibraryName = "catalytic";
}
