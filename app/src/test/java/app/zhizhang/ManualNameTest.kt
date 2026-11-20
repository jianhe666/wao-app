package app.zhizhang

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ManualNameTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun savedManualNameAppearsInLedger() {
        composeRule.onNodeWithTag("tab-ledger").performClick()
        composeRule.onNodeWithContentDescription("记一笔").performClick()
        composeRule.onNodeWithText("名称 / 商户").performTextInput("测试拿铁")
        composeRule.onNodeWithText("2").performClick()
        composeRule.onNodeWithText("8").performClick()
        composeRule.onNodeWithText("保存").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription("记一笔").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("测试拿铁").assertExists()
    }
}
