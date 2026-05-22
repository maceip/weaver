package com.weaver.app.bridge

import android.content.Context
import com.weaver.app.bridge.transport.BridgeTransport
import com.weaver.app.bridge.transport.TransportStatus
import com.weaver.app.offline.Outbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * End-to-end coverage of the export + upload journey: a UI-equivalent
 * `Inbound` action driven through a real [Bridge] all the way to the wire
 * JSON the content script parses, the `Outbound.ExportComplete` path back to
 * the Compose event stream, and offline buffer-then-flush for both.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ExportUploadE2eTest {
    /** A transport whose readiness the test drives; records the wire payloads. */
    private class RecordingTransport(
        initial: TransportStatus,
    ) : BridgeTransport {
        override val id = "recording"
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
        context
            .getSharedPreferences("weaver_outbox", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun readyBridge(): Pair<Bridge, RecordingTransport> {
        val bridge = Bridge(dispatcher = Dispatchers.Unconfined)
        val transport = RecordingTransport(TransportStatus.Ready)
        bridge.bindTransport(transport)
        return bridge to transport
    }

    // ── Export request — every kind survives the wire round trip ────────────
    @Test
    fun exportRequest_everyKind_serializesAndDecodesBackThroughTheBridge() {
        val (bridge, transport) = readyBridge()
        for (kind in ExportKind.entries) {
            val action = Inbound.RequestExport(kind = kind, id = "node-1")
            bridge.send(action)
            val decoded = bridge.json.decodeFromString(Inbound.serializer(), transport.sent.last())
            assertEquals("wire round trip failed for $kind", action, decoded)
        }
        assertEquals(ExportKind.entries.size, transport.sent.size)
    }

    @Test
    fun exportRequest_wireJsonMatchesTheContentScriptContract() {
        val (bridge, transport) = readyBridge()
        bridge.send(Inbound.RequestExport(ExportKind.Firebase, id = "screen-7"))
        val wire = transport.sent.single()
        assertTrue(wire, wire.contains("\"type\":\"request_export\""))
        assertTrue(wire, wire.contains("\"kind\":\"Firebase\""))
        assertTrue(wire, wire.contains("\"id\":\"screen-7\""))
    }

    // ── Upload — file bytes survive the wire ────────────────────────────────
    @Test
    fun uploadFiles_bytesSurviveTheWireRoundTrip() {
        val (bridge, transport) = readyBridge()
        val file = AttachedFile(name = "hero.png", mime = "image/png", data = "iVBORw0KGgo=")
        bridge.send(Inbound.AttachFiles(listOf(file)))
        val decoded = bridge.json.decodeFromString(Inbound.serializer(), transport.sent.single())
        assertEquals(Inbound.AttachFiles(listOf(file)), decoded)
    }

    @Test
    fun uploadFiles_multipleFilesReachTheWireIntact() {
        val (bridge, transport) = readyBridge()
        val files =
            listOf(
                AttachedFile("logo.png", "image/png", "QQ=="),
                AttachedFile("tokens.json", "application/json", "Qg=="),
            )
        bridge.send(Inbound.AttachFiles(files))
        val decoded = bridge.json.decodeFromString(Inbound.serializer(), transport.sent.single())
        assertEquals(Inbound.AttachFiles(files), decoded)
    }

    // ── ExportComplete — the download → bridge → Compose event path ─────────
    @Test
    fun exportComplete_fromADownload_reachesTheComposeEventStream() =
        runTest {
            val bridge = Bridge()
            val received = mutableListOf<Outbound>()
            backgroundScope.launch { bridge.events.collect { received += it } }
            runCurrent()

            bridge.handleOutbound(
                """{"type":"export_complete","kind":"Zip","payload":{"url":"https://x.test/app.zip"}}""",
            )
            runCurrent()

            val complete = received.filterIsInstance<Outbound.ExportComplete>().single()
            assertEquals(ExportKind.Zip, complete.kind)
        }

    // ── Offline — export + upload buffer, then flush on reconnect ────────────
    @Test
    fun exportRequest_offline_isBufferedThenFlushedOnReconnect() {
        val outbox = Outbox(context)
        val bridge = Bridge(outbox = outbox, dispatcher = Dispatchers.Unconfined)
        val transport = RecordingTransport(TransportStatus.Idle)
        bridge.bindTransport(transport)

        bridge.send(Inbound.RequestExport(ExportKind.Zip, id = "n1"))
        assertEquals(1, outbox.pendingCount)
        assertEquals("Export Zip", outbox.snapshot().single().label)
        assertTrue(transport.sent.isEmpty())

        transport.statusFlow.value = TransportStatus.Ready
        assertEquals(0, outbox.pendingCount)
        assertTrue(transport.sent.single().contains("request_export"))
    }

    @Test
    fun uploadFiles_offline_isBufferedThenFlushedOnReconnect() {
        val outbox = Outbox(context)
        val bridge = Bridge(outbox = outbox, dispatcher = Dispatchers.Unconfined)
        val transport = RecordingTransport(TransportStatus.Idle)
        bridge.bindTransport(transport)

        bridge.send(Inbound.AttachFiles(listOf(AttachedFile("x.png", "image/png", "QQ=="))))
        assertEquals(1, outbox.pendingCount)
        assertEquals("Attach 1 file(s)", outbox.snapshot().single().label)

        transport.statusFlow.value = TransportStatus.Ready
        assertEquals(0, outbox.pendingCount)
        assertTrue(transport.sent.single().contains("attach_files"))
    }
}
