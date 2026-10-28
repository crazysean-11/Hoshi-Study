package moe.antimony.hoshi.features.anki

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiFieldTemplatesTest {
    @Test fun basicTemplatesMapWordAndDictionaryMeaningWithoutManualFieldSetup() {
        for (fields in listOf(listOf("Front", "Back"), listOf("正面", "背面"), listOf("表面", "裏面"))) {
            val type = AnkiNoteType(1, "Basic", fields)
            assertTrue(AnkiFieldTemplates.matches(type))
            assertFalse(AnkiFieldTemplates.matchesDedicatedTemplate(type))
            val mappings = AnkiFieldTemplates.defaultMappings(type)
            assertEquals("{expression}", mappings[fields[0]])
            assertTrue(mappings[fields[1]]!!.contains("{glossary}"))
            assertTrue(mappings[fields[1]]!!.contains("{sentence}"))
            assertEquals(mapOf(fields[0] to "my template"), AnkiFieldTemplates.applyDefaultsIfUnmapped(type, mapOf(fields[0] to "my template")))
        }
    }

    @Test
    fun buildsAndroidLapisDefaultMappingsForExactModelName() {
        val noteType = AnkiNoteType(
            id = 10L,
            name = "Lapis",
            fields = listOf(
                "Expression",
                "ExpressionFurigana",
                "ExpressionReading",
                "ExpressionAudio",
                "SelectionText",
                "MainDefinition",
                "DefinitionPicture",
                "Sentence",
                "SentenceAudio",
                "Picture",
                "Glossary",
                "PitchPosition",
                "PitchCategories",
                "Frequency",
                "FreqSort",
                "MiscInfo",
                "IsWordAndSentenceCard",
            ),
        )

        assertTrue(AnkiFieldTemplates.matches(noteType))
        assertTrue(AnkiFieldTemplates.matchesDedicatedTemplate(noteType))
        assertEquals(
            mapOf(
                "Expression" to "{expression}",
                "ExpressionFurigana" to "{furigana-plain}",
                "ExpressionReading" to "{reading}",
                "ExpressionAudio" to "{audio}",
                "SelectionText" to "{popup-selection-text}",
                "MainDefinition" to "{glossary-first}",
                "Sentence" to "{sentence}",
                "SentenceAudio" to "{sasayaki-audio}",
                "Picture" to "{book-cover}",
                "Glossary" to "{glossary}",
                "PitchPosition" to "{pitch-accent-positions}",
                "PitchCategories" to "{pitch-accent-categories}",
                "Frequency" to "{frequencies}",
                "FreqSort" to "{frequency-harmonic-rank}",
                "MiscInfo" to "{document-title}",
            ),
            AnkiFieldTemplates.defaultMappings(noteType),
        )
    }

    @Test
    fun buildsKikuDefaultsForExactModelName() {
        val noteType = AnkiNoteType(
            id = 11L,
            name = "Kiku",
            fields = listOf(
                "Expression",
                "MainDefinition",
                "Sentence",
                "SentenceAudio",
                "Picture",
                "Frequency",
                "IsWordAndSentenceCard",
            ),
        )

        assertTrue(AnkiFieldTemplates.matches(noteType))
        assertEquals(
            mapOf(
                "Expression" to "{expression}",
                "MainDefinition" to "{glossary-first}",
                "Sentence" to "{sentence}",
                "SentenceAudio" to "{sasayaki-audio}",
                "Picture" to "{book-cover}",
                "Frequency" to "{frequencies}",
            ),
            AnkiFieldTemplates.defaultMappings(noteType),
        )
    }

    @Test
    fun buildsSenrenDefaultsForExactModelName() {
        val noteType = AnkiNoteType(
            id = 12L,
            name = "Senren",
            fields = listOf(
                "word",
                "reading",
                "sentence",
                "sentenceFurigana",
                "sentenceTranslation",
                "sentenceCard",
                "audioCard",
                "notes",
                "selectionText",
                "definition",
                "wordAudio",
                "sentenceAudio",
                "picture",
                "glossary",
                "pitchAccents",
                "pitchPositions",
                "pitchCategories",
                "frequencies",
                "freqSort",
                "miscInfo",
                "dictionaryPreference",
            ),
        )

        assertTrue(AnkiFieldTemplates.matches(noteType))
        assertEquals(
            mapOf(
                "word" to "{expression}",
                "reading" to "{reading}",
                "sentence" to "<span class=\"group\">{cloze-prefix}<span class=\"highlight\">{cloze-body}</span>{cloze-suffix}</span>",
                "selectionText" to "{popup-selection-text}",
                "definition" to "{glossary-first}",
                "wordAudio" to "{audio}",
                "sentenceAudio" to "{sasayaki-audio}",
                "picture" to "{book-cover}",
                "glossary" to "{glossary}",
                "pitchPositions" to "{pitch-accent-positions}",
                "pitchCategories" to "{pitch-accent-categories}",
                "frequencies" to "{frequencies}",
                "freqSort" to "{frequency-harmonic-rank}",
                "miscInfo" to "{document-title}",
            ),
            AnkiFieldTemplates.defaultMappings(noteType),
        )
    }

    @Test
    fun doesNotOverwriteSelectedModelMappingsWhenApplyingDefaults() {
        val noteType = AnkiNoteType(
            id = 10L,
            name = "Lapis",
            fields = listOf("Expression", "MainDefinition"),
        )

        val merged = AnkiFieldTemplates.applyDefaultsIfUnmapped(
            noteType = noteType,
            currentMappings = mapOf("MainDefinition" to "{single-glossary-JMdict}"),
        )

        assertEquals(mapOf("MainDefinition" to "{single-glossary-JMdict}"), merged)
    }

    @Test
    fun appliesDefaultsWhenSelectedModelHasNoMappedFields() {
        val noteType = AnkiNoteType(
            id = 10L,
            name = "Lapis",
            fields = listOf("Expression", "MainDefinition"),
        )

        val merged = AnkiFieldTemplates.applyDefaultsIfUnmapped(
            noteType = noteType,
            currentMappings = mapOf("Front" to "{expression}"),
        )

        assertEquals(
            mapOf(
                "Expression" to "{expression}",
                "MainDefinition" to "{glossary-first}",
            ),
            merged,
        )
    }

    @Test
    fun doesNotMatchCustomLapisLikeOrUnrelatedNoteTypes() {
        assertFalse(
            AnkiFieldTemplates.matches(
                AnkiNoteType(id = 10L, name = "Custom Lapis", fields = listOf("Expression", "MainDefinition")),
            ),
        )
        assertFalse(
            AnkiFieldTemplates.matches(
                AnkiNoteType(id = 11L, name = "Custom", fields = listOf("Prompt", "Answer")),
            ),
        )
    }
}
