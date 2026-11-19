package com.aquarescue.app.util

import android.content.res.Resources
import com.aquarescue.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Форматирование чисел, единиц и дат на языке интерфейса.
 * Единицы измерения и шаблоны берутся из strings.xml, десятичный разделитель —
 * из текущей локали (ru/kk — запятая, en — точка). Координаты всегда с точкой.
 */
object Fmt {

    fun locale(res: Resources): Locale = res.configuration.locales[0] ?: Locale.getDefault()

    fun decimal(res: Resources, value: Double, digits: Int): String =
        String.format(locale(res), "%.${digits}f", value)

    /** «0,42 м» / «0.42 m». */
    fun depth(res: Resources, meters: Double): String =
        res.getString(R.string.unit_meters, decimal(res, meters, 2))

    /** Число без единицы — для крупного значения на индикаторе. */
    fun depthValue(res: Resources, meters: Double): String = decimal(res, meters, 2)

    /** Порог с одним знаком: «0,5 м». */
    fun depthShort(res: Resources, meters: Double): String =
        res.getString(R.string.unit_meters, decimal(res, meters, 1))

    fun temperature(res: Resources, c: Double): String = res.getString(R.string.unit_celsius, decimal(res, c, 1))

    fun pressure(res: Resources, mbar: Double): String = res.getString(R.string.unit_mbar, decimal(res, mbar, 0))

    fun rssi(res: Resources, dbm: Int): String = res.getString(R.string.unit_dbm, dbm)

    fun seconds(res: Resources, sec: Int): String = res.getString(R.string.unit_seconds, sec)

    fun metersInt(res: Resources, m: Int): String = res.getString(R.string.unit_meters_int, m)

    fun kilometersInt(res: Resources, km: Int): String = res.getString(R.string.unit_km_int, km)

    fun coordinate(value: Double): String = String.format(Locale.US, "%.6f", value)

    fun coordinates(lat: Double, lon: Double): String = "${coordinate(lat)}, ${coordinate(lon)}"

    fun latDms(res: Resources, lat: Double): String =
        toDms(lat) + " " + res.getString(if (lat >= 0) R.string.dir_north else R.string.dir_south)

    fun lonDms(res: Resources, lon: Double): String =
        toDms(lon) + " " + res.getString(if (lon >= 0) R.string.dir_east else R.string.dir_west)

    private fun toDms(value: Double): String {
        val a = abs(value)
        val deg = a.toInt()
        val minFull = (a - deg) * 60
        val min = minFull.toInt()
        val sec = (minFull - min) * 60
        return String.format(Locale.US, "%d°%02d'%04.1f\"", deg, min, sec)
    }

    fun time(epochMs: Long): String =
        DateTimeFormatter.ofPattern("HH:mm:ss").format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    fun dateTime(res: Resources, epochMs: Long): String =
        DateTimeFormatter.ofPattern(res.getString(R.string.pattern_date_time), locale(res))
            .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    fun localDateOf(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()

    fun day(res: Resources, date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> res.getString(R.string.time_today)
        today.minusDays(1) -> res.getString(R.string.time_yesterday)
        else -> DateTimeFormatter.ofPattern(res.getString(R.string.pattern_day), locale(res)).format(date)
    }

    /** «только что», «12 с назад», «3 мин назад»… */
    fun ago(res: Resources, epochMs: Long, now: Long): String {
        val sec = ((now - epochMs) / 1000).coerceAtLeast(0)
        return when {
            sec < 2 -> res.getString(R.string.time_just_now)
            sec < 60 -> res.getString(R.string.time_seconds_ago, sec)
            sec < 3600 -> res.getString(R.string.time_minutes_ago, sec / 60)
            sec < 86_400 -> res.getString(R.string.time_hours_ago, sec / 3600)
            else -> res.getString(R.string.time_days_ago, sec / 86_400)
        }
    }

    /** «2 мин 5 с», «1 ч 3 мин». */
    fun duration(res: Resources, ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> res.getString(R.string.duration_hours_minutes, h, m)
            m > 0 -> res.getString(R.string.duration_minutes_seconds, m, s)
            else -> res.getString(R.string.duration_seconds, s)
        }
    }
}
