package com.weaver.app.bridge.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A [BridgeTransport] whose status the test drives directly, used to exercise
 * [BridgeRouter] through the handoff scenarios without a real WebView or
 * WebSocket. [setStatus] simulates a backend coming online / failing / etc.;
 * [sent] records what the router routed to it; [emitOutbound] simulates the
 * backend pushing a Stitch event back.
 */
class FakeTransport(
    override val id: String,
) : BridgeTransport {
    private val _status = MutableStateFlow(TransportStatus.Idle)
    override val status: StateFlow<TransportStatus> = _status

    /** Inbound payloads the router routed here, in order. */
    val sent = mutableListOf<String>()

    var started = false
        private set
    var stopped = false
        private set

    private var sink: ((String) -> Unit)? = null

    override fun setOutboundSink(sink: (String) -> Unit) {
        this.sink = sink
    }

    override fun start() {
        started = true
    }

    override fun stop() {
        stopped = true
    }

    override fun sendInbound(payloadJson: String) {
        sent += payloadJson
    }

    /** Drive the simulated backend's health. */
    fun setStatus(status: TransportStatus) {
        _status.value = status
    }

    /** Simulate this backend emitting a Stitch event back to the bridge. */
    fun emitOutbound(json: String) {
        sink?.invoke(json)
    }
}
