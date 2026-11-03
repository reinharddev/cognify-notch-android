package id.cognify.notch

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Waktu dari teks tugas, sama dengan parser di Cognify Notch Mac/Windows: "jam 7", "pukul 19.30", "14:00", "besok jam 9",
 * "at 5pm", "25 oktober", "senin jam 10". Tanggal tanpa jam = pukul 09.00.
 */
object DueParser {
    private val months = mapOf(
        "januari" to 1, "january" to 1, "jan" to 1, "februari" to 2, "pebruari" to 2, "february" to 2, "feb" to 2,
        "maret" to 3, "march" to 3, "mar" to 3, "april" to 4, "apr" to 4, "mei" to 5, "may" to 5,
        "juni" to 6, "june" to 6, "jun" to 6, "juli" to 7, "july" to 7, "jul" to 7,
        "agustus" to 8, "august" to 8, "agu" to 8, "agt" to 8, "aug" to 8,
        "september" to 9, "sept" to 9, "sep" to 9, "oktober" to 10, "october" to 10, "okt" to 10, "oct" to 10,
        "november" to 11, "nopember" to 11, "nov" to 11, "desember" to 12, "december" to 12, "des" to 12, "dec" to 12,
    )

    // 1 = Minggu ... 7 = Sabtu (sama dengan versi Mac)
    private val weekdays = mapOf(
        "minggu" to 1, "ahad" to 1, "sunday" to 1, "senin" to 2, "monday" to 2, "selasa" to 3, "tuesday" to 3,
        "rabu" to 4, "wednesday" to 4, "kamis" to 5, "thursday" to 5, "jumat" to 6, "jum'at" to 6, "jumaat" to 6, "friday" to 6,
        "sabtu" to 7, "saturday" to 7,
    )

    private class Scan(var text: String)
    private class Day(val date: LocalDate, val weekday: Boolean)

    private fun weekdayOf(d: LocalDate) = (d.dayOfWeek.value % 7) + 1 // Senin=2 ... Minggu=1

    private fun parseDay(s: Scan, now: ZonedDateTime): Day? {
        val today = now.toLocalDate()
        val thisYear = today.year
        val names = months.keys.sortedByDescending { it.length }.joinToString("|")

        fun make(d: Int, m: Int, y: Int?): LocalDate? {
            if (m !in 1..12 || d !in 1..31) return null
            val date = try { LocalDate.of(y ?: thisYear, m, d) } catch (e: DateTimeException) { return null } // 31 Februari tidak ada
            if (y == null && date < today) return try { LocalDate.of(thisYear + 1, m, d) } catch (e: DateTimeException) { null }
            return date
        }
        fun take(f: MatchResult, date: LocalDate?, weekday: Boolean = false): Day? {
            if (date == null) return null
            s.text = s.text.replaceRange(f.range, " ")
            return Day(date, weekday)
        }
        fun find(p: String) = Regex(p).find(s.text)
        fun g(f: MatchResult, i: Int): String? = f.groups[i]?.value
        fun int(x: String?) = x?.toIntOrNull()

        // 25/10/2026, 25-10-2026, 25.10.2026, 25/10 ("bab 1-2" dan "14.30" bukan tanggal)
        find("""(?<![\d/.\-:])(\d{1,2})([/\-.])(\d{1,2})(?:\2(\d{4}))?(?![\d:])""")?.let { f ->
            if (g(f, 4) != null || g(f, 2) == "/") take(f, make(int(g(f, 1)) ?: 0, int(g(f, 3)) ?: 0, int(g(f, 4))))?.let { return it }
        }
        // 25 oktober 2026, 25 okt, 25th oct
        find("""\b(\d{1,2})(?:st|nd|rd|th)?\s+($names)\b(?:\s+(\d{4})\b)?""")?.let { f ->
            take(f, make(int(g(f, 1)) ?: 0, months[g(f, 2) ?: ""] ?: 0, int(g(f, 3))))?.let { return it }
        }
        // oct 25, 2026
        find("""\b($names)\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s+(\d{4})\b)?""")?.let { f ->
            take(f, make(int(g(f, 2)) ?: 0, months[g(f, 1) ?: ""] ?: 0, int(g(f, 3))))?.let { return it }
        }
        find("""\b(?:minggu depan|next week)\b""")?.let { f -> take(f, today.plusDays(7))?.let { return it } }
        find("""\b(?:bulan depan|next month)\b""")?.let { f -> take(f, today.plusMonths(1))?.let { return it } }
        find("""\b(?:tahun depan|next year)\b""")?.let { f -> take(f, today.plusYears(1))?.let { return it } }
        // tahun 2027
        find("""\b(?:tahun|year)\s+(\d{4})\b""")?.let { f ->
            val y = int(g(f, 1))
            if (y != null && y >= thisYear) take(f, if (y == thisYear) today else LocalDate.of(y, 1, 1))?.let { return it }
        }
        // bulan oktober
        find("""\b(?:bulan|month|in)\s+($names)\b""")?.let { f ->
            val m = months[g(f, 1) ?: ""]
            if (m != null) take(f, if (m == today.monthValue) today else make(1, m, null))?.let { return it }
        }
        // tgl 25 / tanggal 25 (bulan ini, atau bulan depan bila sudah lewat)
        find("""\b(?:tgl|tanggal|date)\.?\s*(\d{1,2})\b(?![/.:\-]\d)""")?.let { f ->
            val d = int(g(f, 1))
            if (d != null) {
                var date = make(d, today.monthValue, thisYear)
                if (date != null && date < today) {
                    date = try { today.withDayOfMonth(1).plusMonths(1).withDayOfMonth(d) } catch (e: DateTimeException) { null }
                }
                take(f, date)?.let { return it }
            }
        }
        // hari ini, besok, lusa
        find("""\b(?:lusa|day after tomorrow)\b""")?.let { f -> take(f, today.plusDays(2))?.let { return it } }
        find("""\b(?:besok|tomorrow)\b""")?.let { f -> take(f, today.plusDays(1))?.let { return it } }
        find("""\b(?:hari ini|today)\b""")?.let { f -> take(f, today)?.let { return it } }
        // nama hari
        val days = weekdays.keys.sortedByDescending { it.length }.joinToString("|")
        find("""\b(?:hari\s+)?($days)\b(?!\s+(?:ini|lalu|depan))""")?.let { f ->
            val wd = weekdays[g(f, 1) ?: ""]
            if (wd != null) take(f, today.plusDays(((wd - weekdayOf(today) + 7) % 7).toLong()), weekday = true)?.let { return it }
        }
        return null
    }

