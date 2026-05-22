package com.weaver.app.bridge.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies the local-WebView health signal derived from outbound bridge JSON. */
class OutboundClassifierTest {
    @Test
    fun nodesUpdatedProvesReady() {
        // Only a mounted, authenticated React Flow editor emits this.
        val json = """{"type":"nodes_updated","nodes":[]}"""
        assertEquals(TransportStatus.Ready, OutboundClassifier.classify(json))
    }

    @Test
    fun nodesUpdatedWithRealNodesProvesReady() {
        val json = """{"type":"nodes_updated","nodes":[{"id":"abc","type":"Screen"}]}"""
        assertEquals(TransportStatus.Ready, OutboundClassifier.classify(json))
    }

    @Test
    fun otherEditorEventsProveReady() {
        for (type in listOf("selection_changed", "agent_log_updated", "session_started", "session_progress")) {
            assertEquals(
                "type=$type should prove Ready",
                TransportStatus.Ready,
                OutboundClassifier.classify("""{"type":"$type"}"""),
            )
        }
    }

    @Test
    fun selectorBreakageProvesDegraded() {
        val json = """{"type":"error","code":"selector_breakage","message":"react-flow root never appeared after 10s"}"""
        assertEquals(TransportStatus.Degraded, OutboundClassifier.classify(json))
    }

    @Test
    fun canvasMissingProvesDegraded() {
        val json = """{"type":"error","code":"canvas_missing","message":"no canvas"}"""
        assertEquals(TransportStatus.Degraded, OutboundClassifier.classify(json))
    }

    @Test
    fun unrelatedErrorIsNotASignal() {
        // A generic Stitch error says nothing about the WebView's session.
        val json = """{"type":"error","code":"emit_failed","message":"oops"}"""
        assertNull(OutboundClassifier.classify(json))
    }

    @Test
    fun neutralEventsCarryNoSignal() {
        // generation_progress / asset_ready / export_complete don't move status.
        assertNull(OutboundClassifier.classify("""{"type":"generation_progress","id":"x","state":"Streaming"}"""))
        assertNull(OutboundClassifier.classify("""{"type":"asset_ready","id":"x"}"""))
        assertNull(OutboundClassifier.classify("""{"type":"export_complete","kind":"Zip"}"""))
    }

    @Test
    fun malformedJsonCarriesNoSignal() {
        assertNull(OutboundClassifier.classify("not json"))
        assertNull(OutboundClassifier.classify(""))
        assertNull(OutboundClassifier.classify("{}"))
    }

    @Test
    fun toleratesWhitespaceAroundDiscriminator() {
        assertEquals(
            TransportStatus.Ready,
            OutboundClassifier.classify("""{ "type" : "nodes_updated" }"""),
        )
    }
}
