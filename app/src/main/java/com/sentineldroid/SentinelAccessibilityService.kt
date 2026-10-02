package com.sentineldroid

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent

class SentinelAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        val prefs = getSharedPreferences("sentinel", MODE_PRIVATE)
        val captureText = prefs.getBoolean("capture_ui_text", false)
        val content = buildString {
            append("eventType=").append(event.eventType).append('\n')
            append("className=").append(event.className ?: "").append('\n')
            append("package=").append(packageName).append('\n')
            append("eventText=")
            append(if (captureText) event.text.joinToString(" | ") else "<disabled>")
            if (captureText) {
                append("\nnodeTree=\n")
                appendNodeTree(rootInActiveWindow, this, 0, 250)
            }
        }

        EvidenceFirewall(EvidenceVault.get(this)).ingest(
            "ACCESSIBILITY_EVENT",
            packageName,
            eventSourceUid(packageName),
            "text/plain",
            content
        )
    }

    override fun onInterrupt() = Unit

    private fun eventSourceUid(packageName: String): Int? = runCatching {
        packageManager.getApplicationInfo(packageName, 0).uid
    }.getOrNull()

    private fun appendNodeTree(node: AccessibilityNodeInfo?, out: StringBuilder, depth: Int, remaining: Int): Int {
        if (node == null || depth > 20 || remaining <= 0) return 0
        var used = 1
        val indent = "  ".repeat(depth)
        out.append(indent)
            .append(node.className ?: "")
            .append(" text=").append(node.text ?: "")
            .append(" desc=").append(node.contentDescription ?: "")
            .append(" clickable=").append(node.isClickable)
            .append(" editable=").append(node.isEditable)
            .append('\n')

        for (i in 0 until node.childCount) {
            if (used >= remaining) break
            used += appendNodeTree(node.getChild(i), out, depth + 1, remaining - used)
        }
        return used
    }
}
