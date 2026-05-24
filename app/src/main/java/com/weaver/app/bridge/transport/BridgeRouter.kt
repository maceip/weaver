package com.weaver.app.bridge.transport

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "WeaverRouter"

/**
 * A [BridgeTransport] that routes between the bundled WebView and the remote
 * session bridge, with a per-transport [CircuitBreaker].
 *
 * The routing policy lives in the pure [routeDecision] function and the
 * breaker in [CircuitBreaker] — both unit-tested — so this class is just the
 * wiring: collect both backends' status, recompute, forward.
 */
class BridgeRouter(
    private val local: BridgeTransport,
    private val remote: BridgeTransport,
    failureThreshold: Int = 3,
    cooldownMs: Long = 20_000L,
    /** Injectable for tests; defaults to the monotonic system clock. */
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
    /** Injectable for tests; defaults to a fresh Default-dispatcher scope. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : BridgeTransport {
    override val id = "router"

    private val _status = MutableStateFlow(TransportStatus.Idle)
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private val _activeId = MutableStateFlow(local.id)

    /** Which backend is currently selected — surface in debug UI / Dari. */
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    private val localBreaker = CircuitBreaker(failureThreshold, cooldownMs)
    private val remoteBreaker = CircuitBreaker(failureThreshold, cooldownMs)

    private val collectors = mutableListOf<Job>()

    override fun setOutboundSink(sink: (String) -> Unit) {
        // Either backend may produce outbound; both feed the same Bridge sink.
        local.setOutboundSink(sink)
        remote.setOutboundSink(sink)
    }

    override fun start() {
        local.start()
        remote.start()
        collectors +=
            scope.launch {
                local.status.collect { s ->
                    localBreaker.onStatus(s, now())
                    recompute()
                }
            }
        collectors +=
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
        // Cancel only our own collectors — the scope may be owned by a caller
        // (or a test) and must not be torn down here.
        collectors.forEach { it.cancel() }
        collectors.clear()
        _status.value = TransportStatus.Idle
    }

    override fun sendInbound(payloadJson: String) {
        when (_activeId.value) {
            local.id -> local.sendInbound(payloadJson)
            remote.id -> remote.sendInbound(payloadJson)
            else -> Log.w(TAG, "no viable transport — inbound dropped")
        }
    }

    private fun recompute() {
        val t = now()
        val choice =
            routeDecision(
                localStatus = local.status.value,
                remoteStatus = remote.status.value,
                localUsable = localBreaker.usable(t),
                remoteUsable = remoteBreaker.usable(t),
            )
        val chosen =
            when (choice) {
                RouteChoice.Local -> local.id
                RouteChoice.Remote -> remote.id
                RouteChoice.None -> ""
            }
        if (chosen != _activeId.value) {
            Log.i(TAG, "route ${_activeId.value} -> $chosen")
            _activeId.value = chosen
        }
        _status.value =
            when (choice) {
                RouteChoice.Local -> local.status.value
                RouteChoice.Remote -> remote.status.value
                RouteChoice.None -> TransportStatus.Failed
            }
    }
}
