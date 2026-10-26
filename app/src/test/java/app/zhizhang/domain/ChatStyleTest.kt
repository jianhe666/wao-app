package app.zhizhang.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStyleTest {

    @Test
    fun `presets include cute and serious`() {
        assertEquals("可爱", ChatStyles.resolve("cute", "").name)
        assertEquals("正经", ChatStyles.resolve("serious", "").name)
        assertEquals("理性", ChatStyles.default.name)
    }

    @Test
    fun `custom style uses instruction`() {
        val style = ChatStyles.resolve("custom", "像温柔的朋友，提醒我别冲动。")
        assertEquals("自定义", style.name)
        assertTrue(style.instruction.contains("温柔"))
    }

    @Test
    fun `blank custom falls back to sane instruction`() {
        val style = ChatStyles.resolve("custom", "   ")
        assertTrue(style.instruction.contains("朋友"))
    }

    @Test
    fun `styles are visibly different`() {
        val cute = ChatStyles.resolve("cute", "")
        val serious = ChatStyles.resolve("serious", "")
        val rational = ChatStyles.resolve("rational", "")
        assertTrue(cute.instruction.contains("呀"))
        assertTrue(serious.instruction.contains("书面"))
        assertTrue(rational.instruction.contains("短句"))
        assertTrue(cute.temperature > serious.temperature)
        assertTrue(cute.temperature > rational.temperature)
    }

    @Test
    fun `named custom styles resolve independently`() {
        val styles = listOf(
            CustomChatStyle("custom-a", "温柔", "像朋友一样说话。"),
            CustomChatStyle("custom-b", "严格", "直接指出超支。"),
        )
        assertEquals("温柔", ChatStyles.resolve("custom-a", styles).name)
        assertEquals("严格", ChatStyles.resolve("custom-b", styles).name)
        assertTrue(ChatStyles.resolve("custom-a", styles).instruction.contains("朋友"))
    }
}
