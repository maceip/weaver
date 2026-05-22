package com.weaver.app.webview

import com.weaver.app.bridge.ExportKind
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The injected content script is a generated string; these checks catch a
 * dropped `request_export` handler or an export kind that lost its menu-label
 * mapping before it silently no-ops on a real device.
 */
class StitchContentScriptTest {

    private val source = StitchContentScript.source

    @Test
    fun requestExportHandlerOpensTheMenuAndMatchesAnItem() {
        assertTrue(source.contains("case 'request_export'"))
        // Drives Stitch's own Export menu: matches a [role=menuitem] by text.
        assertTrue(source.contains("[role=\"menuitem\"]"))
    }

    @Test
    fun attachFilesHandlerInjectsViaDataTransferAndDispatchesChange() {
        assertTrue(source.contains("case 'attach_files'"))
        assertTrue(source.contains("new DataTransfer()"))
        assertTrue(source.contains("input[type=\"file\"]"))
        assertTrue(source.contains("inp.files=dt.files"))
        // Stitch only uploads when the change event fires — it must be dispatched.
        assertTrue(source.contains("new Event('change'"))
    }

    @Test
    fun emitsDiagnosticWhenNoFileInputToReceiveUpload() {
        assertTrue(source.contains("attach_input_missing"))
    }

    @Test
    fun exportLabelMapCoversEveryExportKind() {
        for (kind in ExportKind.entries) {
            assertTrue("no Stitch menu label wired for ${kind.name}", source.contains(kind.name + ":["))
        }
    }

    @Test
    fun emitsDiagnosticsWhenExportSelectorsBreak() {
        assertTrue(source.contains("export_button_missing"))
        assertTrue(source.contains("export_item_missing"))
    }
}
