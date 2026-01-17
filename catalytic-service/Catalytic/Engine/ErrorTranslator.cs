using System.Collections.Generic;

namespace Catalytic.Engine;

/// <summary>
/// 将 Engine FFI 错误码翻译为人类可读消息
/// </summary>
public static class ErrorTranslator
{
    private static readonly Dictionary<int, string> ErrorMessages = new()
    {
        { 0, null! },  // SUCCESS - 无错误
        { -1, "状态无效：当前操作在此状态下不允许执行（如：尝试暂停未运行的测试）" },
        { -2, "参数无效：请检查 JSON 格式或字段值是否正确" },
        { -3, "内部错误：引擎发生未知异常，请查看日志" },
    };
    
    /// <summary>
    /// 将错误码翻译为人类可读消息
    /// </summary>
    /// <param name="code">FFI 返回的错误码</param>
    /// <returns>人类可读的错误消息，如果成功则返回 null</returns>
    public static string? Translate(int code)
    {
        if (code == 0) return null;
        
        return ErrorMessages.TryGetValue(code, out var message) 
            ? message 
            : $"未知错误 (错误码: {code})";
    }
    
    /// <summary>
    /// 检查错误码并返回格式化的消息，用于异常抛出
    /// </summary>
    public static string FormatError(string operation, int code)
    {
        var message = Translate(code);
        return message != null ? $"{operation}失败: {message}" : null!;
    }
}
