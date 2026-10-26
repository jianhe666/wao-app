package app.zhizhang.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCoachTest {

    @Test
    fun `no budget means no hint`() {
        assertNull(BudgetCoach.evaluate(amountCents = 5_000, monthExpenseCents = 10_000, budgetCents = 0))
    }

    @Test
    fun `ordinary small spending does not nag`() {
        assertNull(BudgetCoach.evaluate(3_850, 209_890, 600_000))
        assertNull(BudgetCoach.evaluate(8_000, 100_000, 600_000))
    }

    @Test
    fun `warns when crossing ninety percent`() {
        val pulse = BudgetCoach.evaluate(240_000, 300_000, 600_000)
        assertEquals(BudgetTone.WATCH, pulse?.tone)
    }

    @Test
    fun `alerts when over budget`() {
        val pulse = BudgetCoach.evaluate(300_000, 500_000, 600_000)
        assertEquals(BudgetTone.ALERT, pulse?.tone)
        assertTrue(pulse?.message.orEmpty().contains("超"))
    }

    @Test
    fun `nudges large purchase even with room`() {
        val pulse = BudgetCoach.evaluate(120_000, 10_000, 5_000_000)
        assertEquals(BudgetTone.BIG, pulse?.tone)
    }
}
