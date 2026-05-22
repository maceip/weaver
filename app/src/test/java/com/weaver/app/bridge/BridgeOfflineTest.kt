package com.weaver.app.bridge

import android.content.Context
import com.weaver.app.bridge.transport.BridgeTransport
import com.weaver.app.bridge.transport.TransportStatus
import com.weaver.app.offline.Outbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Covers the offline buffering + flush behaviour added to Bridge. */
@RunWith(RobolectricTestRunner::class)
class BridgeOfflineTest {

    /** A transport whose readiness the test drives directly. */
    private class FakeTransport(initial: TransportStatus) : BridgeTransport {
        override val id = "fake"
        val statusFlow = MutableStateFlow(initial)
        override val status: StateFlow<TransportStatus> = statusFlow
        val sent = mutableListOf<String>()
        override fun setOutboundSink(sink: (String) -> Unit) = Unit
        override fun start() = Unit
        override fun stop() = Unit
        override fun sendInbound(payloadJson: String) {
            sent += payloadJson
        }
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("weaver_outbox", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // Unconfined: the transport-status collector runs synchronously, so a
    // status flip is observable on the test thread without waiting.
    private fun bridge(outbox: Outbox?) = Bridge(outbox = outbox, dispatcher = Dispatchers.Unconfined)

    @Test
    fun sendWhileReadyReachesTransportDirectly() {
        val outbox = Outbox(context)
        val bridge = bridge(outbox)
        val transport = FakeTransport(TransportStatus.Ready)
        bridge.bindTransport(transport)

        bridge.send(Inbound.SubmitPrompt(text = "ship it"))

        assertEquals(1, transport.sent.size)
        assertEquals(0, outbox.pendingCount)
    }

    @Test
    fun bufferableActionIsQueuedWhileOffline() {
        val outbox = Outbox(context)
        val bridge = bridge(outbox)
        bridge.bindTransport(FakeTransport(TransportStatus.Idle))

        bridge.send(Inbound.SubmitPrompt(text = "make it blue"))

        assertEquals(1, outbox.pendingCount)
        assertEquals("make it blue", outbox.snapshot().single().label)
    }

    @Test
    fun transientEventIsDroppedNotQueuedWhileOffline() {
        val outbox = Outbox(context)
        val bridge = bridge(outbox)
        val transport = FakeTransport(TransportStatus.Idle)
        bridge.bindTransport(transport)

        bridge.send(Inbound.SelectNode("n1"))

        assertEquals(0, outbox.pendingCount)
        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun bufferedActionsFlushInOrderWhenTransportBecomesReady() {
        val outbox = Outbox(context)
        val bridge = bridge(outbox)
        val transport = FakeTransport(TransportStatus.Idle)
        bridge.bindTransport(transport)

        bridge.send(Inbound.SubmitPrompt(text = "first"))
        bridge.send(Inbound.ToggleFavorite("n2"))
        assertEquals(2, outbox.pendingCount)

        transport.statusFlow.value = TransportStatus.Ready

        assertEquals(0, outbox.pendingCount)
        assertEquals(2, transport.sent.size)
        assertTrue("flush keeps FIFO order", transport.sent[0].contains("first"))
        assertTrue(transport.sent[1].contains("toggle_favorite"))
    }

    @Test
    fun onlineFlagTracksTransportStatus() {
        val bridge = bridge(Outbox(context))
        val transport = FakeTransport(TransportStatus.Idle)
        bridge.bindTransport(transport)
        assertFalse(bridge.online.value)

        transport.statusFlow.value = TransportStatus.Ready
        assertTrue(bridge.online.value)

        transport.statusFlow.value = TransportStatus.Failed
        assertFalse(bridge.online.value)
    }

    @Test
    fun sendWithoutAnOutboxDoesNotCrashOffline() {
        val bridge = bridge(outbox = null)
        bridge.bindTransport(FakeTransport(TransportStatus.Idle))
        bridge.send(Inbound.SubmitPrompt(text = "no outbox")) // dropped, no throw
    }

    @Test
    fun seedNodesPopulatesAnEmptyCanvas() {
        val bridge = bridge(null)
        assertTrue(bridge.nodes.value.isEmpty())

        bridge.seedNodes(listOf(StitchNode(id = "cached")))

        assertEquals(listOf("cached"), bridge.nodes.value.map { it.id })
    }

    @Test
    fun seedNodesNeverOverwritesLiveNodes() {
        val bridge = bridge(null)
        bridge.handleOutbound("""{"type":"nodes_updated","nodes":[{"id":"live"}]}""")

        bridge.seedNodes(listOf(StitchNode(id = "cached")))

        assertEquals(listOf("live"), bridge.nodes.value.map { it.id })
    }

    @Test
    fun disposeIsSafe() {
        val bridge = bridge(Outbox(context))
        bridge.bindTransport(FakeTransport(TransportStatus.Ready))
        bridge.dispose() // cancels the internal scope, must not throw
    }
}
