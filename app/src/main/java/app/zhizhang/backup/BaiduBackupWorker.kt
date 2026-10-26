package app.zhizhang.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.BaiduAuthException
import app.zhizhang.data.BaiduException
import app.zhizhang.data.BaiduNetworkException
import java.util.concurrent.TimeUnit

class BaiduBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ZhizhangApp ?: return Result.failure()
        val state = app.baiduRepository.current()
        if (!state.autoBackup || !state.loggedIn) return Result.success()

        return try {
            val json = app.backupManager.buildBackupJson()
            val result = app.baiduPanService.uploadBackup(json, state.directory)
            app.baiduRepository.markBackupSuccess(result.remotePath)
            Result.success()
        } catch (error: BaiduAuthException) {
            app.baiduRepository.markBackupFailure(error.message ?: "百度网盘授权已失效")
            Result.failure()
        } catch (error: BaiduNetworkException) {
            app.baiduRepository.markBackupFailure(error.message ?: "网络错误，稍后重试")
            Result.retry()
        } catch (error: BaiduException) {
            app.baiduRepository.markBackupFailure(error.message ?: "备份失败")
            Result.failure()
        } catch (error: Exception) {
            app.baiduRepository.markBackupFailure("备份失败：${error.message ?: "未知错误"}")
            Result.failure()
        }
    }
}

object BaiduBackupScheduler {
    private const val WORK_NAME = "zhizhang_baidu_auto_backup"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<BaiduBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
