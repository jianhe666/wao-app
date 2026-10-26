package app.zhizhang.domain

import app.zhizhang.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryClassifierTest {

    private val classifier = CategoryClassifier()

    @Test
    fun `classifies common merchants`() {
        assertEquals("food", classifier.classify("美团外卖", TxType.EXPENSE))
        assertEquals("transport", classifier.classify("滴滴出行", TxType.EXPENSE))
        assertEquals("shopping", classifier.classify("京东商城", TxType.EXPENSE))
        assertEquals("housing", classifier.classify("物业服务中心", TxType.EXPENSE))
        assertEquals("communication", classifier.classify("中国移动话费", TxType.EXPENSE))
        assertEquals("travel", classifier.classify("携程酒店", TxType.EXPENSE))
    }

    @Test
    fun `classifies income`() {
        assertEquals("salary", classifier.classify("工资", TxType.INCOME))
        assertEquals("bonus", classifier.classify("年终奖金", TxType.INCOME))
        assertEquals("investment", classifier.classify("基金分红", TxType.INCOME))
        assertEquals("refund", classifier.classify("退款", TxType.INCOME))
    }

    @Test
    fun `learned rules override defaults`() {
        val learned = CategoryClassifier(mapOf("星巴克" to "shopping"))
        assertEquals("shopping", learned.classify("星巴克", TxType.EXPENSE))
    }

    @Test
    fun `learned rules respect type`() {
        val learned = CategoryClassifier(mapOf("公司" to "salary"))
        assertEquals("other_expense", learned.classify("公司", TxType.EXPENSE))
        assertEquals("salary", learned.classify("公司", TxType.INCOME))
    }

    @Test
    fun `unknown merchant falls back to other`() {
        assertEquals("other_expense", classifier.classify("一家没有见过的店", TxType.EXPENSE))
        assertEquals("other_income", classifier.classify("陌生收入", TxType.INCOME))
    }

    @Test
    fun `rule key normalizes punctuation and case`() {
        assertEquals("starbucks", CategoryClassifier.ruleKey("StarBucks"))
        assertEquals("美团外卖", CategoryClassifier.ruleKey("美团 外卖"))
    }
}
