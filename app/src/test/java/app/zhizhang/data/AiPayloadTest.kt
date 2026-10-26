package app.zhizhang.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiPayloadTest {

    @Test
    fun `image turn keeps text and image url`() {
        val dataUrl = "data:image/jpeg;base64,abc123"
        val payload = aiMessagePayload(
            AiChatTurn(role = "user", content = "识别这张账单", imageDataUrl = dataUrl),
        )
        assertEquals("user", payload.getString("role"))
        val content = payload.getJSONArray("content")
        assertEquals(2, content.length())
        assertEquals("text", content.getJSONObject(0).getString("type"))
        assertEquals("识别这张账单", content.getJSONObject(0).getString("text"))
        assertEquals("image_url", content.getJSONObject(1).getString("type"))
        assertEquals(
            dataUrl,
            content.getJSONObject(1).getJSONObject("image_url").getString("url"),
        )
    }

    @Test
    fun `vision response content array is readable`() {
        val message = JSONObject().put(
            "content",
            JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", "识别完成"))
            },
        )
        assertEquals("识别完成", aiMessageText(message))
    }

    @Test
    fun `plain text response content is readable`() {
        val message = JSONObject().put("content", "记好了")
        assertEquals("记好了", aiMessageText(message))
    }

    @Test
    fun `text turn stays plain content`() {
        val payload = aiMessagePayload(AiChatTurn(role = "assistant", content = "记好了"))
        assertEquals("assistant", payload.getString("role"))
        assertEquals("记好了", payload.getString("content"))
        assertTrue(payload.optJSONArray("content") == null)
    }
}
