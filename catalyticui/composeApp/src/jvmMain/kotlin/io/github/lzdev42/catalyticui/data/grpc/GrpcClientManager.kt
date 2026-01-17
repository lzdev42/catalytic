package io.github.lzdev42.catalyticui.data.grpc

import com.catalytic.grpc.*
import com.squareup.wire.GrpcClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * gRPC 连接状态
 */
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * gRPC 客户端管理器
 * 
 * 职责：
 * - 管理 GrpcClient 生命周期
 * - 提供连接状态
 * - 处理自动重连
 */
class GrpcClientManager(
    private val scope: CoroutineScope
) {
    private var grpcClient: GrpcClient? = null
    private var hostServiceClient: HostServiceClient? = null
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private var reconnectJob: Job? = null
    
    /**
     * 连接到 Host
     * @param host 主机地址，如 "localhost"
     * @param port 端口，如 5000
     */
    suspend fun connect(host: String, port: Int): kotlin.Result<HostServiceClient> {
        _connectionState.value = ConnectionState.Connecting
        
        return try {
            val okHttpClient = OkHttpClient.Builder()
                .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)  // 无超时，支持长连接流式订阅
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
            
            val client = GrpcClient.Builder()
                .client(okHttpClient)
                .baseUrl("http://$host:$port")
                .build()
            
            grpcClient = client
            val serviceClient = GrpcHostServiceClient(client)
            hostServiceClient = serviceClient
            
            // 验证连接：尝试获取系统信息
            withContext(Dispatchers.IO) {
                serviceClient.GetSystemInfo().execute(Empty())
            }
            
            _connectionState.value = ConnectionState.Connected
            kotlin.Result.success(serviceClient)
            
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(e.message ?: "连接失败")
            kotlin.Result.failure(e)
        }
    }
    
    /**
     * 获取当前客户端（如果已连接）
     */
    fun getClient(): HostServiceClient? = hostServiceClient
    
    /**
     * 断开连接
     */
    fun disconnect() {
        reconnectJob?.cancel()
        hostServiceClient = null
        grpcClient = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    /**
     * 启动自动重连
     */
    fun startAutoReconnect(host: String, port: Int, maxRetries: Int = 5) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var retryCount = 0
            var delayMs = 1000L
            
            while (retryCount < maxRetries && isActive) {
                if (_connectionState.value !is ConnectionState.Connected) {
                    val result = connect(host, port)
                    if (result.isSuccess) {
                        break
                    }
                    retryCount++
                    delay(delayMs)
                    delayMs = (delayMs * 2).coerceAtMost(30000L) // 指数退避，最大30秒
                } else {
                    break
                }
            }
        }
    }
}
