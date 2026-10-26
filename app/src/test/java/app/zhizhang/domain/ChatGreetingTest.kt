package app.zhizhang.domain

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import kotlin.random.Random

class ChatGreetingTest {

    private val friday = { hour: Int -> LocalDateTime.of(2026, 10, 23, hour, 0) }

    @Test
    fun `morning greeting is time aware`() {
        val greeting = ChatGreetings.forMoment(friday(8), Random(1))
        assertTrue(greeting.message.contains("早"))
        assertTrue(greeting.suggestions.first() == "早餐")
        assertTrue(greeting.suggestions.none { it.startsWith("午饭") || it.startsWith("晚饭") })
        assertTrue(greeting.suggestions.all { item -> item.none(Char::isDigit) })
    }

    @Test
    fun `noon evening and late night differ`() {
        val noon = ChatGreetings.forMoment(friday(12), Random(1))
        val evening = ChatGreetings.forMoment(friday(20), Random(1))
        val late = ChatGreetings.forMoment(friday(2), Random(1))
        assertTrue(noon.message.contains("中午"))
        assertTrue(evening.message.contains("晚上"))
        assertNotEquals(noon.message, evening.message)
        val lateSuggestions = ChatGreetings.forMoment(friday(2), Random(1)).suggestions
        assertTrue(lateSuggestions.first() == "夜宵")
        assertTrue(lateSuggestions.none { it.contains("午餐") || it.contains("晚饭") })
        assertTrue(lateSuggestions.all { item -> item.none(Char::isDigit) })
    }

    @Test
    fun `weekend gets weekend wording`() {
        val saturday = LocalDateTime.of(2026, 10, 24, 15, 0)
        val messages = (0..20).map {
            ChatGreetings.forMoment(saturday, Random(it)).message
        }
        assertTrue(messages.any { it.contains("周末") })
    }

    @Test
    fun `greeting varies with random seed`() {
        val messages = (0..12).map {
            ChatGreetings.forMoment(friday(15), Random(it)).message
        }
        assertTrue(messages.distinct().size > 1)
    }
}
