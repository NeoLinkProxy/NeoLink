package neoproxy.neolink.gui.data

import neoproxy.neolink.config.ConfigOperator
import neoproxy.neolink.gui.model.DesktopConfigSettings
import neoproxy.neolink.gui.model.SessionStoreDocument
import neoproxy.neolink.gui.model.TunnelCardState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@DisplayName("NeoLinkLocalStoreTest")
class NeoLinkLocalStoreTest {
    @TempDir
    lateinit var tempDir: Path

    private var originalWorkingDir: String? = null
    private var originalBasePackageDir: String? = null

    @BeforeEach
    fun setUp() {
        originalWorkingDir = ConfigOperator.WORKING_DIR
        originalBasePackageDir = ConfigOperator.BASE_PACKAGE_DIR
        ConfigOperator.WORKING_DIR = tempDir.toString()
        ConfigOperator.BASE_PACKAGE_DIR = tempDir.toString()
    }

    @AfterEach
    fun tearDown() {
        ConfigOperator.WORKING_DIR = originalWorkingDir
        ConfigOperator.BASE_PACKAGE_DIR = originalBasePackageDir
    }

    @Test
    @DisplayName("loadSession backs up unreadable JSON before returning defaults")
    fun loadSessionBacksUpUnreadableJson() {
        val sessionDir = tempDir.resolve("state")
        Files.createDirectories(sessionDir)
        val sessionFile = sessionDir.resolve("desktop-session.json")
        Files.writeString(sessionFile, "{not-json")

        val session = NeoLinkLocalStore.loadSession()

        assertEquals(SessionStoreDocument(), session)
        assertFalse(Files.exists(sessionFile))
        assertTrue(Files.exists(sessionDir.resolve("desktop-session.json.corrupt")))
    }

    @Test
    @DisplayName("loadTunnels backs up unreadable JSON before returning an empty list")
    fun loadTunnelsBacksUpUnreadableJson() {
        val stateDir = tempDir.resolve("state")
        Files.createDirectories(stateDir)
        val tunnelsFile = stateDir.resolve("tunnels.json")
        Files.writeString(tunnelsFile, "{not-json")

        val tunnels = NeoLinkLocalStore.loadTunnels()

        assertTrue(tunnels.isEmpty())
        assertFalse(Files.exists(tunnelsFile))
        assertTrue(Files.exists(stateDir.resolve("tunnels.json.corrupt")))
    }

    @Test
    @DisplayName("saveTunnels writes complete JSON and does not leave temp files behind")
    fun saveTunnelsWritesCompleteJsonWithoutTempResidue() {
        NeoLinkLocalStore.saveTunnels(listOf(TunnelCardState(id = "tunnel-a", keyAlias = "key-a")))

        val stateDir = tempDir.resolve("state")
        val saved = Files.readString(stateDir.resolve("tunnels.json"))

        assertTrue(saved.contains("\"tunnel-a\""), saved)
        assertFalse(Files.exists(stateDir.resolve("tunnels.json.tmp")))
    }

    @Test
    @DisplayName("saveDesktopConfig preserves template comments and updates managed keys")
    fun saveDesktopConfigPreservesTemplateCommentsAndUpdatesManagedKeys() {
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        Files.writeString(
            configDir.resolve("config.cfg"),
            """
            # NAS explanation now belongs to the settings screen.
            NAS_URL=https://old.example.com/

            # Keep legacy runtime settings intact.
            HOST_HOOK_PORT=44801
            NKM_NODELIST_URL=https://old-node.example.com/client/nodelist
            """.trimIndent()
        )

        NeoLinkLocalStore.saveDesktopConfig(
            DesktopConfigSettings(
                nasUrl = "https://new.example.com/",
                nkmNodeListUrl = "https://new-node.example.com/client/nodelist",
                enableAutoUpdate = false,
                heartbeatPacketDelay = 1500,
                reconnectionIntervalSeconds = 9
            )
        )

        val saved = Files.readString(configDir.resolve("config.cfg"))
        assertTrue(saved.contains("# NAS explanation now belongs to the settings screen."), saved)
        assertTrue(saved.contains("NAS_URL=https://new.example.com/"), saved)
        assertTrue(saved.contains("NKM_NODELIST_URL=https://new-node.example.com/client/nodelist"), saved)
        assertTrue(saved.contains("ENABLE_AUTO_UPDATE=false"), saved)
        assertTrue(saved.contains("HEARTBEAT_PACKET_DELAY=1500"), saved)
        assertTrue(saved.contains("RECONNECTION_INTERVAL=9"), saved)
        assertTrue(saved.contains("HOST_HOOK_PORT=44801"), saved)
    }
}
