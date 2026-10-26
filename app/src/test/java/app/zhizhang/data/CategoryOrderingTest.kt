package app.zhizhang.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryOrderingTest {

    @After
    fun cleanup() {
        Categories.setCustomCategories(emptyList())
    }

    @Test
    fun `custom categories first and other last`() {
        val custom = Category("custom:test", "测试", "🧪", 0xFF123456, TxType.EXPENSE)
        Categories.setCustomCategories(listOf(custom))
        val expense = Categories.expense
        assertEquals("food", expense.first().id)
        assertEquals(custom.id, expense[expense.lastIndex - 1].id)
        assertEquals("other_expense", expense.last().id)
    }
}
