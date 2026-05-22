package com.weaver.app.bridge.transport

/**
 * A three-state circuit breaker driven by [TransportStatus] observations.
 *
 * Closed   — healthy, route freely.
 * Open     — too many consecutive failures; benched until the cooldown elapses.
 * HalfOpen — cooldown elapsed; allow exactly one probe. A [TransportStatus.Ready]
 *            closes it; another failure re-opens it.
 *
 * Time is passed in (`now`) rather than read from a clock so the state machine
 * is deterministically testable.
 */
internal class CircuitBreaker(
    private val failureThreshold: Int,
    private val cooldownMs: Long,
) {
    enum class State { Closed, Open, HalfOpen }

    var state: State = State.Closed
        private set

    var consecutiveFailures: Int = 0
        private set

    private var openedAt: Long = 0L

    /** Feed a status observation. [now] is a monotonic millisecond clock. */
    fun onStatus(status: TransportStatus, now: Long) {
        when (status) {
            TransportStatus.Ready -> {
                consecutiveFailures = 0
                // A Ready closes the breaker only from HalfOpen (a successful
                // probe) or keeps it Closed. It must NOT close an Open breaker
                // — that would let a flapping transport un-bench itself the
                // instant it blips Ready. Open stays Open until the cooldown
                // elapses (see usable()).
                if (state == State.HalfOpen) state = State.Closed
            }
            TransportStatus.Failed -> {
                consecutiveFailures += 1
                // A failed probe re-opens immediately; otherwise trip on the
                // consecutive-failure threshold.
                if (state == State.HalfOpen || consecutiveFailures >= failureThreshold) {
                    state = State.Open
                    openedAt = now
                }
            }
            else -> Unit // Idle / Connecting / Degraded don't move the breaker
        }
    }

    /**
     * True when the router may route to this transport. An Open breaker whose
     * cooldown has elapsed transitions to HalfOpen and permits one probe.
     */
    fun usable(now: Long): Boolean = when (state) {
        State.Closed, State.HalfOpen -> true
        State.Open -> {
            if (now - openedAt >= cooldownMs) {
                state = State.HalfOpen
                true
            } else {
                false
            }
        }
    }
}
