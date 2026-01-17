package io.github.lzdev42.catalyticui.host

import kotlinx.coroutines.delay
import java.io.File
import java.net.Socket
import java.util.logging.Logger

/**
 * Host process lifecycle manager.
 * 
 * Responsibilities:
 * - Find Host executable in ./host/ directory
 * - Check if Host is already running (port probe)
 * - Start Host process with correct parameters
 * - Wait for Host to become ready
 */
object HostLauncher {
    
    private val logger = Logger.getLogger("HostLauncher")
    
    private const val DEFAULT_PORT = 5000
    private const val HOST_EXECUTABLE = "CatalyticService"
    private const val HOST_DIR = "service"
    
    private var hostProcess: Process? = null
    private var currentPort: Int = DEFAULT_PORT
    
    /**
     * Get the port that Host is currently running on.
     */
    fun getCurrentPort(): Int = currentPort
    
    /**
     * Find the Host executable path.
     * Searches in multiple common locations relative to working directory and app home.
     */
    fun findHostPath(): File? {
        val cwd = File(System.getProperty("user.dir"))
        logger.info("Current Working Directory: ${cwd.absolutePath}")
        
        // 优先使用 compose.application.resources.dir (打包后的程序目录)
        val appResourcesDir = System.getProperty("compose.application.resources.dir")
        
        val possibleDirs = mutableListOf<File>()
        
        // 打包后的程序目录
        if (appResourcesDir != null) {
            possibleDirs.add(File(appResourcesDir, HOST_DIR))
            // macOS standard Resources path (when appResourcesDir is Contents/app/resources)
            possibleDirs.add(File(File(appResourcesDir).parentFile?.parentFile, "Resources/$HOST_DIR"))
        }
        
        // 开发时的常见位置
        possibleDirs.addAll(listOf(
            File(cwd, HOST_DIR),                     // ./host
            File(cwd, "composeApp/$HOST_DIR"),       // ./composeApp/host (gradle run from root)
            File(cwd, "../$HOST_DIR"),               // ../host (running from bin)
        ))
        
        for (dir in possibleDirs) {
            val hostFile = File(dir, HOST_EXECUTABLE)
            logger.info("Checking: ${hostFile.absolutePath}")
            
            if (hostFile.exists()) {
                if (hostFile.canExecute()) {
                    logger.info("Found Host at: ${hostFile.absolutePath}")
                    return hostFile
                } else {
                    logger.warning("Found Host at ${hostFile.absolutePath} but not executable. Setting permission...")
                    if (hostFile.setExecutable(true)) {
                        logger.info("Set executable permission success.")
                        return hostFile
                    }
                }
            }
        }
        
        logger.severe("Host executable '$HOST_EXECUTABLE' not found in any checked locations.")
        return null
    }

    /**
     * Check if a service is running on the given port.
     */
    fun isPortInUse(port: Int): Boolean {
        return try {
            Socket("localhost", port).use { true }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Find a free port starting from startPort.
     */
    fun findFreePort(startPort: Int = DEFAULT_PORT): Int {
        var port = startPort
        while (port < 65535) {
            if (!isPortInUse(port)) {
                return port
            }
            port++
        }
        return 0
    }
    
    /**
     * Start the Host process with the given parameters.
     * 
     * @param configPath The config.json file path (Host reads working_dir from it)
     * @param port The gRPC port to use
     * @return The port number if started successfully, null if failed
     */
    fun startHost(configPath: String, port: Int): Int? {
        if (isPortInUse(port)) {
            logger.info("Port $port is already in use")
            return null
        }
        
        val hostPath = findHostPath()
        if (hostPath == null) {
            logger.severe("Host executable not found")
            return null
        }
        
        return try {
            logger.info("Starting Host: ${hostPath.absolutePath} --port $port --config $configPath")
            
            val processBuilder = ProcessBuilder(
                hostPath.absolutePath,
                "--port", port.toString(),
                "--config", configPath
            )
                .directory(hostPath.parentFile)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
            
            hostProcess = processBuilder.start()
            currentPort = port
            logger.info("Host process started (PID: ${hostProcess?.pid()})")
            port
        } catch (e: Exception) {
            logger.severe("Failed to start Host: ${e.message}")
            null
        }
    }
    
    /**
     * Wait for Host to become ready (port accessible).
     * @param port The port to check
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if Host became ready within timeout
     */
    suspend fun waitForReady(port: Int, timeoutMs: Long = 5000): Boolean {
        val startTime = System.currentTimeMillis()
        val pollInterval = 200L
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isPortInUse(port)) {
                logger.info("Host is ready on port $port")
                return true
            }
            delay(pollInterval)
        }
        
        logger.warning("Timeout waiting for Host to become ready on port $port")
        return false
    }
    
    /**
     * Check if we started the Host process (vs connecting to existing).
     */
    fun isHostManagedByUs(): Boolean {
        return hostProcess?.isAlive == true
    }
    
    /**
     * Force kill the Host process if we started it.
     */
    fun forceKillHost() {
        hostProcess?.let { process ->
            if (process.isAlive) {
                logger.info("Force killing Host process")
                process.destroyForcibly()
            }
        }
        hostProcess = null
    }
}
