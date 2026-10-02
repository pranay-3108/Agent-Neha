package com.sentineldroid

import android.content.Context
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Small deterministic conversation layer.
 *
 * Natural language is used to select a safe user-facing operation. It never
 * authorizes intervention or turns application content into instructions.
 */
object NehaConversationEngine {

    enum class Command {
        SCAN,
        CONDITIONS,
        RECENT_ACTIVITY,
        SETUP,
        HELP,
        CHAT
    }

    fun commandFor(input: String): Command {
        val text = input.trim().lowercase(Locale.getDefault())

        return when {
            text.contains("scan") || text.contains("check my phone") || text.contains("check device") -> Command.SCAN
            text.contains("current condition") || text.contains("status") || text.contains("how is my phone") -> Command.CONDITIONS
            text.contains("what changed") || text.contains("recent activity") || text.contains("recent alert") -> Command.RECENT_ACTIVITY
            text.contains("setup") || text.contains("protect my phone") || text.contains("secure my phone") -> Command.SETUP
            text.contains("what can you do") || text.contains("help") -> Command.HELP
            else -> Command.CHAT
        }
    }

    fun reply(context: Context, input: String): String {
        val text = input.trim()
        val lower = text.lowercase(Locale.getDefault())

        return when {
            isGreeting(lower) -> {
                "Hi, I'm Neha. Ask me to check the current conditions, run a full scan, explain a recent alert, or help you set up protection."
            }
            lower.contains("how are you") || lower.contains("how r u") -> {
                "I'm ready. Give me a security task and I'll handle the safe analysis workflow for you."
            }
            lower.contains("who are you") || lower.contains("your name") -> {
                "I'm Agent Neha, the conversational assistant inside SentinelDroid. I collect and explain security results, while Android and SentinelDroid's deterministic policy remain in control of security actions."
            }
            lower.contains("accessibility") -> {
                "Accessibility is a high-impact Android capability. I treat it as one signal in a capability combination, not automatic proof that an app is malicious."
            }
            lower.contains("permission") || lower.contains("permissions") -> {
                "A permission alone does not tell the whole story. I look at combinations, persistence, install source, changes over time, and the evidence SentinelDroid has actually collected."
            }
            lower.contains("safe") -> {
                "I won't guess. I'll separate confirmed evidence, inferred relationships, and things we cannot currently observe."
            }
            lower.contains("thank") -> {
                "You're welcome."
            }
            else -> {
                "I can handle the security side for you. Try saying: \"Neha, tell me the current conditions\" or \"Neha, scan my device.\""
            }
        }
    }

    fun currentConditions(context: Context): String {
        val health = TelemetryHealth.read(context)
        val prefs = context.getSharedPreferences("sentinel_alerts", Context.MODE_PRIVATE)
        val pending = prefs.getInt("pending_count", 0)
        val scanPrefs = context.getSharedPreferences("sentinel_scan", Context.MODE_PRIVATE)
        val lastScan = scanPrefs.getLong("last_scan_at", 0L)
        val apps = scanPrefs.getInt("last_app_count", 0)
        val evidence = EvidenceVault.get(context).count()

        val scanLine = if (lastScan == 0L) {
            "No full scan has been recorded yet."
        } else {
            "Last full scan: ${formatTime(lastScan)} • $apps apps checked"
        }

        val protection = when {
            health.monitoring && health.notifications -> "Protection is running normally."
            health.monitoring -> "Background monitoring is on, but security notifications are unavailable."
            else -> "Background monitoring is off."
        }

        val blindSpots = health.blindSpots().size
        val blindSpotLine = if (blindSpots == 0) {
            "Telemetry: all configured sources are available."
        } else {
            "Telemetry: $blindSpots source${if (blindSpots == 1) "" else "s"} unavailable or limited."
        }

        return buildString {
            append("Current conditions\n")
            append("• $protection\n")
            append("• $scanLine\n")
            append("• Pending alerts: $pending\n")
            append("• Stored evidence: $evidence record${if (evidence == 1) "" else "s"}\n")
            append("• $blindSpotLine")
        }
    }

    fun recentActivity(context: Context): String {
        val vault = EvidenceVault.get(context)
        val rows = vault.latest(8)
        if (rows.isEmpty()) return "I don't have recent security evidence yet."

        val lines = rows.mapNotNull { row ->
            val source = row["source"].orEmpty()
            val pkg = row["package"].orEmpty()
            val event = row["type"].orEmpty()
            when {
                source.isNotBlank() && pkg.isNotBlank() -> "• $event — $pkg — $source"
                event.isNotBlank() -> "• $event"
                else -> null
            }
        }

        return if (lines.isEmpty()) {
            "Recent evidence is present, but there are no user-friendly event summaries to show yet."
        } else {
            "Recent security activity\n${lines.joinToString("\n")}"
        }
    }

    fun setupGuide(context: Context): String {
        val health = TelemetryHealth.read(context)
        val next = when {
            !health.notifications -> "First, enable Notifications so Neha can warn you about important changes."
            !health.usageAccess -> "Next, consider enabling Usage access for better app-activity telemetry."
            !health.accessibility -> "Accessibility telemetry is optional. Enable it only when you want UI-level security evidence."
            !health.notificationAccess -> "Notification access is optional. Enable it when notification telemetry is useful for your investigation."
            !health.monitoring -> "Finally, turn on continuous monitoring so Neha can keep watching in the background."
            else -> "Your main protection sources are enabled. Neha is ready for a fresh scan."
        }
        return "Protection setup\n• Notifications: ${yesNo(health.notifications)}\n• Usage access: ${yesNo(health.usageAccess)}\n• Accessibility telemetry: ${yesNo(health.accessibility)}\n• Notification telemetry: ${yesNo(health.notificationAccess)}\n• Continuous monitoring: ${yesNo(health.monitoring)}\n\n$next"
    }

    private fun yesNo(value: Boolean) = if (value) "ready" else "not enabled"

    private fun formatTime(time: Long): String {
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(time))
    }

    private fun isGreeting(text: String): Boolean {
        return text == "hi" || text == "hello" || text == "hey" ||
            text.startsWith("hi ") || text.startsWith("hello ") || text.startsWith("hey ")
    }
}
