package id.cognify.notch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class DueParserTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    // Selasa, 3 November 2026, 10:00
    private val now = ZonedDateTime.of(2026, 11, 3, 10, 0, 0, 0, zone)

    private fun due(text: String): String? =
        DueParser.parseDue(text, now)?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().toString() }

    @Test fun besokJam() = assertEquals("2026-11-04T09:00", due("rapat besok jam 9"))
    @Test fun besokJamSore() = assertEquals("2026-11-04T17:30", due("besok pukul 5.30 sore"))
    @Test fun jamSudahLewatJadiBesok() = assertEquals("2026-11-04T07:00", due("minum obat jam 7 pagi"))
    @Test fun jamTanpaKeteranganJadiMalamIni() = assertEquals("2026-11-03T19:00", due("makan jam 7"))
    @Test fun hariIniJamSudahLewat() = assertNull(due("hari ini jam 8 pagi"))
    @Test fun jamBulatSudahLewatJadiBesok() = assertEquals("2026-11-04T08:00", due("jam 8 pagi"))
    @Test fun formatDuaTitik() = assertEquals("2026-11-03T14:00", due("kirim laporan 14:00"))
    @Test fun at5pm() = assertEquals("2026-11-03T17:00", due("call at 5pm"))
    @Test fun tanggalTanpaJam() = assertEquals("2026-11-25T09:00", due("bayar listrik 25 november"))
    @Test fun tanggalSlash() = assertEquals("2026-12-01T09:00", due("ujian 1/12"))
    @Test fun tanggalLewatJadiTahunDepan() = assertEquals("2027-10-25T09:00", due("ulang tahun 25 oktober"))
    @Test fun namaHari() = assertEquals("2026-11-09T10:00", due("senin jam 10"))
    @Test fun namaHariIniSudahLewatJadiPekanDepan() = assertEquals("2026-11-10T09:00", due("selasa jam 9 pagi"))
    @Test fun namaHariIniJamTanpaKeteranganJadiMalam() = assertEquals("2026-11-03T21:00", due("selasa jam 9"))
    @Test fun lusa() = assertEquals("2026-11-05T09:00", due("lusa"))
    @Test fun mingguDepan() = assertEquals("2026-11-10T09:00", due("minggu depan"))
    @Test fun rapatBukanJam() = assertNull(due("rapat 5-11"))
    @Test fun babBukanTanggal() = assertNull(due("baca bab 1-2"))
    @Test fun tanpaWaktu() = assertNull(due("beli susu"))
    @Test fun jam25Salah() = assertNull(due("jam 25"))
    // Tanggal tidak valid diabaikan; "jam 9" yang sudah lewat hari ini = 21.00 (sama dengan Mac).
    @Test fun tigaPuluhSatuFebruari() = assertEquals("2026-11-03T21:00", due("31 februari jam 9"))
}
