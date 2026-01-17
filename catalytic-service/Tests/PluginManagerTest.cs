using Xunit;
using Catalytic.Plugin;
using System.IO;
using System.Reflection;

namespace Catalytic.Tests;

public class PluginManagerTest
{
    [Fact]
    public async Task LoadPlugin_ShouldReturnError_WhenManifestMissing()
    {
        var tempDir = Path.Combine(Path.GetTempPath(), "catalytic_test_plugins", "missing_manifest");
        Directory.CreateDirectory(tempDir);
        
        try
        {
            var pm = new PluginManager(tempDir);
            // We need to call LoadPluginAsync using reflection because it is private
            var method = typeof(PluginManager).GetMethod("LoadPluginAsync", BindingFlags.NonPublic | BindingFlags.Instance);
            Assert.NotNull(method);
            
            var task = (Task<(LoadedPlugin? Plugin, string? Error)>)method.Invoke(pm, new object[] { tempDir })!;
            var result = await task;
            
            Assert.Null(result.Plugin);
            Assert.NotNull(result.Error);
            Assert.Contains("manifest.json", result.Error);
        }
        finally
        {
            if (Directory.Exists(tempDir)) Directory.Delete(tempDir, true);
        }
    }
}
