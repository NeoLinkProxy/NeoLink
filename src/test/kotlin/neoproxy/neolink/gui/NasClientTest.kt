package neoproxy.neolink.gui

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertFalse
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
}
