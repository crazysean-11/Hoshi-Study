package moe.antimony.hoshi.features.study

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class StudyAiClientTest {
    private class Connection(private val status: Int = 200, val body: String = """{"choices":[{"finish_reason":"stop","message":{"content":"中文语法讲解"}}]}""") : HttpsURLConnection(URL("https://example.com/v1/chat/completions")) {
        val request = ByteArrayOutputStream()
        var closed = false
        override fun getOutputStream() = request
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getResponseCode() = status
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun connect() {}
        override fun getCipherSuite() = "TLS_TEST"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
    }
    private val settings = StudySettings(endpoint = "https://example.com/v1/chat/completions", model = "test-model", apiKey = "local-test-key")

    @Test fun deepSeekWorksheetsDisableThinkingAndHaveRoomForCompleteAnswers() = runBlocking {
        val connection = Connection()
        StudyAiClient(Dispatchers.Unconfined) { connection }.complete(
            settings.copy(endpoint = "https://api.deepseek.com/chat/completions"), "worksheet",
        )
        val request = Json.parseToJsonElement(connection.request.toString("UTF-8")).jsonObject
        assertEquals("disabled", request["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(request["max_tokens"]!!.jsonPrimitive.int >= 8192)
    }

    @Test fun sendsConfiguredModelAndSelectedTextAndParsesChineseResult() = runBlocking {
        val connection = Connection()
        val client = StudyAiClient(Dispatchers.Unconfined) { endpoint -> assertEquals(settings.endpoint, endpoint); connection }
        assertEquals("中文语法讲解", client.complete(settings, studyPrompt("彼は来なかった。")))
        val request = Json.parseToJsonElement(connection.request.toString("UTF-8")).jsonObject
        assertFalse(request.containsKey("thinking"))
        assertEquals("test-model", request["model"]!!.jsonPrimitive.content)
        assertTrue(request["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonPrimitive.content.contains("彼は来なかった。"))
        assertEquals("Bearer local-test-key", connection.getRequestProperty("Authorization"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.closed)
    }

    @Test fun missingKeyDoesNotContactAnyServer() = runBlocking {
        var contacted = false
        val client = StudyAiClient(Dispatchers.Unconfined) { contacted = true; Connection() }
        try { client.complete(settings.copy(apiKey = ""), "test"); fail("Expected configuration error") }
        catch (_: StudyConfigurationException) { assertFalse(contacted) }
    }

    @Test fun authenticationFailureDoesNotExposeProviderResponse() = runBlocking {
        val connection = Connection(401, "private error with credentials")
        try { StudyAiClient(Dispatchers.Unconfined) { connection }.complete(settings, "test"); fail() }
        catch (error: StudyConfigurationException) { assertNull(error.message); assertTrue(connection.closed) }
    }

    @Test fun rejectsTruncatedAiAnswers() = runBlocking {
        val connection = Connection(body = """{"choices":[{"finish_reason":"length","message":{"content":"partial"}}]}""")
        try { StudyAiClient(Dispatchers.Unconfined) { connection }.complete(settings, "test"); fail() }
        catch (_: IOException) { assertTrue(connection.closed) }
    }

    @Test fun redirectIsNeverFollowedWithCredentials() = runBlocking {
        val connection = Connection(302)
        try { StudyAiClient(Dispatchers.Unconfined) { connection }.complete(settings, "test"); fail() }
        catch (_: IOException) { assertFalse(connection.instanceFollowRedirects); assertTrue(connection.closed) }
    }
    @Test fun nullOrRefusedAnswerIsNotShownAsACompletedExplanation() = runBlocking {
        val connection = Connection(body = """{"choices":[{"finish_reason":"stop","message":{"content":null,"refusal":"Unavailable"}}]}""")
        try { StudyAiClient(Dispatchers.Unconfined) { connection }.complete(settings, "test"); fail() }
        catch (_: IOException) { assertTrue(connection.closed) }
    }
}
