package app.zhizhang.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

data class AssetCategory(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Long,
)

object AssetCategories {
    val all = listOf(
        AssetCategory("cash", "现金", "💵", 0xFF7BA05B),
        AssetCategory("deposit", "存款", "🏦", 0xFF4F76C7),
        AssetCategory("investment", "投资", "📈", 0xFF2A9D8F),
        AssetCategory("property", "房产", "🏠", 0xFF5C6BC0),
        AssetCategory("vehicle", "车辆", "🚗", 0xFFE0A458),
        AssetCategory("digital", "数码", "💻", 0xFF3A9BC1),
        AssetCategory("other", "其他资产", "✨", 0xFF8A8F9C),
    )

    fun byId(id: String?): AssetCategory =
        all.firstOrNull { it.id == id } ?: all.last()

    fun fromRaw(raw: String?): AssetCategory {
        val value = raw?.trim().orEmpty()
        return all.firstOrNull { it.id == value || it.name == value } ?: all.last()
    }
}

enum class AssetSource {
    MANUAL,
    AI,
}

@Entity(tableName = "assets", indices = [Index("acquiredAt")])
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val categoryId: String,
    val valueCents: Long,
    val note: String = "",
    val acquiredAt: Long = System.currentTimeMillis(),
    val source: String = AssetSource.MANUAL.name,
    val transactionId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val category: AssetCategory get() = AssetCategories.byId(categoryId)
    val assetSource: AssetSource get() = AssetSource.entries.firstOrNull { it.name == source }
        ?: AssetSource.MANUAL
}

@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY acquiredAt DESC, id DESC")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets ORDER BY acquiredAt DESC, id DESC")
    suspend fun all(): List<AssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: AssetEntity): Long

    @Update
    suspend fun update(asset: AssetEntity)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM assets")
    suspend fun deleteAll()
}

class AssetRepository(private val database: AppDatabase) {
    private val dao = database.assetDao()

    val assets: Flow<List<AssetEntity>> = dao.observeAll()

    suspend fun all(): List<AssetEntity> = dao.all()

    suspend fun add(
        name: String,
        categoryId: String,
        valueCents: Long,
        note: String = "",
        acquiredAt: Long = System.currentTimeMillis(),
        source: AssetSource = AssetSource.MANUAL,
        transactionId: Long? = null,
    ): Long? {
        if (name.isBlank() || valueCents <= 0L) return null
        return dao.insert(
            AssetEntity(
                name = name.trim().take(60),
                categoryId = AssetCategories.byId(categoryId).id,
                valueCents = valueCents,
                note = note.trim().take(160),
                acquiredAt = acquiredAt,
                source = source.name,
                transactionId = transactionId,
            ),
        )
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun clearAll() = dao.deleteAll()

    suspend fun replaceAll(assets: List<AssetEntity>) {
        database.withTransaction {
            dao.deleteAll()
            assets.forEach { dao.insert(it) }
        }
    }
}
