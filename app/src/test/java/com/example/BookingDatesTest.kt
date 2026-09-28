package com.example

import com.example.ui.utils.BookingDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class BookingDatesTest {
    private val today = LocalDate.of(2026, 9, 28) // a Monday

    @Test
    fun upcomingDaysStartTomorrow() {
        assertEquals(listOf("2026-09-29", "2026-09-30", "2026-10-01"), BookingDates.upcomingDays(3, today))
        assertEquals("2026-09-29", BookingDates.firstBookableDate(today))
    }

    @Test
    fun monthsRollOverYearsWithCorrectGridOffsets() {
        val months = BookingDates.upcomingMonths(5, LocalDate.of(2026, 11, 15), Locale.US)
        assertEquals(listOf("November 2026", "December 2026", "January 2027", "February 2027", "March 2027"), months.map { it.label })
        assertEquals(0, months[0].leadingBlanks) // Nov 1 2026 is a Sunday
        assertEquals(2, months[1].leadingBlanks) // Dec 1 2026 is a Tuesday
        assertEquals(28, months[3].dates.size)  // Feb 2027
        assertEquals("2027-01-01", months[2].dates.first())
        assertEquals("Jan", months[2].shortLabel)
    }

    @Test
    fun pastAndTodayAreNotBookable() {
        assertTrue(BookingDates.isPast("2026-09-27", today))
        assertTrue(BookingDates.isPast("2026-09-28", today))
        assertFalse(BookingDates.isPast("2026-09-29", today))
        assertTrue(BookingDates.isPast("not-a-date", today))
    }
}
