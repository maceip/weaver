package com.weaver.app.bridge.transport

/**
 * Classifies a raw `Outbound` bridge JSON payload into a transport-health
 * verdict, factored out of [LocalWebViewTransport] so the signal detection
 * can be tested against real message shapes.
 *
 * `nodes_updated` (and other editor-only events) can only originate from a
 * mounted React Flow editor, which only renders for an authenticated Stitch
 * session — so it proves the local WebView is healthy. `error{selector_breakage}`
 * / `error{canvas_missing}` prove the opposite.
 *
 * Returns null when the message carries no health signal (the caller leaves
 * the status unchanged).
 */
internal object OutboundClassifier {
    private val typeRegex = Regex("\"type\"\\s*:\\s*\"([^\"]+)\"")

    private val healthyTypes =
        setOf(
            "nodes_updated",
            "selection_changed",
            "agent_log_updated",
            "session_started",
            "session_progress",
        )

    private val breakageCodes = setOf("selector_breakage", "canvas_missing")

    fun classify(outboundJson: String): TransportStatus? {
        val type = typeRegex.find(outboundJson)?.groupValues?.get(1) ?: return null
        return when {
            type in healthyTypes -> {
                TransportStatus.Ready
            }

            type == "error" && breakageCodes.any { outboundJson.contains(it) } -> {
                TransportStatus.Degraded
            }

            else -> {
                null
            }
        }
    }
}
