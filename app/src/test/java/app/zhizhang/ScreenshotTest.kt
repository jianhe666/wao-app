package app.zhizhang

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * 本机 Robolectric 截图冒烟测试。需要先加入演示数据，再逐页截图。
 * 运行方式：./gradlew :app:testDebugUnitTest --tests app.zhizhang.ScreenshotTest
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun captureMainScreens() {
        val app = ApplicationProvider.getApplicationContext<ZhizhangApp>()
        runBlocking {
            app.repository.addDemoData()
            app.repository.learnRule("瑞幸咖啡", "food")
            app.repository.learnRule("滴滴出行", "transport")
            app.repository.learnRule("京东", "shopping")
            app.settings.setAiConfig("sk-demo", "https://api.deepseek.com", "deepseek-flash")
            app.settings.setAiEnabled(true)
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithContentDescription("添加账单图片")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("添加账单图片").fetchSemanticsNode()
        capture("01-home")

        composeRule.onNodeWithContentDescription("切换聊天风格").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("说话方式").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.waitForIdle()

        // 反复切换底栏，确保每次点击都能进入目标页。
        repeat(2) {
            composeRule.onNodeWithTag("tab-stats").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText("支出构成").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("tab-settings").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText("月度预算").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("tab-home").performClick()
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithContentDescription("添加账单图片")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }

        composeRule.onNodeWithTag("tab-ledger").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("美团外卖").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        capture("02-ledger")

        composeRule.onNodeWithText("2026年10月").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("选择月份").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("美团外卖").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("编辑账目").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("关闭").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("更多操作")[0].performClick()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("删除这笔账目？").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()

        runBlocking { app.settings.setAiEnabled(false) }
        composeRule.onNodeWithTag("tab-settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("智能导入").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("智能导入").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        capture("05-import")
        composeRule.onNodeWithText("示例").performClick()
        composeRule.onNodeWithText("识别").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("识别到", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        capture("06-import-result")
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tab-ledger").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("记一笔").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("保存").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("1").performClick()
        composeRule.onNodeWithText("5").performClick()
        composeRule.onNodeWithText("0").performClick()
        composeRule.onNodeWithText("0").performClick()
        composeRule.waitForIdle()
        capture("07-editor")
        composeRule.onNodeWithText("今天", substring = true).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("选择日期").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("取消").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("关闭").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tab-stats").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("统计").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        capture("03-stats")

        composeRule.onNodeWithTag("tab-settings").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("我的").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        capture("04-settings")

        composeRule.onNodeWithText("AI 智能分类").performScrollTo().performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.OnClick,
        )
        composeRule.waitForIdle()
        capture("09-ai-category")
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("从网盘恢复").performScrollTo()
        composeRule.waitForIdle()
        capture("10-baidu")

        composeRule.onNodeWithTag("tab-home").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("AI 助手还没打开").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        capture("12-ai-off")

        composeRule.onNodeWithText("去打开 AI 助手").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("AI 助手").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            val view = composeRule.activity.window.decorView
            val width = view.width.takeIf { it > 0 } ?: 1080
            val height = view.height.takeIf { it > 0 } ?: 2339
            if (view.width == 0 || view.height == 0) {
                view.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
                )
                view.layout(0, 0, width, height)
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val dir = File("../screenshots").apply { mkdirs() }
            FileOutputStream(File(dir, "$name.png")).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }
}
