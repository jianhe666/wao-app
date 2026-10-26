package app.zhizhang.domain

import app.zhizhang.data.TxType
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** 用于导入去重的稳定指纹；不包含 API Key 等任何敏感信息。 */
object BillFingerprint {

    fun of(
        type: TxType,
        cents: Long,
        occurredAt: Long,
        merchant: String,
        raw: String,
    ): String {
        val key = listOf(
            type.name,
            cents.toString(),
            (occurredAt / 60_000).toString(),
            CategoryClassifier.normalize(merchant).take(32),
            CategoryClassifier.normalize(raw).take(96),
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(32)
    }
}
