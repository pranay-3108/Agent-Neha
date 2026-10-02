package com.sentineldroid

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

object BehavioralBaselineStore {
    private const val PREFS = "sentinel_behavior"
    private const val KEY_PREFIX = "b_"
    private const val MAX_DAYS = 7

    data class Sample(
        val at: Long,
        val rxBytes: Long,
        val txBytes: Long,
        val foregroundMinutes: Double,
        val accessibilityEvents: Int,
        val hour: Int,
        val calledPackages: Set<String>
    )

    data class Stats(val mean: Double, val stdev: Double, val count: Int)
    data class Baseline(
        val packageName: String,
        val rx: Stats,
        val tx: Stats,
        val foregroundMinutes: Stats,
        val accessibilityEvents: Stats,
        val activeHours: Set<Int>,
        val calledPackages: Set<String>,
        val builtDays: Int,
        val confidence: Double
    )

    data class Anomaly(val type: String, val deviation: Double, val at: Long)

    fun add(context: Context, sample: Sample) {
        val existing = samples(context, sample.packageName).toMutableList()
        existing += sample
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val trimmed = existing.filter { it.at >= cutoff }.takeLast(300)
        save(context, sample.packageName, trimmed)
    }

    fun baseline(context: Context, packageName: String): Baseline? {
        val data = samples(context, packageName)
        if (data.isEmpty()) return null
        fun stats(values: List<Double>): Stats {
            val mean = values.average()
            val variance = values.map { (it - mean) * (it - mean) }.average()
            return Stats(mean, sqrt(variance), values.size)
        }
        val activeHours = data.groupBy { java.util.Calendar.getInstance().apply { timeInMillis = it.at }.get(java.util.Calendar.HOUR_OF_DAY) }
            .filterValues { it.size >= 2 }.keys
        return Baseline(
            packageName,
            stats(data.map { it.rxBytes.toDouble() }),
            stats(data.map { it.txBytes.toDouble() }),
            stats(data.map { it.foregroundMinutes }),
            stats(data.map { it.accessibilityEvents.toDouble() }),
            activeHours,
            data.flatMap { it.calledPackages }.toSet(),
            ((data.maxOf { it.at } - data.minOf { it.at }) / (24 * 60 * 60 * 1000L)).toInt().coerceIn(0, MAX_DAYS),
            (data.size / 50.0).coerceIn(0.0, 1.0)
        )
    }

    fun check(base: Baseline, current: Sample): List<Anomaly> {
        val out = mutableListOf<Anomaly>()
        if (base.rx.count >= 5 && current.rxBytes > base.rx.mean + 3 * base.rx.stdev.coerceAtLeast(1.0)) {
            out += Anomaly("NETWORK_RX_SPIKE", safeDeviation(current.rxBytes.toDouble(), base.rx.mean), current.at)
        }
        if (base.tx.count >= 5 && current.txBytes > base.tx.mean + 3 * base.tx.stdev.coerceAtLeast(1.0)) {
            out += Anomaly("NETWORK_TX_SPIKE", safeDeviation(current.txBytes.toDouble(), base.tx.mean), current.at)
        }
        val hour = java.util.Calendar.getInstance().apply { timeInMillis = current.at }.get(java.util.Calendar.HOUR_OF_DAY)
        if (base.confidence >= 0.6 && hour !in base.activeHours) out += Anomaly("OFF_HOURS_ACTIVITY", 1.0, current.at)
        if (base.accessibilityEvents.count >= 5 && current.accessibilityEvents > base.accessibilityEvents.mean + 3 * base.accessibilityEvents.stdev.coerceAtLeast(1.0)) {
            out += Anomaly("ACCESSIBILITY_EVENT_SPIKE", safeDeviation(current.accessibilityEvents.toDouble(), base.accessibilityEvents.mean), current.at)
        }
        val newPackages = current.calledPackages - base.calledPackages
        if (base.confidence >= 0.6 && newPackages.isNotEmpty()) out += Anomaly("NEW_PACKAGE_INTERACTION", newPackages.size.toDouble(), current.at)
        return out
    }

    private fun safeDeviation(value: Double, mean: Double): Double = if (mean <= 0.0) value else value / mean

    private fun samples(context: Context, packageName: String): List<Sample> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PREFIX + packageName, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Sample(
                    o.getLong("at"), o.getLong("rx"), o.getLong("tx"), o.getDouble("fg"),
                    o.getInt("a11y"), o.getInt("hour"),
                    o.optJSONArray("called")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, packageName: String, samples: List<Sample>) {
        val arr = JSONArray()
        samples.forEach { s ->
            arr.put(JSONObject().apply {
                put("at", s.at); put("rx", s.rxBytes); put("tx", s.txBytes); put("fg", s.foregroundMinutes); put("a11y", s.accessibilityEvents); put("hour", s.hour)
                val called = JSONArray(); s.calledPackages.forEach { called.put(it) }; put("called", called)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PREFIX + packageName, arr.toString()).apply()
    }
}
