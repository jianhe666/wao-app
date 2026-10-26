package app.zhizhang.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_events",
    indices = [
        Index(value = ["notificationKey", "contentHash"], unique = true),
        Index(value = ["capturedAt"]),
    ],
)
data class NotificationEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packageName: String,
    val appLabel: String,
    val notificationKey: String,
    val title: String,
    val body: String,
    val postedAt: Long,
    val capturedAt: Long = System.currentTimeMillis(),
    val contentHash: String,
    val processed: Boolean = false,
    val parseError: String? = null,
    val transactionId: Long? = null,
)
