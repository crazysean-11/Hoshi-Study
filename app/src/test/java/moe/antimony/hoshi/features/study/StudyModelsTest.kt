package moe.antimony.hoshi.features.study

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class StudyModelsTest {
    @Test fun publicDefaultsHaveNoAccountCredentials() {
        val settings = StudySettings()
        assertEquals("", settings.apiKey)
        assertEquals("", settings.smtpUser)
        assertEquals("", settings.smtpPassword)
        assertEquals("", settings.recipient)
    }

    @Test fun reviewAcceptsMarkdownAnswerHeadingsWithoutReportingNetworkFailure() {
        for (heading in listOf("**【答案区】**", "### **【答案区】：**", "## 答案与解析", "__参考答案__：", "【答案区】：")) {
            val part = splitStudyReview("知识点和试题\n$heading\n参考解答")
            assertEquals("知识点和试题", part.lesson)
            assertEquals("参考解答", part.answers)
        }
    }

    @Test fun multipleReviewBatchesFormOneDocumentWithEveryAnswerAfterAllExercises() {
        val report = assembleStudyReview(listOf(
            "group1" to StudyReviewPart("lesson1 question1", "answer1"),
            "group2" to StudyReviewPart("lesson2 question2", "answer2"),
        ), "ALL ANSWERS")
        assertTrue(report.indexOf("question2") < report.indexOf("ALL ANSWERS"))
        assertTrue(report.indexOf("ALL ANSWERS") < report.indexOf("answer1"))
        assertTrue(report.indexOf("answer1") < report.indexOf("answer2"))
        assertTrue(report.contains("lesson1") && report.contains("lesson2"))
    }

    @Test fun chosenNotesExcludeUnselectedItemsAndAllowPreviouslySentNotes() {
        val notes = listOf(
            StudyEntry("a", "first", createdAt = 1, emailedAt = 2),
            StudyEntry("b", "skip", createdAt = 3),
            StudyEntry("c", "third", createdAt = 4),
        )
        assertEquals(listOf("a", "c"), selectedStudyEntries(notes, setOf("c", "a", "missing")).map { it.id })
        assertTrue(selectedStudyEntries(notes, emptySet()).isEmpty())
    }

    @Test fun reviewSeparatesExercisesFromAnswersWithoutLeakingAnswersIntoLessons() {
        val part = splitStudyReview("文法と練習問題\n1. 穴埋め\n【答案区】\n1. 答えと理由")
        assertEquals("文法と練習問題\n1. 穴埋め", part.lesson)
        assertEquals("1. 答えと理由", part.answers)
        assertEquals("解説", splitStudyReview("問題\n## 答案区\n解説").answers)
    }

    @Test fun incompleteReviewCannotBeSentAsAnAnswerlessWorksheet() {
        for (text in listOf("only questions", "【答案区】\nonly answers", "questions\n【答案区】", "q\n【答案区】\na\n【答案区】\nb")) {
            assertThrows(IllegalStateException::class.java) { splitStudyReview(text) }
        }
    }

    @Test fun reviewCacheChangesWithNotesAndModelButNeverStoresAnApiKeyInItsName() {
        val settings = StudySettings(apiKey = "private-key")
        val key = studyReviewCacheKey("first note", settings)
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(key, studyReviewCacheKey("second note", settings))
        assertNotEquals(key, studyReviewCacheKey("first note", settings.copy(model = "other-model")))
        assertEquals(key, studyReviewCacheKey("first note", settings.copy(apiKey = "new-key")))
    }
    @Test fun passageLimitPreservesSupplementaryKanji() {
        val bounded = boundedStudyText(" " + "本".repeat(5999) + "𠮷野 ")
        assertEquals(6000, bounded.codePointCount(0, bounded.length))
        assertTrue(bounded.endsWith("𠮷"))
    }
    @Test fun structuredDictionaryNotesContainDefinitionsWithoutFormattingMetadata() {
        val glossary = """[{"type":"structured-content","content":{"tag":"ul","style":{"color":"red"},"content":[{"tag":"li","content":"record"},{"tag":"li","content":"document"}]}}]"""
        assertEquals("record; document", studyGlossaryText(glossary))
    }
    @Test fun plainDictionaryDefinitionsRemainReadable() {
        assertEquals("a library", studyGlossaryText("a library"))
        assertEquals("cat; feline", studyGlossaryText("""["cat","feline"]"""))
    }
    private fun entry(created: String, sent: String? = null) = StudyEntry(
        "1", "彼は来るはずだった。", createdAt = Instant.parse(created).toEpochMilli(),
        emailedAt = sent?.let { Instant.parse(it).toEpochMilli() },
    )

    @Test fun nextSendUsesJapanTimeRegardlessOfDeviceTimezone() {
        assertEquals(Instant.parse("2026-10-25T12:00:00Z"), nextStudyMailTime(Instant.parse("2026-10-25T11:59:59Z")))
        assertEquals(Instant.parse("2026-10-26T12:00:00Z"), nextStudyMailTime(Instant.parse("2026-10-25T12:00:00Z")))
    }

    @Test fun noMailBeforeDeadlineOrWithNoNotes() {
        assertFalse(studyMailDue(Instant.parse("2026-10-25T11:59:59Z"), listOf(entry("2026-10-25T02:00:00Z"))))
        assertFalse(studyMailDue(Instant.parse("2026-10-25T12:00:00Z"), emptyList()))
    }

    @Test fun unsentNotesBecomeDueAtNinePm() {
        assertTrue(studyMailDue(Instant.parse("2026-10-25T12:00:00Z"), listOf(entry("2026-10-25T02:00:00Z"))))
    }

    @Test fun wakingNextMorningCatchesMissedMail() {
        assertTrue(studyMailDue(Instant.parse("2026-10-25T23:00:00Z"), listOf(entry("2026-10-25T02:00:00Z"))))
    }

    @Test fun postDeadlineNotesWaitUntilFollowingEvening() {
        assertFalse(studyMailDue(Instant.parse("2026-10-25T23:00:00Z"), listOf(entry("2026-10-25T13:00:00Z"))))
        assertTrue(studyMailDue(Instant.parse("2026-10-26T12:00:00Z"), listOf(entry("2026-10-25T13:00:00Z"))))
    }

    @Test fun newNotesDoNotCauseRepeatedEmailsInOneEvening() {
        val entries = listOf(entry("2026-10-25T02:00:00Z", "2026-10-25T12:01:00Z"), entry("2026-10-25T12:10:00Z"))
        assertFalse(studyMailDue(Instant.parse("2026-10-25T13:00:00Z"), entries))
        assertTrue(studyMailDue(Instant.parse("2026-10-26T12:00:00Z"), entries))
    }

    @Test fun rejectsInsecureOrCredentialBearingEndpoints() {
        assertTrue(validateStudyEndpoint("https://api.example.com/v1/chat/completions"))
        listOf("http://example.com", "https://user:key@example.com", "file:///etc/passwd", "https://example.com?key=secret", "https://example.com/#key")
            .forEach { assertFalse(it, validateStudyEndpoint(it)) }
    }

    @Test fun preventsEmailHeaderAndEnvelopeInjection() {
        assertTrue(validMailbox("learner+review@example.com"))
        assertFalse(validMailbox("learner@example.com\r\nBcc:other@example.com"))
        assertFalse(validMailbox("Sean <learner@example.com>"))
        assertFalse(validMailbox("learner@example.com>,<other@example.com"))
    }

    @Test fun reviewsIncludeEveryEntryWithoutDuplicatingBatchBoundaries() {
        val entries = (1..37).map { entry("2026-10-25T02:00:00Z").copy(id = "$it") }
        val batches = reviewBatches(entries)
        assertTrue(batches.all { it.size in 1..4 })
        assertEquals(entries, batches.flatten())
    }
}
