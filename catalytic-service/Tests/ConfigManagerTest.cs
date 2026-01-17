using Xunit;
using Catalytic;
using System.IO;

namespace Catalytic.Tests;

public class ConfigManagerTest
{
    [Fact]
    public void LoadFromConfigFile_ShouldParseCorrectly()
    {
        // Setup a temporary config file
        var tempFile = Path.GetTempFileName();
        var tempDir = Path.Combine(Path.GetTempPath(), "catalytic_test_dir");
        Directory.CreateDirectory(tempDir);
        
        try
        {
            // Test legacy behavior: "5000" as string for int port
            var json = $@"{{
                ""working_dir"": ""{tempDir.Replace("\\", "\\\\")}"",
                ""grpc_port"": ""5000"", 
                ""slot_count"": 4
            }}";
            File.WriteAllText(tempFile, json);
            
            var result = ConfigManager.LoadFromConfigFile(tempFile);
            
            Assert.NotNull(result);
            Assert.Equal(tempDir, result.Value.WorkingDir);
            Assert.Equal(5000, result.Value.Config.GrpcPort);
            Assert.Equal(4, result.Value.Config.SlotCount);
        }
        finally
        {
            if (File.Exists(tempFile)) File.Delete(tempFile);
            if (Directory.Exists(tempDir)) Directory.Delete(tempDir);
        }
    }
}
