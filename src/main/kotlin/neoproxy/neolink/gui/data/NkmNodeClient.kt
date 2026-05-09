package neoproxy.neolink.gui.data
import neoproxy.neolink.config.ConfigOperator
import neoproxy.neolink.config.NodeConfig
import neoproxy.neolink.gui.config.DEFAULT_NKM_NODELIST_URL
import neoproxy.neolink.gui.model.NkmNode
import neoproxy.neolink.util.Debugger.debugOperation
import top.ceroxe.api.neolink.NeoNode
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration

data class NkmNodeLoadResult(
    val nodes: List<NkmNode>,
    val source: NkmNodeSource,
    val warning: String? = null
)

enum class NkmNodeSource {
    NETWORK,
    CACHE,
    EMPTY
}

object NkmNodeClient {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun loadOnlineNodes(nodeListUrl: String): NkmNodeLoadResult {
        return try {
            val nodes = fetchNodesFromNkm(nodeListUrl)
            if (nodes.isEmpty()) {
                return loadCachedNodesWithWarning("NKM 节点列表为空，已回退到本地 nodes.json。")
            }
            NodeConfig.saveAll(nodesFile(), nodes.values)
            NkmNodeLoadResult(nodes.values.mapNotNull { it.toNkmNode() }, NkmNodeSource.NETWORK)
        } catch (e: Exception) {
            loadCachedNodesWithWarning("NKM 节点列表请求失败，已回退到本地 nodes.json：${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun fetchNodesFromNkm(nodeListUrl: String): Map<String, NeoNode> {
        val normalizedUrl = nodeListUrl.trim().ifBlank { DEFAULT_NKM_NODELIST_URL }
        val request = HttpRequest.newBuilder(URI.create(normalizedUrl))
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build()

        val tempFile = Files.createTempFile("nkm-node-list-", ".json").toFile()
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
            if (response.statusCode() !in 200..299) {
                throw IllegalStateException("NKM nodelist HTTP ${response.statusCode()}")
            }
            response.body().use { body: InputStream ->
                Files.copy(body, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return NodeConfig.readNodeMap(tempFile, true)
        } finally {
            Files.deleteIfExists(tempFile.toPath())
        }
    }

    private fun loadCachedNodesWithWarning(warningWhenCacheExists: String): NkmNodeLoadResult {
        val cachedNodes = loadCachedNodes()
        return if (cachedNodes.isNotEmpty()) {
            NkmNodeLoadResult(
                nodes = cachedNodes,
                source = NkmNodeSource.CACHE,
                warning = warningWhenCacheExists
            )
        } else {
            NkmNodeLoadResult(
                nodes = emptyList(),
                source = NkmNodeSource.EMPTY,
                warning = warningWhenCacheExists.replace("已回退到本地 nodes.json", "且本地 nodes.json 不存在或为空")
            )
        }
    }

    private fun loadCachedNodes(): List<NkmNode> {
        val nodeFile = nodesFile()
        if (!nodeFile.isFile) {
            return emptyList()
        }
        return try {
            NodeConfig.readNodeMap(nodeFile, true).values.mapNotNull { it.toNkmNode() }
        } catch (e: Exception) {
            debugOperation(e)
            emptyList()
        }
    }

    private fun NeoNode.toNkmNode(): NkmNode? {
        val realId = realId?.trim().orEmpty()
        val name = name?.trim().orEmpty()
        val address = address?.trim().orEmpty()
        val hookPort = hookPort
        val connectPort = connectPort
        if (realId.isBlank() || name.isBlank() || address.isBlank() || hookPort !in 1..65535 || connectPort !in 1..65535) {
            return null
        }
        return NkmNode(
            realId = realId,
            name = name,
            address = address,
            icon = iconSvg.orEmpty(),
            hookPort = hookPort,
            connectPort = connectPort
        )
    }

    private fun nodesFile(): File {
        return File(ConfigOperator.resolveWritableRuntimeDirectory(), NodeConfig.NODE_LIST_FILE_NAME)
    }
}
