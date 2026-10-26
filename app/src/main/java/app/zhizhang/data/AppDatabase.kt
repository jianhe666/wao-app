package app.zhizhang.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase

@Database(
    entities = [TransactionEntity::class, MerchantRuleEntity::class, NotificationEventEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun notificationEventDao(): NotificationEventDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN reimbursementStatus TEXT NOT NULL DEFAULT 'NONE'",
                )
                db.execSQL("ALTER TABLE transactions ADD COLUMN reimbursementForId INTEGER")
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN sourcePackage TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN externalId TEXT")
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN transactionStatus TEXT NOT NULL DEFAULT 'SUCCESS'",
                )
                db.execSQL("ALTER TABLE transactions ADD COLUMN confidence REAL NOT NULL DEFAULT 0.9")
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN needsConfirmation INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE transactions ADD COLUMN notificationEventId INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN refundOfId INTEGER")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_sourcePackage_externalId " +
                        "ON transactions(sourcePackage, externalId)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS notification_events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "packageName TEXT NOT NULL, appLabel TEXT NOT NULL, notificationKey TEXT NOT NULL, " +
                        "title TEXT NOT NULL, body TEXT NOT NULL, postedAt INTEGER NOT NULL, " +
                        "capturedAt INTEGER NOT NULL, contentHash TEXT NOT NULL, processed INTEGER NOT NULL, " +
                        "parseError TEXT, transactionId INTEGER)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_notification_events_notificationKey_contentHash " +
                        "ON notification_events(notificationKey, contentHash)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_notification_events_capturedAt " +
                        "ON notification_events(capturedAt)",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zhizhang.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
