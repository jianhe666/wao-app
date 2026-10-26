package app.zhizhang.data

import app.zhizhang.domain.BillFingerprint
import app.zhizhang.domain.CategoryClassifier
import app.zhizhang.domain.ParsedBill
import app.zhizhang.data.Categories
import app.zhizhang.data.Category
import app.zhizhang.data.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

data class AiConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
)

data class AiChatTurn(
    val role: String,
    val content: String,
    val imageDataUrl: String? = null,
)

data class ReimbursementIntent(
    val amountCents: Long,
    val merchant: String = "",
    val date: LocalDate? = null,
    val accountId: String? = null,
    val receivedAt: Long? = null,
)

data class AiAssetCandidate(
    val name: String,
    val amountCents: Long,
    val categoryId: String,
    val note: String = "",
)

data class AiChatReply(
    val reply: String,
    val bills: List<ParsedBill> = emptyList(),
    val reimbursements: List<ReimbursementIntent> = emptyList(),
    val assets: List<AiAssetCandidate> = emptyList(),
)

data class MerchantSample(
    val merchant: String,
    val type: TxType,
)

data class AiCategorySuggestion(
    val merchant: String,
    val merchantKey: String,
    val categoryId: String,
    val confidence: Float,
    val reason: String,
    val type: TxType,
)

data class AiTransactionCategorySuggestion(
    val transaction: TransactionEntity,
    val categoryId: String,
    val confidence: Float,
    val reason: String,
)

/** 独立构造消息体，确保图片不会在上传链路里被静默丢失。 */
internal fun aiMessagePayload(turn: AiChatTurn): JSONObject {
    val image = turn.imageDataUrl
    return JSONObject().apply {
        put("role", turn.role)
        if (image.isNullOrBlank()) {
            put("content", turn.content)
        } else {
            put(
                "content",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("type", "text")
                            put("text", turn.content)
                        },
                    )
                    put(
                        JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().put("url", image))
                        },
                    )
                },
            )
        }
    }
}

/** 兼容 content 为字符串，以及视觉 API 常见的 [{type,text}] 数组。 */
internal fun aiMessageText(message: JSONObject): String {
    return when (val content = message.opt("content")) {
        is String -> content
        is JSONArray -> buildString {
            for (index in 0 until content.length()) {
                val part = content.optJSONObject(index) ?: continue
                val type = part.optString("type")
                if (type == "text" || type.isBlank()) {
                    val text = part.optString("text")
                    if (text.isNotBlank()) append(text)
                }
            }
        }
        else -> ""
    }.trim()
}

class AiException(
    message: String,
    val retryable: Boolean = false,
    val statusCode: Int? = null,
    val truncated: Boolean = false,
) : Exception(message)

/**
 * 极简的 OpenAI 兼容 Chat Completions 客户端，默认适配 DeepSeek。
 * 不引入第三方网络库，只在用户开启 AI 并主动识别 / 对话时发起请求。
 */
class AiService(private val settings: SettingsRepository) {

    suspend fun loadConfig(): AiConfig {
        val key = settings.aiApiKey.first().trim()
        if (key.isBlank()) {
            throw AiException("还没有配置 API Key，请到「我的 → AI → 连接配置」填写")
        }
        val baseUrl = settings.aiBaseUrl.first().trim().ifBlank { SettingsRepository.DEFAULT_AI_BASE_URL }
        if (!baseUrl.startsWith("https://")) {
            throw AiException("API 地址必须以 https:// 开头")
        }
        val model = settings.aiModel.first().trim().ifBlank { SettingsRepository.DEFAULT_AI_MODEL }
        return AiConfig(apiKey = key, baseUrl = baseUrl, model = model)
    }

