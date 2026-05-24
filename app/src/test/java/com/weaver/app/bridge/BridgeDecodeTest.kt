package com.weaver.app.bridge

import com.weaver.app.bridge.transport.BridgeTransport
import com.weaver.app.bridge.transport.TransportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies Bridge.handleOutbound decodes every `Outbound` shape into the right
 * StateFlow, and that Bridge.send serializes `Inbound` with the `type`
 * discriminator. This is the schema-integrity guard — if the wire format
 * drifts from the Kotlin model, these fail.
 */
class BridgeDecodeTest {
    /** Captures whatever Bridge.send pushes, so we can assert the wire JSON. */
    private class CapturingTransport : BridgeTransport {
        override val id = "capture"
        override val status: StateFlow<TransportStatus> = MutableStateFlow(TransportStatus.Ready)
        val sent = mutableListOf<String>()

        override fun setOutboundSink(sink: (String) -> Unit) = Unit

        override fun start() = Unit

        override fun stop() = Unit

        override fun sendInbound(payloadJson: String) {
            sent += payloadJson
        }
    }

    @Test
    fun nodesUpdatedPopulatesNodesFlow() {
        val bridge = Bridge()
        bridge.handleOutbound(
            """{"type":"nodes_updated","nodes":[{"id":"n1","type":"Screen","label":"Home"}]}""",
        )
        assertEquals(1, bridge.nodes.value.size)
        assertEquals("n1", bridge.nodes.value[0].id)
        assertEquals(NodeType.Screen, bridge.nodes.value[0].type)
    }

    @Test
    fun selectionChangedPopulatesSelectionFlow() {
        val bridge = Bridge()
        bridge.handleOutbound("""{"type":"selection_changed","ids":["a","b"]}""")
        assertEquals(listOf("a", "b"), bridge.selection.value)
    }

    @Test
    fun generationProgressPopulatesGenerationFlow() {
        val bridge = Bridge()
        bridge.handleOutbound("""{"type":"generation_progress","id":"n9","state":"Streaming"}""")
        assertEquals(GenerationState.Streaming, bridge.generation.value["n9"])
    }

    @Test
    fun agentLogUpdatedPopulatesAgentLogFlow() {
        val bridge = Bridge()
        bridge.handleOutbound(
            """{"type":"agent_log_updated","entries":[{"id":"l1","text":"mapping components"}]}""",
        )
        assertEquals(1, bridge.agentLog.value.size)
        assertEquals("mapping components", bridge.agentLog.value[0].text)
    }

    @Test
    fun sessionLifecyclePopulatesSessionsFlow() {
        val bridge = Bridge()
        bridge.handleOutbound("""{"type":"session_started","projectId":"p1","sessionId":"s1"}""")
        assertEquals("p1", bridge.sessions.value["s1"]?.projectId)

        bridge.handleOutbound(
            """{"type":"session_progress","sessionId":"s1","seqNo":4,"stages":[]}""",
        )
        assertEquals(4, bridge.sessions.value["s1"]?.seqNo)

        bridge.handleOutbound("""{"type":"session_finished","sessionId":"s1","totalBytes":962598}""")
        assertTrue(bridge.sessions.value["s1"]?.finished == true)
        assertEquals(962598, bridge.sessions.value["s1"]?.totalBytes)
    }

    @Test
    fun projectThemePopulatesThemesFlow() {
        val bridge = Bridge()
        bridge.handleOutbound(
            """{"type":"project_theme","projectId":"p1","tokens":{"primary":"#d0bcff"}}""",
        )
        assertEquals("#d0bcff", bridge.projectThemes.value["p1"]?.get("primary"))
    }

    @Test
    fun malformedOutboundIsIgnoredNotCrashing() {
        val bridge = Bridge()
        bridge.handleOutbound("not json")
        bridge.handleOutbound("{}")
        bridge.handleOutbound("""{"type":"unknown_event"}""")
        assertTrue(bridge.nodes.value.isEmpty()) // no state corruption
    }

    @Test
    fun sendSerializesInboundWithTypeDiscriminator() {
        val bridge = Bridge()
        val transport = CapturingTransport()
        bridge.bindTransport(transport)

        bridge.send(Inbound.SubmitPrompt(text = "make it minimal"))

        assertEquals(1, transport.sent.size)
        val json = transport.sent[0]
        assertTrue("missing discriminator: $json", json.contains("\"type\":\"submit_prompt\""))
        assertTrue("missing text: $json", json.contains("make it minimal"))
    }

    @Test
    fun sendSelectNodeSerializesId() {
        val bridge = Bridge()
        val transport = CapturingTransport()
        bridge.bindTransport(transport)

        bridge.send(Inbound.SelectNode(id = "node-42"))

        val json = transport.sent.single()
        assertTrue(json.contains("\"type\":\"select_node\""))
        assertTrue(json.contains("node-42"))
    }

    @Test
    fun sendBeforeBindTransportIsDroppedNotCrashing() {
        val bridge = Bridge()
        bridge.send(Inbound.ClearSelection) // no transport bound
        // no exception == pass
    }
}
