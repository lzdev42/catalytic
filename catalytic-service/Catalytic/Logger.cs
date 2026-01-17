namespace Catalytic;

/// <summary>
/// 简单的控制台日志工具类
/// 提供带时间戳的日志输出功能
/// </summary>
public static class Logger
{
    /// <summary>
    /// 输出信息级别日志
    /// </summary>
    public static void Info(string message) =>
        Console.WriteLine($"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {message}");

    /// <summary>
    /// 输出错误级别日志
    /// </summary>
    public static void Error(string message) =>
        Console.WriteLine($"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] ERROR: {message}");

    /// <summary>
    /// 输出警告级别日志
    /// </summary>
    public static void Warning(string message) =>
        Console.WriteLine($"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] WARNING: {message}");

    /// <summary>
    /// 输出调试级别日志
    /// </summary>
    public static void Debug(string message) =>
        Console.WriteLine($"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] DEBUG: {message}");

    /// <summary>
    /// 输出带来源标识的日志
    /// </summary>
    /// <param name="level">日志级别</param>
    /// <param name="source">来源标识（如模块名、插件ID）</param>
    /// <param name="message">日志内容</param>
    public static void Log(string level, string source, string message) =>
        Console.WriteLine($"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] [{level}] [{source}] {message}");
}
