package app.zhizhang.domain

import app.zhizhang.data.Categories
import app.zhizhang.data.TxType

/**
 * 本地规则分类器。越靠前的关键词优先级越高。
 * learnedRules 为「商家关键词 -> 分类 id」，由用户手动纠正后自动积累。
 */
class CategoryClassifier(
    private val learnedRules: Map<String, String> = emptyMap(),
) {

    fun classify(text: String, type: TxType): String {
        val haystack = normalize(text)
        if (haystack.isBlank()) {
            return Categories.fallbackFor(type).id
        }

        val validIds = Categories.forType(type).map { it.id }.toSet()
        val learned = learnedRules
            .filterValues { it in validIds }
            .keys
            .sortedByDescending { normalize(it).length }

        for (key in learned) {
            val needle = normalize(key)
            if (needle.length >= 2 && haystack.contains(needle)) {
                return learnedRules.getValue(key)
            }
        }

        val rules = if (type == TxType.EXPENSE) expenseRules else incomeRules
        for ((categoryId, keywords) in rules) {
            if (keywords.any { haystack.contains(normalize(it)) }) {
                return categoryId
            }
        }
        return Categories.fallbackFor(type).id
    }

    fun classify(merchant: String, rawText: String, type: TxType): String {
        val primary = classify(merchant, type)
        val fallback = Categories.fallbackFor(type).id
        return if (primary != fallback) primary else classify(rawText, type)
    }

    companion object {
        fun normalize(value: String): String =
            value.lowercase().filter { it.isLetterOrDigit() }

        /** 从商户名提取可以长期复用的匹配键。 */
        fun ruleKey(merchant: String): String =
            normalize(merchant).take(24)

        private val expenseRules: List<Pair<String, List<String>>> = listOf(
            "food" to listOf(
                "美团", "饿了么", "外卖", "肯德基", "kfc", "麦当劳", "汉堡王", "星巴克",
                "瑞幸", "咖啡", "奶茶", "蜜雪", "喜茶", "霸王茶姬", "茶百道", "古茗",
                "海底捞", "火锅", "烧烤", "面馆", "餐厅", "餐", "饭", "食堂", "小吃",
                "便利店", "全家", "罗森", "7-11", "烘焙", "面包", "蛋糕",
            ),
            "transport" to listOf(
                "滴滴", "高德打车", "曹操", "出租车", "地铁", "公交", "轨道交通", "12306",
                "铁路", "高铁", "火车", "加油", "中石化", "中石油", "壳牌", "停车",
                "共享单车", "哈啰", "青桔", "美团单车", "违章", "车费", "打车", "交通",
                "通行费", "etc",
            ),
            "shopping" to listOf(
                "淘宝", "天猫", "京东", "拼多多", "唯品会", "得物", "抖音商城", "商城",
                "超市", "沃尔玛", "永辉", "盒马", "山姆", "宜家", "优衣库", "商场",
                "购物", "百货", "专卖店", "旗舰店", "快递", "顺丰", "菜鸟",
            ),
            "housing" to listOf(
                "房租", "租金", "物业", "水费", "电费", "燃气", "煤气", "供暖", "家具",
                "家政", "维修", "装修", "保洁", "中介费",
            ),
            "entertainment" to listOf(
                "电影", "影院", "游戏", "steam", "腾讯视频", "爱奇艺", "优酷", "哔哩哔哩",
                "b站", "网易云", "qq音乐", "会员", "ktv", "酒吧", "剧本杀", "演出",
                "门票", "迪士尼", "环球影城", "展览", "演唱会",
            ),
            "medical" to listOf(
                "医院", "诊所", "药房", "药店", "挂号", "体检", "医疗", "口腔", "牙科",
                "眼科", "保健",
            ),
            "education" to listOf(
                "书店", "图书", "课程", "培训", "学费", "教育", "学校", "考试", "知识付费",
                "得到", "自习室", "文具",
            ),
            "communication" to listOf(
                "话费", "流量", "宽带", "中国移动", "中国联通", "中国电信", "移动话费",
                "联通", "电信",
            ),
            "social" to listOf(
                "红包", "转账给", "礼金", "礼物", "份子钱", "人情", "随礼",
            ),
            "travel" to listOf(
                "酒店", "民宿", "携程", "飞猪", "去哪儿", "机票", "航空", "旅行", "旅游",
                "景区", "度假", "青旅",
            ),
        )

        private val incomeRules: List<Pair<String, List<String>>> = listOf(
            "salary" to listOf("工资", "薪资", "薪酬", "月薪", "代发工资", "劳务费"),
            "bonus" to listOf("奖金", "年终奖", "绩效", "提成", "补贴", "津贴"),
            "refund" to listOf("退款", "退货", "退回", "返款", "撤销", "返还"),
            "investment" to listOf("理财", "收益", "利息", "分红", "基金", "股票", "余额宝"),
            "gift" to listOf("红包", "礼金", "压岁钱", "赠与"),
        )
    }
}
