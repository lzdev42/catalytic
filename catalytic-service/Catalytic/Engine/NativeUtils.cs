using System.Runtime.InteropServices;

namespace Catalytic.Engine;

/// <summary>
/// Internal utility class for safe native interop marshalling.
/// Centralizes unsafe pointer operations and memory ownership management.
/// </summary>
internal static class NativeUtils
{
    /// <summary>
    /// Reads a UTF-8 string from an OWNED pointer, then frees the pointer.
    /// Use this for return values from cat_engine_* methods that return allocated strings.
    /// </summary>
    /// <param name="ptr">Pointer to a null-terminated UTF-8 string allocated by the Engine.</param>
    /// <returns>The string value.</returns>
    public static string ConsumeJsonString(IntPtr ptr)
    {
        if (ptr == IntPtr.Zero) return string.Empty;
        
        try
        {
            return Marshal.PtrToStringUTF8(ptr) ?? string.Empty;
        }
        finally
        {
            NativeMethods.FreeJson(ptr);
        }
    }

    /// <summary>
    /// Reads a UTF-8 string from a BORROWED pointer. Does NOT free the pointer.
    /// Use this for callback arguments where the Engine retains ownership or the pointer is stack-allocated.
    /// </summary>
    /// <param name="ptr">Pointer to a null-terminated UTF-8 string.</param>
    /// <returns>The string value.</returns>
    public static string ReadUtf8String(IntPtr ptr)
    {
        if (ptr == IntPtr.Zero) return string.Empty;
        return Marshal.PtrToStringUTF8(ptr) ?? string.Empty;
    }

    /// <summary>
    /// Reads a byte array from a pointer. Does NOT free the pointer.
    /// </summary>
    /// <param name="ptr">Pointer to the data.</param>
    /// <param name="length">Length of the data in bytes.</param>
    /// <returns>A new byte array containing the data.</returns>
    public static byte[] ReadByteArray(IntPtr ptr, int length)
    {
        if (ptr == IntPtr.Zero || length <= 0) 
            return Array.Empty<byte>();
            
        var buffer = new byte[length];
        Marshal.Copy(ptr, buffer, 0, length);
        return buffer;
    }
}
