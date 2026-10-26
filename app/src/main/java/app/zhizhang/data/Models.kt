package app.zhizhang.data

enum class TxType(val label: String) {
    EXPENSE("支出"),
    INCOME("收入");

    companion object {
        fun from(value: String?): TxType = entries.firstOrNull { it.name == value } ?: EXPENSE
    }
}

enum class TransactionStatus(val label: String) {
    PENDING("待确认"),
    SUCCESS("成功"),
    FAILED("失败"),
    REFUNDED("已退款"),
    CANCELED("已撤销"),
    UNKNOWN("未知");

    companion object {
        fun from(value: String?): TransactionStatus =
            entries.firstOrNull { it.name == value } ?: SUCCESS
    }
}

enum class ReimbursementStatus(val label: String) {
    NONE("未标记"),
    PENDING("待报销"),
    DONE("已报销");

    companion object {
        fun from(value: String?): ReimbursementStatus =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}

enum class TxSource(val label: String) {
    MANUAL("手动"),
    IMPORT("导入"),
    NOTIFICATION("自动"),
    AI("AI");

    companion object {
        fun from(value: String?): TxSource = entries.firstOrNull { it.name == value } ?: MANUAL
    }
}

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val color: Long,
    val type: TxType,
)

data class Account(
    val id: String,
    val name: String,
    val emoji: String,
)

object Categories {
    private val builtInExpense: List<Category> = listOf(
        Category("food", "餐饮", "🍜", 0xFFE76F51, TxType.EXPENSE),
        Category("transport", "交通", "🚇", 0xFF2A9D8F, TxType.EXPENSE),
        Category("shopping", "购物", "🛍️", 0xFFE0A458, TxType.EXPENSE),
        Category("housing", "居住", "🏠", 0xFF5C6BC0, TxType.EXPENSE),
        Category("entertainment", "娱乐", "🎬", 0xFFC65D8A, TxType.EXPENSE),
        Category("medical", "医疗", "💊", 0xFF3A9BC1, TxType.EXPENSE),
        Category("education", "学习", "📚", 0xFF7BA05B, TxType.EXPENSE),
        Category("communication", "通讯", "📱", 0xFFA06CD5, TxType.EXPENSE),
        Category("social", "人情", "🎁", 0xFFC75B5B, TxType.EXPENSE),
        Category("travel", "旅行", "✈️", 0xFF4F76C7, TxType.EXPENSE),
        Category("other_expense", "其他", "✳️", 0xFF8A8F9C, TxType.EXPENSE),
    )

    private val builtInIncome: List<Category> = listOf(
        Category("salary", "工资", "💼", 0xFF2F8F6B, TxType.INCOME),
        Category("bonus", "奖金", "🏆", 0xFFC99A3D, TxType.INCOME),
        Category("refund", "退款", "↩️", 0xFF3F9A8A, TxType.INCOME),
        Category("reimbursement", "报销", "🧾", 0xFF4E9A7A, TxType.INCOME),
        Category("investment", "理财", "📈", 0xFF4B7FA8, TxType.INCOME),
        Category("gift", "红包", "🧧", 0xFFC45A5A, TxType.INCOME),
        Category("other_income", "其他", "🌿", 0xFF7E8F70, TxType.INCOME),
    )

    @Volatile
    private var custom: List<Category> = emptyList()

    val expense: List<Category>
        get() = ordered(builtInExpense, custom.filter { it.type == TxType.EXPENSE })

    val income: List<Category>
        get() = ordered(builtInIncome, custom.filter { it.type == TxType.INCOME })

    val all: List<Category>
        get() = expense + income

    fun customCategories(): List<Category> = custom.toList()

    /** 自定义分类排在最前，内置“其他”永远最后。 */
    private fun ordered(builtIns: List<Category>, customOfType: List<Category>): List<Category> {
        val normalBuiltIns = builtIns.filterNot { it.id == "other_expense" || it.id == "other_income" }
        val fallbackCategories = builtIns.filter { it.id == "other_expense" || it.id == "other_income" }
        return normalBuiltIns + customOfType + fallbackCategories
    }

    fun setCustomCategories(categories: List<Category>) {
        custom = categories
            .filter { it.id.isNotBlank() && it.name.isNotBlank() }
            .distinctBy { it.id }
    }

    fun forType(type: TxType): List<Category> =
        if (type == TxType.EXPENSE) expense else income

    fun byId(id: String?): Category =
        all.firstOrNull { it.id == id } ?: expense.last()

    fun defaultFor(type: TxType): Category =
        if (type == TxType.EXPENSE) byId("food") else byId("other_income")

    fun fallbackFor(type: TxType): Category =
        byId(if (type == TxType.EXPENSE) "other_expense" else "other_income")
}

object Accounts {
    val all: List<Account> = listOf(
        Account("wechat", "微信", "💬"),
        Account("alipay", "支付宝", "🔷"),
        Account("bank", "银行卡", "🏦"),
        Account("cash", "现金", "💵"),
        Account("other", "其他", "🪙"),
    )

    fun byId(id: String?): Account {
        all.firstOrNull { it.id == id }?.let { return it }
        return cardFromId(id) ?: all.first()
    }

    /** 自定义银行卡的 id 形如 card:招商银行:1234；无需额外数据库即可显示账户名。 */
    fun cardFromId(id: String?): Account? {
        if (id == null || !id.startsWith("card:")) return null
        val parts = id.split(":", limit = 3)
        if (parts.size != 3) return null
        val bank = parts[1].ifBlank { "银行卡" }
        val tail = parts[2]
        return Account(id, "$bank 尾号$tail", "🏦")
    }

    fun isValid(id: String?): Boolean =
        id != null && (all.any { it.id == id } || cardFromId(id) != null)

    fun fromText(text: String): Account? = when {
        text.contains("支付宝") -> all[1]
        text.contains("微信") -> all[0]
        listOf("银行", "信用卡", "储蓄卡", "账户", "尾号").any(text::contains) -> all[2]
        listOf("现金", "钱包").any(text::contains) -> all[3]
        else -> null
    }
}

object Formats {
    fun money(cents: Long, symbol: Boolean = true, trimZero: Boolean = false): String {
        val negative = cents < 0
        val abs = kotlin.math.abs(cents)
        val yuan = abs / 100
        val fen = (abs % 100).toInt()
        val grouped = String.format(java.util.Locale.CHINA, "%,d", yuan)
        val decimals = if (trimZero && fen == 0) "" else String.format(
            java.util.Locale.CHINA,
            ".%02d",
            fen,
        )
        val sign = if (negative) "-" else ""
        val prefix = if (symbol) "¥" else ""
        return "$sign$prefix$grouped$decimals"
    }

    fun signedMoney(cents: Long, type: TxType): String {
        val prefix = if (type == TxType.INCOME) "+" else "-"
        return prefix + money(cents)
    }

    fun amountInputToCents(input: String): Long {
        val clean = input.filter { it.isDigit() || it == '.' }
        if (clean.isEmpty() || clean == ".") return 0L
        val parts = clean.split('.')
        val yuan = parts[0].take(9).toLongOrNull() ?: 0L
        val fraction = parts.getOrNull(1)?.take(2)?.padEnd(2, '0')?.toLongOrNull() ?: 0L
        return yuan * 100 + fraction
    }

    fun centsToInput(cents: Long): String {
        val abs = kotlin.math.abs(cents)
        val yuan = abs / 100
        val fen = abs % 100
        return if (fen == 0L) yuan.toString() else "$yuan.${fen.toString().padStart(2, '0')}"
    }
}
