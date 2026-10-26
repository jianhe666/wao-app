package app.zhizhang.domain

/** 去掉支付平台前缀，避免同一家店因微信/支付宝显示成两个名字。 */
object MerchantNormalizer {
    private val platformPrefixes = listOf(
        "微信支付", "微信", "支付宝", "云闪付", "银联", "银行卡", "信用卡",
    )

    fun canonical(raw: String): String {
        var value = raw.trim()
        if (value.isBlank()) return value
        var changed = true
        while (changed) {
            changed = false
            platformPrefixes.forEach { prefix ->
                if (value.startsWith(prefix)) {
                    value = value.removePrefix(prefix)
                        .trimStart(' ', '-', '—', '·', ':', '：', '|', '/')
                    changed = true
                }
                if (value.endsWith("（$prefix）") || value.endsWith("($prefix)")) {
                    value = value.dropLast(prefix.length + 2).trim()
                    changed = true
                }
            }
        }
        return value.trim().ifBlank { raw.trim() }
    }
}
