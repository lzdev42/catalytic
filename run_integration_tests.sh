#!/bin/bash
# 集成测试运行脚本

echo "========================================="
echo "Catalytic 集成测试"
echo "========================================="
echo ""

# 检查 Host 是否运行
echo "⏳ 检查 Host 服务..."
if ! lsof -i :5000 > /dev/null 2>&1; then
    echo "❌ Host 服务未运行 (端口 5000)"
    echo ""
    echo "请先启动 Host:"
    echo "  cd /Users/liuzhe/Projects/sheji/catalytic"
    echo "  dotnet run --project Catalytic"
    echo ""
    exit 1
fi

echo "✅ Host 服务运行中"
echo ""

# 运行测试
echo "========================================="
echo " Layer 1: gRPC 连接测试"
echo "========================================="
cd /Users/liuzhe/Projects/sheji/catalyticui
./gradlew jvmTest --tests "io.github.lzdev42.catalyticui.integration.GrpcConnectionTest"

echo ""
echo "========================================="
echo "测试完成"
echo "========================================="
