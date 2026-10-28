package moe.antimony.hoshi.features.study

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import moe.antimony.hoshi.ui.theme.HoshiReaderTheme

@AndroidEntryPoint
internal class StudyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = boundedStudyText(intent.getStringExtra("selected_text").orEmpty())
        if (text.isBlank()) { finish(); return }
        setContent {
            HoshiReaderTheme(darkTheme = false, eInkMode = true, dynamicColor = false) {
                val vm: StudyViewModel = hiltViewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                var started by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(state.loaded, state.busy) {
                    if (state.loaded && !state.busy && !started) {
                        started = true
                        // Explicit user tap on AI selection action authorizes this passage's AI request.
                        vm.explain(text, intent.getStringExtra("source").orEmpty())
                    }
                }
                val entry = state.selected ?: StudyEntry("preview", text, createdAt = 0)
                StudyExplanationDialog(entry, state.busy, ::finish) { vm.explain(text) }
                state.message?.let { message ->
                    androidx.compose.material3.AlertDialog(onDismissRequest = vm::dismissMessage,
                        text = { Text(stringResource(message)) },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = vm::dismissMessage) { Text(stringResource(moe.antimony.hoshi.R.string.action_close)) } })
                }
            }
        }
    }

    companion object {
        fun open(context: Context, text: String, source: String) {
            if (text.isBlank()) return
            context.startActivity(Intent(context, StudyActivity::class.java).putExtra("selected_text", boundedStudyText(text)).putExtra("source", source.take(500)))
        }
    }
}
