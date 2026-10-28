package moe.antimony.hoshi.features.study

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.antimony.hoshi.R
import moe.antimony.hoshi.di.ApplicationScope
import moe.antimony.hoshi.di.IoDispatcher

@Singleton
internal class StudyStartup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notebook: StudyRepository,
    private val display: moe.antimony.hoshi.features.display.AppDisplaySettingsRepository,
    private val workManager: dagger.Lazy<WorkManager>,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _status = MutableStateFlow(R.string.study_dictionary_installing)
    val status = _status.asStateFlow()
    fun start() {
        scope.launch(io) {
            try {
                val initialized = java.io.File(context.filesDir, "study/eink-initialized")
                if (!initialized.exists()) {
                    display.update { it.copy(eInkMode = true, autoSwitch = false, eInkDarkTheme = false) }
                    initialized.parentFile!!.mkdirs()
                    initialized.writeText("1")
                }
                notebook.load()
                _status.value = R.string.study_dictionary_ready
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { _status.value = R.string.study_dictionary_failed }
        }
        val request = PeriodicWorkRequestBuilder<StudyMailWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.get().enqueueUniquePeriodicWork("study-daily-mail", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

@Singleton
internal class StudyReviewService @Inject constructor(
    private val notebook: StudyRepository,
    private val ai: StudyAiClient,
    private val mailer: StudyMailer,
    @param:ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()

    suspend fun testMail(settings: StudySettings) {
        mailer.send(settings, context.getString(R.string.study_test_mail_subject), context.getString(R.string.study_test_mail_body))
    }

    suspend fun generate(entries: List<StudyEntry>, settings: StudySettings): String {
        require(entries.isNotEmpty())
        val parts = reviewBatches(entries).mapIndexed { index, batch ->
            val prompt = studyReviewPrompt(batch)
            val report = notebook.cachedReview(prompt, settings) ?: try {
                ai.complete(settings, prompt).also {
                    splitStudyReview(it) // Do not cache incomplete reports.
                    notebook.cacheReview(prompt, settings, it)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: StudyReviewFormatException) { throw error }
            catch (error: Exception) { throw StudyReviewGenerationException(error) }
            val part = splitStudyReview(report)
            val heading = context.getString(R.string.study_review_group_format, index + 1, batch.size)
            heading to part
        }
        return assembleStudyReview(parts, context.getString(R.string.study_review_answers))
    }

    suspend fun sendSelected(ids: Set<String>) = mutex.withLock {
        val entries = selectedStudyEntries(notebook.load(), ids)
        require(entries.isNotEmpty())
        sendEntries(entries, notebook.settings())
    }

    suspend fun sendPending() = mutex.withLock {
        val settings = notebook.settings()
        if (!settings.dailyEmail) return@withLock
        val now = Instant.now()
        val all = notebook.load()
        if (!studyMailDue(now, all)) return@withLock
        val pending = all.filter { it.emailedAt == null && it.createdAt <= studyMailCutoff(now) }
        if (pending.isNotEmpty()) sendEntries(pending, settings)
    }

    private suspend fun sendEntries(entries: List<StudyEntry>, settings: StudySettings) {
        validateStudyMailSettings(settings)
        val report = generate(entries, settings)
        val original = entries.joinToString("\n\n") { it.text + "\n" + it.explanation }
        val body = context.getString(R.string.study_original_notes) + "\n\n" + original + "\n\n" + report
        // Combine every AI batch into one SMTP message. Mark sent only after acceptance.
        try { mailer.send(settings, context.getString(R.string.study_mail_subject), body) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { throw StudyMailDeliveryException(error) }
        notebook.markEmailed(entries.map { it.id }.toSet(), System.currentTimeMillis())
    }

}

@HiltWorker
internal class StudyMailWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val service: StudyReviewService,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        service.sendPending()
        Result.success()
    } catch (error: CancellationException) { throw error }
    catch (_: StudyConfigurationException) { Result.failure() }
    catch (_: Exception) { if (runAttemptCount < 4) Result.retry() else Result.failure() }
}
