package app.zhizhang.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

data class SavingsDeposit(
    val id: String,
    val cents: Long,
    val timestamp: Long,
)

data class SavingsGoal(
    val id: String,
    val name: String,
    val targetCents: Long,
    val deposits: List<SavingsDeposit> = emptyList(),
) {
    val savedCents: Long get() = deposits.sumOf { it.cents }
}

class SavingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("savings_goals", Context.MODE_PRIVATE)

    fun goals(): List<SavingsGoal> {
        return decode(JSONArray(prefs.getString("goals", "[]")))
    }

    fun snapshot(): JSONArray = JSONArray(prefs.getString("goals", "[]"))

    private fun decode(array: JSONArray): List<SavingsGoal> {
        return (0 until array.length()).map { index ->
            val goal = array.getJSONObject(index)
            val deposits = goal.getJSONArray("deposits")
            SavingsGoal(
                goal.getString("id"),
                goal.getString("name"),
                goal.getLong("target"),
                (0 until deposits.length()).map { depositIndex ->
                    val deposit = deposits.getJSONObject(depositIndex)
                    SavingsDeposit(deposit.getString("id"), deposit.getLong("cents"), deposit.getLong("timestamp"))
                },
            )
        }
    }

    fun validateSnapshot(array: JSONArray): List<SavingsGoal> {
        val goals = decode(array)
        require(goals.map { it.id }.distinct().size == goals.size) { "攒钱目标重复" }
        goals.forEach { goal ->
            require(goal.name.isNotBlank() && goal.targetCents > 0)
            require(goal.deposits.all { it.cents > 0 })
            goal.deposits.fold(0L) { total, deposit -> Math.addExact(total, deposit.cents) }
        }
        return goals
    }

    fun save(goals: List<SavingsGoal>): Boolean {
        val array = JSONArray()
        goals.forEach { goal ->
            val deposits = JSONArray()
            goal.deposits.forEach { deposit ->
                deposits.put(JSONObject().put("id", deposit.id).put("cents", deposit.cents).put("timestamp", deposit.timestamp))
            }
            array.put(JSONObject().put("id", goal.id).put("name", goal.name).put("target", goal.targetCents).put("deposits", deposits))
        }
        return prefs.edit().putString("goals", array.toString()).commit()
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun amountCents(input: String): Long? {
            if (!input.matches(Regex("[0-9]{1,9}(\\.[0-9]{1,2})?"))) return null
            return runCatching { BigDecimal(input).movePointRight(2).longValueExact() }
                .getOrNull()?.takeIf { it > 0L }
        }
    }
}
