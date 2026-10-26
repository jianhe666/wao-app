package app.zhizhang.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.baiduDataStore by preferencesDataStore(name = "baidu_backup")

data class BaiduState(
    val appKey: String = "",
    val secretKey: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0L,
    val userName: String = "",
    val autoBackup: Boolean = false,
    val directory: String = DEFAULT_BAIDU_DIR,
    val lastBackupAt: Long = 0L,
    val lastBackupStatus: String = "",
    val retentionCount: Int = 10,
) {
    val configured: Boolean get() = appKey.isNotBlank() && secretKey.isNotBlank()
    val loggedIn: Boolean get() = accessToken.isNotBlank() || refreshToken.isNotBlank()
}

const val DEFAULT_BAIDU_DIR = "/apps/有数备份"

/**
 * 百度网盘授权与备份状态。AppKey / SecretKey / token 只保存在应用私有 DataStore，
 * 不写入有数的 JSON 备份，也不会上传到除百度开放平台以外的任何地方。
 */
class BaiduRepository(private val context: Context) {

    val state: Flow<BaiduState> = context.baiduDataStore.data.map { prefs ->
        BaiduState(
            appKey = prefs[APP_KEY] ?: "",
            secretKey = prefs[SECRET_KEY] ?: "",
            accessToken = prefs[ACCESS_TOKEN] ?: "",
            refreshToken = prefs[REFRESH_TOKEN] ?: "",
            expiresAt = prefs[EXPIRES_AT] ?: 0L,
            userName = prefs[USER_NAME] ?: "",
            autoBackup = prefs[AUTO_BACKUP] ?: false,
            directory = prefs[DIRECTORY] ?: DEFAULT_BAIDU_DIR,
            lastBackupAt = prefs[LAST_BACKUP_AT] ?: 0L,
            lastBackupStatus = prefs[LAST_BACKUP_STATUS] ?: "",
            retentionCount = prefs[RETENTION_COUNT] ?: 10,
        )
    }

    suspend fun current(): BaiduState = state.first()

    suspend fun saveCredentials(appKey: String, secretKey: String) {
        context.baiduDataStore.edit {
            it[APP_KEY] = appKey.trim()
            it[SECRET_KEY] = secretKey.trim()
        }
    }

    suspend fun saveAuth(
        accessToken: String,
        refreshToken: String,
        expiresAt: Long,
        userName: String,
    ) {
        context.baiduDataStore.edit {
            it[ACCESS_TOKEN] = accessToken
            it[REFRESH_TOKEN] = refreshToken
            it[EXPIRES_AT] = expiresAt
            if (userName.isNotBlank()) it[USER_NAME] = userName
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String, expiresAt: Long) {
        context.baiduDataStore.edit {
            it[ACCESS_TOKEN] = accessToken
            it[REFRESH_TOKEN] = refreshToken
            it[EXPIRES_AT] = expiresAt
        }
    }

    suspend fun setAutoBackup(enabled: Boolean) {
        context.baiduDataStore.edit { it[AUTO_BACKUP] = enabled }
    }

    suspend fun setDirectory(path: String) {
        context.baiduDataStore.edit { it[DIRECTORY] = path.trim() }
    }

    suspend fun setRetentionCount(count: Int) {
        context.baiduDataStore.edit { it[RETENTION_COUNT] = count.coerceIn(0, 50) }
    }

    suspend fun markBackupSuccess(path: String) {
        context.baiduDataStore.edit {
            it[LAST_BACKUP_AT] = System.currentTimeMillis()
            it[LAST_BACKUP_STATUS] = if (path.isBlank()) "备份成功" else "备份成功 · $path"
        }
    }

    suspend fun markBackupFailure(message: String) {
        context.baiduDataStore.edit {
            it[LAST_BACKUP_AT] = System.currentTimeMillis()
            it[LAST_BACKUP_STATUS] = "备份失败 · ${message.take(160)}"
        }
    }

    suspend fun logout() {
        context.baiduDataStore.edit {
            it.remove(ACCESS_TOKEN)
            it.remove(REFRESH_TOKEN)
            it[EXPIRES_AT] = 0L
            it[USER_NAME] = ""
            it[AUTO_BACKUP] = false
        }
    }

    private companion object {
        val APP_KEY = stringPreferencesKey("app_key")
        val SECRET_KEY = stringPreferencesKey("secret_key")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val USER_NAME = stringPreferencesKey("user_name")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val DIRECTORY = stringPreferencesKey("directory")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val LAST_BACKUP_STATUS = stringPreferencesKey("last_backup_status")
        val RETENTION_COUNT = intPreferencesKey("retention_count")
    }
}
