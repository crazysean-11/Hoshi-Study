package moe.antimony.hoshi.features.study

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyDeviceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val dependencies get() = EntryPointAccessors.fromApplication(context, StudyEntryPoint::class.java)

    @Test fun encryptedSettingsRoundTripAndNotebookSurviveReload() = runBlocking {
        val notebook = dependencies.notebook()
        val original = notebook.settings()
        try {
            val settings = original.copy(apiKey = "test-key-local-only", smtpPassword = "test-password-local-only")
            notebook.saveSettings(settings)
            assertEquals(settings, notebook.settings())
            val file = java.io.File(context.filesDir, "datastore/study_private.preferences_pb")
            val bytes = file.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(bytes.contains("test-key-local-only"))
            assertFalse(bytes.contains("test-password-local-only"))
            val entry = notebook.record("雨が降るかもしれない。", source = "Instrumentation test")
            assertTrue(notebook.load().any { it.id == entry.id && it.text == entry.text })
        } finally { notebook.saveSettings(original) }
    }

    @Test fun actualWebViewSelectionPreservesSentenceAndExcludesFurigana() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val completed = CountDownLatch(1)
        var actual: String? = null
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            web = WebView(context)
            web.settings.javaScriptEnabled = true
            web.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val script = context.assets.open("hoshi-web/reader/study-selection.js").bufferedReader().use { it.readText() }
                    view.evaluateJavascript("var range=document.createRange();range.selectNodeContents(document.getElementById('sentence'));var s=window.getSelection();s.removeAllRanges();s.addRange(range);") {
                        view.evaluateJavascript(script) { value -> actual = value; completed.countDown() }
                    }
                }
            }
            web.loadDataWithBaseURL("https://appassets.androidplatform.net/", "<p id='sentence'>彼は<ruby>図書館<rt>としょかん</rt></ruby>へ行った。</p>", "text/html", "UTF-8", null)
        }
        assertTrue(completed.await(20, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { web.destroy() }
        assertEquals("\"彼は図書館へ行った。\"", actual)
    }
}
