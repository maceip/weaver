package com.weaver.app.ui

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.weaver.app.bridge.SlashCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The composer must survive rotation / process death with a half-typed prompt. */
class PromptInputStateSaverTest {
    private val scope = SaverScope { true }

    private fun roundTrip(state: PromptInputState): PromptInputState {
        val saver = PromptInputState.Saver
        val saved = with(saver) { scope.save(state) }!!
        return saver.restore(saved)!!
    }

    @Test
    fun preservesTextAndCursorSelection() {
        val original =
            PromptInputState(
                text = TextFieldValue("make the header bolder", selection = TextRange(4, 7)),
            )
        val restored = roundTrip(original)
        assertEquals("make the header bolder", restored.text.text)
        assertEquals(TextRange(4, 7), restored.text.selection)
    }

    @Test
    fun preservesActiveSlashCommand() {
        val restored = roundTrip(PromptInputState(activeSlash = SlashCommand.Animate))
        assertEquals(SlashCommand.Animate, restored.activeSlash)
    }

    @Test
    fun preservesPresetAndModelSelection() {
        val restored =
            roundTrip(
                PromptInputState(selectedPresetId = "bauhaus", selectedModelId = "gemini-3.1-flash"),
            )
        assertEquals("bauhaus", restored.selectedPresetId)
        assertEquals("gemini-3.1-flash", restored.selectedModelId)
    }

    @Test
    fun roundTripsTheEmptyDefault() {
        val restored = roundTrip(PromptInputState())
        assertEquals("", restored.text.text)
        assertNull(restored.activeSlash)
        assertNull(restored.selectedPresetId)
    }
}
