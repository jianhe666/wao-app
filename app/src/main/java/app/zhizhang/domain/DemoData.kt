package app.zhizhang.domain

import app.zhizhang.data.TxSource
import app.zhizhang.data.TxType
import app.zhizhang.data.TransactionEntity
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 仅在用户主动点击「加载演示数据」时生成，不与真实账目自动混合。
 */
object DemoData {

    private data class Spec(
        val day: Int,
        val hour: Int,
        val minute: Int,
        val merchant: String,
        val categoryId: String,
        val accountId: String,
        val amountCents: Long,
        val type: TxType = TxType.EXPENSE,
        val source: TxSource = TxSource.MANUAL,
        val note: String = "",
    )

    fun build(now: LocalDateTime): List<TransactionEntity> {
        val zone = ZoneId.systemDefault()
        val today = now.dayOfMonth
        val currentSpecs = listOf(
            Spec(today, 8, 12, "在地咖啡", "food", "wechat", 2_800, source = TxSource.NOTIFICATION),
            Spec(today, 12, 6, "美团外卖", "food", "alipay", 3_850, source = TxSource.IMPORT),
            Spec(today, 18, 40, "盒马鲜生", "shopping", "alipay", 12_680, source = TxSource.IMPORT),
            Spec(today - 1, 8, 30, "地铁 2 号线", "transport", "alipay", 400, source = TxSource.NOTIFICATION),
            Spec(today - 1, 19, 15, "星巴克", "food", "wechat", 3_200, note = "拿铁"),
            Spec(today - 2, 12, 30, "滴滴出行", "transport", "wechat", 2_640, source = TxSource.IMPORT),
            Spec(today - 2, 21, 0, "京东", "shopping", "bank", 29_900, source = TxSource.IMPORT),
            Spec(today - 3, 20, 10, "万达影城", "entertainment", "wechat", 7_800, note = "两张电影票"),
            Spec(today - 4, 9, 0, "中国移动", "communication", "alipay", 10_000, note = "月度话费"),
            Spec(today - 4, 15, 12, "瑞幸咖啡", "food", "wechat", 1_990, source = TxSource.NOTIFICATION),
            Spec(today - 5, 9, 30, "公司", "salary", "bank", 1_850_000, type = TxType.INCOME),
            Spec(today - 6, 17, 45, "益丰大药房", "medical", "bank", 4_650),
            Spec(today - 7, 14, 20, "优衣库", "shopping", "alipay", 19_900),
            Spec(today - 8, 10, 0, "水电燃气", "housing", "alipay", 15_630),
            Spec(today - 9, 21, 8, "朋友红包", "gift", "wechat", 20_000, type = TxType.INCOME),
            Spec(today - 10, 8, 0, "12306", "transport", "bank", 55_300, note = "周末往返高铁"),
            Spec(today - 11, 19, 30, "全家便利店", "food", "cash", 1_250, source = TxSource.NOTIFICATION),
            Spec(today - 12, 11, 0, "物业服务中心", "housing", "alipay", 28_000),
            Spec(today - 14, 15, 25, "茶百道", "food", "wechat", 1_600, source = TxSource.NOTIFICATION),
            Spec(today - 15, 22, 0, "网易云音乐", "entertainment", "alipay", 1_500, note = "黑胶会员"),
            Spec(today - 18, 10, 20, "西西弗书店", "education", "alipay", 6_800),
        ).filter { it.day >= 1 }

        val current = currentSpecs.mapIndexed { index, spec ->
            val day = spec.day.coerceAtMost(today).coerceAtLeast(1)
            val planned = now.toLocalDate().withDayOfMonth(day).atTime(LocalTime.of(spec.hour, spec.minute))
            val occurred = if (planned.isAfter(now)) now.minusMinutes(index + 3L) else planned
            entity(spec, occurred, zone)
        }

        val previousMonth = now.toLocalDate().minusMonths(1)
        val previousSpecs = listOf(
            Spec(3, 10, 0, "房租", "housing", "bank", 320_000),
            Spec(5, 9, 0, "公司", "salary", "bank", 1_850_000, type = TxType.INCOME),
            Spec(7, 19, 30, "海底捞", "food", "wechat", 18_800),
            Spec(11, 8, 30, "地铁", "transport", "alipay", 400, source = TxSource.NOTIFICATION),
            Spec(14, 21, 0, "京东", "shopping", "bank", 59_900),
            Spec(17, 20, 0, "电影院", "entertainment", "wechat", 6_000),
            Spec(20, 18, 0, "永辉超市", "shopping", "alipay", 21_000),
            Spec(22, 9, 0, "中国移动", "communication", "alipay", 10_000),
            Spec(25, 12, 0, "麦当劳", "food", "wechat", 4_250),
            Spec(27, 10, 0, "水电燃气", "housing", "alipay", 14_000),
        ).filter { it.day <= previousMonth.lengthOfMonth() }

        val past = previousSpecs.map { spec ->
            val occurred = previousMonth.withDayOfMonth(spec.day).atTime(LocalTime.of(spec.hour, spec.minute))
            entity(spec, occurred, zone)
        }

        return (current + past).sortedBy { it.occurredAt }
    }

    private fun entity(spec: Spec, occurred: LocalDateTime, zone: ZoneId): TransactionEntity =
        TransactionEntity(
            amountCents = spec.amountCents,
            type = spec.type.name,
            categoryId = spec.categoryId,
            accountId = spec.accountId,
            merchant = spec.merchant,
            note = spec.note,
            occurredAt = occurred.atZone(zone).toInstant().toEpochMilli(),
            source = spec.source.name,
            fingerprint = null,
        )
}
