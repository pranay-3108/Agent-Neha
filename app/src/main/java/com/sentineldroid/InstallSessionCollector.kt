package com.sentineldroid

import android.content.Context

class InstallSessionCollector(private val context: Context) {
    fun collect(vault: EvidenceVault): Int = runCatching {
        val sessions = context.packageManager.packageInstaller.allSessions
        var count = 0
        sessions.take(100).forEach { session ->
            val content = """{"session_id":${session.sessionId},"installer":"${escape(session.installerPackageName.orEmpty())}","app_package":"${escape(session.appPackageName.orEmpty())}","progress":${session.progress},"active":${session.isActive},"staged":${session.isStaged}}"""
            vault.add(
                "INSTALL_SESSION",
                "ANDROID_PACKAGE_INSTALLER",
                "EVIDENCE",
                session.appPackageName,
                null,
                "application/json",
                content,
                "PLATFORM_DERIVED"
            )
            count++
        }
        count
    }.getOrDefault(0)

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
