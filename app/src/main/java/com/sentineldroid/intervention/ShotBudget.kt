package com.sentineldroid.intervention

import android.content.Context
import java.util.Calendar

/**
 * Hard cap on autonomous shots. Resets at local midnight.
 */
object ShotBudget {

    const val MAX_PER_APP_PER_DAY = 3
    const val MAX_PER_DEVICE_PER_DAY = 5

    private const val PREFS = "sentinel_shot_budget"

    fun canFire(ctx: Context, pkg: String): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        rollIfNewDay(prefs)
        val today = todayKey()

        val appCount = prefs.getInt("app:$today:$pkg", 0)
        val devCount = prefs.getInt("dev:$today", 0)

        return appCount < MAX_PER_APP_PER_DAY && devCount < MAX_PER_DEVICE_PER_DAY
    }

    fun recordFire(ctx: Context, pkg: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        rollIfNewDay(prefs)
        val today = todayKey()

        val appKey = "app:$today:$pkg"
        val devKey = "dev:$today"

        prefs.edit()
            .putInt(appKey, prefs.getInt(appKey, 0) + 1)
            .putInt(devKey, prefs.getInt(devKey, 0) + 1)
            .apply()
    }

    private fun rollIfNewDay(prefs: android.content.SharedPreferences) {
        val today = todayKey()
        val stored = prefs.getString("current_day", null)
        if (stored != today) {
            prefs.edit().clear().putString("current_day", today).apply()
        }
    }

    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }
}