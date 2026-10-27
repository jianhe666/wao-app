package app.zhizhang.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatReplyGuardTest {

    @Test
    fun `detects recorded claims`() {
        assertTrue(ChatReplyGuard.claimsRecorded("好的，记下了。"))
        assertTrue(ChatReplyGuard.claimsRecorded("已经帮你记好了"))
        assertTrue(ChatReplyGuard.claimsRecorded("这笔已入账"))
    }

    @Test
    fun `does not treat negative or normal replies as recorded`() {
        assertFalse(ChatReplyGuard.claimsRecorded("这笔还没有记下，请补金额"))
        assertFalse(ChatReplyGuard.claimsRecorded("没有记录成功"))
        assertFalse(ChatReplyGuard.claimsRecorded("你想记多少？"))
    }
}
