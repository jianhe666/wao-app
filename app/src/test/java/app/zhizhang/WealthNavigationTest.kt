package app.zhizhang

import androidx.activity.compose.setContent
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
class WealthNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun overviewOpensIndependentSections() {
        composeRule.activity.setContent {
            app.zhizhang.ui.theme.ZhizhangTheme {
                app.zhizhang.ui.wealth.WealthScreen(onOpenAssets = {})
            }
        }
        composeRule.onNodeWithText("账户余额").assertExists()
        composeRule.onNodeWithText("资产", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("攒攒").performClick()
        composeRule.onNodeWithText("＋ 新增攒钱目标").assertExists()
        composeRule.onNodeWithText("还没有目标，先为想完成的事攒一笔吧。").assertExists()
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("账户余额").performClick()
        composeRule.onAllNodesWithText("账户余额").assertCountEquals(2)
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("订阅").performClick()
        composeRule.onNodeWithText("还没有订阅记录").assertExists()
    }
}
