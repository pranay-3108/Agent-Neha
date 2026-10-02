package com.sentineldroid

import android.content.Context
import android.content.Intent
import android.net.Uri

class IntentSurfaceCollector(private val context: Context) {
    fun collect(vault: EvidenceVault) {
        val cases = listOf(
            "VIEW_WEB" to Intent(Intent.ACTION_VIEW, Uri.parse("https://sentineldroid.invalid")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addCategory(Intent.CATEGORY_DEFAULT)
            },
            "VIEW_CONTENT" to Intent(Intent.ACTION_VIEW).apply { addCategory(Intent.CATEGORY_DEFAULT) },
            "SEND_TEXT" to Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                addCategory(Intent.CATEGORY_DEFAULT)
            },
            "PROCESS_TEXT" to Intent("android.intent.action.PROCESS_TEXT").apply {
                type = "text/plain"
            },
            "OPEN_DOCUMENT" to Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            },
            "GET_CONTENT" to Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            },
            "DIAL" to Intent(Intent.ACTION_DIAL, Uri.parse("tel:0000000000")),
            "SENDTO" to Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:invalid@sentineldroid.invalid"))
        )

        cases.forEach { (name, intent) ->
            val matches = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(intent, 0)
            }.getOrDefault(emptyList())

            matches.forEach { resolveInfo ->
                val pkg = resolveInfo.activityInfo?.packageName ?: return@forEach
                vault.add(
                    "INTENT_ENTRYPOINT",
                    "ANDROID_PACKAGE_MANAGER",
                    "EVIDENCE",
                    pkg,
                    resolveInfo.activityInfo.applicationInfo?.uid,
                    "application/json",
                    "{\"surface\":\"${escape(name)}\",\"activity\":\"${escape(resolveInfo.activityInfo.name)}\"}",
                    "PLATFORM_DERIVED"
                )
            }
        }
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
