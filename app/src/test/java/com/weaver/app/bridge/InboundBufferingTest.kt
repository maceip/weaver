package com.weaver.app.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies which actions the offline outbox buffers, and their labels. */
class InboundBufferingTest {
    @Test
    fun durableMutationsAreBuffered() {
        assertTrue(Inbound.SubmitPrompt(text = "make it blue").isBufferable)
        assertTrue(Inbound.Canvas(CanvasAction.Regenerate, listOf("n1")).isBufferable)
        assertTrue(Inbound.Attach(AttachmentKind.UploadFile).isBufferable)
        assertTrue(Inbound.RequestExport(ExportKind.Zip).isBufferable)
        assertTrue(Inbound.ToggleFavorite("n1").isBufferable)
        assertTrue(Inbound.AddNote("n1", "revisit later").isBufferable)
    }

    @Test
    fun transientUiEventsAreNotBuffered() {
        // Replaying stale selection / tool / viewport on reconnect would fight
        // whatever the user is doing — these must never be buffered.
        assertFalse(Inbound.SelectNode("n1").isBufferable)
        assertFalse(Inbound.SelectTool(CanvasTool.Hand).isBufferable)
        assertFalse(Inbound.ClearSelection.isBufferable)
        assertFalse(Inbound.ViewportChanged(1080, 2400).isBufferable)
        assertFalse(Inbound.SelectPreset("alexandria").isBufferable)
        assertFalse(Inbound.SelectModel("gemini-3.1-pro").isBufferable)
    }

    @Test
    fun outboxLabelIsHumanReadable() {
        assertEquals("make it blue", Inbound.SubmitPrompt(text = "make it blue").outboxLabel)
        assertEquals("Regenerate", Inbound.Canvas(CanvasAction.Regenerate).outboxLabel)
        assertEquals("Favorite", Inbound.ToggleFavorite("n1").outboxLabel)
        assertEquals("Prompt", Inbound.SubmitPrompt(text = "   ").outboxLabel)
    }
}
