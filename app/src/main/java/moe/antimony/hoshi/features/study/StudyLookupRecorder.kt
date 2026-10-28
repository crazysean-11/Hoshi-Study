package moe.antimony.hoshi.features.study

import de.manhhao.hoshi.LookupResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import moe.antimony.hoshi.di.ApplicationScope
import org.jsoup.Jsoup

@Singleton
internal class StudyLookupRecorder @Inject constructor(
    private val notebook: StudyRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private var lastText = ""
    private var lastTime = 0L

    @Synchronized
    fun record(result: LookupResult) {
        val text = result.term.expression
        val now = System.currentTimeMillis()
        if (text == lastText && now - lastTime < 60_000) return
        lastText = text
        lastTime = now
        val explanation = result.term.reading + "\n" + result.term.glossaries.take(4).joinToString("\n") {
            it.dictName + ": " + Jsoup.parse(studyGlossaryText(it.glossary)).text().take(2000)
        }
        scope.launch {
            try { notebook.record(text, kind = "word", explanation = explanation) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* A recording failure must not break an otherwise successful lookup. */ }
        }
    }
}
