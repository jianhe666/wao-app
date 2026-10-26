package app.zhizhang.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BaiduException(message: String) : Exception(message)
class BaiduAuthException(message: String) : Exception(message)
class BaiduNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class BaiduDeviceAuth(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val qrcodeUrl: String,
    val expiresIn: Int,
    val interval: Int,
)

data class BaiduToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

data class BaiduBackupResult(
    val remotePath: String,
    val size: Long,
)

data class BaiduRemoteBackup(
    val path: String,
    val name: String,
    val size: Long,
    val modifiedAt: Long,
    val fsId: Long,
)

/**
 * 百度网盘开放平台的最小实现：
 * - 设备码模式登录（不需要 redirect_uri / 后端）
 * - 获取用户信息
 * - 创建目录
 * - 小文件上传（预上传 -> 定位上传域名 -> 分片上传 -> 创建文件）
 *
 * 使用前需要在百度网盘开放平台创建应用，并获得 AppKey / SecretKey 与上传权限。
 * 参考：https://pan.baidu.com/union/doc/
 */
class BaiduPanService(private val repository: BaiduRepository) {

    suspend fun requestDeviceCode(appKey: String): BaiduDeviceAuth = withContext(Dispatchers.IO) {
        require(appKey.isNotBlank()) { "AppKey 不能为空" }
        val url = "https://openapi.baidu.com/oauth/2.0/device/code" +
            "?response_type=device_code" +
            "&client_id=${encode(appKey)}" +
            "&scope=basic%2Cnetdisk"
        val json = getJson(url, checkErrno = false, allowOauthError = true)
        if (json.optString("error").isNotBlank()) {
            throw BaiduAuthException(
                json.optString("error_description").ifBlank { "获取设备码失败" },
            )
        }
        val deviceCode = json.optString("device_code")
        val userCode = json.optString("user_code")
        if (deviceCode.isBlank() || userCode.isBlank()) {
            throw BaiduException(json.optString("error_description").ifBlank { "获取设备码失败" })
        }
        BaiduDeviceAuth(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUrl = json.optString("verification_url").ifBlank { "https://openapi.baidu.com/device" },
            qrcodeUrl = json.optString("qrcode_url"),
            expiresIn = json.optInt("expires_in", 300),
            interval = json.optInt("interval", 5).coerceAtLeast(5),
        )
    }

    /**
     * 轮询设备码换取 token。需要在协程中运行；取消协程即可停止轮询。
     */
    suspend fun pollForToken(
        appKey: String,
        secretKey: String,
        auth: BaiduDeviceAuth,
        onWaiting: () -> Unit = {},
    ): BaiduToken {
        val deadline = System.currentTimeMillis() + auth.expiresIn * 1_000L
        var interval = auth.interval.coerceAtLeast(5)
        var slowDown = false
        while (System.currentTimeMillis() < deadline) {
            onWaiting()
            val url = "https://openapi.baidu.com/oauth/2.0/token" +
                "?grant_type=device_token" +
                "&code=${encode(auth.deviceCode)}" +
                "&client_id=${encode(appKey)}" +
                "&client_secret=${encode(secretKey)}"
            val json = withContext(Dispatchers.IO) {
                getJson(url, checkErrno = false, allowOauthError = true)
            }
            val access = json.optString("access_token")
            if (access.isNotBlank()) {
                val refresh = json.optString("refresh_token")
                val expiresIn = json.optLong("expires_in", 30L * 24 * 3600)
                return BaiduToken(
                    accessToken = access,
                    refreshToken = refresh,
                    expiresAt = System.currentTimeMillis() + expiresIn * 1000L,
                )
            }
            val error = json.optString("error")
            val description = json.optString("error_description")
            when {
                error.contains("slow_down", true) -> {
                    slowDown = true
                    interval += 5
                }

                error.contains("authorization_pending", true) ||
                    error.contains("authorization_waiting", true) ||
                    description.contains("等待", true) ||
                    description.contains("pending", true) -> Unit

                else -> throw BaiduAuthException(description.ifBlank { error.ifBlank { "授权失败" } })
            }
            delay(interval * 1_000L)
            if (slowDown) slowDown = false
        }
        throw BaiduAuthException("授权码已过期，请重新登录")
    }

    suspend fun fetchUserName(accessToken: String): String = withContext(Dispatchers.IO) {
        val json = getJson(
            "https://pan.baidu.com/rest/2.0/xpan/nas?method=uinfo&access_token=${encode(accessToken)}",
            checkErrno = true,
        )
        json.optString("netdisk_name")
            .ifBlank { json.optString("baidu_name") }
            .ifBlank { "百度网盘用户" }
    }

