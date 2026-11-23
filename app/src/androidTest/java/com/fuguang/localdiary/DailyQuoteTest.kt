package com.fuguang.localdiary

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fuguang.localdiary.data.DailyQuotes
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class DailyQuoteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun threeCalendarYearsIncludingLeapDayHaveUniqueContentAndSources() {
        val start = LocalDate.of(2026, 11, 22)
        val days = ChronoUnit.DAYS.between(start, start.plusYears(3)).toInt()
        assertEquals(1096, days)
        val library = DailyQuotes.library(context)
        assertTrue(library.size >= days)
        val sequence = (0 until days).map { DailyQuotes.indexFor(start, start.plusDays(it.toLong()), library.size) }
        assertEquals(days, sequence.toSet().size)
        assertEquals(days, sequence.map { DailyQuotes.normalized(library[it].text) }.toSet().size)
        assertTrue(library.all { it.author.isNotBlank() && it.work.isNotBlank() && android.net.Uri.parse(it.sourceUrl).host != null })
        val sanmaoCount = library.takeWhile { it.author == "三毛" }.size
        assertTrue(sanmaoCount > 0)
        assertTrue(library.drop(sanmaoCount).none { it.author == "三毛" })
        assertEquals("三毛", library.first().author)
    }

    @Test fun scheduleSurvivesReopenAndDoesNotChangeWithinDayOrOnClockRollback() {
        fun isolated() = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = context.getSharedPreferences("quote_test_$name", mode)
        }
        val isolated = isolated()
        val prefs = isolated.getSharedPreferences("shiling_daily_quote", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            val start = LocalDate.of(2026, 11, 22)
            val first = DailyQuotes.today(isolated, start)
            assertEquals(first, DailyQuotes.today(isolated, start))
            val next = DailyQuotes.today(isolated, start.plusDays(1))
            assertNotEquals(first.id, next.id)
            assertEquals(next, DailyQuotes.today(isolated(), start.plusDays(1)))
            assertEquals(next, DailyQuotes.today(isolated(), start))
            assertEquals(next, DailyQuotes.today(isolated(), start.plusDays(1)))
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun leapYearAndLibraryBoundaryDoNotWrapToFirstQuote() {
        val start = LocalDate.of(2028, 2, 28)
        assertEquals(1, DailyQuotes.indexFor(start, LocalDate.of(2028, 2, 29), 1200))
        assertEquals(2, DailyQuotes.indexFor(start, LocalDate.of(2028, 3, 1), 1200))
        assertEquals(1199, DailyQuotes.indexFor(start, start.plusDays(1200), 1200))
        assertEquals(0, DailyQuotes.indexFor(start, start.minusDays(2), 1200))
    }
}
