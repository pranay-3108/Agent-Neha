package com.sentineldroid

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.Settings

class DeviceStateCollector(private val context: Context) {
    fun collect(vault: EvidenceVault) {
        val dpm = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
        val admins = dpm.activeAdmins.orEmpty()
        val owner = admins.firstOrNull { runCatching { dpm.isDeviceOwnerApp(it.packageName) }.getOrDefault(false) }?.packageName
        val adminNames = admins.map { it.flattenToString() }
        val allFiles = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
        val body = """{
            "sdk":${Build.VERSION.SDK_INT},
            "release":"${esc(Build.VERSION.RELEASE)}",
            "device_owner":"${esc(owner.orEmpty())}",
            "active_admins":[${adminNames.joinToString(",") { "\"${esc(it)}\"" }}],
            "adb_enabled":${setting(Settings.Global.ADB_ENABLED)},
            "developer_options":${setting(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)},
            "sentinel_all_files":$allFiles
        }""".trimIndent().replace(Regex("\\s+"), " ")
        vault.add(
            "DEVICE_STATE",
            "ANDROID_PLATFORM",
            "EVIDENCE",
            null,
            null,
            "application/json",
            body,
            "PLATFORM_DERIVED"
        )
    }

    private fun setting(key: String): Int = runCatching { Settings.Global.getInt(context.contentResolver, key) }.getOrDefault(-1)
    private fun esc(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
