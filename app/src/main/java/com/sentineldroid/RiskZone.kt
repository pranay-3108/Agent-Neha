package com.sentineldroid

enum class RiskZone(val weight: Int, val label: String) {
    GREEN(0, "GREEN"),
    YELLOW(1, "YELLOW"),
    ORANGE(2, "ORANGE"),
    RED(3, "RED");

    companion object {
        fun elevate(zone: RiskZone, steps: Int = 1): RiskZone {
            val wanted = (zone.weight + steps).coerceAtMost(RED.weight)
            return entries.first { it.weight == wanted }
        }
    }
}
