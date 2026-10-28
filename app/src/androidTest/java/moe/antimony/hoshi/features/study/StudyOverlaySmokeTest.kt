package moe.antimony.hoshi.features.study

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import moe.antimony.hoshi.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against a disposable test installation; restores connection settings. Never calls an AI API. */
@RunWith(AndroidJUnit4::class)
class StudyOverlaySmokeTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    @Test fun selectedSentenceOpensOverlayAndIsRetainedWhenApiIsUnconfigured() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notebook = EntryPointAccessors.fromApplication(context, StudyEntryPoint::class.java).notebook()
        val original = runBlocking { notebook.settings() }
        runBlocking { notebook.saveSettings(original.copy(apiKey = "", dailyEmail = false)) }
        try {
            val text = "明日は雨が降るかもしれない。"
            val intent = Intent(context, StudyActivity::class.java).putExtra("selected_text", text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ActivityScenario.launch<StudyActivity>(intent).use {
                composeRule.waitUntil(20_000) {
                    runBlocking { notebook.load().any { it.text == text } }
                }
                composeRule.onNodeWithText(context.getString(R.string.study_configuration_error)).assertIsDisplayed()
                assertTrue(runBlocking { notebook.load().any { it.text == text } })
            }
        } finally { runBlocking { notebook.saveSettings(original) } }
    }
}
