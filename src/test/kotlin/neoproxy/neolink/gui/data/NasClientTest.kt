package neoproxy.neolink.gui.data
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

@DisplayName("NasClientTest")
class NasClientTest {
    @Test
    @DisplayName("verifyIdentity posts the idcard field expected by NeoAuthServer")
    fun verifyIdentityUsesNeoAuthServerIdcardField() {
        var capturedBody = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/id-check") { exchange ->
            capturedBody = exchange.requestBody.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
            val response = """{"success":true,"status":"VERIFIED"}""".toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val client = NasClient("http://127.0.0.1:${server.address.port}", "session-token")

            client.verifyIdentity("张三", "110101199001011234")

            assertTrue(capturedBody.contains("\"idcard\""), capturedBody)
            assertFalse(capturedBody.contains("\"idCard\""), capturedBody)
        } finally {
            server.stop(0)
        }
    }

    @Test
    @DisplayName("user dashboard endpoints parse NAS wrapped responses")
    fun userDashboardEndpointsParseWrappedResponses() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/config") { exchange ->
            json(exchange, """{"success":true,"data":{"price_traffic":0.05,"price_day":0.03,"price_rate_unit":0.01,"rate_limit_warn":30,"purchase_max_traffic_gib":1024,"purchase_max_days":365,"purchase_max_rate_mbps":100,"key_refresh_max_per_day":2}}""")
        }
        server.createContext("/api/my-announcements") { exchange ->
            json(exchange, """{"success":true,"data":[{"id":7,"title":"维护公告","content":"今晚维护","content_type":"text","allow_dismiss":false}]}""")
        }
        server.createContext("/api/create-order") { exchange ->
            json(exchange, """{"success":true,"data":{"orderId":"ord-1","amount":1.23,"status":"SUCCESS"}}""")
        }
        server.createContext("/api/pay-poll") { exchange ->
            json(exchange, """{"success":true,"data":{"status":"SUCCESS"}}""")
        }
        server.start()
        try {
            val client = NasClient("http://127.0.0.1:${server.address.port}", "session-token")

            assertEquals(2, client.config().keyRefreshMaxPerDay)
            assertEquals("维护公告", client.myAnnouncements().single().title)
            assertEquals("ord-1", client.createOrder(1.0, 1, 10).orderId)
            assertEquals("SUCCESS", client.payStatus("ord-1"))
        } finally {
            server.stop(0)
        }
    }

    private fun json(exchange: com.sun.net.httpserver.HttpExchange, body: String) {
        val response = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(200, response.size.toLong())
        exchange.responseBody.use { it.write(response) }
    }
}
