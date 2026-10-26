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
        try {
            fetchFromApi()
        } catch (error: UpdateCheckException) {
            // GitHub API 未登录时限额只有 60 次/小时，公共网络很容易触发。
            // API 失败时退回 release 页面，不依赖 API 限额。
            try {
                fetchFromReleasePage()
            } catch (_: Exception) {
                throw error
            }
        }
    }

    private fun fetchFromApi(): GithubRelease {
        val connection = open(
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest",
        )
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw UpdateCheckException(
                    when (code) {
                        403, 429 -> "GitHub 请求太频繁，正在尝试不限流的页面"
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
            return GithubRelease(
                tagName = tag,
                title = json.optString("name").ifBlank { tag },
                notes = json.optString("body").take(1_800),
                releaseUrl = json.optString("html_url")
                    .ifBlank { releasePageUrl() },
                apkUrl = findApk(json.optJSONArray("assets")),
            )
        } finally {
            connection.disconnect()
        }
    }

    /** 不调用 API：/releases/latest 会 302 到 /releases/tag/vX.Y.Z。 */
    private fun fetchFromReleasePage(): GithubRelease {
        val latest = open("https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
        val tag: String
        val releaseUrl: String
        try {
            val code = latest.responseCode
            if (code !in 200..399) {
                throw UpdateCheckException("GitHub 页面返回错误（$code）")
            }
            val finalUrl = latest.url.toString()
            tag = finalUrl.substringAfterLast("/tag/", "")
                .substringBefore('?')
                .ifBlank { throw UpdateCheckException("没有读取到版本号") }
            releaseUrl = finalUrl.substringBefore('?')
        } finally {
            latest.disconnect()
        }
        val apkUrl = runCatching {
            val assets = open(
                "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/expanded_assets/$tag",
            )
            try {
                if (assets.responseCode !in 200..299) return@runCatching null
                val html = assets.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                findApkInPage(html)
            } finally {
                assets.disconnect()
            }
        }.getOrNull()
        return GithubRelease(
            tagName = tag,
            title = tag,
            notes = "",
            releaseUrl = releaseUrl,
            apkUrl = apkUrl,
        )
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 8_000
            readTimeout = 12_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "wao-app-update-check")
        }

    private fun findApk(assets: org.json.JSONArray?): String? =
        (0 until (assets?.length() ?: 0))
            .mapNotNull { index -> assets?.optJSONObject(index) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?.optString("browser_download_url")
            ?.takeIf { it.isNotBlank() }

    internal fun findApkInPage(html: String): String? {
        val match = APK_LINK.find(html) ?: return null
        val path = match.groupValues[1]
        return if (path.startsWith("http")) path else "https://github.com$path"
    }

    private fun releasePageUrl() =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

    private val APK_LINK = Regex("""href="([^"]+/releases/download/[^"]+\.apk)"""")

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
