package app.zhizhang

import android.app.Application
import app.zhizhang.data.AiService
import app.zhizhang.data.AssetRepository
import app.zhizhang.domain.CategoryClassifier
import app.zhizhang.data.BaiduPanService
import app.zhizhang.data.BaiduRepository
import app.zhizhang.data.AppDatabase
import app.zhizhang.data.BackupManager
import app.zhizhang.data.Accounts
import app.zhizhang.data.Categories
import app.zhizhang.data.CustomCategoryStore
import app.zhizhang.data.SettingsRepository
import app.zhizhang.data.TransactionRepository
import app.zhizhang.data.WealthStore
import app.zhizhang.notification.NotificationCaptureParser
import app.zhizhang.notification.NotificationSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ZhizhangApp : Application() {

    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 在 UI 组合前同步恢复自定义分类，编辑器/统计/筛选都会立即看到。
        Categories.setCustomCategories(CustomCategoryStore(this).load())
        Accounts.setProfileOverrides(WealthStore(this).profiles())
        cleanupScope.launch {
            runCatching {
                val allowAll = settings.autoCaptureAllApps.first()
                val dao = database.notificationEventDao()
                dao.deleteOlderThan(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
                // 旧版本把成功导入的账单也标成待确认；升级后一次性清理。
                repository.clearLegacyImportConfirmations()
                val parser = NotificationCaptureParser(CategoryClassifier(emptyMap()))
                dao.unprocessed(200).forEach { event ->
                    val snapshot = NotificationSnapshot(
                        packageName = event.packageName,
                        appLabel = event.appLabel,
                        notificationKey = event.notificationKey,
                        title = event.title,
                        text = event.body,
                        postedAt = event.postedAt,
                    )
                    if (!parser.isPotentiallyRelevant(snapshot, allowAll)) {
                        dao.deleteById(event.id)
                    }
                }
            }
        }
    }

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val repository: TransactionRepository by lazy {
        TransactionRepository(database, settings)
    }
    val assetRepository: AssetRepository by lazy {
        AssetRepository(database)
    }
    val backupManager: BackupManager by lazy {
        BackupManager(this, repository, settings, assetRepository)
    }
    val aiService: AiService by lazy {
        AiService(settings)
    }
    val baiduRepository: BaiduRepository by lazy {
        BaiduRepository(this)
    }
    val baiduPanService: BaiduPanService by lazy {
        BaiduPanService(baiduRepository)
    }
}
