package com.weaver.app.bridge.transport

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "WeaverRouter"

/**
 * A [BridgeTransport] that routes between the bundled WebView and the remote
 * session bridge, with a per-transport circuit breaker.
 *
 * Routing policy — cheapest viable wins:
 *  1. local is Ready (has a Stitch session) and not circuit-broken  -> local
 *  2. else remote is Ready and not circuit-broken                   -> remote
 *  3. else local is Degraded (up, unauthenticated) and not broken   -> local
 *  4. else whichever transport is least-bad
 *
 * Circuit breaker: a transport that reports [TransportStatus.Failed]
 * `failureThreshold` times in a row is benched (OPEN) for `cooldownMs`,
 * then probed once (HALF_OPEN). A [TransportStatus.Ready] closes it again.
 * This stops a flapping transport from being chosen every time it briefly
 * recovers.
 */
class BridgeRouter(
    private val local: LocalWebViewTransport,
    private val remote: RemoteSessionTransport,
    private val failureThreshold: Int = 3,
    private val cooldownMs: Long = 20_000L,
) : BridgeTransport {
    override val id = "router"

    private val _status = MutableStateFlow(TransportStatus.Idle)
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private val _activeId = MutableStateFlow(local.id)
    /** Which backend is currently selected — surface this in debug UI / Dari. */
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    private val localBreaker = Breaker(failureThreshold, cooldownMs)
    private val remoteBreaker = Breaker(failureThreshold, cooldownMs)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun setOutboundSink(sink: (String) -> Unit) {
        // Either backend may produce outbound; both feed the same Bridge sink.
        local.setOutboundSink(sink)
        remote.setOutboundSink(sink)
    }

    override fun start() {
        local.start()
        remote.start()
        scope.launch {
            local.status.collect { s ->
                localBreaker.onStatus(s, now())
                recompute()
            }
        }
        scope.launch {
            remote.status.collect { s ->
                remoteBreaker.onStatus(s, now())
                recompute()
            }
        }
    }

    override fun stop() {
        local.stop()
        remote.stop()
        scope.cancel()
        _status.value = TransportStatus.Idle
    }

    override fun sendInbound(payloadJson: String) {
        when (active()) {
            local.id -> local.sendInbound(payloadJson)
            remote.id -> remote.sendInbound(payloadJson)
            else -> Log.w(TAG, "no viable transport — inbound dropped")
        }
    }

    private fun active(): String = _activeId.value

    private fun recompute() {
        val t = now()
        val localReady = local.status.value == TransportStatus.Ready && localBreaker.usable(t)
        val remoteReady = remote.status.value == TransportStatus.Ready && remoteBreaker.usable(t)
        val localDegraded = local.status.value == TransportStatus.Degraded && localBreaker.usable(t)

        val chosen = when {
            localReady -> local.id          // 1. cheapest viable
            remoteReady -> remote.id         // 2. always-authenticated fallback
            localDegraded -> local.id        // 3. up but unauthenticated
            remoteBreaker.usable(t) -> remote.id
            localBreaker.usable(t) -> local.id
            else -> ""                       // 4. nothing viable
        }

        if (chosen != _activeId.value) {
            Log.i(TAG, "route ${_activeId.value} -> $chosen")
            _activeId.value = chosen
        }
        _status.value = when (chosen) {
            local.id -> local.status.value
            remote.id -> remote.status.value
            else -> TransportStatus.Failed
        }
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    /** A minimal three-state circuit breaker driven by transport status. */
    private class Breaker(
        private val failureThreshold: Int,
        private val cooldownMs: Long,
    ) {
        private enum class State { Closed, Open, HalfOpen }

        private var state = State.Closed
        private var consecutiveFailures = 0
        private var openedAt = 0L

        fun onStatus(status: TransportStatus, now: Long) {
            when (status) {
                TransportStatus.Ready -> {
                    consecutiveFailures = 0
                    state = State.Closed
                }
                TransportStatus.Failed -> {
                    consecutiveFailures += 1
                    if (consecutiveFailures >= failureThreshold) {
                        state = State.Open
                        openedAt = now
                    }
                }
                else -> Unit
            }
        }

        /** True when the router is allowed to route to this transport. */
        fun usable(now: Long): Boolean = when (state) {
            State.Closed, State.HalfOpen -> true
            State.Open -> {
                if (now - openedAt >= cooldownMs) {
                    state = State.HalfOpen // allow exactly one probe
                    true
                } else {
                    false
                }
            }
        }
    }
}
