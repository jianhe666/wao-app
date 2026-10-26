package app.zhizhang.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatHistoryStoreTest {

    @Test
    fun `chat history is retained until cleared`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("ai_chat_history", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = ChatHistoryStore(context)
        store.save("[{\"role\":\"USER\"}]")
        assertEquals("[{\"role\":\"USER\"}]", ChatHistoryStore(context).load())
        store.clear()
        assertNull(ChatHistoryStore(context).load())
    }
}
