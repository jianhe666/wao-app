package app.zhizhang.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AssetRepositoryTest {

    @Test
    fun `adds lists and deletes assets`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = AssetRepository(database)
            val id = repository.add(
                name = "相机",
                categoryId = "digital",
                valueCents = 499_900,
                note = "Sony",
                acquiredAt = 1_000L,
                source = AssetSource.AI,
            )
            assertNotNull(id)
            val assets = repository.all()
            assertEquals(1, assets.size)
            assertEquals("相机", assets.first().name)
            assertEquals(AssetSource.AI, assets.first().assetSource)
            assertTrue(
                repository.update(
                    id = id!!,
                    name = "相机（编辑）",
                    categoryId = "other",
                    valueCents = 399_900,
                    note = "已更新",
                ),
            )
            val edited = repository.all().first()
            assertEquals("相机（编辑）", edited.name)
            assertEquals(399_900L, edited.valueCents)
            assertEquals("other", edited.categoryId)
            repository.delete(id)
            assertTrue(repository.all().isEmpty())
        } finally {
            database.close()
        }
    }
}
