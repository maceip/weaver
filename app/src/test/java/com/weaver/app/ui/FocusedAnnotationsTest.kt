package com.weaver.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** End-to-end UI coverage of the per-design star + notes controls. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FocusedAnnotationsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tappingTheStarTogglesFavorite() {
        var toggles = 0
        rule.setContent {
            FocusedAnnotations(
                isFavorite = false,
                noteCount = 0,
                onToggleFavorite = { toggles++ },
                onAddNote = {},
            )
        }
        rule.onNodeWithContentDescription("Favorite").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun noteCountBadgeShowsTheCount() {
        rule.setContent {
            FocusedAnnotations(
                isFavorite = true,
                noteCount = 3,
                onToggleFavorite = {},
                onAddNote = {},
            )
        }
        rule.onNodeWithText("3").assertExists()
    }

    @Test
    fun addingANoteFlowsThroughTheDialog() {
        var saved: String? = null
        rule.setContent {
            FocusedAnnotations(
                isFavorite = false,
                noteCount = 0,
                onToggleFavorite = {},
                onAddNote = { saved = it },
            )
        }
        rule.onNodeWithContentDescription("Add note").performClick()
        rule.onNodeWithText("Add a note").assertExists()
        rule.onNodeWithTag("noteField").performTextInput("tighten the spacing")
        rule.onNodeWithText("Save").performClick()

        assertEquals("tighten the spacing", saved)
    }

    @Test
    fun cancellingTheDialogSavesNothing() {
        var saved: String? = null
        rule.setContent {
            FocusedAnnotations(
                isFavorite = false,
                noteCount = 0,
                onToggleFavorite = {},
                onAddNote = { saved = it },
            )
        }
        rule.onNodeWithContentDescription("Add note").performClick()
        rule.onNodeWithTag("noteField").performTextInput("a note")
        rule.onNodeWithText("Cancel").performClick()

        assertNull(saved)
        rule.onNodeWithText("Add a note").assertDoesNotExist()
    }
}
