package com.weaver.app.bridge.transport

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the routing-policy matrix: status × breaker for both backends. */
class RouteDecisionTest {

    private fun decide(
        local: TransportStatus,
        remote: TransportStatus,
        localUsable: Boolean = true,
        remoteUsable: Boolean = true,
    ) = routeDecision(local, remote, localUsable, remoteUsable)

    @Test
    fun prefersLocalWhenLocalReady() {
        // Local Ready is cheapest — chosen even when remote is also Ready.
        assertEquals(RouteChoice.Local, decide(TransportStatus.Ready, TransportStatus.Ready))
        assertEquals(RouteChoice.Local, decide(TransportStatus.Ready, TransportStatus.Failed))
    }

    @Test
    fun fallsToRemoteWhenLocalNotReady() {
        assertEquals(RouteChoice.Remote, decide(TransportStatus.Degraded, TransportStatus.Ready))
        assertEquals(RouteChoice.Remote, decide(TransportStatus.Failed, TransportStatus.Ready))
        assertEquals(RouteChoice.Remote, decide(TransportStatus.Connecting, TransportStatus.Ready))
    }

    @Test
    fun degradedLocalBeatsNonReadyRemote() {
        // Local up-but-unauthenticated is still better than no remote.
        assertEquals(RouteChoice.Local, decide(TransportStatus.Degraded, TransportStatus.Failed))
        assertEquals(RouteChoice.Local, decide(TransportStatus.Degraded, TransportStatus.Connecting))
    }

    @Test
    fun readyLocalIgnoredWhenLocalBreakerOpen() {
        // A Ready transport whose breaker is open must not be chosen.
        assertEquals(
            RouteChoice.Remote,
            decide(TransportStatus.Ready, TransportStatus.Ready, localUsable = false),
        )
    }

    @Test
    fun readyRemoteIgnoredWhenRemoteBreakerOpen() {
        assertEquals(
            RouteChoice.Local,
            decide(TransportStatus.Degraded, TransportStatus.Ready, remoteUsable = false),
        )
    }

    @Test
    fun fallsThroughToAnyUsableTransport() {
        // Neither Ready/Degraded, but a breaker still permits — pick remote first.
        assertEquals(RouteChoice.Remote, decide(TransportStatus.Connecting, TransportStatus.Connecting))
        // Remote breaker open -> last resort is local.
        assertEquals(
            RouteChoice.Local,
            decide(TransportStatus.Connecting, TransportStatus.Connecting, remoteUsable = false),
        )
    }

    @Test
    fun noneWhenBothBreakersOpen() {
        assertEquals(
            RouteChoice.None,
            decide(
                TransportStatus.Failed, TransportStatus.Failed,
                localUsable = false, remoteUsable = false,
            ),
        )
    }

    @Test
    fun bootWindowWithNothingReadyPicksRemote() {
        // Cold start: both still Idle, both breakers closed -> remote preferred.
        assertEquals(RouteChoice.Remote, decide(TransportStatus.Idle, TransportStatus.Idle))
    }
}
