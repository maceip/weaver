package com.weaver.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.weaver.app.bridge.ExportKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasToolbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun singleSelectExportButtonOpensTheSheet() {
        composeRule.setContent {
            MaterialTheme {
                CanvasToolbar(
                    selectedIds = listOf("node-1"),
                    onAction = { _, _ -> },
                    onExport = { _, _ -> },
                    figmaInstalled = false,
                )
            }
        }
        composeRule.onNodeWithContentDescription("Export").performClick()
        composeRule.onNodeWithText("Export design").assertIsDisplayed()
    }

    @Test
    fun pickingExportRoutesKindAndSelectedId() {
        var kind: ExportKind? = null
        var ids: List<String>? = null
        composeRule.setContent {
            MaterialTheme {
                CanvasToolbar(
                    selectedIds = listOf("node-1"),
                    onAction = { _, _ -> },
                    onExport = { k, i ->
                        kind = k
                        ids = i
                    },
                    figmaInstalled = true,
                )
            }
        }
        composeRule.onNodeWithContentDescription("Export").performClick()
        composeRule.onNodeWithText("Copy to Figma").performClick()
        assertEquals(ExportKind.Figma, kind)
        assertEquals(listOf("node-1"), ids)
    }

    @Test
    fun multiSelectToolbarHasNoExportButton() {
        composeRule.setContent {
            MaterialTheme {
                CanvasToolbar(
                    selectedIds = listOf("node-1", "node-2"),
                    onAction = { _, _ -> },
                    onExport = { _, _ -> },
                    figmaInstalled = false,
                )
            }
        }
        composeRule.onNodeWithText("DESIGN.md").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Export").assertDoesNotExist()
    }
}
