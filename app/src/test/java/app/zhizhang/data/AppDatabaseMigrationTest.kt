package app.zhizhang.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    @Test
    fun `migration 1 to 2 adds default reimbursement columns`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${System.nanoTime()}.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        val old = SQLiteDatabase.openOrCreateDatabase(path, null)
        old.execSQL(
            """
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                amountCents INTEGER NOT NULL,
                type TEXT NOT NULL,
                categoryId TEXT NOT NULL,
                accountId TEXT NOT NULL,
                merchant TEXT NOT NULL,
                note TEXT NOT NULL,
                occurredAt INTEGER NOT NULL,
                source TEXT NOT NULL,
                fingerprint TEXT,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        old.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_occurredAt ON transactions(occurredAt)")
        old.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_fingerprint ON transactions(fingerprint)")
        old.execSQL(
            """
            CREATE TABLE IF NOT EXISTS merchant_rules (
                merchantKey TEXT NOT NULL,
                categoryId TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(merchantKey)
            )
            """.trimIndent(),
        )
        old.execSQL(
            "INSERT INTO transactions(amountCents,type,categoryId,accountId,merchant,note,occurredAt,source,createdAt) " +
                "VALUES (1234,'EXPENSE','food','wechat','旧账','',1000,'MANUAL',1000)",
        )
        old.version = 1
        old.close()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
        try {
            runBlocking {
                val row = database.transactionDao().byId(1L)
                assertEquals("旧账", row?.merchant)
                assertEquals(ReimbursementStatus.NONE.name, row?.reimbursementStatus)
                assertNull(row?.reimbursementForId)
                assertEquals(TransactionStatus.SUCCESS.name, row?.transactionStatus)
                assertNull(row?.sourcePackage)
                assertEquals(0.9f, row?.confidence ?: 1f, 0.001f)
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
