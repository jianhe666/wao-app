package app.zhizhang.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

enum class SubscriptionCycle(val label: String, val months: Int) {
    MONTHLY("包月", 1),
    QUARTERLY("包季", 3),
    YEARLY("包年", 12),
    CUSTOM("自定义", 0);

    companion object {
        fun from(value: String?): SubscriptionCycle =
            entries.firstOrNull { it.name == value } ?: MONTHLY
    }
}

data class Subscription(
    val id: Long,
    val name: String,
    val cycle: SubscriptionCycle,
    val accountId: String,
    val amountCents: Long,
    val nextPaymentAt: Long,
)

/** 账户显示名、期初余额和订阅都放在本机。 */
class WealthStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("wealth_profile", Context.MODE_PRIVATE)

    fun profiles(): Map<String, Account> {
        val raw = prefs.getString(PROFILES, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { id ->
                    val item = json.optJSONObject(id) ?: return@forEach
                    val name = item.optString("name").trim()
                    if (name.isNotBlank()) {
                        put(id, Account(id, name, item.optString("emoji").ifBlank { "💳" }))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveProfile(id: String, name: String, emoji: String) {
        if (id.isBlank() || name.isBlank()) return
        val json = JSONObject()
        profiles().forEach { (key, account) ->
            json.put(key, JSONObject().apply {
                put("name", account.name)
                put("emoji", account.emoji)
            })
        }
        json.put(id, JSONObject().apply {
            put("name", name.trim().take(24))
            put("emoji", emoji.ifBlank { "💳" }.take(2))
        })
        prefs.edit().putString(PROFILES, json.toString()).apply()
    }

    fun openingBalance(accountId: String): Long =
        prefs.getLong("balance:$accountId", 0L)

    fun setOpeningBalance(accountId: String, cents: Long) {
        prefs.edit().putLong("balance:$accountId", cents).apply()
    }

    fun subscriptions(): List<Subscription> {
        val raw = prefs.getString(SUBSCRIPTIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val name = item.optString("name").trim()
                    val amount = item.optLong("amountCents", 0L)
                    val next = item.optLong("nextPaymentAt", 0L)
                    if (name.isBlank() || amount <= 0L || next <= 0L) continue
                    add(
                        Subscription(
                            id = item.optLong("id", index.toLong() + 1),
                            name = name,
                            cycle = SubscriptionCycle.from(item.optString("cycle")),
                            accountId = item.optString("accountId").ifBlank { "wechat" },
                            amountCents = amount,
                            nextPaymentAt = next,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveSubscription(subscription: Subscription): Subscription {
        val current = subscriptions().toMutableList()
        val id = if (subscription.id > 0L) subscription.id else {
            (current.maxOfOrNull { it.id } ?: 0L) + 1L
        }
        val saved = subscription.copy(id = id)
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) current[index] = saved else current += saved
        saveSubscriptions(current)
        return saved
    }

    fun deleteSubscription(id: Long) {
        saveSubscriptions(subscriptions().filterNot { it.id == id })
    }

    fun nextDate(cycle: SubscriptionCycle, from: LocalDate = LocalDate.now()): LocalDate =
        if (cycle.months <= 0) from else from.plusMonths(cycle.months.toLong())

    fun nextDate(cycle: SubscriptionCycle, fromMillis: Long): Long =
        nextDate(cycle, java.time.Instant.ofEpochMilli(fromMillis)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate())
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun saveSubscriptions(subscriptions: List<Subscription>) {
        val array = JSONArray()
        subscriptions.forEach { subscription ->
            array.put(JSONObject().apply {
                put("id", subscription.id)
                put("name", subscription.name)
                put("cycle", subscription.cycle.name)
                put("accountId", subscription.accountId)
                put("amountCents", subscription.amountCents)
                put("nextPaymentAt", subscription.nextPaymentAt)
            })
        }
        prefs.edit().putString(SUBSCRIPTIONS, array.toString()).apply()
    }

    private companion object {
        const val PROFILES = "profiles"
        const val SUBSCRIPTIONS = "subscriptions"
    }
}
