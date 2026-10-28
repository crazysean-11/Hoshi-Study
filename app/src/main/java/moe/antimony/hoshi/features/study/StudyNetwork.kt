package moe.antimony.hoshi.features.study

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import moe.antimony.hoshi.di.IoDispatcher

internal class StudyConfigurationException : IOException()

@Singleton
internal class StudyAiClient internal constructor(
    private val io: CoroutineDispatcher,
    private val connect: (String) -> HttpsURLConnection,
) {
    @Inject constructor(@IoDispatcher io: CoroutineDispatcher) : this(io, { URL(it).openConnection() as HttpsURLConnection })
    suspend fun complete(settings: StudySettings, prompt: String): String = withContext(io) {
        if (!validateStudyEndpoint(settings.endpoint) || settings.apiKey.isBlank()) throw StudyConfigurationException()
        val request = buildJsonObject {
            put("model", settings.model)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", "你是一位严谨的日语教师，用简体中文帮助N2学习者。") })
                add(buildJsonObject { put("role", "user"); put("content", prompt) })
            })
            put("max_tokens", 8192)
            // Avoid spending the response budget on reasoning for routine reading worksheets.
            if (java.net.URI(settings.endpoint).host.equals("api.deepseek.com", ignoreCase = true)) {
                put("thinking", buildJsonObject { put("type", "disabled") })
            }
        }.toString().toByteArray(Charsets.UTF_8)
        val connection = connect(settings.endpoint)
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000
            connection.readTimeout = 180_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(request) }
            val status = connection.responseCode
            if (status in 400..499 && status != 408 && status != 429) throw StudyConfigurationException()
            if (status !in 200..299) throw IOException("AI request failed")
            val response = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 1_048_576) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 1_048_577 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (response.size > 1_048_576) throw IOException("AI response too large")
            val result = Json.parseToJsonElement(String(response, Charsets.UTF_8)).jsonObject
            val choice = result["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: throw IOException("Missing AI result")
            if (choice["finish_reason"]?.jsonPrimitive?.content in listOf("length", "content_filter", "tool_calls")) throw IOException("Incomplete AI result")
            choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: throw IOException("Empty AI result")
        } finally {
            connection.disconnect()
        }
    }
}

internal fun smtpData(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')
    .split('\n').joinToString("\r\n") { if (it.startsWith('.')) ".$it" else it }

internal fun smtpReply(reader: BufferedReader): Int {
    var code = -1
    repeat(100) {
        val line = reader.readLine() ?: throw IOException("SMTP disconnected")
        if (line.length > 8192 || line.length < 3) throw IOException("Invalid SMTP response")
        val current = line.take(3).toIntOrNull() ?: throw IOException("Invalid SMTP status")
        if (code != -1 && code != current) throw IOException("Inconsistent SMTP status")
        code = current
        if (line.length == 3 || line[3] == ' ') return code
        if (line[3] != '-') throw IOException("Invalid SMTP continuation")
    }
    throw IOException("SMTP response too long")
}

@Singleton
internal class StudyMailer @Inject constructor(@param:IoDispatcher private val io: CoroutineDispatcher) {
    // Implicit TLS on port 465 only: credentials are never sent before verified TLS.
    suspend fun send(settings: StudySettings, subject: String, body: String) = withContext(io) {
        if (!validMailbox(settings.smtpUser) || !validMailbox(settings.recipient) || settings.smtpPassword.isBlank() ||
            !settings.smtpHost.matches(Regex("[A-Za-z0-9.-]+"))) throw StudyConfigurationException()
        val plain = Socket()
        try {
            plain.connect(InetSocketAddress(settings.smtpHost, 465), 20_000)
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
                .createSocket(plain, settings.smtpHost, 465, true) as SSLSocket
            tls.use { socket ->
                socket.soTimeout = 30_000
                socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                socket.startHandshake()
                val reader = socket.inputStream.bufferedReader(Charsets.US_ASCII)
                val writer = socket.outputStream.bufferedWriter(Charsets.UTF_8)
                sendSmtpSession(reader, writer, settings, subject, body)
            }
        } finally { plain.close() }
    }
}

internal fun sendSmtpSession(reader: BufferedReader, writer: BufferedWriter, settings: StudySettings, subject: String, body: String) {
    require(validMailbox(settings.smtpUser) && validMailbox(settings.recipient))
    fun expect(vararg codes: Int) {
        val code = smtpReply(reader)
        if (code !in codes) {
            if (code in 500..599) throw StudyConfigurationException()
            throw IOException("SMTP request failed")
        }
    }
    fun command(value: String, vararg codes: Int) {
        writer.write(value + "\r\n"); writer.flush(); expect(*codes)
    }
    expect(220)
    command("EHLO hoshi-reader.local", 250)
    command("AUTH LOGIN", 334)
    command(Base64.getEncoder().encodeToString(settings.smtpUser.toByteArray(Charsets.UTF_8)), 334)
    command(Base64.getEncoder().encodeToString(settings.smtpPassword.toByteArray(Charsets.UTF_8)), 235)
    command("MAIL FROM:<${settings.smtpUser}>", 250)
    command("RCPT TO:<${settings.recipient}>", 250, 251)
    command("DATA", 354)
    val encodedSubject = Base64.getEncoder().encodeToString(subject.toByteArray(Charsets.UTF_8))
    val encodedBody = Base64.getMimeEncoder(76, "\r\n".toByteArray()).encodeToString(body.toByteArray(Charsets.UTF_8))
    val date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC))
    val messageId = java.util.UUID.randomUUID().toString() + "@" + settings.smtpUser.substringAfter('@')
    val message = "From: <${settings.smtpUser}>\r\nTo: <${settings.recipient}>\r\n" +
        "Date: $date\r\nMessage-ID: <$messageId>\r\nSubject: =?UTF-8?B?$encodedSubject?=\r\nMIME-Version: 1.0\r\n" +
        "Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n$encodedBody"
    writer.write(smtpData(message) + "\r\n.\r\n"); writer.flush(); expect(250)
    // Delivery has been accepted. A failed QUIT must not cause a duplicate retry.
    runCatching { writer.write("QUIT\r\n"); writer.flush() }
}
