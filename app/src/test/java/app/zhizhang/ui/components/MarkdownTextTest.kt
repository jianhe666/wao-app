package app.zhizhang.ui.components

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun `markdown markers are removed for plain release notes`() {
        val result = MarkdownText.toPlainText(
            """
            # 哇哦记账 v1.1.3

            - **修复**了一些[已知问题](https://example.com)
            - `优化`了部分体验
            """.trimIndent(),
        )
        assertTrue(result.startsWith("哇哦记账 v1.1.3"))
        assertTrue(result.contains("• 修复了一些已知问题 (https://example.com)"))
        assertTrue(result.contains("• 优化了部分体验"))
        assertFalse(result.contains("**"))
        assertFalse(result.contains("#"))
    }
}
