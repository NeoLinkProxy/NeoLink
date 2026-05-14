package neoproxy.neolink.gui.data
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import neoproxy.neolink.app.ApplicationFiles
import neoproxy.neolink.config.LineConfigParser
import neoproxy.neolink.gui.config.DEFAULT_NAS_URL
import neoproxy.neolink.gui.config.DEFAULT_NKM_NODELIST_URL
import neoproxy.neolink.gui.model.DesktopConfigSettings
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
    private val DesktopConfigKeys = setOf(
        "NAS_URL",
        "NKM_NODELIST_URL",
        "ENABLE_AUTO_UPDATE",
        "PROXY_IP_TO_LOCAL_SERVER",
        "PROXY_IP_TO_NEO_SERVER",
        "HEARTBEAT_PACKET_DELAY",
        "RECONNECTION_INTERVAL"
    )

    fun loadDesktopConfig(): DesktopConfigSettings {
        val configFile = ApplicationFiles.configFile()
        if (!configFile.isFile) {
            return defaultDesktopConfig()
        }
        return try {
            val parser = LineConfigParser(configFile)
            parser.load()
            DesktopConfigSettings(
                nasUrl = parser.getOptional("NAS_URL").orElse("").ifBlank { DEFAULT_NAS_URL },
                nkmNodeListUrl = parser.getOptional("NKM_NODELIST_URL").orElse("").ifBlank { DEFAULT_NKM_NODELIST_URL },
                enableAutoUpdate = parser.getOptional("ENABLE_AUTO_UPDATE").map { it.toBooleanStrictOrNull() }.orElse(null) ?: true,
                proxyIPToLocalServer = parser.getOptional("PROXY_IP_TO_LOCAL_SERVER").orElse(""),
                proxyIPToNeoServer = parser.getOptional("PROXY_IP_TO_NEO_SERVER").orElse(""),
                heartbeatPacketDelay = parser.getOptional("HEARTBEAT_PACKET_DELAY")
                    .map { it.toIntOrNull()?.takeIf { value -> value > 0 } }
                    .orElse(null) ?: 1000,
                reconnectionIntervalSeconds = parser.getOptional("RECONNECTION_INTERVAL")
                    .map { it.toIntOrNull()?.takeIf { value -> value > 0 } }
                    .orElse(null) ?: 30
            )
        } catch (e: Exception) {
            debugOperation(e)
            defaultDesktopConfig()
        }
    }

    fun loadNasUrlFromConfig(): String {
        return loadDesktopConfig().nasUrl
    }

    fun loadNkmNodeListUrlFromConfig(): String {
        return loadDesktopConfig().nkmNodeListUrl
    }

    fun ensureDesktopConfigDefaults() {
        val configFile = ApplicationFiles.configFile()
        val missingDefaults = if (!configFile.isFile) {
            true
        } else {
            try {
                val parser = LineConfigParser(configFile)
                parser.load()
                DesktopConfigKeys.any { parser.getOptional(it).orElse("").isBlank() }
            } catch (e: Exception) {
                debugOperation(e)
                true
            }
        }
        if (missingDefaults) {
            saveDesktopConfig(loadDesktopConfig())
        }
    }

    fun saveNasUrlToConfig(nasUrl: String) {
        saveDesktopConfig(loadDesktopConfig().copy(nasUrl = nasUrl))
    }

    fun saveDesktopConfig(config: DesktopConfigSettings) {
        val configFile = ApplicationFiles.configFile()
        Files.createDirectories(configFile.toPath().parent)
        val normalized = normalizeDesktopConfig(config)
        val replacements = linkedMapOf(
            "NAS_URL" to normalized.nasUrl,
            "NKM_NODELIST_URL" to normalized.nkmNodeListUrl,
            "ENABLE_AUTO_UPDATE" to normalized.enableAutoUpdate.toString(),
            "PROXY_IP_TO_LOCAL_SERVER" to normalized.proxyIPToLocalServer,
            "PROXY_IP_TO_NEO_SERVER" to normalized.proxyIPToNeoServer,
            "HEARTBEAT_PACKET_DELAY" to normalized.heartbeatPacketDelay.toString(),
            "RECONNECTION_INTERVAL" to normalized.reconnectionIntervalSeconds.toString()
        )
        val lines = mutableListOf<String>()
        val replaced = mutableSetOf<String>()
        if (configFile.isFile) {
            Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8).forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isBlank() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    lines.add(line)
                    return@forEach
                }
                val key = trimmed.substringBefore("=").trim()
                val replacement = replacements[key]
                if (replacement == null) {
                    lines.add(line)
                } else if (replaced.add(key)) {
                    lines.add("$key=$replacement")
                }
            }
        }
        replacements.forEach { (key, value) ->
            if (key !in replaced) {
                lines.add("$key=$value")
            }
        }
        writeAtomically(configFile, lines.joinToString(System.lineSeparator()) + System.lineSeparator())
    }

    private fun defaultDesktopConfig(): DesktopConfigSettings {
        return DesktopConfigSettings(DEFAULT_NAS_URL, DEFAULT_NKM_NODELIST_URL)
    }

    private fun normalizeDesktopConfig(config: DesktopConfigSettings): DesktopConfigSettings {
        return config.copy(
            nasUrl = config.nasUrl.trim().ifBlank { DEFAULT_NAS_URL },
            nkmNodeListUrl = config.nkmNodeListUrl.trim().ifBlank { DEFAULT_NKM_NODELIST_URL },
            proxyIPToLocalServer = config.proxyIPToLocalServer.trim(),
            proxyIPToNeoServer = config.proxyIPToNeoServer.trim(),
            heartbeatPacketDelay = config.heartbeatPacketDelay.takeIf { it > 0 } ?: 1000,
            reconnectionIntervalSeconds = config.reconnectionIntervalSeconds.takeIf { it > 0 } ?: 30
        )
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
