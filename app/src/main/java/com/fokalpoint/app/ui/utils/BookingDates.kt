package com.fokalpoint.app.ui.utils

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Calendar helpers for booking UIs, always relative to today (ISO `yyyy-MM-dd` strings). */
object BookingDates {

    data class Month(
        val label: String,       // "October 2026"
        val shortLabel: String,  // "Oct"
        val leadingBlanks: Int,  // Sunday-first grid offset
        val dates: List<String>  // ISO dates for every day of the month
    )

    fun today(): LocalDate = LocalDate.now()

    fun iso(date: LocalDate): String = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** The earliest bookable day (tomorrow). */
    fun firstBookableDate(today: LocalDate = today()): String = iso(today.plusDays(1))

    fun isPast(isoDate: String, today: LocalDate = today()): Boolean =
        runCatching { LocalDate.parse(isoDate) <= today }.getOrDefault(true)

    /** The next [count] bookable days, starting tomorrow. */
    fun upcomingDays(count: Int, today: LocalDate = today()): List<String> =
        (1..count).map { iso(today.plusDays(it.toLong())) }

    /** This month and the following [count] - 1 months. */
    fun upcomingMonths(count: Int, today: LocalDate = today(), locale: Locale = Locale.getDefault()): List<Month> =
        (0 until count).map { offset ->
            val ym = YearMonth.from(today).plusMonths(offset.toLong())
            val name = ym.month.getDisplayName(TextStyle.FULL, locale)
            Month(
                label = "$name ${ym.year}",
                shortLabel = ym.month.getDisplayName(TextStyle.SHORT, locale),
                leadingBlanks = ym.atDay(1).dayOfWeek.value % 7, // Monday=1..Sunday=7 -> Sunday=0
                dates = (1..ym.lengthOfMonth()).map { iso(ym.atDay(it)) }
            )
        }

    /** Short chip label, e.g. "Tue 14 Oct". */
    fun chipLabel(isoDate: String, locale: Locale = Locale.getDefault()): String =
        runCatching { LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("EEE d MMM", locale)) }
            .getOrDefault(isoDate)
}
