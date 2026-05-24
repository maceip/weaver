package com.weaver.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.weaver.app.agent.TaskEntry
import com.weaver.app.agent.TaskStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** End-to-end UI coverage of the floating agent orb. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentOrbTest {
    @get:Rule
    val rule = createComposeRule()

    private fun task(
        prompt: String,
        status: TaskStatus,
    ) = TaskEntry(id = prompt, prompt = prompt, status = status, startedAt = 0L)

    @Test
    fun collapsedOrbHidesThePanel() {
        // Infinite pulse animation — keep the clock manual so waitForIdle won't hang.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AgentOrb(tasks = listOf(task("p1", TaskStatus.Running)), online = true)
        }
        rule.onNodeWithTag("agentOrb").assertExists()
        rule.onNodeWithText("Recent prompts").assertDoesNotExist()
    }

    @Test
    fun tappingTheOrbRevealsTheLastThreePrompts() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AgentOrb(
                tasks =
                    listOf(
                        task("draft the hero", TaskStatus.Done),
                        task("add a footer", TaskStatus.Running),
                        task("tighten spacing", TaskStatus.Queued),
                    ),
                online = true,
            )
        }
        rule.onNodeWithTag("agentOrb").performClick()
        rule.mainClock.advanceTimeBy(800)

        rule.onNodeWithText("Recent prompts").assertExists()
        rule.onNodeWithText("draft the hero").assertExists()
        rule.onNodeWithText("add a footer").assertExists()
        rule.onNodeWithText("tighten spacing").assertExists()
    }

    @Test
    fun panelShowsAStatusLabelPerTask() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AgentOrb(
                tasks =
                    listOf(
                        task("a", TaskStatus.Done),
                        task("b", TaskStatus.Running),
                        task("c", TaskStatus.Queued),
                    ),
                online = true,
            )
        }
        rule.onNodeWithTag("agentOrb").performClick()
        rule.mainClock.advanceTimeBy(800)

        rule.onNodeWithText("done").assertExists()
        rule.onNodeWithText("running").assertExists()
        rule.onNodeWithText("queued").assertExists()
    }

    @Test
    fun onlyTheThreeMostRecentPromptsAreShown() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AgentOrb(
                tasks =
                    listOf(
                        task("oldest", TaskStatus.Done),
                        task("second", TaskStatus.Done),
                        task("third", TaskStatus.Done),
                        task("newest", TaskStatus.Running),
                    ),
                online = true,
            )
        }
        rule.onNodeWithTag("agentOrb").performClick()
        rule.mainClock.advanceTimeBy(800)

        rule.onNodeWithText("oldest").assertDoesNotExist()
        rule.onNodeWithText("newest").assertExists()
        rule.onNodeWithText("third").assertExists()
    }

    @Test
    fun offlinePanelShowsTheQueuedCount() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AgentOrb(
                tasks =
                    listOf(
                        task("queued one", TaskStatus.Queued),
                        task("queued two", TaskStatus.Queued),
                    ),
                online = false,
            )
        }
        rule.onNodeWithTag("agentOrb").performClick()
        rule.mainClock.advanceTimeBy(800)

        rule.onNodeWithText("offline · 2 queued").assertExists()
    }
}
