package com.weaver.app.bridge.transport

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

/**
 * End-to-end coverage of [RemoteSessionTransport]: a real OkHttp WebSocket
 * client driven against a real [MockWebServer] WebSocket endpoint. Exercises
 * the actual upgrade, the hello handshake, frame parsing, the status machine,
 * and reconnect — nothing about the socket is mocked.
 */
class RemoteSessionTransportTest {
    private val json = Json { ignoreUnknownKeys = true }
    private var server: MockWebServer? = null
    private var transport: RemoteSessionTransport? = null

    @After
    fun tearDown() {
        transport?.stop()
        // Best-effort: MockWebServer.shutdown() can throw while a just-closed
        // client socket is still draining — not a product concern.
        runCatching { server?.shutdown() }
    }

    /** Server end of a MockWebServer WebSocket — records what the client sends. */
    private class ServerEnd : WebSocketListener() {
        val opened = CountDownLatch(1)
        val received = LinkedBlockingQueue<String>()

        @Volatile
        var socket: WebSocket? = null

        override fun onOpen(
            webSocket: WebSocket,
            response: Response,
        ) {
            socket = webSocket
            opened.countDown()
        }

        override fun onMessage(
            webSocket: WebSocket,
            text: String,
        ) {
            received.add(text)
        }

        fun take(): String = requireNotNull(received.poll(3, SECONDS)) { "expected a frame from the client" }

        fun send(frame: String) {
            requireNotNull(socket) { "server socket not open" }.send(frame)
        }
    }

    /** Enqueue a WS endpoint and return its server end. */
    private fun startServer(): ServerEnd {
        val end = ServerEnd()
        val mock = MockWebServer()
        mock.enqueue(MockResponse().withWebSocketUpgrade(end))
        mock.start()
        server = mock
        return end
    }

    private fun newTransport(
        endpoint: String,
        idToken: String? = "tok-123",
        attestation: String? = null,
        sink: (String) -> Unit = {},
    ): RemoteSessionTransport =
        RemoteSessionTransport(
            endpoint = endpoint,
            deviceId = "phone-A",
            idTokenProvider = { idToken },
            json = json,
            attestationHeader = { attestation },
        ).also {
            it.setOutboundSink(sink)
            transport = it
        }

