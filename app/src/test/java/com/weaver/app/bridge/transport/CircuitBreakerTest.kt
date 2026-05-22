package com.weaver.app.bridge.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exhaustive state-machine coverage for the router's circuit breaker. */
class CircuitBreakerTest {

    private fun breaker() = CircuitBreaker(failureThreshold = 3, cooldownMs = 1_000L)

    @Test
    fun startsClosedAndUsable() {
        val b = breaker()
        assertEquals(CircuitBreaker.State.Closed, b.state)
        assertTrue(b.usable(now = 0))
    }

    @Test
    fun staysClosedBelowFailureThreshold() {
        val b = breaker()
        b.onStatus(TransportStatus.Failed, now = 0)
        b.onStatus(TransportStatus.Failed, now = 1)
        assertEquals(CircuitBreaker.State.Closed, b.state)
        assertTrue(b.usable(now = 2))
    }

    @Test
    fun tripsOpenAtFailureThreshold() {
        val b = breaker()
        repeat(3) { i -> b.onStatus(TransportStatus.Failed, now = i.toLong()) }
        assertEquals(CircuitBreaker.State.Open, b.state)
    }

    @Test
    fun openIsNotUsableDuringCooldown() {
        val b = breaker()
        repeat(3) { b.onStatus(TransportStatus.Failed, now = 100) }
        assertFalse(b.usable(now = 100))
        assertFalse(b.usable(now = 999)) // 1ms before cooldown elapses
    }

    @Test
    fun openTransitionsToHalfOpenAfterCooldown() {
        val b = breaker()
        repeat(3) { b.onStatus(TransportStatus.Failed, now = 100) }
        assertTrue(b.usable(now = 1_100)) // cooldown elapsed -> one probe allowed
        assertEquals(CircuitBreaker.State.HalfOpen, b.state)
    }

    @Test
    fun halfOpenClosesOnReady() {
        val b = breaker()
        repeat(3) { b.onStatus(TransportStatus.Failed, now = 100) }
        b.usable(now = 1_100) // -> HalfOpen
        b.onStatus(TransportStatus.Ready, now = 1_200)
        assertEquals(CircuitBreaker.State.Closed, b.state)
        assertEquals(0, b.consecutiveFailures)
    }

    @Test
    fun halfOpenReopensOnRepeatedFailure() {
        val b = breaker()
        repeat(3) { b.onStatus(TransportStatus.Failed, now = 100) }
        b.usable(now = 1_100) // -> HalfOpen
        // A failure in HalfOpen still counts; threshold is already exceeded.
        b.onStatus(TransportStatus.Failed, now = 1_200)
        assertEquals(CircuitBreaker.State.Open, b.state)
        assertFalse(b.usable(now = 1_300)) // fresh cooldown from 1_200
    }

    @Test
    fun readyResetsFailureRun() {
        val b = breaker()
        b.onStatus(TransportStatus.Failed, now = 0)
        b.onStatus(TransportStatus.Failed, now = 1)
        b.onStatus(TransportStatus.Ready, now = 2) // resets the run
        b.onStatus(TransportStatus.Failed, now = 3)
        b.onStatus(TransportStatus.Failed, now = 4)
        assertEquals(CircuitBreaker.State.Closed, b.state) // 2 failures, not 4
    }

    @Test
    fun nonTerminalStatusesDoNotMoveBreaker() {
        val b = breaker()
        b.onStatus(TransportStatus.Connecting, now = 0)
        b.onStatus(TransportStatus.Degraded, now = 1)
        b.onStatus(TransportStatus.Idle, now = 2)
        assertEquals(CircuitBreaker.State.Closed, b.state)
        assertEquals(0, b.consecutiveFailures)
    }
}
