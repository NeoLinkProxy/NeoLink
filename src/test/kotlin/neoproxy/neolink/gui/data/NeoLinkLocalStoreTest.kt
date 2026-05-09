package neoproxy.neolink.gui.data

import neoproxy.neolink.config.ConfigOperator
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
        val sessionFile = tempDir.resolve("desktop-session.json")
        Files.writeString(sessionFile, "{not-json")

        val session = NeoLinkLocalStore.loadSession()

        assertEquals(SessionStoreDocument(), session)
        assertFalse(Files.exists(sessionFile))
        assertTrue(Files.exists(tempDir.resolve("desktop-session.json.corrupt")))
    }

    @Test
    @DisplayName("loadTunnels backs up unreadable JSON before returning an empty list")
    fun loadTunnelsBacksUpUnreadableJson() {
        val tunnelsFile = tempDir.resolve("tunnels.json")
        Files.writeString(tunnelsFile, "{not-json")

        val tunnels = NeoLinkLocalStore.loadTunnels()

        assertTrue(tunnels.isEmpty())
        assertFalse(Files.exists(tunnelsFile))
        assertTrue(Files.exists(tempDir.resolve("tunnels.json.corrupt")))
    }

    @Test
    @DisplayName("saveTunnels writes complete JSON and does not leave temp files behind")
    fun saveTunnelsWritesCompleteJsonWithoutTempResidue() {
        NeoLinkLocalStore.saveTunnels(listOf(TunnelCardState(id = "tunnel-a", keyAlias = "key-a")))

        val saved = Files.readString(tempDir.resolve("tunnels.json"))

        assertTrue(saved.contains("\"tunnel-a\""), saved)
        assertFalse(Files.exists(tempDir.resolve("tunnels.json.tmp")))
    }
}
