package app.zhizhang

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatsInteractionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tapDonutShowsCategoryDetail() {
        val app = ApplicationProvider.getApplicationContext<ZhizhangApp>()
        runBlocking { app.repository.addDemoData() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("对话").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tab-stats").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("支出构成").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        val donut = composeRule.onNodeWithTag("category-donut")
        donut.assertExists()
        donut.performTouchInput {
            click(Offset(width / 2f, height * 0.08f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("donut-selected-category").assertExists()
    }
}
