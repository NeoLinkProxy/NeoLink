package neoproxy.neolink.android.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.ceroxe.api.neolink.NeoNode
import java.lang.reflect.Field

class TunnelInputGuardTest {

    @Test
    fun buildStartConfigAllowsDisablingBothProtocolsLikeDesktop() {
        val result = TunnelInputGuard.buildStartConfig(validStartRequest(tcpEnabled = false, udpEnabled = false))

        assertNotNull(result.config)
        assertNull(result.error)
        assertEquals(false, result.config!!.tcpEnabled)
        assertEquals(false, result.config.udpEnabled)
    }

    @Test
    fun buildStartConfigRejectsOutOfRangePortsBeforeServiceStart() {
        val result = TunnelInputGuard.buildStartConfig(validStartRequest(localPort = "70000"))

        assertNull(result.config)
        assertEquals("本地端口无效 (1-65535)", result.error)
    }

    @Test
    fun buildStartConfigRejectsNonPositiveHeartbeatBeforeServiceStart() {
        val result = TunnelInputGuard.buildStartConfig(validStartRequest(heartbeatDelay = "0"))

        assertNull(result.config)
        assertEquals("心跳间隔无效 (必须大于 0ms)", result.error)
    }

    @Test
    fun buildStartConfigUsesSanitizedSelectedNodeForRemoteEndpoint() {
        val node = NeoNode(" Public ", "node-1", " public.example.com ", "", 4100, 4200)
        val result = TunnelInputGuard.buildStartConfig(
            validStartRequest(
                remoteDomain = "manual.example.com",
                hookPort = "44801",
                connectPort = "44802",
                selectedNodeId = "node-1",
                nodes = listOf(node)
            )
        )

        val config = result.config
        assertNotNull(config)
        assertNull(result.error)
        assertEquals("public.example.com", config!!.remoteDomain)
        assertEquals(4100, config.hookPort)
        assertEquals(4200, config.connectPort)
    }

    @Test
    fun sanitizeFetchedNodesDropsInvalidNodesAndNormalizesFallbackFields() {
        val validWithoutNameOrId = NeoNode("node", "node-1", " node.example.com ", null, 4100, 4200).apply {
            corruptTextField("name", "")
            corruptTextField("realId", "")
        }
        val invalidBlankAddress = NeoNode("bad", "bad-1", "bad.example.com", "", 4100, 4200).apply {
            corruptTextField("address", " ")
        }
        val invalidPort = NeoNode("bad", "bad-2", "bad.example.com", "", 4100, 4200).apply {
            corruptIntField("hookPort", 0)
        }

        val sanitized = TunnelInputGuard.sanitizeFetchedNodes(
            listOf(validWithoutNameOrId, invalidBlankAddress, invalidPort)
        )

        assertEquals(1, sanitized.size)
        assertEquals("node.example.com", sanitized.single().name)
        assertEquals("node.example.com", sanitized.single().address)
        assertTrue(sanitized.single().realId.startsWith("node.example.com:4100:4200:"))
    }

    @Test
    fun validateNodeSelectionReportsInvalidNodeInsteadOfUpdatingForm() {
        val node = NeoNode("bad", "bad", "bad.example.com", "", 4100, 4200).apply {
            corruptTextField("address", "")
        }
        val result = TunnelInputGuard.validateNodeSelection(node)

        assertNull(result.selection)
        assertEquals("节点数据无效：地址为空", result.error)
    }

    private fun validStartRequest(
        remoteDomain: String = "manual.example.com",
        hookPort: String = "44801",
        connectPort: String = "44802",
        accessKey: String = "access-key",
        localPort: String = "25565",
        tcpEnabled: Boolean = true,
        udpEnabled: Boolean = true,
        ppv2Enabled: Boolean = false,
        autoReconnect: Boolean = true,
        heartbeatDelay: String = "1000",
        selectedNodeId: String? = null,
        nodes: List<NeoNode> = emptyList()
    ): TunnelInputGuard.StartRequest {
        return TunnelInputGuard.StartRequest(
            remoteDomain = remoteDomain,
            hookPort = hookPort,
            connectPort = connectPort,
            accessKey = accessKey,
            localPort = localPort,
            tcpEnabled = tcpEnabled,
            udpEnabled = udpEnabled,
            ppv2Enabled = ppv2Enabled,
            autoReconnect = autoReconnect,
            heartbeatDelay = heartbeatDelay,
            selectedNodeId = selectedNodeId,
            nodes = nodes
        )
    }

    private fun NeoNode.corruptTextField(name: String, value: String?) {
        declaredField(name).set(this, value)
    }

    private fun NeoNode.corruptIntField(name: String, value: Int) {
        declaredField(name).setInt(this, value)
    }

    private fun declaredField(name: String): Field {
        return NeoNode::class.java.getDeclaredField(name).apply {
            isAccessible = true
        }
    }
}
