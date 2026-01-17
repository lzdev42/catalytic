using System.Runtime.InteropServices;

namespace Catalytic.Engine;

/// <summary>
/// Engine 句柄
/// 封装 Engine 指针，确保正确释放
/// </summary>
internal sealed class EngineHandle : SafeHandle
{
    public EngineHandle() : base(IntPtr.Zero, true) { }
    
    public EngineHandle(IntPtr handle) : base(IntPtr.Zero, true)
    {
        SetHandle(handle);
    }
    
    public override bool IsInvalid => handle == IntPtr.Zero;
    
    protected override bool ReleaseHandle()
    {
        if (!IsInvalid)
        {
            NativeMethods.Destroy(handle);
        }
        return true;
    }
}
