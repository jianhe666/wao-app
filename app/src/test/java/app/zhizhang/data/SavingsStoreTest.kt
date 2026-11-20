package app.zhizhang.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SavingsStoreTest {
    @Test
    fun independentGoalsSurviveReloadAndBackup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = SavingsGoal("a", "旅行", 100_000, listOf(
            SavingsDeposit("1", 5000, 100),
            SavingsDeposit("2", 3000, 200),
        ))
        val second = SavingsGoal("b", "相机", 200_000)
        assertTrue(SavingsStore(context).save(listOf(first, second)))
        val reloaded = SavingsStore(context)
        assertEquals(8000L, reloaded.goals()[0].savedCents)
        assertEquals(0L, reloaded.goals()[1].savedCents)
        assertEquals(listOf(first, second), reloaded.validateSnapshot(reloaded.snapshot()))
        assertTrue(reloaded.save(listOf(first.copy(deposits = first.deposits.drop(1)), second)))
        assertEquals(3000L, SavingsStore(context).goals()[0].savedCents)
    }

    @Test
    fun amountsRejectSilentCorrections() {
        assertEquals(1234L, SavingsStore.amountCents("12.34"))
        for (invalid in listOf("-3", "1.234", "1..2", "0", "NaN", "9999999999")) {
            assertNull(SavingsStore.amountCents(invalid))
        }
    }
}
