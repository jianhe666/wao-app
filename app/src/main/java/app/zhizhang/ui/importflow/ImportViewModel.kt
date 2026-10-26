package app.zhizhang.ui.importflow

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zhizhang.ZhizhangApp
import app.zhizhang.data.Accounts
import app.zhizhang.data.ImportSelection
import app.zhizhang.domain.BillParser
import app.zhizhang.domain.CategoryClassifier
import app.zhizhang.domain.CsvBillCodec
import app.zhizhang.domain.BillImportException
import app.zhizhang.domain.CsvException
import app.zhizhang.domain.OfficialBillCsv
import app.zhizhang.domain.ParsedBill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ImportUiState(
    val text: String = "",
    val parsing: Boolean = false,
    val importing: Boolean = false,
    val bills: List<ParsedBill> = emptyList(),
    val skipped: Int = 0,
    val error: String? = null,
    val hint: String? = null,
    val importedCount: Int? = null,
    val source: String? = null,
    val warnings: List<String> = emptyList(),
) {
    val canParse: Boolean get() = text.isNotBlank() && !parsing
    val canImport: Boolean get() = bills.isNotEmpty() && !importing
}

class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ZhizhangApp
    private val repository = app.repository
    private val settings = app.settings
    private val aiService = app.aiService
    private val zone = ZoneId.systemDefault()

    private val _state = MutableStateFlow(ImportUiState())
    val state = _state.asStateFlow()

    val aiReady = combine(settings.aiEnabled, settings.aiApiKey) { enabled, key ->
        enabled && key.isNotBlank()
    }

    fun onTextChange(value: String) {
        _state.update {
            it.copy(
                text = value.take(20_000),
                error = null,
                hint = null,
                source = null,
                warnings = emptyList(),
            )
        }
    }

    fun fillSample() {
        _state.update {
            it.copy(
                text = "10月23日 12:30 美团外卖 支出 38.50元\n" +
                    "微信支付 付款成功 ¥19.90 商户：瑞幸咖啡",
                bills = emptyList(),
                skipped = 0,
                error = null,
                hint = null,
                source = null,
                warnings = emptyList(),
            )
        }
    }

    fun parseText() {
        val text = _state.value.text.trim()
        if (text.isBlank() || _state.value.parsing) {
            if (text.isBlank()) _state.update { it.copy(error = "先粘贴一段账单文字") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(parsing = true, error = null, hint = null, bills = emptyList(), skipped = 0)
            }
            val now = LocalDateTime.now(zone).truncatedTo(ChronoUnit.MINUTES)
            val learned = repository.rulesSnapshot()
            val defaultAccount = settings.lastAccountId.first()
            val local = BillParser(CategoryClassifier(learned)).parse(
                input = text,
                now = now,
                zone = zone,
                defaultAccountId = defaultAccount,
            )
            var hint: String? = null
            val bills = if (settings.aiEnabled.first() && settings.aiApiKey.first().isNotBlank()) {
                val accounts = Accounts.all + settings.customAccounts.first()
                val aiBills = runCatching {
                    aiService.parseBills(text, now, zone, accounts)
                }.getOrNull()
                if (aiBills.isNullOrEmpty()) {
                    hint = "AI 没有返回结果，已用本地规则识别"
                    local
                } else {
                    aiBills
                }
            } else {
                local
            }
            _state.update {
                it.copy(
                    parsing = false,
                    bills = bills,
                    hint = hint,
                    error = if (bills.isEmpty()) "没有识别到账目，补上金额或日期再试" else null,
                )
            }
        }
    }

    fun parseCsv(uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    parsing = true,
                    error = null,
                    hint = null,
                    bills = emptyList(),
                    skipped = 0,
                    source = null,
                    warnings = emptyList(),
                )
            }
            val bytes = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.use { input -> input.readBytes().take(10_000_000).toByteArray() }
            }
            if (bytes == null || bytes.isEmpty()) {
                _state.update { it.copy(parsing = false, error = "这个文件没有内容") }
                return@launch
            }
            val result = runCatching {
                OfficialBillCsv.decode(
                    bytes = bytes,
                    fileName = uri.lastPathSegment.orEmpty(),
                    rules = repository.rulesSnapshot(),
                    zone = zone,
                    now = LocalDateTime.now(zone).truncatedTo(ChronoUnit.MINUTES),
                )
            }.getOrElse { error ->
                _state.update {
                    it.copy(
                        parsing = false,
                        error = when (error) {
                            is BillImportException -> error.message
                            is CsvException -> error.message
                            else -> "账单读取失败：${error.message ?: "格式不支持"}"
                        },
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    parsing = false,
                    bills = result.bills,
                    skipped = result.skipped,
                    source = result.source,
                    warnings = result.warnings,
                    error = if (result.bills.isEmpty()) "没有可导入的账目" else null,
                    hint = buildString {
                        append("识别为 ${result.source}")
                        if (result.skipped > 0) append("，跳过 ${result.skipped} 行")
                        if (result.warnings.isNotEmpty()) {
                            append("；")
                            append(result.warnings.take(2).joinToString("；"))
                        }
                    },
                )
            }
        }
    }

    fun cancelImport() {
        _state.update {
            it.copy(
                bills = emptyList(),
                skipped = 0,
                source = null,
                warnings = emptyList(),
                error = null,
                hint = null,
                parsing = false,
                importing = false,
            )
        }
    }

    fun importBills() {
        val bills = _state.value.bills
        if (bills.isEmpty() || _state.value.importing) return
        viewModelScope.launch {
            _state.update { it.copy(importing = true, error = null) }
            val inserted = repository.importBills(
                bills.map { ImportSelection(bill = it, categoryId = it.categoryId) },
            )
            _state.update { it.copy(importing = false, importedCount = inserted) }
        }
    }

    fun consumeImported() {
        _state.update { it.copy(importedCount = null, bills = emptyList()) }
    }
}
