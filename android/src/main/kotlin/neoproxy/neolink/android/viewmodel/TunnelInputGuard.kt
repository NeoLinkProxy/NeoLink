package neoproxy.neolink.android.viewmodel

import neoproxy.neolink.android.service.TunnelConfig
import top.ceroxe.api.neolink.NeoNode

/**
 * Android UI 点击链路的纯 Kotlin 防护层。
 *
 * 这里刻意不依赖 Context / Service / Compose，让关键输入边界可以在 JVM 单元测试里直接覆盖。
 */
internal object TunnelInputGuard {
    data class StartRequest(
        val remoteDomain: String,
        val hookPort: String,
        val connectPort: String,
        val accessKey: String,
        val localPort: String,
        val tcpEnabled: Boolean,
        val udpEnabled: Boolean,
        val ppv2Enabled: Boolean,
        val autoReconnect: Boolean,
        val heartbeatDelay: String,
        val selectedNodeId: String?,
        val nodes: List<NeoNode>
    )

    data class ValidationResult(
        val config: TunnelConfig?,
        val error: String?
    )

    data class NodeSelection(
        val address: String,
        val hookPort: Int,
        val connectPort: Int
    )

    data class NodeSelectionResult(
        val selection: NodeSelection?,
        val error: String?
    )

    fun buildStartConfig(request: StartRequest): ValidationResult {
        if (request.remoteDomain.isBlank()) return ValidationResult(null, "请输入远程域名")
        if (request.accessKey.isBlank()) return ValidationResult(null, "请输入访问密钥")
        val localPort = parsePort(request.localPort) ?: return ValidationResult(null, "本地端口无效 (1-65535)")
        val hookPort = parsePort(request.hookPort) ?: return ValidationResult(null, "Hook 端口无效 (1-65535)")
        val connectPort = parsePort(request.connectPort) ?: return ValidationResult(null, "Connect 端口无效 (1-65535)")
        val heartbeat = request.heartbeatDelay.toIntOrNull()
            ?.takeIf { it > 0 }
            ?: return ValidationResult(null, "心跳间隔无效 (必须大于 0ms)")

        val selectedNode = request.nodes.firstOrNull { it.realId == request.selectedNodeId }
        val config = TunnelConfig(
            remoteDomain = selectedNode?.address?.trim()?.takeIf { it.isNotBlank() } ?: request.remoteDomain.trim(),
            hookPort = selectedNode?.hookPort?.takeIf { it in 1..65535 } ?: hookPort,
            connectPort = selectedNode?.connectPort?.takeIf { it in 1..65535 } ?: connectPort,
            key = request.accessKey.trim(),
            localPort = localPort,
            tcpEnabled = request.tcpEnabled,
            udpEnabled = request.udpEnabled,
            ppv2Enabled = request.ppv2Enabled,
            autoReconnect = request.autoReconnect,
            heartbeatDelay = heartbeat
        )
        return ValidationResult(config, null)
    }

    fun sanitizeFetchedNodes(fetched: List<NeoNode>): List<NeoNode> {
        return fetched.mapIndexedNotNull { index, node ->
            val address = node.address?.trim()?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val hookPort = node.hookPort.takeIf { it in 1..65535 } ?: return@mapIndexedNotNull null
            val connectPort = node.connectPort.takeIf { it in 1..65535 } ?: return@mapIndexedNotNull null
            val name = node.name?.trim()?.takeIf { it.isNotBlank() } ?: address
            val realId = node.realId?.trim()?.takeIf { it.isNotBlank() } ?: "$address:$hookPort:$connectPort:$index"
            val icon = node.iconSvg?.trim().orEmpty()
            NeoNode(name, realId, address, icon, hookPort, connectPort)
        }
    }

    fun validateNodeSelection(node: NeoNode): NodeSelectionResult {
        val address = node.address?.trim()?.takeIf { it.isNotBlank() }
            ?: return NodeSelectionResult(null, "节点数据无效：地址为空")
        val hookPort = node.hookPort.takeIf { it in 1..65535 }
            ?: return NodeSelectionResult(null, "节点数据无效：Hook 端口越界")
        val connectPort = node.connectPort.takeIf { it in 1..65535 }
            ?: return NodeSelectionResult(null, "节点数据无效：Connect 端口越界")

        return NodeSelectionResult(
            NodeSelection(address, hookPort, connectPort),
            null
        )
    }

    private fun parsePort(value: String): Int? {
        return value.toIntOrNull()?.takeIf { it in 1..65535 }
    }
}
