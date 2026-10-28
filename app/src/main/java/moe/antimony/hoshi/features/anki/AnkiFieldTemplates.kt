package moe.antimony.hoshi.features.anki

object AnkiFieldTemplates {
    private val templates = mapOf(
        "Lapis" to mapOf(
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
        "Kiku" to mapOf(
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
        "Senren" to mapOf(
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
    )

    private fun basicMappings(noteType: AnkiNoteType): Map<String, String> {
        val pairs = listOf("Front" to "Back", "正面" to "背面", "表面" to "裏面")
        val pair = pairs.firstOrNull { (front, back) -> noteType.fields == listOf(front, back) } ?: return emptyMap()
        return mapOf(pair.first to "{expression}", pair.second to "{reading}<br>{glossary}<br>{sentence}")
    }

    internal fun matchesDedicatedTemplate(noteType: AnkiNoteType): Boolean =
        templates.containsKey(noteType.name)

    fun matches(noteType: AnkiNoteType): Boolean =
        matchesDedicatedTemplate(noteType) || basicMappings(noteType).isNotEmpty()

    fun defaultMappings(noteType: AnkiNoteType): Map<String, String> {
        val defaults = templates[noteType.name] ?: basicMappings(noteType)
        return noteType.fields.mapNotNull { field ->
            defaults[field]?.let { field to it }
        }.toMap()
    }

    fun applyDefaultsIfUnmapped(
        noteType: AnkiNoteType,
        currentMappings: Map<String, String>,
    ): Map<String, String> {
        if (!matches(noteType)) return currentMappings
        if (noteType.fields.any { field -> currentMappings[field] != null }) return currentMappings
        return defaultMappings(noteType)
    }
}
