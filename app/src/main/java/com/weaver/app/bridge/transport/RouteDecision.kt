package com.weaver.app.bridge.transport

/** Which backend the router selected. */
internal enum class RouteChoice { Local, Remote, None }

/**
 * The pure routing decision, factored out of [BridgeRouter] so it can be
 * tested exhaustively against the full status × breaker matrix.
 *
 * Policy — cheapest viable wins:
 *  1. local Ready, breaker permits          -> Local  (no server hop)
 *  2. else remote Ready, breaker permits    -> Remote (always authenticated)
 *  3. else local Degraded, breaker permits  -> Local  (up but unauthenticated)
 *  4. else remote permitted                 -> Remote
 *  5. else local permitted                  -> Local
 *  6. else                                  -> None
 */
internal fun routeDecision(
    localStatus: TransportStatus,
    remoteStatus: TransportStatus,
    localUsable: Boolean,
    remoteUsable: Boolean,
): RouteChoice = when {
    localStatus == TransportStatus.Ready && localUsable -> RouteChoice.Local
    remoteStatus == TransportStatus.Ready && remoteUsable -> RouteChoice.Remote
    localStatus == TransportStatus.Degraded && localUsable -> RouteChoice.Local
    remoteUsable -> RouteChoice.Remote
    localUsable -> RouteChoice.Local
    else -> RouteChoice.None
}
