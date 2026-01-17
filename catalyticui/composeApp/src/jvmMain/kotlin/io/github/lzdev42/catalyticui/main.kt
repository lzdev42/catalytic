package io.github.lzdev42.catalyticui

import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.lzdev42.catalyticui.config.ConfigRepository
import io.github.lzdev42.catalyticui.data.grpc.GrpcClientManager
import io.github.lzdev42.catalyticui.data.grpc.GrpcRepository
import io.github.lzdev42.catalyticui.host.HostLauncher
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.logging.Logger

private val logger = Logger.getLogger("Main")

fun main() = application {
    FileKit.init(appId = "Catalytic")
    
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var appConfig by remember { mutableStateOf(ConfigRepository.load()) }
    
    // Real gRPC Repository for production
    val clientManager = remember { GrpcClientManager(appScope) }
    val grpcRepository = remember { GrpcRepository(clientManager, appScope) }
    
    // [VERIFICATION MODE] Mock Repository - uncomment to use for UI testing
    // val grpcRepository = remember { io.github.lzdev42.catalyticui.data.mock.MockEngineRepository() }
    
    var connectedPort by remember { mutableStateOf(0) }
    
    // Initialize and connect to Host
    LaunchedEffect(appConfig.workingDir) {
        val workingDir = appConfig.workingDir
        if (workingDir != null) {
            logger.info("Starting with working directory: $workingDir")
            
            // Ensure working directory structure exists
            io.github.lzdev42.catalyticui.config.WorkingDirectoryInitializer.initialize(workingDir)
            
            // Initialize LogManager
            io.github.lzdev42.catalyticui.log.LogManager.init("$workingDir/logs")
            io.github.lzdev42.catalyticui.log.LogManager.addLog("应用启动")
            
            // Step 1: Kill any existing Catalytic Host process (by process name, not port)
            logger.info("Killing any existing Host process...")
            HostLauncher.forceKillHost()
            killAllCatalyticProcesses()
            delay(1000)
            
            // Step 2: Find a free port
            var targetPort = appConfig.grpcPort
            if (HostLauncher.isPortInUse(targetPort)) {
                targetPort = HostLauncher.findFreePort(targetPort + 1)
                logger.info("Port ${appConfig.grpcPort} still occupied, using $targetPort")
            }
            
            // Step 3: Start Host on free port (pass config.json path, not workDir)
            val configPath = ConfigRepository.getConfigFile().absolutePath
            logger.info("Starting Host on port $targetPort with config: $configPath")
            val launchedPort = HostLauncher.startHost(configPath, targetPort)
            if (launchedPort != null) {
                if (HostLauncher.waitForReady(launchedPort)) {
                    val result = grpcRepository.connect("localhost", launchedPort)
                    if (result.isSuccess) {
                        logger.info("Connected to Host on port $launchedPort")
                        connectedPort = launchedPort
                        
                        // Update config if port changed
                        if (launchedPort != appConfig.grpcPort) {
                            val newConfig = appConfig.copy(grpcPort = launchedPort)
                            ConfigRepository.save(newConfig)
                            appConfig = newConfig
                        }
                    } else {
                        logger.severe("Failed to connect: ${result.exceptionOrNull()?.message}")
                    }
                } else {
                    logger.severe("Host startup timeout")
                }
            } else {
                logger.severe("Failed to start Host")
            }
        }
    }

    Window(
        onCloseRequest = {
            appScope.launch {
                try {
                    if (connectedPort > 0) {
                        logger.info("Sending shutdown command...")
                        grpcRepository.shutdown()
                        delay(500)
                    }
                    HostLauncher.forceKillHost()
                } catch (e: Exception) {
                    logger.warning("Shutdown error: ${e.message}")
                } finally {
                    exitApplication()
                }
            }
        },
        title = "Catalytic",
    ) {
        App(
            repository = grpcRepository,
            initialConfig = appConfig,
            onConfigChange = { newConfig ->
                ConfigRepository.save(newConfig)
                appConfig = newConfig
            }
        )
    }
}

/**
 * Kill ALL Catalytic processes by name (cross-platform, forced)
 * Windows: taskkill /F /IM Catalytic.exe
 * macOS/Linux: pkill -9 -f Catalytic
 */
private fun killAllCatalyticProcesses() {
    val os = System.getProperty("os.name").lowercase()
    val command = when {
        os.contains("win") -> arrayOf("cmd", "/c", "taskkill /F /IM CatalyticService.exe 2>nul & taskkill /F /IM CatalyticService 2>nul & exit /b 0")
        else -> arrayOf("sh", "-c", "pkill -9 -f 'CatalyticService' 2>/dev/null; killall -9 CatalyticService 2>/dev/null; exit 0")
    }
    
    try {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
        if (process.isAlive) {
            process.destroyForcibly()
        }
        logger.info("Killed all Catalytic processes (OS: $os)")
    } catch (e: Exception) {
        logger.warning("Failed to kill Catalytic processes: ${e.message}")
    }
}