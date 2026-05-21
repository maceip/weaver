package com.weaver.app.bridge.transport

import kotlinx.coroutines.flow.StateFlow

/**
 * A pipe that carries bridge JSON between the native [com.weaver.app.bridge.Bridge]
 * and a Stitch surface. Two implementations exist:
 *
 *  - [LocalWebViewTransport]  — the bundled on-device WebView
 *  - [RemoteSessionTransport] — a WebSocket to the AWS session bridge
 *
 * [BridgeRouter] is itself a transport that circuit-breaks between them.
 *
 * The contract is symmetric: [sendInbound] pushes an `Inbound` payload toward
 * the Stitch surface; the transport pushes `Outbound` payloads back through
 * the sink registered with [setOutboundSink]. Payloads are the raw bridge
 * JSON — transports never parse them.
 */
interface BridgeTransport {
    /** Stable id for logs / routing decisions ("local", "remote", "router"). */
    val id: String

    /** Observable health, drives the router's circuit breaker. */
    val status: StateFlow<TransportStatus>

    /** Register where decoded `Outbound` JSON should be delivered. */
    fun setOutboundSink(sink: (String) -> Unit)

    /** Begin connecting / become usable. Idempotent. */
    fun start()

    /** Tear down. Idempotent. */
    fun stop()

    /** Push one `Inbound` bridge payload (already-serialized JSON) toward Stitch. */
    fun sendInbound(payloadJson: String)
}

enum class TransportStatus {
    /** Not started, or stopped. */
    Idle,

    /** Working toward [Ready] — connecting a socket, loading a page. */
    Connecting,

    /** Usable: inbound will reach Stitch, outbound will flow back. */
    Ready,

    /**
     * Reachable but not trustworthy — e.g. the local WebView is up but has
     * no Stitch session. The router prefers a [Ready] peer over a [Degraded]
     * one but will still use [Degraded] as a last resort.
     */
    Degraded,

    /** Unusable. The router circuit-breaks away from a [Failed] transport. */
    Failed,
}