    suspend fun uploadBackup(json: String, directory: String): BaiduBackupResult =
        withContext(Dispatchers.IO) {
            val state = repository.current()
            if (!state.loggedIn) throw BaiduAuthException("还没有登录百度网盘")
            val dir = normalizeDirectory(directory)
            val byteArray = json.toByteArray(StandardCharsets.UTF_8)
            if (byteArray.size > 4 * 1024 * 1024) {
                throw BaiduException("备份超过 4 MB，暂不支持自动上传，请先导出到本地")
            }
            val token = validAccessToken(state)
            ensureDirectory(token, dir)

            val fileName = "youshu-backup-${
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            }.json"
            val remotePath = "$dir/$fileName"
            val md5 = md5Hex(byteArray)
            val uploadId = precreate(token, remotePath, byteArray.size, md5)
            val host = locateUploadHost(token, remotePath, uploadId)
            uploadPart(host, token, remotePath, uploadId, byteArray)
            createFile(token, remotePath, byteArray.size, md5, uploadId)
            runCatching { pruneBackups(token, dir, state.retentionCount) }
            BaiduBackupResult(remotePath = remotePath, size = byteArray.size.toLong())
        }

    /** 最近备份，按时间倒序；没有目录时返回空列表。 */
    suspend fun listBackups(directory: String): List<BaiduRemoteBackup> =
        withContext(Dispatchers.IO) {
            val state = repository.current()
            if (!state.loggedIn) throw BaiduAuthException("还没有登录百度网盘")
            val token = validAccessToken(state)
            listBackups(token, normalizeDirectory(directory))
        }

    suspend fun downloadBackup(backup: BaiduRemoteBackup): String =
        withContext(Dispatchers.IO) {
            val state = repository.current()
            if (!state.loggedIn) throw BaiduAuthException("还没有登录百度网盘")
            val token = validAccessToken(state)
            val url = "https://pan.baidu.com/rest/2.0/xpan/multimedia?method=filemetas" +
                "&access_token=${encode(token)}" +
                "&fsids=${encode(JSONArray().put(backup.fsId).toString())}" +
                "&dlink=1"
            val json = getJson(url, checkErrno = true)
            val dlink = json.optJSONArray("list")
                ?.optJSONObject(0)
                ?.optString("dlink")
                .orEmpty()
            if (dlink.isBlank()) throw BaiduException("没有拿到备份下载地址")
            val separator = if (dlink.contains('?')) '&' else '?'
            val fullUrl = if (dlink.contains("access_token=")) {
                dlink
            } else {
                "$dlink${separator}access_token=${encode(token)}"
            }
            val content = request(fullUrl, method = "GET", contentType = null, body = null)
            if (content.length > 20_000_000) throw BaiduException("备份文件过大")
            if (content.isBlank()) throw BaiduException("网盘备份是空的")
            content
        }

    private fun listBackups(token: String, directory: String): List<BaiduRemoteBackup> {
        val url = "https://pan.baidu.com/rest/2.0/xpan/file?method=list" +
            "&access_token=${encode(token)}" +
            "&dir=${encode(directory)}" +
            "&order=time&start=0&limit=200"
        val json = getJson(url, checkErrno = false)
        val errno = json.optInt("errno", 0)
        // -9: 目录不存在；第一次使用或还没备份时视为空。
        if (errno == -9) return emptyList()
        if (errno != 0) throw apiError(errno, json, "读取备份目录失败")
        val array = json.optJSONArray("list") ?: return emptyList()
        val result = mutableListOf<BaiduRemoteBackup>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optInt("isdir", 0) != 0) continue
            val name = item.optString("server_filename")
            // 兼容旧版 App 备份文件名。
            if ((!name.startsWith("youshu-backup-") && !name.startsWith("zhizhang-backup-")) ||
                !name.endsWith(".json")
            ) {
                continue
            }
            val path = item.optString("path").ifBlank { "$directory/$name" }
            val modifiedSeconds = item.optLong(
                "server_mtime",
                item.optLong("mtime", 0L),
            )
            result += BaiduRemoteBackup(
                path = path,
                name = name,
                size = item.optLong("size", 0L),
                modifiedAt = modifiedSeconds * 1000L,
                fsId = item.optLong("fs_id", 0L),
            )
        }
        return result.sortedWith(
            compareByDescending<BaiduRemoteBackup> { it.modifiedAt }.thenByDescending { it.name },
        )
    }

    private suspend fun pruneBackups(token: String, directory: String, retention: Int) {
        if (retention <= 0) return
        val files = listBackups(token, directory)
        if (files.size <= retention) return
        val paths = files.drop(retention).map { it.path }
        if (paths.isEmpty()) return
        val json = postForm(
            "https://pan.baidu.com/rest/2.0/xpan/file?method=filemanager" +
                "&access_token=${encode(token)}&opera=delete",
            mapOf(
                "async" to "0",
                "filelist" to JSONArray(paths).toString(),
            ),
        )
        val errno = json.optInt("errno", 0)
        if (errno != 0 && errno != -9) {
            throw apiError(errno, json, "清理旧备份失败")
        }
    }

    private suspend fun validAccessToken(state: BaiduState): String {
        val now = System.currentTimeMillis()
        if (state.accessToken.isNotBlank() && state.expiresAt > now + 5 * 60 * 1000L) {
            return state.accessToken
        }
        if (state.refreshToken.isBlank()) {
            throw BaiduAuthException("百度网盘授权已过期，请重新登录")
        }
        val url = "https://openapi.baidu.com/oauth/2.0/token" +
            "?grant_type=refresh_token" +
            "&refresh_token=${encode(state.refreshToken)}" +
            "&client_id=${encode(state.appKey)}" +
            "&client_secret=${encode(state.secretKey)}"
        val json = withContext(Dispatchers.IO) {
            getJson(url, checkErrno = false, allowOauthError = true)
        }
        val access = json.optString("access_token")
        if (access.isBlank()) {
            throw BaiduAuthException(
                json.optString("error_description").ifBlank {
                    "百度网盘授权已过期，请重新登录"
                },
            )
        }
        val refresh = json.optString("refresh_token").ifBlank { state.refreshToken }
        val expiresAt = now + json.optLong("expires_in", 30L * 24 * 3600) * 1000L
        repository.updateTokens(access, refresh, expiresAt)
        return access
    }

    private suspend fun ensureDirectory(token: String, directory: String) {
        val segments = directory.split('/').filter { it.isNotBlank() }
        if (segments.firstOrNull() != "apps") {
            throw BaiduException("备份目录必须位于 /apps/ 下")
        }
        var current = ""
        segments.forEachIndexed { index, segment ->
            current += "/$segment"
            if (index == 0) return@forEachIndexed
            val json = postForm(
                "https://pan.baidu.com/rest/2.0/xpan/file?method=create&access_token=${encode(token)}",
                mapOf(
                    "path" to current,
                    "isdir" to "1",
                    "rtype" to "0",
                    "mode" to "3",
                ),
            )
            val errno = json.optInt("errno", 0)
            if (errno != 0 && errno != -8) {
                throw apiError(errno, json, "创建备份目录失败")
            }
        }
    }

    private fun precreate(token: String, path: String, size: Int, md5: String): String {
        val json = postForm(
            "https://pan.baidu.com/rest/2.0/xpan/file?method=precreate&access_token=${encode(token)}",
            mapOf(
                "path" to path,
                "size" to size.toString(),
                "isdir" to "0",
                "autoinit" to "1",
                "rtype" to "3",
                "block_list" to JSONArray().put(md5).toString(),
                "content-md5" to md5,
            ),
        )
        val errno = json.optInt("errno", 0)
        if (errno != 0) throw apiError(errno, json, "创建上传任务失败")
        return json.optString("uploadid").ifBlank { throw BaiduException("没有拿到 uploadid") }
    }

    private fun locateUploadHost(token: String, path: String, uploadId: String): String {
        val url = "https://d.pcs.baidu.com/rest/2.0/pcs/file?method=locateupload" +
            "&appid=250528" +
            "&access_token=${encode(token)}" +
            "&path=${encode(path)}" +
            "&uploadid=${encode(uploadId)}" +
            "&upload_version=2.0"
        val json = runCatching { getJson(url, checkErrno = false) }.getOrNull()
            ?: return "https://d.pcs.baidu.com"
        val servers = json.optJSONArray("servers")
        val server = servers?.optJSONObject(0)?.optString("server").orEmpty()
        if (server.isNotBlank()) return server.trimEnd('/')
        val host = json.optString("host")
        if (host.isNotBlank()) return if (host.startsWith("http")) host.trimEnd('/') else "https://$host"
        return "https://d.pcs.baidu.com"
    }

    private fun uploadPart(
        host: String,
        token: String,
        path: String,
        uploadId: String,
        bytes: ByteArray,
    ) {
        val url = "$host/rest/2.0/pcs/superfile2?method=upload" +
            "&access_token=${encode(token)}" +
            "&type=tmpfile" +
            "&path=${encode(path)}" +
            "&uploadid=${encode(uploadId)}" +
            "&partseq=0"
        val boundary = "----ZhizhangBoundary${System.currentTimeMillis()}"
        val prefix = "--$boundary\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"backup.json\"\r\n" +
            "Content-Type: application/json\r\n\r\n"
        val suffix = "\r\n--$boundary--\r\n"
        val out = ByteArrayOutputStream()
        out.write(prefix.toByteArray(StandardCharsets.UTF_8))
        out.write(bytes)
        out.write(suffix.toByteArray(StandardCharsets.UTF_8))
        val response = request(
            url = url,
            method = "POST",
            contentType = "multipart/form-data; boundary=$boundary",
            body = out.toByteArray(),
        )
        val json = parseJsonOrThrow(response, "上传分片失败")
        val errno = json.optInt("errno", 0)
        if (errno != 0) throw apiError(errno, json, "上传分片失败")
    }

    private fun createFile(
        token: String,
        path: String,
        size: Int,
        md5: String,
        uploadId: String,
    ) {
        val json = postForm(
            "https://pan.baidu.com/rest/2.0/xpan/file?method=create&access_token=${encode(token)}",
            mapOf(
                "path" to path,
                "size" to size.toString(),
                "isdir" to "0",
                "block_list" to JSONArray().put(md5).toString(),
                "uploadid" to uploadId,
                "rtype" to "3",
            ),
        )
        val errno = json.optInt("errno", 0)
        if (errno != 0) throw apiError(errno, json, "创建网盘文件失败")
    }

    private fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }.toByteArray(StandardCharsets.UTF_8)
        val response = request(
            url = url,
            method = "POST",
            contentType = "application/x-www-form-urlencoded",
            body = body,
        )
        return parseJsonOrThrow(response, "百度网盘请求失败")
    }

    private fun getJson(
        url: String,
        checkErrno: Boolean,
        allowOauthError: Boolean = false,
    ): JSONObject {
        val response = request(
            url = url,
            method = "GET",
            contentType = null,
            body = null,
            acceptErrorBody = allowOauthError,
        )
        val json = parseJsonOrThrow(response, "百度网盘请求失败")
        if (checkErrno) {
            val errno = json.optInt("errno", 0)
            if (errno != 0) throw apiError(errno, json, "百度网盘请求失败")
        }
        if (!allowOauthError) {
            val error = json.optString("error")
            if (error.isNotBlank() && error != "0") {
                throw BaiduAuthException(
                    json.optString("error_description").ifBlank { "百度授权失败" },
                )
            }
        }
        return json
    }

    private fun request(
        url: String,
        method: String,
        contentType: String?,
        body: ByteArray?,
        acceptErrorBody: Boolean = false,
    ): String {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 120_000
                setRequestProperty("User-Agent", "pan.baidu.com")
                setRequestProperty("Accept", "application/json")
                if (contentType != null) setRequestProperty("Content-Type", contentType)
                if (body != null) doOutput = true
            }
        } catch (error: IOException) {
            throw BaiduNetworkException("无法连接百度网盘", error)
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                // OAuth 的 pending / invalid_client 等错误也放在 JSON body 里，
                // 需要把 body 交给上层解析，才能给出准确提示。
                if (acceptErrorBody && text.isNotBlank()) return text
                if (code == 401 || code == 403) {
                    throw BaiduAuthException("百度网盘授权已失效，请重新登录")
                }
                throw BaiduNetworkException("百度网盘服务异常（$code）")
            }
            return text
        } catch (error: BaiduAuthException) {
            throw error
        } catch (error: BaiduNetworkException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw BaiduNetworkException("连接百度网盘超时", error)
        } catch (error: IOException) {
            throw BaiduNetworkException("网络请求失败，请检查网络", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseJsonOrThrow(text: String, fallback: String): JSONObject {
        if (text.isBlank()) throw BaiduNetworkException(fallback)
        return runCatching { JSONObject(text) }.getOrElse {
            throw BaiduNetworkException(fallback)
        }
    }

    private fun apiError(errno: Int, json: JSONObject, fallback: String): Exception {
        val message = when (errno) {
            -6, 110, 111 -> return BaiduAuthException("百度网盘授权已失效，请重新登录")
            31024 -> "应用没有上传权限，请在百度网盘开放平台申请"
            -10 -> "百度网盘空间不足"
            31061 -> "网盘里已有同名文件"
            31299, 31364, 31363 -> "上传分片不完整，请稍后重试"
            else -> json.optString("show_msg").ifBlank { "$fallback（错误码 $errno）" }
        }
        return BaiduException(message)
    }

    private fun normalizeDirectory(path: String): String {
        val trimmed = path.trim().ifBlank { DEFAULT_BAIDU_DIR }
        if (!trimmed.startsWith("/apps/")) {
            throw BaiduException("备份目录必须以 /apps/ 开头")
        }
        if (trimmed.contains("..")) throw BaiduException("备份目录不合法")
        return trimmed.trimEnd('/')
    }

    private fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        fun formatExpiry(expiresAt: Long): String =
            if (expiresAt <= 0) "" else {
                java.time.Instant.ofEpochMilli(expiresAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            }
    }
}
