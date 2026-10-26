package app.zhizhang.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GithubRelease(
    val tagName: String,
    val title: String,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String?,
)

class UpdateCheckException(message: String) : Exception(message)

/**
 * 只读取公开仓库的 latest release，不做后台轮询。
 * 更新地址：https://github.com/jianhe666/wao-app/releases
 */
object UpdateChecker {
    const val GITHUB_OWNER = "jianhe666"
    const val GITHUB_REPO = "wao-app"

    suspend fun fetchLatest(): GithubRelease = withContext(Dispatchers.IO) {
        val url = URL(
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "wao-app-update-check")
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw UpdateCheckException(
                    when (code) {
                        403 -> "GitHub 请求太频繁，请稍后再试"
                        404 -> "还没有发布版本"
                        else -> "GitHub 返回错误（$code）"
                    },
                )
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank {
                throw UpdateCheckException("没有读取到版本号")
            }
            val assets = json.optJSONArray("assets")
            val apkUrl = (0 until (assets?.length() ?: 0))
                .mapNotNull { index -> assets?.optJSONObject(index) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                ?.takeIf { it.isNotBlank() }
            GithubRelease(
                tagName = tag,
                title = json.optString("name").ifBlank { tag },
                notes = json.optString("body").take(1_800),
                releaseUrl = json.optString("html_url"),
                apkUrl = apkUrl,
            )
        } catch (error: UpdateCheckException) {
            throw error
        } catch (error: Exception) {
            throw UpdateCheckException("检查更新失败，请稍后再试")
        } finally {
            connection.disconnect()
        }
    }

    /** 返回 latest 是否比 current 新。支持 v1.0.1 / 1.0.1 两种写法。 */
    fun isNewer(latest: String, current: String): Boolean {
        val left = versionParts(latest)
        val right = versionParts(current)
        if (left.isEmpty() || right.isEmpty()) return latest != current
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val l = left.getOrElse(index) { 0 }
            val r = right.getOrElse(index) { 0 }
            if (l != r) return l > r
        }
        return false
    }

    private fun versionParts(raw: String): List<Int> =
        raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .split('.')
            .mapNotNull { it.toIntOrNull() }
}
