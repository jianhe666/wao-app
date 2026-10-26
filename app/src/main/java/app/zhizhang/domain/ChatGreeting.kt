package app.zhizhang.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import kotlin.random.Random

data class ChatGreeting(
    val message: String,
    val suggestions: List<String>,
)

/**
 * 首页的第一句话不应该每次都是同一句。
 * 按早上/中午/下午/晚上/深夜，加上工作日/周末，生成一句短问候。
 */
object ChatGreetings {

    fun forMoment(
        now: LocalDateTime = LocalDateTime.now(),
        random: Random = Random.Default,
    ): ChatGreeting {
        val weekend = now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY
        val weekday = when (now.dayOfWeek) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }
        val timeSlot = when (now.hour) {
            in 5..10 -> TimeSlot.MORNING
            in 11..13 -> TimeSlot.NOON
            in 14..17 -> TimeSlot.AFTERNOON
            in 18..22 -> TimeSlot.EVENING
            else -> TimeSlot.LATE
        }

        val messages = when (timeSlot) {
            TimeSlot.MORNING -> listOf(
                "早呀，$weekday。今天第一笔准备花在哪？",
                "早安，$weekday。先记一笔，再开始忙？",
            )
            TimeSlot.NOON -> listOf(
                "${weekday}中午好。今天吃了什么好吃的？",
                "到${weekday}中午了，午饭花了多少？",
            )
            TimeSlot.AFTERNOON -> listOf(
                "${weekday}下午好。今天有什么想记的？",
                "下午了，$weekday。顺手把今天的账补上？",
            )
            TimeSlot.EVENING -> listOf(
                "${weekday}晚上好。今天过得怎么样？",
                "到${weekday}晚上了，今天最后一笔是什么？",
            )
            TimeSlot.LATE -> listOf(
                "夜深了。今天还有什么没记的吗？",
                "还没睡？今天最后一笔是什么？",
            )
        } + if (weekend && timeSlot != TimeSlot.LATE) {
            listOf("周末快乐！今天准备怎么过？")
        } else {
            emptyList()
        }

        // 候选只给消费项目，不带金额；金额由用户自己输入。
        // 主场景固定匹配时段，再给 3 个相关项目，避免早餐时出现晚饭。
        val primary = when (timeSlot) {
            TimeSlot.MORNING -> "早餐"
            TimeSlot.NOON -> "午饭"
            TimeSlot.AFTERNOON -> "咖啡"
            TimeSlot.EVENING -> "晚饭"
            TimeSlot.LATE -> "夜宵"
        }
        val extras = when (timeSlot) {
            TimeSlot.MORNING -> listOf("咖啡", "打车", "购物", "水果")
            TimeSlot.NOON -> listOf("奶茶", "打车", "购物", "水果")
            TimeSlot.AFTERNOON -> listOf("购物", "打车", "娱乐", "咖啡")
            TimeSlot.EVENING -> listOf("购物", "打车", "娱乐", "奶茶")
            TimeSlot.LATE -> listOf("打车", "购物", "烧烤")
        } + if (weekend && (timeSlot == TimeSlot.AFTERNOON || timeSlot == TimeSlot.EVENING)) {
            listOf("电影")
        } else {
            emptyList()
        }
        val suggestions = listOf(primary) + extras.shuffled(random).distinct().take(3)
        return ChatGreeting(
            message = messages.random(random),
            suggestions = suggestions,
        )
    }

    private enum class TimeSlot {
        MORNING,
        NOON,
        AFTERNOON,
        EVENING,
        LATE,
    }
}
