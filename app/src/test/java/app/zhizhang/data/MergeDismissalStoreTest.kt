package app.zhizhang.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MergeDismissalStoreTest {

    @Test
    fun `dismissed merge pair is remembered`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("merge_dismissals", Context.MODE_PRIVATE).edit().clear().commit()
        val store = MergeDismissalStore(context)
        store.add("1:2")
        assertTrue("1:2" in MergeDismissalStore(context).load())
        context.getSharedPreferences("merge_dismissals", Context.MODE_PRIVATE).edit().clear().commit()
    }
}
