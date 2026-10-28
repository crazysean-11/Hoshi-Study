package moe.antimony.hoshi.features.study

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.antimony.hoshi.di.IoDispatcher

private val Context.studyDataStore by preferencesDataStore(name = "study_private")

@Singleton
internal class StudyRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val settingsKey = stringPreferencesKey("encrypted_settings")
    private val file get() = AtomicFile(File(context.filesDir, "study/notebook.json").also { it.parentFile!!.mkdirs() })
    private val _entries = MutableStateFlow<List<StudyEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    suspend fun settings(): StudySettings = withContext(io) {
        val encrypted = context.studyDataStore.data.first()[settingsKey] ?: return@withContext StudySettings()
        json.decodeFromString<StudySettings>(decrypt(encrypted))
    }

    suspend fun saveSettings(settings: StudySettings) = withContext(io) {
        validateStudySettings(settings)
        val encrypted = encrypt(json.encodeToString(settings))
        context.studyDataStore.edit { it[settingsKey] = encrypted }
    }

    suspend fun load(): List<StudyEntry> = mutex.withLock {
        withContext(io) { read().also { _entries.value = it } }
    }

    suspend fun record(text: String, source: String = "", kind: String = "sentence", explanation: String = "", incrementCount: Boolean = true): StudyEntry =
        mutex.withLock {
            withContext(io) {
                val clean = boundedStudyText(text)
                require(clean.isNotEmpty())
                val current = read().toMutableList()
                val now = System.currentTimeMillis()
                val index = current.indexOfFirst { it.text == clean && it.kind == kind }
                val entry = if (index >= 0) current[index].copy(
                    explanation = explanation.ifBlank { current[index].explanation },
                    source = source.ifBlank { current[index].source }, count = current[index].count + (if (incrementCount) 1 else 0), lastSeen = now,
                ) else StudyEntry(UUID.randomUUID().toString(), clean, source.take(500), kind, explanation, now)
                if (index >= 0) current[index] = entry else current.add(entry)
                write(current)
                entry
            }
        }

    suspend fun markEmailed(ids: Set<String>, at: Long) = mutex.withLock {
        withContext(io) { write(read().map { if (it.id in ids) it.copy(emailedAt = at) else it }) }
    }

    suspend fun cachedReview(prompt: String, settings: StudySettings): String? = mutex.withLock {
        withContext(io) {
            val cache = reviewFile(prompt, settings)
            if (!cache.baseFile.exists()) null else cache.openRead().bufferedReader().use { it.readText().takeIf(String::isNotBlank) }
        }
    }

    suspend fun cacheReview(prompt: String, settings: StudySettings, text: String) = mutex.withLock {
        withContext(io) {
            val cache = reviewFile(prompt, settings)
            val output = cache.startWrite()
            try { output.write(text.toByteArray(Charsets.UTF_8)); cache.finishWrite(output) }
            catch (error: Throwable) { cache.failWrite(output); throw error }
        }
    }

    private fun reviewFile(prompt: String, settings: StudySettings): AtomicFile = AtomicFile(
        File(context.filesDir, "study/reviews/" + studyReviewCacheKey(prompt, settings) + ".txt").also { it.parentFile!!.mkdirs() },
    )

    private fun read(): List<StudyEntry> =
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) emptyList()
        else file.openRead().bufferedReader().use { json.decodeFromString<StudyNotebook>(it.readText()).entries }

    private fun write(entries: List<StudyEntry>) {
        val atomic = file
        val output = atomic.startWrite()
        try {
            output.write(json.encodeToString(StudyNotebook(entries)).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
        _entries.value = entries
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("hoshi-study", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("hoshi-study", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(text: String): String {
        val bytes = Base64.decode(text, Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
}
