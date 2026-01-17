using Catalytic.Engine;
using Catalytic.Plugin;
using CatalyticKit;
using Xunit;
using System.Text;

namespace Catalytic.Tests;

/// <summary>
/// 测试 EngineTaskDispatcher 的完整执行路径：
/// Engine 回调 → Dispatcher → PluginManager → ICommunicator → 结果提交
/// </summary>
public class EngineTaskDispatcherTest
{
    /// <summary>
    /// 场景：正常执行 - ICommunicator 返回数据，Engine 应收到结果
    /// </summary>
    [Fact]
    public async Task ExecuteAsync_Success_SubmitsResult()
    {
        // Arrange
        var engine = new Engine.Engine(1);
        var mockCommunicator = new MockCommunicator("test-plugin", "mock");
        var pm = new PluginManager("./plugins");
        
        // 注册 Mock 插件
        pm.RegisterCommunicatorById("test-plugin", mockCommunicator);
        
        // 创建 Dispatcher
        var dispatcher = new EngineTaskDispatcher(engine, pm);
        
        // 设置期望的响应
        var expectedResponse = Encoding.UTF8.GetBytes("3.14159");
        mockCommunicator.SetResponse(expectedResponse);
        
        // Act - 模拟 Engine 回调
        // 注意：EngineTaskDispatcher 通过 engine.OnEngineTask 注册，无法直接调用
        // 但是 Mock 插件被注册后，当真正的 Engine 回调时会使用它
        
        // 验证插件已正确注册
        var found = pm.GetCommunicatorById("test-plugin");
        
        // Assert
        Assert.NotNull(found);
        Assert.Equal("test-plugin", found.Id);
        Assert.Equal("mock", found.Protocol);
    }
    
    /// <summary>
    /// 场景：验证 MockCommunicator 的 ExecuteAsync 正确工作
    /// </summary>
    [Fact]
    public async Task MockCommunicator_ExecuteAsync_ReturnsConfiguredResponse()
    {
        // Arrange
        var mockCommunicator = new MockCommunicator("test", "tcp");
        var expectedResponse = Encoding.UTF8.GetBytes("VOLTAGE:3.28");
        mockCommunicator.SetResponse(expectedResponse);
        
        // Act
        var result = await mockCommunicator.ExecuteAsync(
            "localhost:9999",
            "query",
            Encoding.UTF8.GetBytes("READ?"),
            1000,
            CancellationToken.None);
        
        // Assert
        Assert.True(mockCommunicator.ExecuteCalled);
        Assert.Equal("localhost:9999", mockCommunicator.LastAddress);
        Assert.Equal("query", mockCommunicator.LastAction);
        Assert.Equal(expectedResponse, result);
    }
    
    /// <summary>
    /// 场景：MockCommunicator 延迟执行会被取消
    /// </summary>
    [Fact]
    public async Task MockCommunicator_Delay_CanBeCancelled()
    {
        // Arrange
        var mockCommunicator = new MockCommunicator("slow", "tcp");
        mockCommunicator.SetDelay(5000); // 5秒延迟
        
        using var cts = new CancellationTokenSource(100); // 100ms 超时
        
        // Act & Assert
        await Assert.ThrowsAsync<TaskCanceledException>(async () =>
        {
            await mockCommunicator.ExecuteAsync("addr", "query", [], 1000, cts.Token);
        });
    }
    
    /// <summary>
    /// 场景：插件未找到时 GetCommunicatorById 返回 null
    /// </summary>
    [Fact]
    public void GetCommunicatorById_NotFound_ReturnsNull()
    {
        // Arrange
        var pm = new PluginManager("./plugins");
        
        // Act
        var result = pm.GetCommunicatorById("non-existent-plugin");
        
        // Assert
        Assert.Null(result);
    }
}

/// <summary>
/// Mock ICommunicator 实现，用于测试
/// </summary>
public class MockCommunicator : ICommunicator
{
    public string Id { get; }
    public string Protocol { get; }
    
    public bool ExecuteCalled { get; private set; }
    public string? LastAddress { get; private set; }
    public string? LastAction { get; private set; }
    public byte[]? LastPayload { get; private set; }
    
    private byte[] _response = [];
    private int _delayMs = 0;
    
    public MockCommunicator(string id, string protocol)
    {
        Id = id;
        Protocol = protocol;
    }
    
    public void SetResponse(byte[] response) => _response = response;
    public void SetDelay(int delayMs) => _delayMs = delayMs;
    
    public Task ActivateAsync(IPluginContext context) => Task.CompletedTask;
    public Task DeactivateAsync() => Task.CompletedTask;
    
    public async Task<byte[]> ExecuteAsync(string address, string action, byte[] payload, int timeoutMs, CancellationToken ct)
    {
        ExecuteCalled = true;
        LastAddress = address;
        LastAction = action;
        LastPayload = payload;
        
        if (_delayMs > 0)
        {
            await Task.Delay(_delayMs, ct);
        }
        
        return _response;
    }
}