    /** Poll the status flow until it reaches [expected] or time runs out. */
    private fun awaitStatus(
        t: RemoteSessionTransport,
        expected: TransportStatus,
    ) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (t.status.value == expected) return
            Thread.sleep(25)
        }
        assertEquals("transport never reached $expected", expected, t.status.value)
    }

    private fun bridgeUrl(): String = server!!.url("/bridge").toString()

    // ── Handshake ───────────────────────────────────────────────────────────
    @Test
    fun handshake_sendsHelloAndReachesReady() {
        val end = startServer()
        val t = newTransport(bridgeUrl())
        t.start()

        assertTrue("socket never upgraded", end.opened.await(3, SECONDS))
        val hello = json.parseToJsonElement(end.take()).jsonObject
        assertEquals("hello", hello["kind"]?.jsonPrimitive?.content)
        assertEquals("tok-123", hello["idToken"]?.jsonPrimitive?.content)
        assertEquals("phone-A", hello["deviceId"]?.jsonPrimitive?.content)

        end.send("""{"kind":"ready","identity":"id-1","sessionId":"s1","attachedDevices":1}""")
        awaitStatus(t, TransportStatus.Ready)
    }

    @Test
    fun upgradeRequest_carriesTheAttestationHeader() {
        val end = startServer()
        newTransport(bridgeUrl(), attestation = "att-xyz").start()

        assertTrue(end.opened.await(3, SECONDS))
        val request = requireNotNull(server!!.takeRequest(3, SECONDS))
        assertEquals("att-xyz", request.getHeader("X-Weaver-Attestation"))
    }

    @Test
    fun noIdToken_failsWithoutOpeningASocket() {
        // No server needed — the transport bails before dialing.
        val t = newTransport("http://127.0.0.1:1/bridge", idToken = null)
        t.start()
        awaitStatus(t, TransportStatus.Failed)
    }

    // ── Inbound ─────────────────────────────────────────────────────────────
    @Test
    fun sendInbound_afterReady_wrapsThePayloadAndReachesTheServer() {
        val end = startServer()
        val t = newTransport(bridgeUrl())
        t.start()
        end.take() // hello
        end.send("""{"kind":"ready","identity":"id","sessionId":"s","attachedDevices":1}""")
        awaitStatus(t, TransportStatus.Ready)

        t.sendInbound("""{"type":"submit_prompt","text":"make a login screen"}""")

        val frame = json.parseToJsonElement(end.take()).jsonObject
        assertEquals("inbound", frame["kind"]?.jsonPrimitive?.content)
        val payload = frame["payload"]!!.jsonObject
        assertEquals("submit_prompt", payload["type"]?.jsonPrimitive?.content)
        assertEquals("make a login screen", payload["text"]?.jsonPrimitive?.content)
    }

    @Test
    fun sendInbound_beforeReady_isDropped() {
        val end = startServer()
        val t = newTransport(bridgeUrl())
        t.start()
        assertTrue(end.opened.await(3, SECONDS))
        assertEquals(
            "hello",
            json
                .parseToJsonElement(end.take())
                .jsonObject["kind"]
                ?.jsonPrimitive
                ?.content,
        )

        // Not Ready yet — this must not be sent.
        t.sendInbound("""{"type":"submit_prompt","text":"too early"}""")
        assertNull("inbound before ready must be dropped", end.received.poll(400, MILLISECONDS))
    }

    // ── Outbound ────────────────────────────────────────────────────────────
    @Test
    fun outboundFrame_isUnwrappedAndDeliveredToTheSink() {
        val sink = LinkedBlockingQueue<String>()
        val end = startServer()
        val t = newTransport(bridgeUrl(), sink = { sink.add(it) })
        t.start()
        end.take() // hello
        end.send("""{"kind":"ready","identity":"id","sessionId":"s","attachedDevices":1}""")
        awaitStatus(t, TransportStatus.Ready)

        end.send("""{"kind":"outbound","payload":{"type":"nodes_updated","nodes":[]}}""")

        val delivered = requireNotNull(sink.poll(3, SECONDS)) { "sink never invoked" }
        val payload = json.parseToJsonElement(delivered).jsonObject
        assertEquals("nodes_updated", payload["type"]?.jsonPrimitive?.content)
    }

    // ── Heartbeat ───────────────────────────────────────────────────────────
    @Test
    fun pingFrame_isAnsweredWithPong() {
        val end = startServer()
        val t = newTransport(bridgeUrl())
        t.start()
        end.take() // hello

        end.send("""{"kind":"ping"}""")

        val reply = json.parseToJsonElement(end.take()).jsonObject
        assertEquals("pong", reply["kind"]?.jsonPrimitive?.content)
    }

    // ── Error handling ──────────────────────────────────────────────────────
    @Test
    fun fatalErrorFrame_movesTheTransportToFailed() {
        val end = startServer()
        val t = newTransport(bridgeUrl())
        t.start()
        end.take() // hello

        end.send("""{"kind":"error","code":"auth_failed","message":"bad token","fatal":true}""")
        awaitStatus(t, TransportStatus.Failed)
    }

    // ── Reconnect ───────────────────────────────────────────────────────────
    @Test
    fun socketClosedByServer_reconnectsAndRecovers() {
        val first = ServerEnd()
        val second = ServerEnd()
        val mock = MockWebServer()
        mock.enqueue(MockResponse().withWebSocketUpgrade(first))
        mock.enqueue(MockResponse().withWebSocketUpgrade(second))
        mock.start()
        server = mock

        val t = newTransport(mock.url("/bridge").toString())
        t.start()

        assertTrue(first.opened.await(3, SECONDS))
        first.take() // hello
        first.socket!!.close(1000, "server drained")

        // Backoff is ~2s after the first failure; the transport should redial.
        assertTrue("transport never reconnected", second.opened.await(8, SECONDS))
        second.take() // hello on the new socket
        second.send("""{"kind":"ready","identity":"id","sessionId":"s2","attachedDevices":1}""")
        awaitStatus(t, TransportStatus.Ready)
    }
}
