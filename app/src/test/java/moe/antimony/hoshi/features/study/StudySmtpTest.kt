package moe.antimony.hoshi.features.study

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class StudySmtpTest {
    @Test fun completeSessionAuthenticatesAndSendsUtf8Review() {
        val server = listOf("220 ready", "250-example.com", "250 AUTH LOGIN", "334 username", "334 password", "235 authenticated", "250 sender", "250 recipient", "354 body", "250 accepted").joinToString("\r\n", postfix = "\r\n")
        val output = java.io.StringWriter()
        val settings = StudySettings(smtpUser = "sender@example.com", smtpPassword = "test-authorization", recipient = "learner@example.com")
        sendSmtpSession(server.reader().buffered(), output.buffered(), settings, "日语复习", "原句：図書館へ行く。\n答案区：へ")
        val session = output.toString()
        assertTrue(session.startsWith("EHLO hoshi-reader.local\r\nAUTH LOGIN\r\n"))
        assertTrue(session.contains("MAIL FROM:<sender@example.com>\r\nRCPT TO:<learner@example.com>"))
        assertFalse(session.contains("test-authorization"))
        assertTrue(session.contains("\r\nDate: "))
        assertTrue(session.contains("\r\nMessage-ID: <"))
        val encoded = session.substringAfter("Content-Transfer-Encoding: base64\r\n\r\n").substringBefore("\r\n.\r\n")
        assertEquals("原句：図書館へ行く。\n答案区：へ", String(java.util.Base64.getMimeDecoder().decode(encoded), Charsets.UTF_8))
        assertTrue(session.endsWith("\r\n.\r\nQUIT\r\n"))
    }
    @Test(expected = StudyConfigurationException::class) fun rejectedAuthenticationStopsBeforeMailBody() {
        val output = java.io.StringWriter()
        val server = "220 ready\n250 hello\n334 username\n334 password\n535 bad authorization\n"
        try {
            sendSmtpSession(server.reader().buffered(), output.buffered(), StudySettings(smtpUser = "sender@example.com", smtpPassword = "bad", recipient = "learner@example.com"), "subject", "private notes")
        } finally { assertFalse(output.toString().contains("DATA")) }
    }
    @Test fun parsesMultilineCapabilities() {
        assertEquals(250, smtpReply("250-example.com\r\n250-AUTH LOGIN\r\n250 SIZE 2000000\r\n".reader().buffered()))
    }
    @Test fun parsesAuthenticationFailureWithoutLeakingServerText() {
        assertEquals(535, smtpReply("535 Authentication failed\r\n".reader().buffered()))
    }
    @Test(expected = IOException::class) fun failsOnDisconnect() { smtpReply("".reader().buffered()) }
    @Test(expected = IOException::class) fun failsOnMixedReplyCodes() { smtpReply("250-hello\n550 error\n".reader().buffered()) }
    @Test(expected = IOException::class) fun failsOnMalformedReply() { smtpReply("garbage\n".reader().buffered()) }
    @Test fun protectsDotLinesAndNormalizesLineBreaks() {
        assertEquals("hello\r\n..\r\n..start\r\nend", smtpData("hello\n.\r\n.start\rend"))
    }
}
