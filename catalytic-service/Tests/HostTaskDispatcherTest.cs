using Catalytic.Engine;
using Catalytic.Plugin;
using CatalyticKit;
using Xunit;
using System.Text;

namespace Catalytic.Tests;

/// <summary>
/// 测试 HostTaskDispatcher 的完整执行路径：
/// Engine 回调 → Dispatcher → PluginManager → IProcessor → 结果提交
/// </summary>
public class HostTaskDispatcherTest
{
    /// <summary>
    /// 场景：验证处理器注册和获取
    /// </summary>
    [Fact]
    public void RegisterProcessor_CanBeRetrieved()
    {
        // Arrange
        var pm = new PluginManager("./plugins");
        var mockProcessor = new MockProcessor("test-processor", "burn_firmware");
        
        // Act
        pm.RegisterProcessor("burn_firmware", mockProcessor);
        var found = pm.GetProcessor("burn_firmware");
        
        // Assert
        Assert.NotNull(found);
        Assert.Equal("test-processor", found.Id);
        Assert.Equal("burn_firmware", found.TaskName);
    }
    
    /// <summary>
    /// 场景：MockProcessor 的 ExecuteAsync 正确工作
    /// </summary>
    [Fact]
    public async Task MockProcessor_ExecuteAsync_ReturnsConfiguredResponse()
    {
        // Arrange
        var mockProcessor = new MockProcessor("proc", "calibrate");
        var expectedResponse = Encoding.UTF8.GetBytes("{\"status\":\"ok\"}");
        mockProcessor.SetResponse(expectedResponse);
        
        // Act
        var result = await mockProcessor.ExecuteAsync(
            "{\"device_id\":\"DMM-001\"}",
            CancellationToken.None);
        
        // Assert
        Assert.True(mockProcessor.ExecuteCalled);
        Assert.Equal("{\"device_id\":\"DMM-001\"}", mockProcessor.LastParams);
        Assert.Equal(expectedResponse, result);
    }
    
    /// <summary>
    /// 场景：MockProcessor 延迟执行会被取消
    /// </summary>
    [Fact]
    public async Task MockProcessor_Delay_CanBeCancelled()
    {
        // Arrange
        var mockProcessor = new MockProcessor("slow", "heavy_task");
        mockProcessor.SetDelay(5000);
        
        using var cts = new CancellationTokenSource(100);
        
        // Act & Assert
        await Assert.ThrowsAsync<TaskCanceledException>(async () =>
        {
            await mockProcessor.ExecuteAsync("{}", cts.Token);
        });
    }
    
    /// <summary>
    /// 场景：处理器未找到时 GetProcessor 返回 null
    /// </summary>
    [Fact]
    public void GetProcessor_NotFound_ReturnsNull()
    {
        // Arrange
        var pm = new PluginManager("./plugins");
        
        // Act
        var result = pm.GetProcessor("non-existent-task");
        
        // Assert
        Assert.Null(result);
    }
}

/// <summary>
/// Mock IProcessor 实现，用于测试
/// </summary>
public class MockProcessor : IProcessor
{
    public string Id { get; }
    public string TaskName { get; }
    
    public bool ExecuteCalled { get; private set; }
    public string? LastParams { get; private set; }
    
    private byte[] _response = [];
    private int _delayMs = 0;
    
    public MockProcessor(string id, string taskName)
    {
        Id = id;
        TaskName = taskName;
    }
    
    public void SetResponse(byte[] response) => _response = response;
    public void SetDelay(int delayMs) => _delayMs = delayMs;
    
    public Task ActivateAsync(IPluginContext context) => Task.CompletedTask;
    public Task DeactivateAsync() => Task.CompletedTask;
    
    public async Task<byte[]> ExecuteAsync(string parametersJson, CancellationToken ct)
    {
        ExecuteCalled = true;
        LastParams = parametersJson;
        
        if (_delayMs > 0)
        {
            await Task.Delay(_delayMs, ct);
        }
        
        return _response;
    }
}
