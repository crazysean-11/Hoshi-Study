package moe.antimony.hoshi.features.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.antimony.hoshi.R

internal data class StudyUiState(
    val settings: StudySettings = StudySettings(),
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val message: Int? = null,
    val entries: List<StudyEntry> = emptyList(),
    val selected: StudyEntry? = null,
    val reviewIds: Set<String> = emptySet(),
    val report: String = "",
)

@HiltViewModel
internal class StudyViewModel @Inject constructor(
    private val notebook: StudyRepository,
    private val ai: StudyAiClient,
    private val reviews: StudyReviewService,
    internal val startup: StudyStartup,
) : ViewModel() {
    private val _state = MutableStateFlow(StudyUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { notebook.entries.collect { entries -> _state.update { it.copy(entries = entries) } } }
        runAction {
            val settings = notebook.settings()
            notebook.load()
            _state.update { it.copy(settings = settings, loaded = true) }
        }
    }

    fun edit(transform: (StudySettings) -> StudySettings) { _state.update { it.copy(settings = transform(it.settings), message = null) } }
    fun dismissMessage() { _state.update { it.copy(message = null) } }
    fun dismissSelection() { _state.update { it.copy(selected = null) } }
    fun open(entry: StudyEntry) { _state.update { it.copy(selected = entry) } }

    fun toggleReview(id: String) {
        if (_state.value.busy) return
        _state.update { state ->
            state.copy(reviewIds = if (id in state.reviewIds) state.reviewIds - id else state.reviewIds + id, report = "")
        }
    }

    fun selectReview(pendingOnly: Boolean = false) {
        if (_state.value.busy) return
        _state.update { state -> state.copy(
            reviewIds = state.entries.filter { !pendingOnly || it.emailedAt == null }.map { it.id }.toSet(), report = "",
        ) }
    }

    fun clearReview() {
        if (!_state.value.busy) _state.update { it.copy(reviewIds = emptySet(), report = "") }
    }

    fun save() = runAction {
        notebook.saveSettings(_state.value.settings)
        _state.update { it.copy(message = R.string.study_saved) }
    }

    fun testAi() = runAction {
        ai.complete(_state.value.settings, studyPrompt("今日はよい天気ですね。"))
        _state.update { it.copy(message = R.string.study_ai_connected) }
    }

    fun testMail() = runAction {
        reviews.testMail(_state.value.settings)
        _state.update { it.copy(message = R.string.study_mail_sent) }
    }

    fun explain(text: String, source: String = "") = runAction {
        // Save the selected passage even if offline or credentials are not configured.
        val entry = notebook.record(text, source)
        _state.update { it.copy(selected = entry) }
        val explanation = ai.complete(notebook.settings(), studyPrompt(entry.text))
        val complete = notebook.record(entry.text, source, explanation = explanation, incrementCount = false)
        _state.update { if (it.selected?.id == entry.id) it.copy(selected = complete) else it }
    }

    fun review() = runAction {
        val entries = selectedStudyEntries(notebook.load(), _state.value.reviewIds)
        if (entries.isEmpty()) { _state.update { it.copy(message = R.string.study_empty) }; return@runAction }
        notebook.saveSettings(_state.value.settings)
        val report = reviews.generate(entries, _state.value.settings)
        _state.update { it.copy(report = report) }
    }

    fun sendMail() = runAction {
        val ids = _state.value.reviewIds
        if (ids.isEmpty()) {
            _state.update { it.copy(message = R.string.study_select_notes) }
            return@runAction
        }
        notebook.saveSettings(_state.value.settings)
        reviews.sendSelected(ids)
        _state.update { it.copy(message = R.string.study_mail_sent) }
    }

    private fun runAction(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (_: StudyReviewFormatException) { _state.update { it.copy(message = R.string.study_review_format_error) } }
            catch (_: StudyReviewGenerationException) { _state.update { it.copy(message = R.string.study_review_generation_error) } }
            catch (_: StudyMailDeliveryException) { _state.update { it.copy(message = R.string.study_mail_delivery_error) } }
            catch (_: IllegalArgumentException) { _state.update { it.copy(message = R.string.study_invalid_settings) } }
            catch (_: StudyConfigurationException) { _state.update { it.copy(message = R.string.study_configuration_error) } }
            catch (_: Exception) { _state.update { it.copy(message = R.string.study_network_error) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
}