    suspend fun parseBills(
        text: String,
        now: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES),
        zone: ZoneId = ZoneId.systemDefault(),
        accounts: List<Account> = Accounts.all,
    ): List<ParsedBill> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()
        if (trimmed.length > 20_000) {
            throw AiException("文本太长了，请分成几段分别识别")
        }
        val config = loadConfig()
        val messages = listOf(
            AiChatTurn(
                "system",
                systemPrompt(
                    now = now,
                    zone = zone,
                    ledgerContext = null,
                    styleInstruction = "简洁、准确，不展开闲聊。",
                ),
            ),
            AiChatTurn(
                "user",
                "请解析下面的账单文本，提取其中所有真实交易。不要补全文本里没有出现的金额或日期。\n\n$trimmed",
            ),
        )
        val content = request(config, messages, maxTokens = IMPORT_MAX_TOKENS)
        val json = runCatching { extractJsonObject(content) }
            .getOrElse { throw AiException("AI 返回的内容无法解析，已切回本地规则") }
        return parseTransactions(
            array = json.optJSONArray("transactions"),
            raw = trimmed,
            now = now,
            zone = zone,
            accounts = accounts,
        )
    }

    suspend fun chat(
        history: List<AiChatTurn>,
        ledgerContext: String,
        lastUserText: String,
        styleInstruction: String = "",
        temperature: Double = 0.3,
        now: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES),
        zone: ZoneId = ZoneId.systemDefault(),
        accounts: List<Account> = Accounts.all,
    ): AiChatReply {
        val config = loadConfig()
        val messages = buildList {
            add(
                AiChatTurn(
                    "system",
                    systemPrompt(now, zone, ledgerContext, styleInstruction, accounts),
                ),
            )
            addAll(history.takeLast(12).filter { it.role == "user" || it.role == "assistant" })
        }
        val content = request(config, messages, maxTokens = CHAT_MAX_TOKENS, temperature = temperature)
        val json = runCatching { extractJsonObject(content) }.getOrNull()
            ?: return AiChatReply(reply = readableText(content))
        val reply = json.optString("reply").trim().ifBlank { "我整理好了，你看看对不对。" }
        val bills = parseTransactions(
            array = json.optJSONArray("transactions"),
            raw = lastUserText.ifBlank { reply } + "#ai-chat",
            now = now,
            zone = zone,
            accounts = accounts,
        )
        val reimbursements = parseReimbursements(
            array = json.optJSONArray("reimbursements"),
            now = now,
            zone = zone,
            accounts = accounts,
        )
        return AiChatReply(
            reply = reply,
            bills = bills,
            reimbursements = reimbursements,
            assets = parseAssetCandidates(json.optJSONArray("assets")),
        )
    }

    suspend fun classifyTransactions(
        transactions: List<TransactionEntity>,
    ): List<AiTransactionCategorySuggestion> {
        val items = transactions
            .filter { it.txType == TxType.EXPENSE || it.txType == TxType.INCOME }
            .take(60)
        if (items.isEmpty()) return emptyList()
        val config = loadConfig()
        val categoryLines = Categories.all.joinToString("\n") {
            "${it.id} | ${it.name} | ${if (it.type == TxType.EXPENSE) "支出" else "收入"}"
        }
        val transactionLines = items.mapIndexed { index, tx ->
            val type = if (tx.txType == TxType.EXPENSE) "支出" else "收入"
            val note = tx.note.take(60).replace("\n", " ")
            "[$index] $type | ${tx.merchant.ifBlank { "未命名商户" }} | 备注：$note"
        }.joinToString("\n")
        val system = """
            你是哇哦记账的账单分类助手。只输出一个 JSON 对象，不要解释。
            只能把每笔账单映射到下面已有分类，不能编造分类。
            每笔账单必须单独判断，即使商户相同也可以给不同分类。
            不确定时使用 other_expense 或 other_income，并把 confidence 设低。
            分类列表：
            $categoryLines
            输出格式：
            {"suggestions":[{"index":0,"category":"food","confidence":0.0,"reason":"简短理由"}]}
        """.trimIndent()
        val user = "请逐笔分类：\n$transactionLines"
        val content = request(
            config = config,
            messages = listOf(
                AiChatTurn("system", system),
                AiChatTurn("user", user),
            ),
            maxTokens = 8_192,
            temperature = 0.1,
        )
        val json = runCatching { extractJsonObject(content) }
            .getOrElse { throw AiException("AI 分类结果无法解析") }
        val array = json.optJSONArray("suggestions") ?: return emptyList()
        val result = mutableListOf<AiTransactionCategorySuggestion>()
        for (i in 0 until minOf(array.length(), items.size * 2)) {
            val item = array.optJSONObject(i) ?: continue
            val index = item.optInt("index", -1)
            val transaction = items.getOrNull(index) ?: continue
            val categoryRaw = item.optString("category").trim()
            val category = Categories.all.firstOrNull {
                it.id == categoryRaw || it.name == categoryRaw
            } ?: continue
            if (category.type != transaction.txType) continue
            result += AiTransactionCategorySuggestion(
                transaction = transaction,
                categoryId = category.id,
                confidence = item.optDouble("confidence", 0.5).toFloat().coerceIn(0f, 1f),
                reason = item.optString("reason").trim().take(80),
            )
        }
        return result.distinctBy { it.transaction.id }
    }

    suspend fun classifyMerchants(samples: List<MerchantSample>): List<AiCategorySuggestion> {
        val clean = samples
            .map { MerchantSample(it.merchant.trim().take(40), it.type) }
            .filter { it.merchant.isNotBlank() }
            .distinctBy { CategoryClassifier.ruleKey(it.merchant) }
            .take(40)
        if (clean.isEmpty()) return emptyList()
        val config = loadConfig()
        val categoryLines = Categories.all.joinToString("\n") {
            "${it.id} | ${it.name} | ${if (it.type == TxType.EXPENSE) "支出" else "收入"}"
        }
        val merchantLines = clean.joinToString("\n") {
            "${it.merchant} | ${if (it.type == TxType.EXPENSE) "支出" else "收入"}"
        }
        val system = """
            你是哇哦记账的商户分类助手。只输出一个 JSON 对象，不要解释。
            只能把用户给出的商户映射到下面已有分类，不得编造商户或分类。
            如果无法确定，给空分类或低 confidence。
            分类列表：
            $categoryLines
            输出格式：
            {"suggestions":[{"merchant":"商户原文","category":"分类id","confidence":0.0,"reason":"简短理由"}]}
        """.trimIndent()
        val user = "请分类这些商户（只返回列表中的商户）：\n$merchantLines"
        val content = request(
            config = config,
            messages = listOf(
                AiChatTurn("system", system),
                AiChatTurn("user", user),
            ),
            maxTokens = 8_192,
            temperature = 0.1,
        )
        val json = runCatching { extractJsonObject(content) }
            .getOrElse { throw AiException("AI 分类结果无法解析") }
        val array = json.optJSONArray("suggestions") ?: return emptyList()
        val inputKeys = clean.associateBy { CategoryClassifier.ruleKey(it.merchant) }
        val result = mutableListOf<AiCategorySuggestion>()
        for (index in 0 until minOf(array.length(), 60)) {
            val item = array.optJSONObject(index) ?: continue
            val merchant = item.optString("merchant").trim()
            val key = CategoryClassifier.ruleKey(merchant)
            val sample = inputKeys[key] ?: continue
            val categoryRaw = item.optString("category").trim()
            val category = Categories.all.firstOrNull {
                it.id == categoryRaw || it.name == categoryRaw
            } ?: continue
            if (category.type != sample.type) continue
            val confidence = item.optDouble("confidence", 0.5)
                .toFloat()
                .coerceIn(0f, 1f)
            result += AiCategorySuggestion(
                merchant = sample.merchant,
                merchantKey = key,
                categoryId = category.id,
                confidence = confidence,
                reason = item.optString("reason").trim().take(80),
                type = sample.type,
            )
        }
        return result.distinctBy { it.merchantKey }
    }

    private suspend fun request(
        config: AiConfig,
        messages: List<AiChatTurn>,
        maxTokens: Int,
        temperature: Double = 0.3,
    ): String {
        var jsonMode = true
        var tokenBudget = maxTokens
        var lastError: AiException? = null
        for (attempt in 0..2) {
            try {
                return requestOnce(
                    config = config,
                    messages = messages,
                    maxTokens = tokenBudget,
                    jsonMode = jsonMode,
                    temperature = temperature,
                )
            } catch (error: AiException) {
                // 兼容只支持较小 max_tokens 的第三方兼容服务：
                // 先降到 32K，再走后续的 JSON/普通模式重试，避免直接失败。
                val maxTokensRejected = error.statusCode == 400 &&
                    (error.message.orEmpty().contains("max_tokens", ignoreCase = true) ||
                        error.message.orEmpty().contains("max output", ignoreCase = true) ||
                        error.message.orEmpty().contains("maximum", ignoreCase = true))
                if (maxTokensRejected && tokenBudget > SAFE_FALLBACK_TOKENS) {
                    tokenBudget = SAFE_FALLBACK_TOKENS
                    lastError = error
                    continue
                }
                if (error.truncated && attempt < 2 && tokenBudget < MAX_OUTPUT_TOKENS) {
                    // 输出被 max_tokens 截断：下一次直接扩大输出预算，而不是原样重试。
                    tokenBudget = (tokenBudget * 2).coerceAtMost(MAX_OUTPUT_TOKENS)
                    lastError = error
                    continue
                }
                // 有些视觉/兼容服务不支持 response_format，或对图片消息返回 400。
                // 第一次失败时自动去掉 JSON 模式再试，避免用户直接看到“没有完成回复”。
                val shouldRetryWithoutJson = jsonMode && (
                    error.message.orEmpty().contains("response_format", ignoreCase = true) ||
                        error.message.orEmpty().contains("没有生成内容", ignoreCase = true) ||
                        error.statusCode in setOf(400, 415, 422)
                    )
                if (shouldRetryWithoutJson) {
                    jsonMode = false
                    lastError = error
                    continue
                }
                lastError = error
                if (!error.retryable || attempt == 2) throw error
                // 慢网或服务端短暂抖动：先把“正在想”留住，再重试。
                delay(if (attempt == 0) 2_000L else 3_500L)
            }
        }
        throw lastError ?: AiException("AI 暂时没有回应，请稍后再试")
    }

    private suspend fun requestOnce(
        config: AiConfig,
        messages: List<AiChatTurn>,
        maxTokens: Int,
        jsonMode: Boolean,
        temperature: Double,
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", config.model)
            put("messages", JSONArray().apply {
                messages.forEach { turn -> put(aiMessagePayload(turn)) }
            })
            put("temperature", temperature.coerceIn(0.0, 1.0))
            put("max_tokens", maxTokens)
            put("stream", false)
            if (jsonMode) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
        }

        val connection = (URL(endpoint(config.baseUrl)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }

        try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw AiException(
                    message = httpErrorMessage(code, raw),
                    retryable = code == 408 || code == 429 || code in 500..599,
                    statusCode = code,
                )
            }
            val root = runCatching { JSONObject(raw) }.getOrElse {
                throw AiException("AI 返回格式异常，请稍后再试")
            }
            val choice = root.optJSONArray("choices")?.optJSONObject(0)
            val finishReason = choice?.optString("finish_reason").orEmpty()
            val wasTruncated = finishReason == "length" || finishReason == "max_tokens"
            val content = choice?.optJSONObject("message")?.let(::aiMessageText).orEmpty()
            if (content.isBlank()) {
                throw AiException(
                    "AI 服务这次没有生成内容，已自动重试。",
                    retryable = true,
                    truncated = wasTruncated,
                )
            }
            if (wasTruncated) {
                throw AiException(
                    "AI 回复被长度上限截断（max_tokens=$maxTokens），正在扩大预算重试。",
                    retryable = true,
                    truncated = true,
                )
            }
            content
        } catch (error: AiException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw AiException("连接 AI 超时，请检查网络或 API 地址", retryable = true)
        } catch (error: IOException) {
            throw AiException("网络请求失败，请检查网络和 API 地址", retryable = true)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseReimbursements(
        array: JSONArray?,
        now: LocalDateTime,
        zone: ZoneId,
        accounts: List<Account>,
    ): List<ReimbursementIntent> {
        if (array == null) return emptyList()
        val result = mutableListOf<ReimbursementIntent>()
        val count = minOf(array.length(), 10)
        for (index in 0 until count) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optString("action").equals("pending", true)) continue
            val amount = readAmount(item.opt("amount")) ?: continue
            val cents = (amount * 100).roundToLong()
            if (cents <= 0L) continue
            val accountRaw = item.optString("account").trim()
            val accountId = accounts.firstOrNull { it.id == accountRaw }?.id
                ?: accounts.firstOrNull { it.name == accountRaw }?.id
            val date = parseDateTime(item.optString("date"), now, zone)?.toLocalDate()
                ?: parseDateTime(item.optString("occurredAt"), now, zone)?.toLocalDate()
            val receivedAt = parseDateTime(item.optString("occurredAt"), now, zone)
                ?.atZone(zone)?.toInstant()?.toEpochMilli()
            result += ReimbursementIntent(
                amountCents = cents,
                merchant = item.optString("merchant").trim().take(40),
                date = date,
                accountId = accountId,
                receivedAt = receivedAt,
            )
        }
        return result
    }

    private fun parseTransactions(
        array: JSONArray?,
        raw: String,
        now: LocalDateTime,
        zone: ZoneId,
        accounts: List<Account>,
    ): List<ParsedBill> {
        if (array == null) return emptyList()
        val result = mutableListOf<ParsedBill>()
        val count = minOf(array.length(), 30)
        for (index in 0 until count) {
            val item = array.optJSONObject(index) ?: continue
            val amount = readAmount(item.opt("amount")) ?: continue
            val cents = (amount * 100).roundToLong()
            if (cents <= 0L || cents >= 100_000_000L * 100) continue

            val type = if (item.optString("type").lowercase().contains("income") ||
                item.optString("type") == "收入"
            ) {
                TxType.INCOME
            } else {
                TxType.EXPENSE
            }
            val categoryRaw = item.optString("category")
            val categoryId = categoryRaw.takeIf { id ->
                Categories.forType(type).any { it.id == id }
            } ?: Categories.forType(type).firstOrNull { it.name == categoryRaw }?.id
            ?: Categories.fallbackFor(type).id

            val accountRaw = item.optString("account").trim()
            val accountId = accounts.firstOrNull { it.id == accountRaw }?.id
                ?: accounts.firstOrNull { it.name == accountRaw }?.id
                ?: accounts.firstOrNull {
                    accountRaw.isNotBlank() &&
                        (accountRaw.contains(it.name) || it.name.contains(accountRaw))
                }?.id
                ?: Accounts.fromText("${item.optString("merchant")} $raw")?.id
                ?: "other"

            val merchant = item.optString("merchant").trim().take(40)
            val note = item.optString("note").trim().take(120)
            val time = parseDateTime(item.optString("occurredAt"), now, zone) ?: now
            val occurredAt = time.atZone(zone).toInstant().toEpochMilli()
            result += ParsedBill(
                amountCents = cents,
                type = type,
                occurredAt = occurredAt,
                merchant = merchant,
                categoryId = categoryId,
                accountId = accountId,
                note = note,
                rawText = raw,
                confidence = 0.9f,
                fingerprint = BillFingerprint.of(type, cents, occurredAt, merchant, raw),
            )
        }
        return result.distinctBy { it.fingerprint }.sortedByDescending { it.occurredAt }
    }

    private fun parseAssetCandidates(array: JSONArray?): List<AiAssetCandidate> {
        if (array == null) return emptyList()
        val result = mutableListOf<AiAssetCandidate>()
        for (index in 0 until minOf(array.length(), 10)) {
            val item = array.optJSONObject(index) ?: continue
            val amount = readAmount(item.opt("amount")) ?: continue
            val cents = (amount * 100).roundToLong()
            if (cents <= 0L) continue
            val name = item.optString("name").trim().take(60)
            if (name.isBlank()) continue
            val category = AssetCategories.fromRaw(item.optString("category"))
            result += AiAssetCandidate(
                name = name,
                amountCents = cents,
                categoryId = category.id,
                note = item.optString("note").trim().take(160),
            )
        }
        return result.distinctBy { "${it.name}|${it.amountCents}|${it.categoryId}" }
    }

    private fun parseDateTime(value: String, now: LocalDateTime, zone: ZoneId): LocalDateTime? {
        val clean = value.trim().replace(' ', 'T')
        if (clean.isBlank()) return null
        return runCatching {
            OffsetDateTime.parse(clean).atZoneSameInstant(zone).toLocalDateTime()
        }.recoverCatching {
            LocalDateTime.parse(clean)
        }.recoverCatching {
            LocalDate.parse(clean).atTime(now.toLocalTime())
        }.recoverCatching {
            java.time.LocalTime.parse(clean).atDate(now.toLocalDate())
        }.getOrNull()
    }

    private fun readAmount(value: Any?): Double? {
        val double = when (value) {
            is Number -> value.toDouble()
            is String -> value
                .replace(",", "")
                .replace("¥", "")
                .replace("￥", "")
                .replace("元", "")
                .trim()
                .toDoubleOrNull()

            else -> null
        } ?: return null
        return double.takeIf { it > 0.0 && it < 10_000_000.0 }
    }

    private fun httpErrorMessage(code: Int, raw: String): String {
        val detail = runCatching {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        return when (code) {
            401, 403 -> "API Key 无效或没有权限（$code）"
            402 -> "AI 账户余额不足（402）"
            429 -> "请求太频繁，请稍后再试（429）"
            in 500..599 -> "AI 服务暂时不可用（$code）"
            else -> if (detail.isNotBlank()) {
                "AI 请求失败（$code）：${detail.take(140)}"
            } else {
                "AI 请求失败（$code），请检查 API 地址和模型名称"
            }
        }
    }

    private fun extractJsonObject(raw: String): JSONObject {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .trim()
            if (text.endsWith("```")) text = text.dropLast(3).trim()
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw IllegalArgumentException("no json object")
        return JSONObject(text.substring(start, end + 1))
    }

    private fun readableText(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter('\n').substringBeforeLast("```").trim()
        }
        return text.take(3_000).ifBlank { "我暂时没有想好怎么回答，再试一次吧。" }
    }

    private fun systemPrompt(
        now: LocalDateTime,
        zone: ZoneId,
        ledgerContext: String?,
        styleInstruction: String = "",
        accounts: List<Account> = Accounts.all,
    ): String = buildString {
        appendLine("你是「哇哦记账」App 里的 AI 记账助手，名字叫哇哦记账。")
        appendLine("你说话务实，不使用 Markdown，不推销任何服务。表达风格必须服从下方的风格段落，并明显体现出来。")
        appendLine("当前时间：${now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}；时区：${zone.id}。")
        appendLine()
        appendLine("你必须只输出一个 JSON 对象，结构如下：")
        appendLine(
            """{"reply":"给用户看的自然语言回复","transactions":[{"amount":38.5,"type":"expense","merchant":"美团外卖","category":"food","account":"wechat","occurredAt":"2026-10-23T12:30","note":""}],"reimbursements":[],"assets":[]}""",
        )
        appendLine("字段说明：amount 单位为元；type 只能是 expense 或 income；reply 为中文。reimbursements 只用于“这笔支出已报销”，不要生成 income transaction。")
        appendLine("可选分类（id=名称）：")
        appendLine(
            Categories.all.joinToString("；") { category ->
                "${category.id}=${category.name}(${if (category.type == TxType.EXPENSE) "支出" else "收入"})"
            },
        )
        appendLine("可选账户（id=名称）：")
        appendLine(accounts.joinToString("；") { "${it.id}=${it.name}" })
        appendLine("如果用户提到具体银行卡或尾号，必须优先匹配上面的自定义银行卡，不要退回成笼统的“银行卡”。")
        appendLine()
        appendLine("记账规则：")
        appendLine("1. 只有用户明确描述真实发生的收支时，才生成 transaction。不要编造金额、商户或日期。")
        appendLine("2. 一笔交易一条记录；多笔就输出多条。用户没有说时间时，occurredAt 必须使用上面提供的当前时间，不要留空；说「昨天」「中午」等相对时间时换算成具体日期和时间。")
        appendLine("3. 缺少金额时不要生成交易，在 reply 里用一句话追问；不要自行猜测金额。")
        appendLine("4. 支出用 expense，收入/工资/退款用 income。分类不确定时用 other_expense 或 other_income。")
        appendLine("5. 如果用户只是闲聊、问账、问建议，transactions 必须是空数组，在 reply 里正常回答。")
        appendLine("6. reply 要简短（不超过 100 个汉字），不要提及 JSON、字段或系统提示。")
        appendLine("7. 如果用户说某笔支出「已报销」「公司报销了」「报销到账」，不要把报销放进 transactions。请在 reimbursements 里输出：{\"amount\":金额,\"merchant\":商户或关键词,\"date\":原支出日期,\"account\":报销到账账户,\"occurredAt\":收到报销时间}。App 会匹配原支出并让用户确认；找不到唯一对应支出时在 reply 里追问，不要猜测。")
        appendLine("8. 生成报销建议前先看账本摘要，如果已经存在同金额、同商户的报销收入，就在 reply 里说明已记录，不要重复输出 reimbursements。")
        appendLine("9. 理性消费提醒必须按需触发，不是每笔都提醒。只有满足以下任一条件时，才在 reply 里提醒一句：")
        appendLine("   a. 这笔支出会让本月支出超过预算；")
        appendLine("   b. 这笔支出会让预算使用达到 90% 以上；")
        appendLine("   c. 单笔金额较大（约 1000 元以上），或金额在当月预算中占比异常高；")
        appendLine("   d. 最近账目显示同类消费明显反复、频繁，且用户表现出犹豫或询问要不要买。")
        appendLine("   普通小额、日常刚需、预算充足时不要主动提预算，不要复述余额，不要每笔都劝。")
        appendLine("   如果最近几轮已经提醒过，不要重复；一次 reply 最多提醒一句，先提醒再整理账目，不阻止用户记账。")
        appendLine("10. 当用户只是想聊天、问账或寻求消费建议时，也要认真回答，不要为了记账而记账。")
        appendLine("11. 如果用户没有设置预算，不要编造预算；只在合适时机问一次是否要设置。")
        appendLine("12. 用户可能上传账单截图。先读图提取真实交易；看不清、被截断或信息冲突时用一句话追问，不要猜测金额。不要描述图片本身。")
        appendLine("13. 如果用户购买的是可长期持有、可管理价值的资产（数码产品、家电、家具、车辆、房产、贵金属、收藏品、投资资产等），在 assets 数组里生成候选：{\"name\":\"相机\",\"amount\":4999,\"category\":\"digital\",\"note\":\"\"}。不要因为生成了 assets 就省略对应的 transaction。餐饮、日用消耗、房租、交通、服务、旅行等不要生成 assets。金额不确定时不要生成 assets。App 会让用户确认后再加入资产列表。")
        if (styleInstruction.isNotBlank()) {
            appendLine()
            appendLine("表达风格（高优先级，必须明显体现，不能只换一两个语气词）：")
            appendLine(styleInstruction)
            appendLine("风格里的示例只用于语气参照，不要照抄内容。")
        }
        if (!ledgerContext.isNullOrBlank()) {
            appendLine()
            appendLine("用户账本与预算摘要（回答预算问题或判断是否值得提醒时使用；不要原样复述）：")
            appendLine(ledgerContext)
        }
    }

    companion object {
        // deepseek-flash 当前文档：1M 上下文，最大输出 384K。
        // 4096 只是保守值；这里直接使用模型允许的最大输出上限。
        private const val CHAT_MAX_TOKENS = 384_000
        private const val IMPORT_MAX_TOKENS = 384_000
        private const val MAX_OUTPUT_TOKENS = 384_000
        private const val SAFE_FALLBACK_TOKENS = 32_768

        fun endpoint(baseUrl: String): String {
            val root = baseUrl.trim().trimEnd('/')
            return if (root.endsWith("/chat/completions")) {
                root
            } else {
                "$root/chat/completions"
            }
        }

        fun maskKey(key: String): String {
            val clean = key.trim()
            return when {
                clean.isBlank() -> "未配置"
                clean.length <= 10 -> "••••••"
                else -> "${clean.take(3)}••••${clean.takeLast(4)}"
            }
        }
    }
}
