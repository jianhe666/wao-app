package app.zhizhang.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountsTest {

    @Test
    fun `custom card id is resolved for display`() {
        val account = Accounts.byId("card:招商银行:1234")
        assertEquals("招商银行 尾号1234", account.name)
        assertTrue(Accounts.isValid(account.id))
    }

    @Test
    fun `unknown account is not valid`() {
        assertFalse(Accounts.isValid("card:broken"))
        assertFalse(Accounts.isValid("not-an-account"))
    }
}
