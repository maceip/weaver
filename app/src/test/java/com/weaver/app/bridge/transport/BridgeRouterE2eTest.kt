package com.weaver.app.bridge.transport

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end coverage of the local <-> remote transport handoffs the local
 * agent must not regress. Each test drives two [FakeTransport]s through a
 * real [BridgeRouter] (its coroutine collectors running on the test scope)
 * and asserts which backend is active and where traffic is routed.
 *
 * The router prefers local-when-Ready (no server hop), else remote
 * (always authenticated), and circuit-breaks a flapping backend.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BridgeRouterE2eTest {
    /** Build a router wired to the test scope + virtual clock, already started. */
    private fun routerOf(
        local: FakeTransport,
        remote: FakeTransport,
        scope: kotlinx.coroutines.CoroutineScope,
        now: () -> Long,
        cooldownMs: Long = 20_000L,
    ): BridgeRouter =
        BridgeRouter(local, remote, cooldownMs = cooldownMs, now = now, scope = scope).also {
            it.start()
        }

    // ── Scenario 1: local-only authed, then remote comes online ─────────────
    @Test
    fun localAuthed_thenRemoteComesOnline_staysLocal() =
        runTest {
            val local = FakeTransport("local")
            val remote = FakeTransport("remote")
            val router = routerOf(local, remote, backgroundScope, { testScheduler.currentTime })
            runCurrent()

            local.setStatus(TransportStatus.Ready) // webview proved a Stitch session
            runCurrent()
            assertEquals("local", router.activeId.value)

            remote.setStatus(TransportStatus.Ready) // remote bridge later comes online
            runCurrent()
            assertEquals("local stays preferred — no server hop", "local", router.activeId.value)

            router.sendInbound("""{"type":"submit_prompt","text":"hi"}""")
            assertEquals(1, local.sent.size)
            assertTrue(remote.sent.isEmpty())
        }

    // ── Scenario 2: remote-only browser session ─────────────────────────────
    @Test
    fun remoteOnly_localNeverAuthed_routesRemote() =
        runTest {
            val local = FakeTransport("local")
            val remote = FakeTransport("remote")
            val router = routerOf(local, remote, backgroundScope, { testScheduler.currentTime })
            runCurrent()

            local.setStatus(TransportStatus.Degraded) // webview up, no Stitch session
            remote.setStatus(TransportStatus.Ready)
            runCurrent()
            assertEquals("remote", router.activeId.value)

            router.sendInbound("""{"type":"select_node","id":"n1"}""")
            assertEquals(1, remote.sent.size)
            assertTrue(local.sent.isEmpty())
        }

    // ── Scenario 3: remote first, shares cookies, transitions to local ──────
    @Test
    fun remoteFirst_thenCookiesShared_transitionsToLocal() =
        runTest {
            val local = FakeTransport("local")
            val remote = FakeTransport("remote")
            val router = routerOf(local, remote, backgroundScope, { testScheduler.currentTime })
            runCurrent()

            local.setStatus(TransportStatus.Degraded)
            remote.setStatus(TransportStatus.Ready)
            runCurrent()
            assertEquals("remote", router.activeId.value)
            router.sendInbound("a")

            // Remote shares its Stitch cookies into the local WebView; the content
            // script then proves a live editor -> local goes Ready.
            local.setStatus(TransportStatus.Ready)
            runCurrent()
            assertEquals("local", router.activeId.value)

            router.sendInbound("b")
            assertEquals(listOf("a"), remote.sent)
            assertEquals(listOf("b"), local.sent)
        }

    // ── Scenario 4: remote first, then user signs in on the local webview ───
    @Test
    fun remoteFirst_thenUserLogsIntoLocalWebview_transitionsToLocal() =
        runTest {
            val local = FakeTransport("local")
            val remote = FakeTransport("remote")
            val router = routerOf(local, remote, backgroundScope, { testScheduler.currentTime })
            runCurrent()

            remote.setStatus(TransportStatus.Ready)
            runCurrent()
            assertEquals("remote", router.activeId.value)

            // The local webview navigates through login: Connecting -> Degraded
            // (page up, unauthenticated) -> Ready (nodes_updated after sign-in).
            local.setStatus(TransportStatus.Connecting)
            runCurrent()
            assertEquals("still remote while local connects", "remote", router.activeId.value)
            local.setStatus(TransportStatus.Degraded)
            runCurrent()
            assertEquals("still remote — local not authed", "remote", router.activeId.value)
            local.setStatus(TransportStatus.Ready)
            runCurrent()
            assertEquals("local", router.activeId.value)
        }

    // ── Failover: an authed local backend dies -> fall to remote ────────────
    @Test
    fun localFails_failsOverToRemote() =
        runTest {
            val local = FakeTransport("local")
            val remote = FakeTransport("remote")
            val router = routerOf(local, remote, backgroundScope, { testScheduler.currentTime })
            runCurrent()

            local.setStatus(TransportStatus.Ready)
            remote.setStatus(TransportStatus.Ready)
            runCurrent()
            assertEquals("local", router.activeId.value)

            local.setStatus(TransportStatus.Failed)
            runCurrent()
            assertEquals("remote", router.activeId.value)
        }

    // ── Circuit breaker: a flapping remote gets benched ─────────────────────
    @Test
    fun flappingRemote_isBenchedByBreaker() =
        runTest {
            val local = FakeTransport("local")
            val remote = FakeTransport("remote")
            val router = routerOf(local, remote, backgroundScope, { testScheduler.currentTime }, cooldownMs = 20_000L)
            runCurrent()

            local.setStatus(TransportStatus.Degraded) // local not a real option
            // Remote fails 3x consecutively -> breaker trips Open. (Connecting
            // between failures does not reset the count; only a Ready would.)
            repeat(3) {
                remote.setStatus(TransportStatus.Failed)
                runCurrent()
                remote.setStatus(TransportStatus.Connecting)
                runCurrent()
            }
            // The remote blips Ready while the breaker is still Open — it must
            // stay benched (a just-tripped breaker can't un-bench on a blip).
            remote.setStatus(TransportStatus.Ready)
            runCurrent()
            assertEquals("benched while Open", "local", router.activeId.value)

            // After the cooldown the breaker half-opens; a reconnect (a real
            // status change) lets the router re-evaluate and adopt remote.
            testScheduler.advanceTimeBy(20_001)
            remote.setStatus(TransportStatus.Connecting)
            runCurrent()
            remote.setStatus(TransportStatus.Ready)
            runCurrent()
            assertEquals("remote", router.activeId.value)
        }

    // ── Outbound from either backend reaches the bridge sink ────────────────
    @Test
    fun outboundFromEitherBackendReachesTheSink() =
        runTest {
            val local = FakeTransport("local")
            val remote = FakeTransport("remote")
            val router = routerOf(local, remote, backgroundScope, { testScheduler.currentTime })
            val received = mutableListOf<String>()
            router.setOutboundSink { received += it }
            runCurrent()

            local.emitOutbound("""{"type":"nodes_updated"}""")
            remote.emitOutbound("""{"type":"session_progress"}""")
            assertEquals(2, received.size)
        }

    // ── Scenario 5: two phones, independent routers, independent handoffs ───
    @Test
    fun twoPhones_routersAreIndependent() =
        runTest {
            // Phone A and Phone B each run their own router. A going local must
            // not affect B (no shared client state — the session is shared on the
            // server, not the routers).
            val aLocal = FakeTransport("local")
            val aRemote = FakeTransport("remote")
            val bLocal = FakeTransport("local")
            val bRemote = FakeTransport("remote")
            val routerA = routerOf(aLocal, aRemote, backgroundScope, { testScheduler.currentTime })
            val routerB = routerOf(bLocal, bRemote, backgroundScope, { testScheduler.currentTime })
            runCurrent()

            // Both phones start on the shared remote session.
            aRemote.setStatus(TransportStatus.Ready)
            bRemote.setStatus(TransportStatus.Ready)
            runCurrent()
            assertEquals("remote", routerA.activeId.value)
            assertEquals("remote", routerB.activeId.value)

            // Phone A's webview authenticates; A flips to local, B is unaffected.
            aLocal.setStatus(TransportStatus.Ready)
            runCurrent()
            assertEquals("local", routerA.activeId.value)
            assertEquals("remote", routerB.activeId.value)

            // Then Phone B authenticates too.
            bLocal.setStatus(TransportStatus.Ready)
            runCurrent()
            assertEquals("local", routerB.activeId.value)
        }

    // ── Scenario 6: two users on one remote session diverge to own locals ───
    @Test
    fun twoUsersShareRemote_thenDivergeToOwnLocalWebviews() =
        runTest {
            // Both clients attached to one shared remote session, then each signs
            // into their own Google account on their own local webview and the
            // two diverge to isolated local sessions.
            val u1Local = FakeTransport("local")
            val u1Remote = FakeTransport("remote")
            val u2Local = FakeTransport("local")
            val u2Remote = FakeTransport("remote")
            val router1 = routerOf(u1Local, u1Remote, backgroundScope, { testScheduler.currentTime })
            val router2 = routerOf(u2Local, u2Remote, backgroundScope, { testScheduler.currentTime })
            runCurrent()

            u1Remote.setStatus(TransportStatus.Ready)
            u2Remote.setStatus(TransportStatus.Ready)
            runCurrent()
            router1.sendInbound("u1-on-remote")
            router2.sendInbound("u2-on-remote")
            assertEquals(listOf("u1-on-remote"), u1Remote.sent)
            assertEquals(listOf("u2-on-remote"), u2Remote.sent)

            // User 1 authenticates locally and diverges; user 2 still on remote.
            u1Local.setStatus(TransportStatus.Ready)
            runCurrent()
            router1.sendInbound("u1-on-local")
            router2.sendInbound("u2-still-remote")
            assertEquals(listOf("u1-on-local"), u1Local.sent)
            assertEquals(listOf("u2-on-remote", "u2-still-remote"), u2Remote.sent)

            // User 2 then authenticates locally too — fully diverged.
            u2Local.setStatus(TransportStatus.Ready)
            runCurrent()
            assertEquals("local", router1.activeId.value)
            assertEquals("local", router2.activeId.value)
            assertTrue("users never cross-talk", u1Local.sent.none { it.startsWith("u2") })
        }
}
