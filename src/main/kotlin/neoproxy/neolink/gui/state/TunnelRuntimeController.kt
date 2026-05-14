package neoproxy.neolink.gui.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import neoproxy.neolink.gui.model.TunnelCardState
import top.ceroxe.api.neolink.NeoLinkAPI
import top.ceroxe.api.print.log.LogType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the cancellable runtime boundary for tunnel processes.
 *
 * The ViewModel should decide what the user wants; this class decides how a long-running
 * NeoLinkAPI instance is started, stopped, reconnected, and cleaned up without leaving
 * background jobs behind.
 */
internal class TunnelRuntimeController(
    private val scope: CoroutineScope,
    private val buildApi: (TunnelCardState, String) -> NeoLinkAPI,
    private val isAutoReconnectEnabled: (String) -> Boolean,
    private val reconnectionIntervalSeconds: () -> Int,
    private val appendLog: (String, String, LogType) -> Unit,
    private val onActiveConnectionsReset: (String) -> Unit,
    private val onStopped: suspend (String) -> Unit
) {
    private val activeApis = ConcurrentHashMap<String, NeoLinkAPI>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val runRequested = ConcurrentHashMap<String, AtomicBoolean>()

    fun isActive(tunnelId: String): Boolean {
        return activeJobs.containsKey(tunnelId)
    }

    fun start(tunnel: TunnelCardState): Boolean {
        if (activeJobs.containsKey(tunnel.id)) {
            return false
        }
        runRequested[tunnel.id] = AtomicBoolean(true)
        val job = scope.launch(Dispatchers.IO) {
            runLoop(tunnel.id, tunnel.copy())
        }
        activeJobs[tunnel.id] = job
        job.invokeOnCompletion { activeJobs.remove(tunnel.id, job) }
        return true
    }

    fun stop(tunnelId: String) {
        runRequested[tunnelId]?.set(false)
        activeApis.remove(tunnelId)?.close()
        activeJobs.remove(tunnelId)?.cancel()
    }

    fun setProtocolFlags(tunnelId: String, tcpEnabled: Boolean, udpEnabled: Boolean): Boolean {
        val api = activeApis[tunnelId] ?: return false
        api.updateRuntimeProtocolFlags(tcpEnabled, udpEnabled)
        return true
    }

    fun setPpv2Enabled(tunnelId: String, enabled: Boolean): Boolean {
        val api = activeApis[tunnelId] ?: return false
        api.setPPV2Enabled(enabled)
        return true
    }

    private suspend fun runLoop(tunnelId: String, tunnelSnapshot: TunnelCardState) {
        try {
            do {
                if (runRequested[tunnelId]?.get() != true) {
                    break
                }
                val api = buildApi(tunnelSnapshot, tunnelId)
                activeApis[tunnelId] = api
                try {
                    api.start()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    appendLog(tunnelId, "隧道异常：${e.message ?: e.javaClass.simpleName}", LogType.ERROR)
                } finally {
                    activeApis.remove(tunnelId, api)
                    api.close()
                    onActiveConnectionsReset(tunnelId)
                }

                if (runRequested[tunnelId]?.get() == true && isAutoReconnectEnabled(tunnelId)) {
                    val intervalSeconds = reconnectionIntervalSeconds().coerceAtLeast(1)
                    appendLog(tunnelId, "自动重连将在 ${intervalSeconds} 秒后执行。", LogType.INFO)
                    delay(intervalSeconds * 1000L)
                }
            } while (runRequested[tunnelId]?.get() == true && isAutoReconnectEnabled(tunnelId))
        } finally {
            runRequested.remove(tunnelId)
            withContext(NonCancellable + Dispatchers.Main) {
                onStopped(tunnelId)
            }
        }
    }
}
