package com.aquarescue.app.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Расстояние между точками по формуле гаверсинусов, м. */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Смещение точки относительно опорной в метрах (x — восток, y — север). */
    fun offsetMeters(refLat: Double, refLon: Double, lat: Double, lon: Double): Pair<Double, Double> {
        val x = Math.toRadians(lon - refLon) * EARTH_RADIUS_M * cos(Math.toRadians(refLat))
        val y = Math.toRadians(lat - refLat) * EARTH_RADIUS_M
        return x to y
    }
}
