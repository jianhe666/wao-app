package app.zhizhang.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import app.zhizhang.domain.CustomChatStyle
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore(name = "zhizhang_settings")

class SettingsRepository(private val context: Context) {

    val budgetCents: Flow<Long> = context.settingsDataStore.data.map { prefs ->
        prefs[BUDGET] ?: 0L
    }

    val autoCaptureEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[AUTO_CAPTURE] ?: false
    }

    val autoCaptureAllApps: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[AUTO_CAPTURE_ALL_APPS] ?: false
    }

    val lastAccountId: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[LAST_ACCOUNT] ?: "wechat"
    }

    val aiEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[AI_ENABLED] ?: false
    }

    val aiApiKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[AI_API_KEY] ?: ""
    }

    val aiBaseUrl: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[AI_BASE_URL] ?: DEFAULT_AI_BASE_URL
    }

    val aiModel: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[AI_MODEL] ?: DEFAULT_AI_MODEL
    }

    val chatStyleId: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[CHAT_STYLE] ?: "rational"
    }

    val customChatStyle: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[CUSTOM_CHAT_STYLE] ?: ""
    }

    val customChatStyles: Flow<List<CustomChatStyle>> = context.settingsDataStore.data.map { prefs ->
        parseCustomStyles(
            raw = prefs[CUSTOM_CHAT_STYLES],
            legacy = prefs[CUSTOM_CHAT_STYLE],
        )
    }

    val customAccounts: Flow<List<Account>> = context.settingsDataStore.data.map { prefs ->
        parseCustomAccounts(prefs[CUSTOM_ACCOUNTS])
    }

    val dailyReminderEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[REMINDER_ENABLED] ?: false
    }

    val dailyReminderHour: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[REMINDER_HOUR] ?: 20
    }

    val dailyReminderMinute: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[REMINDER_MINUTE] ?: 30
    }

    val backgroundId: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[BACKGROUND_ID] ?: "default"
    }

    val backgroundOpacity: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[BACKGROUND_OPACITY] ?: 1f
    }

    val customBackgroundPath: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[CUSTOM_BACKGROUND_PATH] ?: ""
    }

    val updateUseMirror: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[UPDATE_USE_MIRROR] ?: false
    }

    val glassTransparency: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[GLASS_TRANSPARENCY] ?: 0.30f
    }


    suspend fun snapshotJson(): JSONObject {
        val accounts = customAccounts.first()
        val styles = customChatStyles.first()
        return JSONObject().apply {
            put("budgetCents", budgetCents.first())
            put("autoCaptureEnabled", autoCaptureEnabled.first())
            put("autoCaptureAllApps", autoCaptureAllApps.first())
            put("lastAccountId", lastAccountId.first())
            put("aiEnabled", aiEnabled.first())
            // 不把 API Key 写进备份，避免备份文件泄露凭据。
            put("aiBaseUrl", aiBaseUrl.first())
            put("aiModel", aiModel.first())
            put("chatStyleId", chatStyleId.first())
            put("customChatStyles", JSONArray().apply {
                styles.forEach { style ->
                    put(JSONObject().apply {
                        put("id", style.id)
                        put("name", style.name)
                        put("instruction", style.instruction)
                    })
                }
            })
            put("customAccounts", JSONArray().apply {
                accounts.forEach { account ->
                    put(JSONObject().apply {
                        put("id", account.id)
                        put("name", account.name)
                        put("emoji", account.emoji)
                    })
                }
            })
            put("dailyReminderEnabled", dailyReminderEnabled.first())
            put("dailyReminderHour", dailyReminderHour.first())
            put("dailyReminderMinute", dailyReminderMinute.first())
            put("backgroundId", backgroundId.first())
            put("backgroundOpacity", backgroundOpacity.first())
            put("customBackgroundPath", customBackgroundPath.first())
            put("updateUseMirror", updateUseMirror.first())
            put("glassTransparency", glassTransparency.first())
        }
    }

    suspend fun restoreSnapshot(json: JSONObject) {
        val accounts = json.optJSONArray("customAccounts")
        val styles = json.optJSONArray("customChatStyles")
        context.settingsDataStore.edit { prefs ->
            prefs[BUDGET] = json.optLong("budgetCents", 0L)
            prefs[AUTO_CAPTURE] = json.optBoolean("autoCaptureEnabled", false)
            prefs[AUTO_CAPTURE_ALL_APPS] = json.optBoolean("autoCaptureAllApps", false)
            prefs[LAST_ACCOUNT] = json.optString("lastAccountId").ifBlank { "wechat" }
            prefs[AI_ENABLED] = json.optBoolean("aiEnabled", false)
            if (json.has("aiApiKey")) {
                val key = json.optString("aiApiKey").trim()
                if (key.isBlank()) prefs.remove(AI_API_KEY) else prefs[AI_API_KEY] = key
            }
            prefs[AI_BASE_URL] = json.optString("aiBaseUrl").ifBlank { DEFAULT_AI_BASE_URL }
            prefs[AI_MODEL] = json.optString("aiModel").ifBlank { DEFAULT_AI_MODEL }
            prefs[CHAT_STYLE] = json.optString("chatStyleId").ifBlank { "rational" }
            if (styles == null) prefs.remove(CUSTOM_CHAT_STYLES) else prefs[CUSTOM_CHAT_STYLES] = styles.toString()
            if (accounts == null) prefs.remove(CUSTOM_ACCOUNTS) else prefs[CUSTOM_ACCOUNTS] = accounts.toString()
            prefs[REMINDER_ENABLED] = json.optBoolean("dailyReminderEnabled", false)
            prefs[REMINDER_HOUR] = json.optInt("dailyReminderHour", 20)
            prefs[REMINDER_MINUTE] = json.optInt("dailyReminderMinute", 30)
            prefs[BACKGROUND_ID] = json.optString("backgroundId").ifBlank { "default" }
            prefs[BACKGROUND_OPACITY] = json.optDouble("backgroundOpacity", 1.0)
                .toFloat().coerceIn(0f, 1f)
            prefs[CUSTOM_BACKGROUND_PATH] = json.optString("customBackgroundPath")
            prefs[UPDATE_USE_MIRROR] = json.optBoolean("updateUseMirror", false)
            prefs[GLASS_TRANSPARENCY] = json.optDouble("glassTransparency", 0.30)
                .toFloat().coerceIn(0f, 0.75f)
        }
    }

    suspend fun setBudget(cents: Long) {
        context.settingsDataStore.edit { prefs ->
            if (cents <= 0) prefs.remove(BUDGET) else prefs[BUDGET] = cents
        }
    }

    suspend fun setAutoCapture(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTO_CAPTURE] = enabled }
    }

    suspend fun setAutoCaptureAllApps(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTO_CAPTURE_ALL_APPS] = enabled }
    }

    suspend fun setLastAccount(accountId: String) {
        context.settingsDataStore.edit { it[LAST_ACCOUNT] = accountId }
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[AI_ENABLED] = enabled }
    }

    suspend fun setAiConfig(apiKey: String, baseUrl: String, model: String) {
        context.settingsDataStore.edit { prefs ->
            val cleanKey = apiKey.trim()
            if (cleanKey.isBlank()) prefs.remove(AI_API_KEY) else prefs[AI_API_KEY] = cleanKey
            prefs[AI_BASE_URL] = baseUrl.trim().ifBlank { DEFAULT_AI_BASE_URL }
            prefs[AI_MODEL] = model.trim().ifBlank { DEFAULT_AI_MODEL }
        }
    }

    suspend fun addCustomAccount(bank: String, tail: String): Account? {
        val cleanBank = bank.trim().replace(":", " ").take(20)
        val cleanTail = tail.filter(Char::isDigit).take(6)
        if (cleanBank.isBlank() || cleanTail.length < 3) return null
        val account = Account("card:$cleanBank:$cleanTail", "$cleanBank 尾号$cleanTail", "🏦")
        val current = customAccounts.first().toMutableList()
        if (current.none { it.id == account.id }) {
            current += account
            context.settingsDataStore.edit { prefs ->
                prefs[CUSTOM_ACCOUNTS] = JSONArray().apply {
                    current.forEach { item ->
                        put(
                            JSONObject()
                                .put("id", item.id)
                                .put("name", item.name)
                                .put("emoji", item.emoji),
                        )
                    }
                }.toString()
            }
        }
        return account
    }

    private fun parseCustomAccounts(raw: String?): List<Account> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val result = mutableListOf<Account>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (!id.startsWith("card:")) continue
                val name = item.optString("name").ifBlank { "银行卡" }
                result += Account(id, name, item.optString("emoji").ifBlank { "🏦" })
            }
            result
        }.getOrDefault(emptyList())
    }

    suspend fun setDailyReminderEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[REMINDER_ENABLED] = enabled }
    }

    suspend fun setBackground(id: String, customPath: String? = null) {
        context.settingsDataStore.edit { prefs ->
            prefs[BACKGROUND_ID] = id
            if (customPath != null) prefs[CUSTOM_BACKGROUND_PATH] = customPath
        }
    }

    suspend fun setGlassTransparency(value: Float) {
        context.settingsDataStore.edit {
            it[GLASS_TRANSPARENCY] = value.coerceIn(0f, 1f)
        }
    }

    suspend fun setUpdateUseMirror(enabled: Boolean) {
        context.settingsDataStore.edit { it[UPDATE_USE_MIRROR] = enabled }
    }

    suspend fun setBackgroundOpacity(value: Float) {
        context.settingsDataStore.edit {
            it[BACKGROUND_OPACITY] = value.coerceIn(0f, 1f)
        }
    }

    suspend fun setDailyReminderTime(hour: Int, minute: Int) {
        context.settingsDataStore.edit {
            it[REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setChatStyle(id: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[CHAT_STYLE] = id
        }
    }

    /** 新增或更新一个命名风格，返回它的稳定 id，并立即选中。 */
    suspend fun saveCustomChatStyle(
        id: String?,
        name: String,
        instruction: String,
    ): String {
        val current = customChatStyles.first().toMutableList()
        val cleanName = name.trim().take(24).ifBlank { "自定义风格" }
        val cleanInstruction = instruction.trim().take(240)
        val styleId = id?.takeIf { it.isNotBlank() }
            ?: "custom-${UUID.randomUUID().toString().take(8)}"
        val newStyle = CustomChatStyle(styleId, cleanName, cleanInstruction)
        val index = current.indexOfFirst { it.id == styleId }
        if (index >= 0) current[index] = newStyle else current += newStyle
        context.settingsDataStore.edit { prefs ->
            prefs[CUSTOM_CHAT_STYLES] = encodeCustomStyles(current)
            prefs[CHAT_STYLE] = styleId
        }
        return styleId
    }

    suspend fun deleteCustomChatStyle(id: String) {
        val current = customChatStyles.first().toMutableList()
        if (current.removeAll { it.id == id }) {
            context.settingsDataStore.edit { prefs ->
                prefs[CUSTOM_CHAT_STYLES] = encodeCustomStyles(current)
                if (prefs[CHAT_STYLE] == id) prefs[CHAT_STYLE] = "rational"
            }
        }
    }

    private fun parseCustomStyles(raw: String?, legacy: String?): List<CustomChatStyle> {
        if (!raw.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(raw)
                val result = mutableListOf<CustomChatStyle>()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    if (id.isBlank()) continue
                    val name = item.optString("name").ifBlank { "自定义风格" }
                    val instruction = item.optString("instruction")
                    if (instruction.isNotBlank()) {
                        result += CustomChatStyle(id, name, instruction)
                    }
                }
                return result
            }
        }
        if (!legacy.isNullOrBlank()) {
            return listOf(CustomChatStyle("custom", "我的风格", legacy))
        }
        return emptyList()
    }

    private fun encodeCustomStyles(styles: List<CustomChatStyle>): String =
        JSONArray().apply {
            styles.forEach { style ->
                put(
                    JSONObject()
                        .put("id", style.id)
                        .put("name", style.name)
                        .put("instruction", style.instruction),
                )
            }
        }.toString()

    suspend fun clearAiConfig() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(AI_API_KEY)
            prefs[AI_ENABLED] = false
        }
    }

    companion object {
        private val BUDGET = longPreferencesKey("monthly_budget_cents")
        private val AUTO_CAPTURE = booleanPreferencesKey("auto_capture_enabled")
        private val AUTO_CAPTURE_ALL_APPS = booleanPreferencesKey("auto_capture_all_apps")
        private val LAST_ACCOUNT = stringPreferencesKey("last_account_id")
        private val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        private val AI_API_KEY = stringPreferencesKey("ai_api_key")
        private val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        private val AI_MODEL = stringPreferencesKey("ai_model")
        private val CHAT_STYLE = stringPreferencesKey("chat_style")
        private val CUSTOM_CHAT_STYLE = stringPreferencesKey("custom_chat_style")
        private val CUSTOM_CHAT_STYLES = stringPreferencesKey("custom_chat_styles")
        private val CUSTOM_ACCOUNTS = stringPreferencesKey("custom_accounts")
        private val REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        private val REMINDER_HOUR = intPreferencesKey("daily_reminder_hour")
        private val REMINDER_MINUTE = intPreferencesKey("daily_reminder_minute")
        private val BACKGROUND_ID = stringPreferencesKey("background_id")
        private val BACKGROUND_OPACITY = floatPreferencesKey("background_opacity")
        private val CUSTOM_BACKGROUND_PATH = stringPreferencesKey("custom_background_path")
        private val UPDATE_USE_MIRROR = booleanPreferencesKey("update_use_mirror")
        private val GLASS_TRANSPARENCY = floatPreferencesKey("glass_transparency")

        const val DEFAULT_AI_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_AI_MODEL = "deepseek-flash"
    }
}
