package com.weaver.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.weaver.app.bridge.StitchNode
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Single on-device integration test. Fails unless the real app stack works:
 * MainActivity → WebView/Stitch → [com.weaver.app.bridge.Bridge] nodes → UI export → wire.
 *
 * **Not run in CI** (see [TESTING.md]). Requires a plugged-in device, network, and
 * Stitch loading in the headless WebView. No injected canvas state, no isolated composables.
 *
 * Debug builds use dev-mode sign-in (placeholder OAuth client id). That is still not
 * production Google OAuth, but it is the real sign-in code path in this APK flavor.
 */
@RunWith(AndroidJUnit4::class)
class WeaverIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val bridge get() = composeRule.activity.instrumentationBridge

    @Test
    fun liveStitchSession_nodesFromWebView_exportHitsBridge() {
        signInIfNeeded()
        openFreshProject()
        val node = awaitNodesFromWebView(STITCH_NODES_TIMEOUT_MS)
        focusNode(node)
        pickExportToFigma(node.id)
        assertExportWire(node.id)
    }

    private fun signInIfNeeded() {
        composeRule.waitUntil(20_000) {
            val login = composeRule.onAllNodesWithText("Continue with Google").fetchSemanticsNodes()
            val home = composeRule.onAllNodesWithText("Start a new thread", substring = true)
                .fetchSemanticsNodes()
            val inProject = composeRule.onAllNodesWithTag("agentOrb").fetchSemanticsNodes()
            login.isNotEmpty() || home.isNotEmpty() || inProject.isNotEmpty()
        }
        if (composeRule.onAllNodesWithText("Continue with Google").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Continue with Google").performClick()
        }
    }

    private fun openFreshProject() {
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("Start a new thread", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("integration probe ${System.currentTimeMillis()}")
        composeRule.onNodeWithContentDescription("send").performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("agentOrb").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Blocks until [Bridge.nodes] is populated from the WebView content script.
     * Never calls [com.weaver.app.bridge.Bridge.handleOutbound] — fake injection is not allowed here.
     */
    private fun awaitNodesFromWebView(timeoutMs: Long): StitchNode {
        var nodes: List<StitchNode> = emptyList()
        try {
            composeRule.waitUntil(timeoutMs) {
                composeRule.runOnUiThread { nodes = bridge.nodes.value }
                nodes.isNotEmpty()
            }
        } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
            fail(
                """
                Stitch WebView did not deliver nodes_updated within ${timeoutMs / 1000}s.
                This test requires a device with network access and a loadable Stitch session
                (content script + canvas). It is not a UI smoke test.
                """.trimIndent(),
            )
        }
        return nodes.first()
    }

    private fun focusNode(node: StitchNode) {
        composeRule.onNodeWithText(node.id).performClick()
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("canvasExportButton").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pickExportToFigma(nodeId: String) {
        composeRule.onNodeWithTag("canvasExportButton").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("Copy to Figma", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Copy to Figma", useUnmergedTree = true).performClick()
        composeRule.waitUntil(10_000) {
            bridge.inboundSentForInstrumentation().any { wire ->
                wire.contains("request_export") && wire.contains("Figma") && wire.contains(nodeId)
            }
        }
    }

    private fun assertExportWire(nodeId: String) {
        val exportWire = bridge.inboundSentForInstrumentation().last {
            it.contains("request_export") && it.contains("Figma")
        }
        assertTrue(
            "Export UI did not produce request_export wire JSON for node $nodeId: $exportWire",
            exportWire.contains(nodeId),
        )
    }

    private companion object {
        const val STITCH_NODES_TIMEOUT_MS = 90_000L
    }
}
