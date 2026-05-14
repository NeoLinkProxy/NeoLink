package neoproxy.neolink.gui.data
import neoproxy.neolink.gui.model.NasKey
import neoproxy.neolink.gui.model.NasNode
import neoproxy.neolink.gui.model.NasAnnouncement
import neoproxy.neolink.gui.model.NasPricingConfig
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URLEncoder
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

    fun logout(): Response {
        return post("/api/logout", emptyMap())
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

    fun resetPassword(email: String, code: String, password: String): Response {
        return post("/api/reset-password", mapOf("email" to email, "code" to code, "password" to password))
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

    fun config(): NasPricingConfig {
        val response = get("/api/config")
        if (!response.success) {
            throw IllegalStateException(response.message)
        }
        val data = response.body["data"] as? Map<*, *> ?: return NasPricingConfig()
        return NasPricingConfig(
            priceTraffic = number(data["price_traffic"]).takeIf { it > 0 } ?: 0.05,
            priceDay = number(data["price_day"]).takeIf { it > 0 } ?: 0.03,
            priceRateUnit = number(data["price_rate_unit"]).takeIf { it > 0 } ?: 0.01,
            rateLimitWarn = number(data["rate_limit_warn"]).toInt().takeIf { it > 0 } ?: 30,
            purchaseMaxTrafficGiB = number(data["purchase_max_traffic_gib"]).takeIf { it > 0 } ?: 1024.0,
            purchaseMaxDays = number(data["purchase_max_days"]).toInt().takeIf { it > 0 } ?: 365,
            purchaseMaxRateMbps = number(data["purchase_max_rate_mbps"]).toInt().takeIf { it > 0 } ?: 100,
            keyRefreshMaxPerDay = number(data["key_refresh_max_per_day"]).toInt().takeIf { it > 0 } ?: 1
        )
    }

    fun myAnnouncements(): List<NasAnnouncement> {
        val response = get("/api/my-announcements")
        if (!response.success) {
            throw IllegalStateException(response.message)
        }
        val data = response.body["data"] as? List<*> ?: return emptyList()
        return data.mapNotNull { parseAnnouncement(it as? Map<*, *>) }
    }

    fun markAnnouncementRead(announcementId: Int, dismissed: Boolean): Response {
        return post("/api/announcement/read", mapOf("announcement_id" to announcementId, "dismissed" to dismissed))
    }

    fun createOrder(trafficGiB: Double, days: Int, rateMbps: Int, targetKey: String = ""): NasOrder {
        val payload = mutableMapOf<String, Any?>(
            "traffic" to trafficGiB,
            "days" to days,
            "rate" to rateMbps
        )
        if (targetKey.isNotBlank()) {
            payload["targetKey"] = targetKey
        }
        val response = post("/api/create-order", payload)
        if (!response.success) {
            throw IllegalStateException(response.message)
        }
        val data = response.body["data"] as? Map<*, *> ?: emptyMap<Any, Any>()
        return NasOrder(
            orderId = text(data["orderId"]),
            amount = number(data["amount"]),
            status = text(data["status"])
        )
    }

    fun payStatus(orderId: String): String {
        val encoded = URLEncoder.encode(orderId, Charsets.UTF_8)
        val response = get("/api/pay-poll?oid=$encoded")
        if (!response.success) {
            throw IllegalStateException(response.message)
        }
        val data = response.body["data"] as? Map<*, *> ?: return "PENDING"
        return text(data["status"]).ifBlank { "PENDING" }
    }

    fun refreshKey(keyName: String): Response {
        return post("/api/refresh-key", mapOf("keyName" to keyName))
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
            refreshCount = number(source["refreshCount"]).toInt(),
            refreshMaxPerDay = number(source["refreshMaxPerDay"]).toInt(),
            refreshRemainingToday = number(source["refreshRemainingToday"]).toInt()
        )
    }

    private fun parseAnnouncement(source: Map<*, *>?): NasAnnouncement? {
        if (source == null) {
            return null
        }
        val id = number(source["id"]).toInt()
        if (id <= 0) {
            return null
        }
        return NasAnnouncement(
            id = id,
            title = text(source["title"]),
            content = text(source["content"]),
            contentType = text(source["content_type"]).ifBlank { "html" },
            allowDismiss = source["allow_dismiss"] != false
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
                connectPort = number(map["connectPort"]).toInt().takeIf { it in 1..65535 } ?: 44802
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

    data class NasOrder(
        val orderId: String,
        val amount: Double,
        val status: String
    )
}
