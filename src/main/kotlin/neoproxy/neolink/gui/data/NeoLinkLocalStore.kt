package neoproxy.neolink.gui.data
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import neoproxy.neolink.app.ApplicationFiles
import neoproxy.neolink.config.LineConfigParser
import neoproxy.neolink.gui.config.DEFAULT_NAS_URL
import neoproxy.neolink.gui.config.DEFAULT_NKM_NODELIST_URL
import neoproxy.neolink.gui.model.SessionStoreDocument
import neoproxy.neolink.gui.model.TunnelCardState
import neoproxy.neolink.gui.model.TunnelStoreDocument
import neoproxy.neolink.util.Debugger.debugOperation
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object NeoLinkJson {
    val mapper: ObjectMapper = ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(SerializationFeature.INDENT_OUTPUT)
}

object NeoLinkLocalStore {
    fun loadNasUrlFromConfig(): String {
        val configFile = ApplicationFiles.configFile()
        if (!configFile.isFile) {
            return DEFAULT_NAS_URL
        }
        return try {
            val parser = LineConfigParser(configFile)
            parser.load()
            parser.getOptional("NAS_URL").orElse("").ifBlank { DEFAULT_NAS_URL }
        } catch (e: Exception) {
            debugOperation(e)
            DEFAULT_NAS_URL
        }
    }

    fun loadNkmNodeListUrlFromConfig(): String {
        val configFile = ApplicationFiles.configFile()
        if (!configFile.isFile) {
            return DEFAULT_NKM_NODELIST_URL
        }
        return try {
            val parser = LineConfigParser(configFile)
            parser.load()
            parser.getOptional("NKM_NODELIST_URL").orElse("").ifBlank { DEFAULT_NKM_NODELIST_URL }
        } catch (e: Exception) {
            debugOperation(e)
            DEFAULT_NKM_NODELIST_URL
        }
    }

    fun ensureDesktopConfigDefaults() {
        val configFile = ApplicationFiles.configFile()
        val missingDefaults = if (!configFile.isFile) {
            true
        } else {
            try {
                val parser = LineConfigParser(configFile)
                parser.load()
                parser.getOptional("NAS_URL").orElse("").isBlank() ||
                    parser.getOptional("NKM_NODELIST_URL").orElse("").isBlank()
            } catch (e: Exception) {
                debugOperation(e)
                true
            }
        }
        if (missingDefaults) {
            saveDesktopConfigDefaults(DEFAULT_NAS_URL, DEFAULT_NKM_NODELIST_URL)
        }
    }

    fun saveNasUrlToConfig(nasUrl: String) {
        saveDesktopConfigDefaults(nasUrl, loadNkmNodeListUrlFromConfig())
    }

    private fun saveDesktopConfigDefaults(nasUrl: String, nkmNodeListUrl: String) {
        val configFile = ApplicationFiles.configFile()
        Files.createDirectories(configFile.toPath().parent)
        val normalizedNasUrl = nasUrl.trim().ifBlank { DEFAULT_NAS_URL }
        val normalizedNkmNodeListUrl = nkmNodeListUrl.trim().ifBlank { DEFAULT_NKM_NODELIST_URL }
        val lines = if (configFile.isFile) {
            Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8).toMutableList()
        } else {
            mutableListOf(
                "# NeoAuthServer / NAS 地址。新版桌面 UI 使用它登录、注册、实名认证并拉取密钥列表。",
                "NAS_URL=$DEFAULT_NAS_URL",
                "",
                "# 向 NKM 获取当前在线节点列表。GUI 会与 NAS 授权节点取交集后显示。",
                "NKM_NODELIST_URL=$DEFAULT_NKM_NODELIST_URL"
            )
        }

        var nasReplaced = false
        var nkmReplaced = false
        for (i in lines.indices) {
            val trimmed = lines[i].trimStart()
            if (trimmed.startsWith("NAS_URL=")) {
                lines[i] = "NAS_URL=$normalizedNasUrl"
                nasReplaced = true
            } else if (trimmed.startsWith("NKM_NODELIST_URL=")) {
                lines[i] = "NKM_NODELIST_URL=$normalizedNkmNodeListUrl"
                nkmReplaced = true
            }
        }
        if (!nasReplaced) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) {
                lines.add("")
            }
            lines.add("# NeoAuthServer / NAS 地址。")
            lines.add("NAS_URL=$normalizedNasUrl")
        }
        if (!nkmReplaced) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) {
                lines.add("")
            }
            lines.add("# 向 NKM 获取当前在线节点列表。GUI 会与 NAS 授权节点取交集后显示。")
            lines.add("NKM_NODELIST_URL=$normalizedNkmNodeListUrl")
        }
        writeAtomically(configFile, lines.joinToString(System.lineSeparator()) + System.lineSeparator())
    }

    fun loadSession(): SessionStoreDocument {
        val file = ApplicationFiles.sessionFile()
        if (!file.isFile) {
            return SessionStoreDocument()
        }
        return try {
            NeoLinkJson.mapper.readValue(file, SessionStoreDocument::class.java)
        } catch (e: Exception) {
            backupUnreadableFile(file)
            debugOperation(e)
            SessionStoreDocument()
        }
    }

    fun saveSession(session: SessionStoreDocument) {
        writeJson(ApplicationFiles.sessionFile(), session)
    }

    fun clearSession() {
        Files.deleteIfExists(ApplicationFiles.sessionFile().toPath())
    }

    fun loadTunnels(): MutableList<TunnelCardState> {
        val file = ApplicationFiles.tunnelsFile()
        if (!file.isFile) {
            return mutableListOf()
        }
        return try {
            NeoLinkJson.mapper.readValue(file, TunnelStoreDocument::class.java).tunnels
        } catch (e: Exception) {
            backupUnreadableFile(file)
            debugOperation(e)
            mutableListOf()
        }
    }

    fun saveTunnels(tunnels: List<TunnelCardState>) {
        writeJson(ApplicationFiles.tunnelsFile(), TunnelStoreDocument(tunnels.toMutableList()))
    }

    private fun writeJson(file: File, value: Any) {
        writeAtomically(file, NeoLinkJson.mapper.writeValueAsString(value) + System.lineSeparator())
    }

    private fun writeAtomically(file: File, content: String) {
        val parent = file.toPath().parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8)
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun backupUnreadableFile(file: File) {
        if (!file.isFile) {
            return
        }
        val backup = File(file.parentFile, "${file.name}.corrupt")
        try {
            Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            debugOperation(e)
        }
    }
}
