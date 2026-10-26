package app.zhizhang.domain

data class ChatStyle(
    val id: String,
    val name: String,
    val emoji: String,
    val instruction: String,
    val temperature: Double = 0.3,
)

data class CustomChatStyle(
    val id: String,
    val name: String,
    val instruction: String,
)

object ChatStyles {
    val presets = listOf(
        ChatStyle(
            id = "cute",
            name = "可爱",
            emoji = "🌸",
            instruction = "句子短、语气软，像关系亲近的朋友。可以用少量「呀、啦、呢」，最多一个温和 emoji；" +
                "金额和预算必须说清楚。不要每句都撒娇，不要用「宝宝」之类的称呼。" +
                "语气示例：「午饭 32 元，记好啦～今天也辛苦啦。」",
            temperature = 0.8,
        ),
        ChatStyle(
            id = "serious",
            name = "正经",
            emoji = "🧐",
            instruction = "使用完整、克制的书面语，像一位值得信赖的财务顾问。不用 emoji、感叹号和网络语气词；" +
                "先给结论，再给金额、趋势或可执行建议。" +
                "语气示例：「已记录餐饮支出 32 元。本月餐饮累计 146.90 元，建议减少一次外食。」",
            temperature = 0.15,
        ),
        ChatStyle(
            id = "rational",
            name = "理性",
            emoji = "🧊",
            instruction = "冷静、直接、不讨好，短句和数字优先。不用 emoji 和感叹号；看到超预算或冲动消费时" +
                "坦率指出，并只给一个具体替代方案，不评判、不说教、不制造羞耻感。" +
                "语气示例：「32 元，餐饮。这个月外食偏多，下一顿简单点。」",
            temperature = 0.3,
        ),
        ChatStyle(
            id = "custom",
            name = "自定义",
            emoji = "✨",
            instruction = "",
        ),
    )

    val default: ChatStyle = presets.first { it.id == "rational" }

    fun resolve(id: String, customStyles: List<CustomChatStyle>): ChatStyle {
        customStyles.firstOrNull { it.id == id }?.let { custom ->
            return ChatStyle(
                id = custom.id,
                name = custom.name.ifBlank { "自定义" },
                emoji = "✨",
                instruction = custom.instruction.trim().take(240).ifBlank {
                    "保持自然、真诚、简洁，像一位可靠的朋友；在消费决策上温和地提醒用户慢一点。"
                },
                temperature = 0.7,
            )
        }
        return presets.firstOrNull { it.id == id && it.id != "custom" } ?: default
    }

    /** 兼容旧的自定义指令 API（旧数据迁移与旧测试）。 */
    fun resolve(id: String, customInstruction: String): ChatStyle =
        resolve(
            id,
            if (id == "custom") {
                listOf(CustomChatStyle("custom", "自定义", customInstruction))
            } else {
                emptyList()
            },
        )
}
