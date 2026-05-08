package neoproxy.neolink.gui

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import neoproxy.neolink.config.ConfigOperator
import neoproxy.neolink.config.LineConfigParser
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TUNNELS_FILE = "tunnels.json"
private const val SESSION_FILE = "desktop-session.json"
const val DEFAULT_NAS_URL = "https://neolink.ceroxe.top/"

object NeoLinkJson {
    val mapper: ObjectMapper = ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(SerializationFeature.INDENT_OUTPUT)
}

object NeoLinkLocalStore {
    fun loadNasUrlFromConfig(): String {
        val configFile = File(ConfigOperator.WORKING_DIR, "config.cfg")
        if (!configFile.isFile) {
            return DEFAULT_NAS_URL
        }
        return try {
            val parser = LineConfigParser(configFile)
            parser.load()
            parser.getOptional("NAS_URL").orElse("").ifBlank { DEFAULT_NAS_URL }
        } catch (_: Exception) {
            DEFAULT_NAS_URL
        }
    }

    fun ensureNasUrlInConfig() {
        val configFile = File(ConfigOperator.WORKING_DIR, "config.cfg")
        val needsDefault = if (!configFile.isFile) {
            true
        } else {
            try {
                val parser = LineConfigParser(configFile)
                parser.load()
                parser.getOptional("NAS_URL").orElse("").isBlank()
            } catch (_: Exception) {
                true
            }
        }
        if (needsDefault) {
            saveNasUrlToConfig(DEFAULT_NAS_URL)
        }
    }

    fun saveNasUrlToConfig(nasUrl: String) {
        val configFile = File(ConfigOperator.WORKING_DIR, "config.cfg")
        Files.createDirectories(configFile.toPath().parent)
        val normalized = nasUrl.trim().ifBlank { DEFAULT_NAS_URL }
        val lines = if (configFile.isFile) {
            Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8).toMutableList()
        } else {
            mutableListOf(
                "# NeoAuthServer / NAS 地址。新版桌面 UI 使用它登录、注册、实名认证并拉取密钥列表。",
                "NAS_URL=$DEFAULT_NAS_URL"
            )
        }

        var replaced = false
        for (i in lines.indices) {
            val trimmed = lines[i].trimStart()
            if (trimmed.startsWith("NAS_URL=")) {
                lines[i] = "NAS_URL=$normalized"
                replaced = true
                break
            }
        }
        if (!replaced) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) {
                lines.add("")
            }
            lines.add("# NeoAuthServer / NAS 地址。")
            lines.add("NAS_URL=$normalized")
        }
        writeAtomically(configFile, lines.joinToString(System.lineSeparator()) + System.lineSeparator())
    }

    fun loadSession(): SessionStoreDocument {
        val file = runtimeFile(SESSION_FILE)
        if (!file.isFile) {
            return SessionStoreDocument()
        }
        return try {
            NeoLinkJson.mapper.readValue(file, SessionStoreDocument::class.java)
        } catch (_: Exception) {
            SessionStoreDocument()
        }
    }

    fun saveSession(session: SessionStoreDocument) {
        writeJson(runtimeFile(SESSION_FILE), session)
    }

    fun clearSession() {
        Files.deleteIfExists(runtimeFile(SESSION_FILE).toPath())
    }

    fun loadTunnels(): MutableList<TunnelCardState> {
        val file = runtimeFile(TUNNELS_FILE)
        if (!file.isFile) {
            return mutableListOf()
        }
        return try {
            NeoLinkJson.mapper.readValue(file, TunnelStoreDocument::class.java).tunnels
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun saveTunnels(tunnels: List<TunnelCardState>) {
        writeJson(runtimeFile(TUNNELS_FILE), TunnelStoreDocument(tunnels.toMutableList()))
    }

    private fun runtimeFile(name: String): File {
        val dir = ConfigOperator.resolveWritableRuntimeDirectory()
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, name)
    }

    private fun writeJson(file: File, value: Any) {
        writeAtomically(file, NeoLinkJson.mapper.writeValueAsString(value) + System.lineSeparator())
    }

    private fun writeAtomically(file: File, content: String) {
        Files.createDirectories(file.toPath().parent)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8)
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
