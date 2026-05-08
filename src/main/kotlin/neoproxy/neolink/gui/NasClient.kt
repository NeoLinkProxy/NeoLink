package neoproxy.neolink.gui

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class NasClient(
    private val baseUrl: String,
    private val sessionToken: String = ""
) {
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val httpClient = HttpClient.newBuilder()
        .cookieHandler(cookieManager)
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    data class Response(
        val statusCode: Int,
        val body: Map<String, Any?>,
        val sessionToken: String
    ) {
        val success: Boolean
            get() = body["success"] == true

        val message: String
            get() = (body["msg"] ?: body["message"] ?: "请求失败").toString()
    }

    fun sendCode(email: String, mode: String): Response {
        return post("/api/send-code", mapOf("email" to email, "mode" to mode))
    }

    fun register(email: String, password: String, code: String): Response {
        return post("/api/register", mapOf("email" to email, "password" to password, "code" to code))
    }

    fun login(email: String, password: String): Response {
        return post("/api/login", mapOf("email" to email, "password" to password))
    }

    fun heartbeat(): Response {
        return post("/api/heartbeat", emptyMap())
    }

    fun identityStatus(): String {
        val response = post("/api/id-check", emptyMap())
        if (response.statusCode == 401) {
            throw IllegalStateException(response.message)
        }
        if (response.statusCode == 403) {
            throw IllegalStateException(response.message)
        }
        return (response.body["status"] ?: response.body["data"] ?: "UNVERIFIED").toString()
    }

    fun verifyIdentity(name: String, idCard: String): Response {
        return post("/api/id-check", mapOf("name" to name, "idcard" to idCard))
    }

    fun myKeys(): List<NasKey> {
        val response = get("/api/my-keys")
        if (!response.success) {
            throw IllegalStateException(response.message)
        }
        val data = response.body["data"]
        if (data !is List<*>) {
            return emptyList()
        }
        return data.mapNotNull { parseKey(it as? Map<*, *>) }
    }

    private fun get(path: String): Response {
        val request = baseRequest(path).GET().build()
        return send(request)
    }

    private fun post(path: String, payload: Map<String, Any?>): Response {
        val json = NeoLinkJson.mapper.writeValueAsString(payload)
        val request = baseRequest(path)
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        return send(request)
    }

    private fun baseRequest(path: String): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(resolve(path))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
        if (sessionToken.isNotBlank()) {
            builder.header("Cookie", "neosession=$sessionToken")
            builder.header("Authorization", sessionToken)
        }
        return builder
    }

    private fun send(request: HttpRequest): Response {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        val body = parseObject(response.body())
        val token = extractSessionToken(response).ifBlank { sessionToken }
        return Response(response.statusCode(), body, token)
    }

    private fun resolve(path: String): URI {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isBlank()) {
            throw IllegalArgumentException("NAS_URL 不能为空")
        }
        return URI.create(normalizedBase + path)
    }

    private fun parseObject(json: String): Map<String, Any?> {
        if (json.isBlank()) {
            return emptyMap()
        }
        @Suppress("UNCHECKED_CAST")
        return NeoLinkJson.mapper.readValue(json, Map::class.java) as Map<String, Any?>
    }

    private fun extractSessionToken(response: HttpResponse<String>): String {
        return response.headers().allValues("Set-Cookie")
            .asSequence()
            .flatMap { it.split(";").asSequence() }
            .map { it.trim() }
            .firstOrNull { it.startsWith("neosession=") }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
            ?: ""
    }

    private fun parseKey(source: Map<*, *>?): NasKey? {
        if (source == null) {
            return null
        }
        val alias = text(source["alias"] ?: source["name"])
        if (alias.isBlank()) {
            return null
        }
        return NasKey(
            alias = alias,
            realKey = text(source["realKey"] ?: source["real_key"]),
            type = text(source["type"]),
            balanceMiB = number(source["balance"]),
            expire = text(source["expire"]),
            status = text(source["status"]),
            rate = text(source["rate"]),
            port = text(source["port"]),
            availableNodes = parseNodes(source["availableNodes"]),
            availableNodesConsole = text(source["availableNodesConsole"]),
            refreshCount = number(source["refreshCount"]).toInt(),
            refreshMaxPerDay = number(source["refreshMaxPerDay"]).toInt(),
            refreshRemainingToday = number(source["refreshRemainingToday"]).toInt()
        )
    }

    private fun parseNodes(value: Any?): List<NasNode> {
        if (value !is List<*>) {
            return emptyList()
        }
        return value.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val nodeId = text(map["nodeId"] ?: map["realId"])
            val displayName = text(map["displayName"] ?: map["name"]).ifBlank { nodeId }
            NasNode(
                nodeId = nodeId,
                displayName = displayName,
                isOnline = map["isOnline"] == true,
                address = text(map["address"]),
                hookPort = number(map["hookPort"]).toInt().takeIf { it in 1..65535 } ?: 44801,
                connectPort = number(map["connectPort"]).toInt().takeIf { it in 1..65535 } ?: 44802,
                version = text(map["version"]),
                iconSvg = text(map["iconSvg"] ?: map["icon_svg"] ?: map["svg"])
            )
        }
    }

    private fun text(value: Any?): String = value?.toString()?.trim().orEmpty()

    private fun number(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(",", "").toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }
}
