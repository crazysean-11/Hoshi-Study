package moe.antimony.hoshi.features.anki

import moe.antimony.hoshi.R
import moe.antimony.hoshi.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class AnkiMiningFeedbackTest {
    @Test fun successfulAddConfirmsTheDestinationDeck() {
        val state = AnkiUiState(settings = AnkiSettings(cardFormats = listOf(
            AnkiCardFormat("word", "Word", selectedDeckName = "Japanese"),
        )))
        assertEquals(UiText.Resource(R.string.anki_mining_added_to_deck, "Japanese"), ankiMiningFeedback(state, "word", true, true))
    }

    @Test fun deniedPermissionDoesNotAppearAsAnAddedNote() {
        val state = AnkiUiState(isAnkiDroidAvailable = true)
        assertEquals(UiText.Resource(R.string.anki_mining_permission_required), ankiMiningFeedback(state, null, false, false))
    }

    @Test fun installedAnkiWithoutDeckConfigurationShowsSetupInstructions() {
        val state = AnkiUiState(isAnkiDroidAvailable = true)
        assertEquals(UiText.Resource(R.string.anki_mining_setup_required), ankiMiningFeedback(state, null, false, true))
    }
}
