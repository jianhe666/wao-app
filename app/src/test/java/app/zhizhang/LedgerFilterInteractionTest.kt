package app.zhizhang

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LedgerFilterInteractionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun categoryFilterSheetHidesOtherCategories() {
        val app = ApplicationProvider.getApplicationContext<ZhizhangApp>()
        runBlocking { app.repository.addDemoData() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("对话").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("tab-ledger").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("美团外卖").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("ledger-filter").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("筛选流水").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("filter-category-food").performClick()
        composeRule.onNodeWithTag("filter-apply").performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithText("盒马鲜生").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("在地咖啡").fetchSemanticsNodes().isNotEmpty())
    }
}
