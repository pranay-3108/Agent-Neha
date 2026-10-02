package com.sentineldroid

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView

class SecurityCenterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 40)
            setBackgroundColor(Color.rgb(9, 14, 18))
        }
        root.addView(title("Agent Neha 0.6 Security Center", 24f))
        root.addView(text("Deterministic posture view. AI explanations can describe these records but cannot rewrite them."))

        val packages = PackageCollector(this).scan(false)
        val boot = PackageCollector(this).bootReceiverPackages()
        val facts = AccessCollector(this).buildFacts(packages, boot)
        val health = TelemetryHealth.read(this)
        root.addView(section("Protection health"))
        root.addView(text("Notifications: ${on(health.notifications)}\nNotification access: ${on(health.notificationAccess)}\nAccessibility: ${on(health.accessibility)}\nUsage access: ${on(health.usageAccess)}\nBackground monitoring: ${on(health.monitoring)}"))
        root.addView(section("Blind spots"))
        root.addView(text(health.blindSpots().ifEmpty { listOf("None reported") }.joinToString("\n")))

        root.addView(section("Applications"))
        packages.take(120).forEach { pkg ->
            val appFacts = facts.filter { it.packageName == pkg.packageName }
            val zone = CapabilityRiskClassifier.classify(appFacts)
            val score = TrustScore.calculate(this, pkg, facts).score
            if (zone != RiskZone.GREEN || score < 90) {
                val source = InstallSourceClassifier.classify(pkg).name
                val watched = WatchdogRegistry.isWatched(this, pkg.packageName)
                root.addView(text("${pkg.appLabel}\n${pkg.packageName}\nZone: ${zone.name}   Trust: $score/100\nSource: $source   Watchdog: ${if (watched) "ACTIVE" else "off"}\nScars: ${countScars(pkg.packageName)}"))
            }
        }
        root.addView(section("Forensics"))
        root.addView(text("A forensic snapshot is a signed JSON record in the Evidence Vault. Select an app from the main review flow to create one."))
        root.addView(section("Self-defense audit"))
        root.addView(text(SelfDefense.audit(this).ifEmpty { listOf("No self-defense issues observed.") }.joinToString("\n")))

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun countScars(packageName: String): Int = EvidenceVault.get(this).latest(500).count { it["source"] == "CAPABILITY_MONITOR" && it["package"] == packageName }
    private fun on(v: Boolean) = if (v) "ON" else "OFF"
    private fun title(s: String, size: Float) = TextView(this).apply { text=s; textSize=size; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setPadding(0,0,0,8) }
    private fun section(s: String) = TextView(this).apply { text=s; textSize=15f; typeface=Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(56,220,180)); setPadding(0,24,0,6) }
    private fun text(s: String) = TextView(this).apply { text=s; textSize=13f; setTextColor(Color.rgb(205,214,218)); setPadding(0,4,0,8) }
}
