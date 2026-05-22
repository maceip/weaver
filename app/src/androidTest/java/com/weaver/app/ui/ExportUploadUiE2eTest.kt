package com.weaver.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.weaver.app.bridge.AttachmentKind
import com.weaver.app.bridge.Bridge
import com.weaver.app.bridge.Inbound
import com.weaver.app.bridge.transport.BridgeTransport
import com.weaver.app.bridge.transport.TransportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end UI coverage: a real user gesture on the Compose surface, routed
 * through a real [Bridge], producing the wire JSON a backend would receive.
 */
@RunWith(AndroidJUnit4::class)
class ExportUploadUiE2eTest {
    @get:Rule
    val composeRule = createComposeRule()

    private class RecordingTransport : BridgeTransport {
        override val id = "recording"
        override val status: StateFlow<TransportStatus> = MutableStateFlow(TransportStatus.Ready)
        val sent = mutableListOf<String>()

        override fun setOutboundSink(sink: (String) -> Unit) = Unit

        override fun start() = Unit

        override fun stop() = Unit

        override fun sendInbound(payloadJson: String) {
            sent += payloadJson
        }
    }

    /** Canvas toolbar → export sheet → pick → Bridge → wire, the whole path. */
    @Test
    fun exportPickedInTheCanvasToolbarReachesTheBridgeWire() {
        val bridge = Bridge()
        val transport = RecordingTransport()
        bridge.bindTransport(transport)

        composeRule.setContent {
            MaterialTheme {
                CanvasToolbar(
                    selectedIds = listOf("node-1"),
                    onAction = { _, _ -> },
                    onExport = { kind, ids ->
                        bridge.send(Inbound.RequestExport(kind, ids.firstOrNull()))
                    },
                    figmaInstalled = true,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Export").performClick()
        composeRule.onNodeWithText("Copy to Figma").performClick()

        val wire = transport.sent.single()
        assertTrue(wire, wire.contains("\"type\":\"request_export\""))
        assertTrue(wire, wire.contains("\"kind\":\"Figma\""))
        assertTrue(wire, wire.contains("node-1"))
    }

    /** "Upload Files" in the prompt composer raises the native-upload request. */
    @Test
    fun uploadFilesMenuItemRaisesTheUploadRequest() {
        var attached: AttachmentKind? = null

        composeRule.setContent {
            MaterialTheme {
                PromptInput(
                    state = PromptInputState(),
                    presets = emptyList(),
                    onStateChange = {},
                    onSubmit = {},
                    onAttach = { attached = it },
                    onVoice = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Add").performClick()
        composeRule.onNodeWithText("Upload Files").performClick()

        assertEquals(AttachmentKind.UploadFile, attached)
    }
}
