package com.weaver.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.weaver.app.bridge.ExportKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val allLabels = listOf(
        "Copy to Figma", "Download code", "Copy code", "Google AI Studio",
        "Firebase", "Jules", "Lovable", "Bolt",
    )

    @Test
    fun showsEveryExportOption() {
        composeRule.setContent {
            MaterialTheme { ExportSheet(figmaInstalled = false, onPick = {}, onDismiss = {}) }
        }
        allLabels.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun recommendsAnOptionWhenFigmaIsNotInstalled() {
        composeRule.setContent {
            MaterialTheme { ExportSheet(figmaInstalled = false, onPick = {}, onDismiss = {}) }
        }
        composeRule.onNodeWithText("RECOMMENDED").assertIsDisplayed()
    }

    @Test
    fun recommendsAnOptionWhenFigmaIsInstalled() {
        composeRule.setContent {
            MaterialTheme { ExportSheet(figmaInstalled = true, onPick = {}, onDismiss = {}) }
        }
        composeRule.onNodeWithText("RECOMMENDED").assertIsDisplayed()
    }

    @Test
    fun pickingFirebaseReportsFirebaseKind() {
        var picked: ExportKind? = null
        composeRule.setContent {
            MaterialTheme {
                ExportSheet(figmaInstalled = true, onPick = { picked = it }, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("Firebase").performClick()
        assertEquals(ExportKind.Firebase, picked)
    }

    @Test
    fun pickingCopyToFigmaReportsFigmaKind() {
        var picked: ExportKind? = null
        composeRule.setContent {
            MaterialTheme {
                ExportSheet(figmaInstalled = false, onPick = { picked = it }, onDismiss = {})
            }
        }
        composeRule.onNodeWithText("Copy to Figma").performClick()
        assertEquals(ExportKind.Figma, picked)
    }
}
