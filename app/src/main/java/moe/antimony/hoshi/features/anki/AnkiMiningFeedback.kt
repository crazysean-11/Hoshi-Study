package moe.antimony.hoshi.features.anki

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import moe.antimony.hoshi.R
import moe.antimony.hoshi.ui.UiText
import moe.antimony.hoshi.ui.resolve

internal fun ankiMiningFeedback(state: AnkiUiState, formatId: String?, added: Boolean, permissionGranted: Boolean): UiText {
    if (added) {
        val format = state.settings.resolveAnkiCardFormat(formatId)
        val deck = format?.selectedDeckName ?: state.availableDecks.firstOrNull { it.id == format?.selectedDeckId }?.name
        return if (deck.isNullOrBlank()) UiText.Resource(R.string.anki_mining_added)
            else UiText.Resource(R.string.anki_mining_added_to_deck, deck)
    }
    if (state.settings.backendKind == AnkiBackendKind.AnkiDroid) {
        if (!state.isAnkiDroidAvailable) return UiText.Resource(R.string.anki_fetch_api_unavailable)
        if (!permissionGranted) return UiText.Resource(R.string.anki_mining_permission_required)
    }
    val format = state.settings.resolveAnkiCardFormat(formatId)
    if (format == null || state.popupSettings.formats.none { it.id == format.id && it.isValid }) {
        return UiText.Resource(R.string.anki_mining_setup_required)
    }
    return UiText.Resource(R.string.anki_mining_failed)
}

internal fun showAnkiMiningFeedback(context: Context, state: AnkiUiState, formatId: String?, added: Boolean) {
    val granted = ContextCompat.checkSelfPermission(context, "com.ichi2.anki.permission.READ_WRITE_DATABASE") == PackageManager.PERMISSION_GRANTED
    Toast.makeText(context, ankiMiningFeedback(state, formatId, added, granted).resolve(context), Toast.LENGTH_LONG).show()
}