    private val timePattern = Regex("""(?:\b(?:jam|pukul|at)|@)\s*(\d{1,2})(?:[.:](\d{2}))?\s*(am|pm|pagi|siang|sore|malam)?|\b(\d{1,2})[.:](\d{2})\b""")

    /** Waktu jatuh tempo dalam milidetik epoch, atau null bila teks tidak menyebut waktu (atau waktunya sudah lewat). */
    fun parseDue(text: String, now: ZonedDateTime = ZonedDateTime.now()): Long? {
        val s = Scan(text.lowercase())
        val parsed = parseDay(s, now)
        val m = timePattern.find(s.text)
        var hour = 9
        var minute = 0
        var period: String? = null
        if (m != null) {
            hour = (m.groups[1]?.value ?: m.groups[4]?.value)?.toIntOrNull() ?: return null
            if (hour >= 24) return null
            minute = (m.groups[2]?.value ?: m.groups[5]?.value ?: "0").toIntOrNull() ?: 0
            if (minute >= 60) return null
            period = m.groups[3]?.value
            when (period) {
                "pm", "sore", "malam" -> if (hour < 12) hour += 12
                "siang" -> if (hour < 11) hour += 12
                "am", "pagi" -> if (hour == 12) hour = 0
            }
        } else if (parsed == null) {
            return null
        }
        val day = parsed?.date ?: now.toLocalDate()
        val date = day.atTime(hour, minute).atZone(now.zone)
        if (!date.isBefore(now)) return date.toInstant().toEpochMilli()
        // "jam 7" pukul 10 pagi tanpa keterangan = 19.00 hari ini, bukan 07.00 besok.
        if (m != null && period == null && hour < 12 && day == now.toLocalDate()) {
            val evening = day.atTime(hour + 12, minute).atZone(now.zone)
            if (evening.isAfter(now)) return evening.toInstant().toEpochMilli()
        }
        if (parsed?.weekday == true) return date.plusDays(7).toInstant().toEpochMilli()
        if (parsed == null && m != null) return date.plusDays(1).toInstant().toEpochMilli() // hanya jam yang sudah lewat = besok
        return null // tanggal atau hari ini yang jamnya sudah lewat: tanpa pengingat
    }
}
