package moe.antimony.hoshi.features.study

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.antimony.hoshi.R
import moe.antimony.hoshi.features.settings.SettingsDetailScaffold

@Composable
internal fun StudyScreen(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: StudyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dictionaryState by viewModel.startup.status.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.study_share)
    SettingsDetailScaffold(stringResource(R.string.study_title), onClose, modifier) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(R.string.study_notes, R.string.study_review, R.string.study_settings).forEachIndexed { index, label ->
                    if (tab == index) Button(onClick = { tab = index }) { Text(stringResource(label)) }
                    else OutlinedButton(onClick = { tab = index }) { Text(stringResource(label)) }
                }
            }
            if (state.busy) Text(stringResource(R.string.study_working), Modifier.padding(16.dp))
            state.message?.let { message -> Text(stringResource(message), Modifier.padding(16.dp)) }
            if (tab == 0) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(stringResource(R.string.study_notes_help))
                        Text(stringResource(dictionaryState))
                        if (dictionaryState == R.string.study_dictionary_failed) {
                            OutlinedButton(onClick = { viewModel.startup.start() }) { Text(stringResource(R.string.study_retry_dictionary)) }
                        }
                        if (state.entries.isEmpty()) Text(stringResource(R.string.study_empty))
                    }
                    items(state.entries.asReversed(), key = { it.id }) { entry ->
                            Surface(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().clickable { viewModel.open(entry) }) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(entry.text, style = MaterialTheme.typography.titleMedium)
                                    if (entry.source.isNotBlank()) Text(entry.source, style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(if (entry.emailedAt == null) R.string.study_pending else R.string.study_emailed))
                                }
                            }
                    }
                }
            } else if (tab == 1) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(stringResource(R.string.study_review_help))
                        Text(stringResource(R.string.study_selected_count, state.reviewIds.size))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { viewModel.selectReview() }, enabled = !state.busy) { Text(stringResource(R.string.study_select_all)) }
                            TextButton(onClick = { viewModel.selectReview(pendingOnly = true) }, enabled = !state.busy) { Text(stringResource(R.string.study_select_pending)) }
                            TextButton(onClick = viewModel::clearReview, enabled = !state.busy) { Text(stringResource(R.string.study_clear_selection)) }
                        }
                        Text(stringResource(R.string.study_mail_to, state.settings.recipient))
                        Button(onClick = viewModel::review, enabled = state.loaded && !state.busy && state.reviewIds.isNotEmpty()) {
                            Text(stringResource(R.string.study_generate_review))
                        }
                        OutlinedButton(onClick = viewModel::sendMail, enabled = state.loaded && !state.busy && state.reviewIds.isNotEmpty()) {
                            Text(stringResource(R.string.study_send_selected))
                        }
                    }
                    items(state.entries.asReversed(), key = { it.id }) { entry ->
                        Surface(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Checkbox(checked = entry.id in state.reviewIds, onCheckedChange = { viewModel.toggleReview(entry.id) }, enabled = !state.busy)
                                Column(Modifier.weight(1f).clickable(enabled = !state.busy) { viewModel.toggleReview(entry.id) }.padding(vertical = 8.dp)) {
                                    Text(entry.text, style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(if (entry.emailedAt == null) R.string.study_pending else R.string.study_emailed))
                                    TextButton(onClick = { viewModel.open(entry) }, enabled = !state.busy) { Text(stringResource(R.string.study_explain)) }
                                }
                            }
                        }
                    }
                    if (state.report.isNotBlank()) item {
                        OutlinedButton(onClick = {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, state.report)
                            }, shareTitle))
                        }) { Text(stringResource(R.string.study_share)) }
                        SelectionContainer { Text(state.report) }
                    }
                }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    2 -> {
                        Text(stringResource(R.string.study_privacy))
                        Text(stringResource(R.string.study_ai_section), style = MaterialTheme.typography.titleLarge)
                        StudyField(R.string.study_endpoint, state.settings.endpoint, state.loaded && !state.busy) { v -> viewModel.edit { it.copy(endpoint = v.trim()) } }
                        StudyField(R.string.study_model, state.settings.model, state.loaded && !state.busy) { v -> viewModel.edit { it.copy(model = v.trim()) } }
                        StudyField(R.string.study_api_key, state.settings.apiKey, state.loaded && !state.busy, true) { v -> viewModel.edit { it.copy(apiKey = v.trim()) } }
                        OutlinedButton(onClick = viewModel::testAi, enabled = state.loaded && !state.busy) { Text(stringResource(R.string.study_test_ai)) }
                        Text(stringResource(R.string.study_mail_section), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.study_smtp_help))
                        StudyField(R.string.study_smtp_host, state.settings.smtpHost, state.loaded && !state.busy) { v -> viewModel.edit { it.copy(smtpHost = v.trim()) } }
                        StudyField(R.string.study_smtp_user, state.settings.smtpUser, state.loaded && !state.busy) { v -> viewModel.edit { it.copy(smtpUser = v.trim()) } }
                        StudyField(R.string.study_smtp_password, state.settings.smtpPassword, state.loaded && !state.busy, true) { v -> viewModel.edit { it.copy(smtpPassword = v.trim()) } }
                        StudyField(R.string.study_recipient, state.settings.recipient, state.loaded && !state.busy) { v -> viewModel.edit { it.copy(recipient = v.trim()) } }
                        OutlinedButton(onClick = viewModel::testMail, enabled = state.loaded && !state.busy) { Text(stringResource(R.string.study_test_mail)) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.study_daily_mail), Modifier.weight(1f))
                            Switch(state.settings.dailyEmail, { v -> viewModel.edit { it.copy(dailyEmail = v) } }, enabled = state.loaded && !state.busy)
                        }
                        Text(stringResource(R.string.study_mail_delay))
                        Button(onClick = viewModel::save, enabled = state.loaded && !state.busy) { Text(stringResource(R.string.study_save)) }
                        OutlinedButton(onClick = { tab = 1 }, enabled = state.loaded && !state.busy) { Text(stringResource(R.string.study_choose_email_notes)) }
                        Text(stringResource(R.string.study_dictionary_attribution))
                    }
                }
            }
        }
    }
    state.selected?.let { entry -> StudyExplanationDialog(entry, state.busy, viewModel::dismissSelection) { viewModel.explain(entry.text, entry.source) } }
}

@Composable
private fun StudyField(label: Int, value: String, enabled: Boolean, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled, label = { Text(stringResource(label)) }, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None)
}

@Composable
internal fun StudyExplanationDialog(entry: StudyEntry, busy: Boolean, onClose: () -> Unit, onExplain: () -> Unit) {
    AlertDialog(onDismissRequest = onClose,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraLarge),
        title = { Text(stringResource(R.string.study_explain)) },
        text = { Column(Modifier.heightIn(max = 550.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SelectionContainer { Text(entry.text) }
            if (busy) Text(stringResource(R.string.study_working))
            else if (entry.explanation.isBlank()) Text(stringResource(R.string.study_explain_help))
            else SelectionContainer { Text(entry.explanation) }
        } },
        confirmButton = { TextButton(onClick = onExplain, enabled = !busy) { Text(stringResource(R.string.study_explain)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.action_close)) } })
}
