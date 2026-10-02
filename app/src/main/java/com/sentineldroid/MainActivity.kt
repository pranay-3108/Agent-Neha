package com.sentineldroid

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.VpnService
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import java.io.File

class MainActivity : Activity() {
    private lateinit var vault: EvidenceVault
    private lateinit var statusTitle: TextView
    private lateinit var statusText: TextView
    private lateinit var attentionText: TextView
    private lateinit var monitoringSwitch: Switch
    private lateinit var setupButton: Button
    private var packages: List<PackageSnapshot> = emptyList()
    private var facts: List<CapabilityFact> = emptyList()
    private var pendingVpnPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = EvidenceVault.get(this)
        buildUi()
        SentinelNotifications.ensureChannels(this)
        handleReviewIntent(intent)
        refreshUi()
        if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SETUP_SEEN, false)) {
            window.decorView.post { showSetupDialog(firstRun = true) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReviewIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(color(R.color.sentinel_bg))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(28))
        }
        scroll.addView(root)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_sentinel)
        }, LinearLayout.LayoutParams(dp(46), dp(46)))

        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titleBox.addView(TextView(this).apply {
            text = "SentinelDroid"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.sentinel_text))
        }, lp())
        titleBox.addView(TextView(this).apply {
            text = "Agent Neha • Android Security Agent"
            textSize = 12.5f
            setTextColor(color(R.color.sentinel_muted))
        }, lp())
        header.addView(titleBox, lp(1f, 0, ViewGroup.LayoutParams.WRAP_CONTENT))
        header.addView(smallButton("Setup") { showSetupDialog(false) })
        root.addView(header)

        root.addView(space(14))
        statusTitle = TextView(this)
        statusText = TextView(this)
        root.addView(statusCard())

        root.addView(space(14))
        root.addView(heroCard())

        root.addView(space(14))
        root.addView(attentionCard())

        root.addView(space(14))
        root.addView(monitoringCard())

        root.addView(space(14))
        root.addView(primaryButton("Open Security Center") {
            open(Intent(this, SecurityCenterActivity::class.java))
        }, lp())

        root.addView(space(4))
        root.addView(secondaryButton("More security tools") {
            showMoreToolsDialog()
        }, lp())

        root.addView(space(12))
        root.addView(TextView(this).apply {
            text = "Start here: talk to Neha. She can check conditions, run a full scan, explain recent activity and guide setup. Detailed tools stay behind Security Center so the main screen remains easy to understand."
            textSize = 11.5f
            setTextColor(color(R.color.sentinel_muted))
            setPadding(dp(4), 0, dp(4), 0)
        }, lp())

        setContentView(scroll)
    }

    private fun heroCard(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(color(R.color.sentinel_surface), dp(22))
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        box.addView(TextView(this).apply {
            text = "ONE CONVERSATION. THE AGENT HANDLES THE REST."
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.07f
            setTextColor(color(R.color.sentinel_primary))
        }, lp())
        box.addView(TextView(this).apply {
            text = "Talk to Agent Neha"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.sentinel_text))
            setPadding(0, dp(4), 0, dp(2))
        }, lp())
        box.addView(TextView(this).apply {
            text = "Ask in normal language. Neha will collect evidence, analyze it and explain the result before any user-authorized response."
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.sentinel_muted))
            setPadding(0, 0, 0, dp(14))
        }, lp())

        box.addView(primaryButton("🎙  Talk to Neha") {
            open(Intent(this, AgentNehaActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(smallButton("Scan now") { scanDevice() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(smallButton("Current status") {
            open(Intent(this, AgentNehaActivity::class.java).putExtra("neha_prompt", "current conditions"))
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        box.addView(row, lp())

        return box
    }

    private fun secondaryButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(R.color.sentinel_text))
        background = rounded(color(R.color.sentinel_surface_2), dp(16))
        minHeight = dp(50)
        setOnClickListener { action() }
    }

    private fun showMoreToolsDialog() {
        val options = arrayOf(
            "Review installed app capabilities",
            "Evidence Firewall",
            "AI-safe case preview",
            "Network isolation",
            "Emergency network lockdown",
            "Verify evidence integrity",
            "Run adversarial evidence lab",
            "Export AI-safe payload"
        )

        AlertDialog.Builder(this)
            .setTitle("More security tools")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showCapabilitySummary()
                    1 -> showEvidence()
                    2 -> previewAiPayload()
                    3 -> choosePackageForIsolation()
                    4 -> emergencyLockdown()
                    5 -> verifyEvidenceChain()
                    6 -> runAemSuite()
                    7 -> exportAiPayload()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun statusCard(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.sentinel_surface), dp(18))
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        statusTitle.apply {
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.sentinel_text))
        }
        statusText.apply {
            textSize = 13f
            setTextColor(color(R.color.sentinel_muted))
            setPadding(0, dp(6), 0, 0)
        }
        box.addView(statusTitle, lp())
        box.addView(statusText, lp())
        return box
    }

    private fun attentionCard(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(color(R.color.sentinel_surface_2), dp(16))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        attentionText = TextView(this).apply {
            textSize = 13f
            setTextColor(color(R.color.sentinel_text))
        }
        val clear = smallButton("Clear") {
            SentinelNotifications.clearPending(this)
            refreshUi()
        }
        box.addView(attentionText, lp())
        box.addView(clear, lp())
        return box
    }

    private fun monitoringCard(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.sentinel_surface), dp(16))
            setPadding(dp(16), dp(8), dp(12), dp(8))
        }
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(TextView(this).apply {
            text = "Continuous monitoring"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.sentinel_text))
        }, lp())
        textBox.addView(TextView(this).apply {
            text = "Runs in the background with a visible system notification."
            textSize = 12f
            setTextColor(color(R.color.sentinel_muted))
        }, lp())
        row.addView(textBox, lp(1f, 0, ViewGroup.LayoutParams.WRAP_CONTENT))
        monitoringSwitch = Switch(this).apply {
            setOnCheckedChangeListener { _, checked ->
                if (checked) startMonitoring() else stopMonitoring()
            }
        }
        row.addView(monitoringSwitch, LinearLayout.LayoutParams(dp(64), dp(52)))
        return row
    }

    private fun accessCard(title: String, desc: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.sentinel_surface), dp(16))
            setPadding(dp(16), dp(12), dp(12), dp(12))
        }
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.sentinel_text))
        }, lp())
        textBox.addView(TextView(this).apply {
            text = desc
            textSize = 12f
            setTextColor(color(R.color.sentinel_muted))
        }, lp())
        row.addView(textBox, lp(1f, 0, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(smallButton("Open") { action() })
        return row.apply {
            val params = lp()
            params.bottomMargin = dp(8)
            layoutParams = params
        }
    }

    private fun actionCard(title: String, desc: String, actionText: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.sentinel_surface), dp(16))
            setPadding(dp(16), dp(12), dp(12), dp(12))
        }
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(TextView(this).apply {
            text = title
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.sentinel_text))
        }, lp())
        textBox.addView(TextView(this).apply {
            text = desc
            textSize = 12f
            setTextColor(color(R.color.sentinel_muted))
        }, lp())
        row.addView(textBox, lp(1f, 0, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(smallButton(actionText) { action() })
        return row.apply {
            val params = lp()
            params.bottomMargin = dp(8)
            layoutParams = params
        }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
        setTextColor(color(R.color.sentinel_muted))
        setPadding(dp(3), 0, 0, dp(8))
    }

    private fun primaryButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(R.color.sentinel_bg))
        background = rounded(color(R.color.sentinel_primary), dp(16))
        setPadding(dp(18), dp(4), dp(18), dp(4))
        setOnClickListener { action() }
        minHeight = dp(52)
    }

    private fun smallButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 12f
        setTextColor(color(R.color.sentinel_primary))
        background = rounded(color(R.color.sentinel_surface_2), dp(14))
        setPadding(dp(12), 0, dp(12), 0)
        setOnClickListener { action() }
        minimumWidth = 0
        minHeight = dp(42)
    }

    private fun showSetupDialog(firstRun: Boolean) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        root.addView(TextView(this).apply {
            text = "Agent Neha works inside SentinelDroid and only uses the protections you explicitly choose. We never grant another app's permission for you. Android remains the authority for Accessibility, Notification access and VPN approval."
            textSize = 14f
            setTextColor(color(R.color.sentinel_text))
        }, lp())
        root.addView(space(10))
        root.addView(permissionRow("Notifications", notificationStatus(), "Enable") { requestNotificationPermission() })
        root.addView(permissionRow("Accessibility", accessibilityStatus(), "Open") { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) })
        root.addView(permissionRow("Notification access", notificationListenerStatus(), "Open") { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) })
        root.addView(permissionRow("Usage access", usageAccessStatus(), "Open") { open(Settings.ACTION_USAGE_ACCESS_SETTINGS) })
        root.addView(permissionRow("Voice chat", microphoneStatus(), "Allow") { requestMicrophonePermission() })
        root.addView(space(8))
        root.addView(TextView(this).apply {
            text = "Recommended starting point: allow Notifications so Agent Neha can alert you when an installed app gains a sensitive capability. Accessibility and Notification access are optional and should only be enabled when you want those telemetry sources."
            textSize = 12f
            setTextColor(color(R.color.sentinel_muted))
        }, lp())

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (firstRun) "Set up protection" else "Protection access")
            .setView(root)
            .setNegativeButton("Later", null)
            .setPositiveButton("Finish setup") { _, _ ->
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_SETUP_SEEN, true).apply()
                MonitorScheduler.schedule(this)
                refreshUi()
            }
            .create()
        dialog.show()
    }

    private fun permissionRow(title: String, state: String, button: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.sentinel_surface), dp(14))
            setPadding(dp(14), dp(8), dp(8), dp(8))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.sentinel_text))
        }, lp())
        texts.addView(TextView(this).apply {
            text = state
            textSize = 12f
            setTextColor(color(R.color.sentinel_muted))
        }, lp())
        row.addView(texts, lp(1f, 0, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(smallButton(button) { action() })
        val params = lp()
        params.bottomMargin = dp(8)
        row.layoutParams = params
        return row
    }

    private fun scanDevice() {
        statusTitle.text = "Agent Neha is scanning…"
        statusText.text = "Collecting package, capability and platform evidence."
        Thread {
            runCatching {
                val collector = PackageCollector(this)
                val result = collector.scan(includeApkHash = true)
                val boot = collector.bootReceiverPackages()
                packages = result
                facts = persistFacts(AccessCollector(this).buildFacts(result, boot))

                result.forEach { pkg ->
                    vault.add(
                        "PACKAGE_SNAPSHOT",
                        "ANDROID_PACKAGE_MANAGER",
                        "EVIDENCE",
                        pkg.packageName,
                        pkg.uid,
                        "application/json",
                        SnapshotJson.build(pkg),
                        "PLATFORM_DERIVED"
                    )
                }
                EntryPointCollector(this).collect(result, vault)
                InstallSessionCollector(this).collect(vault)
                IntentSurfaceCollector(this).collect(vault)
                UsageCollector(this).collectAndStore(vault)
                NetworkUsageCollector(this).collect(result, vault)
                RuntimeCollector(this).collectGlobal(vault)
                DeviceStateCollector(this).collect(vault)
                CapabilityMonitor.observeAndReconcile(
                    context = this,
                    facts = facts,
                    source = "COLD_START",
                    notifyUser = false
                )

                runOnUiThread {
                    statusTitle.text = "Agent Neha finished the scan"
                    statusText.text = "${result.size} apps checked • ${facts.size} capability facts • ${vault.count()} evidence records"
                    refreshUi()
                    toast("Scan complete")
                }
            }.onFailure { error ->
                runOnUiThread {
                    statusTitle.text = "Agent Neha stopped the scan safely"
                    statusText.text = error.message ?: "Unknown scan error"
                    toast("Scan could not complete")
                }
            }
        }.start()
    }

    private fun persistFacts(rawFacts: List<CapabilityFact>): List<CapabilityFact> {
        return rawFacts.map { fact ->
            val id = vault.add(
                "CAPABILITY_FACT",
                fact.source,
                "PLATFORM_FACT",
                fact.packageName.takeUnless { it == "<device>" },
                null,
                "application/json",
                "{\"package\":\"${escape(fact.packageName)}\",\"capability\":\"${escape(fact.capability)}\",\"state\":\"${escape(fact.state)}\",\"source\":\"${escape(fact.source)}\",\"observed_at\":${fact.observedAt}}",
                "PLATFORM_DERIVED"
            )
            fact.copy(evidenceId = id)
        }
    }

    private fun showCapabilitySummary() {
        if (packages.isEmpty()) {
            toast("Scan the device first")
            return
        }
        val text = packages.take(80).joinToString("\n\n") { pkg ->
            val assessment = DetectionEngine.assess(pkg, facts)
            val labels = assessment.labels.joinToString(", ").ifBlank { "no correlated flags" }
            "${pkg.appLabel} (${pkg.packageName})\ncorrelation=$labels\nservices=${pkg.services.size}, receivers=${pkg.receivers.size}, providers=${pkg.providers.size}, activities=${pkg.activities.size}"
        }
        showText("App capability overview", text)
    }

    private fun exportAiPayload() {
        val payload = AiPayloadBuilder.build(facts, vault.latest(500), vault.publicSigningKey())
        val file = File(filesDir, "ai_payload.json")
        file.writeText(payload)
        statusText.text = "AI-safe case exported locally to ${file.name}"
        toast("AI-safe case exported")
    }

    private fun previewAiPayload() {
        val payload = AiPayloadBuilder.build(facts, vault.latest(25), vault.publicSigningKey())
        showText("AI-safe case preview", payload.take(30_000))
    }

    private fun verifyEvidenceChain() {
        val verified = vault.verifyChain()
        statusTitle.text = if (verified) "Evidence integrity verified" else "Evidence integrity check failed"
        statusText.text = if (verified) {
            "Hashes, signatures and previous-record links match."
        } else {
            "A stored hash, signature or chain link did not match the recorded value."
        }
    }

    private fun showEvidence() {
        val lines = vault.latest(30).map {
            "${it["id"]} | ${it["source"]} | ${it["origin"]} | ${it["verification"]}\n${it["package"]}\n${it["content"]?.take(400).orEmpty()}"
        }
        showText("Recent evidence", lines.ifEmpty { listOf("No evidence yet.") }.joinToString("\n\n"))
    }

    private fun runAemSuite() {
        val result = AemSimulator(this).runSuite()
        showText("Adversarial Evidence Lab", result.joinToString("\n"))
    }

    private fun startMonitoring() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_MONITORING, true).apply()
        MonitorScheduler.schedule(this)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this, MonitorService::class.java))
            else startService(Intent(this, MonitorService::class.java))
        }.onFailure {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_MONITORING, false).apply()
            toast("Continuous monitoring could not start")
        }
        refreshUi()
    }

    private fun stopMonitoring() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_MONITORING, false).apply()
        stopService(Intent(this, MonitorService::class.java))
        MonitorScheduler.cancel(this)
        refreshUi()
    }

    private fun emergencyLockdown() {
        AlertDialog.Builder(this)
            .setTitle("Emergency data protection")
            .setMessage("Agent Neha will request Android VPN approval and then use the VPN as a blocking tunnel for other applications. No remote system is attacked, and nothing starts until you confirm.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ -> requestGlobalVpn() }
            .show()
    }

    private fun requestGlobalVpn() {
        val prep = VpnService.prepare(this)
        if (prep != null) startActivityForResult(prep, REQUEST_GLOBAL_VPN) else startGlobalIsolation()
    }

    private fun startGlobalIsolation() {
        val decision = PolicyEngine.evaluate("EMERGENCY_NETWORK_LOCKDOWN", "<all-other-apps>")
        if (!decision.allowed) {
            toast("Emergency protection denied by policy")
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(Intent(this, BlockPackageVpnService::class.java).putExtra(BlockPackageVpnService.EXTRA_GLOBAL_LOCKDOWN, true))
            } else {
                startService(Intent(this, BlockPackageVpnService::class.java).putExtra(BlockPackageVpnService.EXTRA_GLOBAL_LOCKDOWN, true))
            }
            statusTitle.text = "Emergency network protection requested"
            statusText.text = "Verify the active VPN notification before treating containment as active."
        }.onFailure { toast("Emergency protection could not start") }
    }

    private fun choosePackageForIsolation() {
        if (packages.isEmpty()) {
            toast("Scan the device first")
            return
        }
        val usable = packages.filter { it.packageName != packageName }
        val labels = usable.take(160).map { "${it.appLabel} — ${it.packageName}" }
        val list = android.widget.ListView(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, labels)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose an app to isolate")
            .setMessage("The selected app will be routed through the Agent Neha VPN containment tunnel. Android controls the final VPN authorization.")
            .setView(list)
            .setNegativeButton("Cancel", null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            requestVpnFor(usable[position].packageName)
        }
        dialog.show()
    }

    private fun requestVpnFor(targetPackage: String) {
        val prep = VpnService.prepare(this)
        if (prep != null) {
            pendingVpnPackage = targetPackage
            startActivityForResult(prep, REQUEST_VPN)
        } else {
            startIsolation(targetPackage)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_VPN) {
            val target = pendingVpnPackage
            pendingVpnPackage = null
            if (resultCode == RESULT_OK && target != null) startIsolation(target)
            else toast("VPN authorization was not granted")
        } else if (requestCode == REQUEST_GLOBAL_VPN) {
            if (resultCode == RESULT_OK) startGlobalIsolation()
            else toast("VPN authorization was not granted")
        }
    }

    private fun startIsolation(packageName: String) {
        val decision = PolicyEngine.evaluate("ISOLATE_NETWORK", packageName)
        if (!decision.allowed) {
            toast("Containment denied by policy")
            return
        }
        runCatching {
            val intent = Intent(this, BlockPackageVpnService::class.java)
                .putExtra(BlockPackageVpnService.EXTRA_TARGET_PACKAGE, packageName)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            statusTitle.text = "Network isolation requested"
            statusText.text = packageName
        }.onFailure { toast("Network isolation could not start") }
    }

    private fun stopContainment() {
        stopService(Intent(this, BlockPackageVpnService::class.java))
        toast("Containment stopped")
    }

    private fun refreshUi() {
        val monitoring = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_MONITORING, false)
        monitoringSwitch.setOnCheckedChangeListener(null)
        monitoringSwitch.isChecked = monitoring
        monitoringSwitch.setOnCheckedChangeListener { _, checked -> if (checked) startMonitoring() else stopMonitoring() }

        val alerts = SentinelNotifications.pendingCount(this)
        val last = SentinelNotifications.lastAlert(this)
        attentionText.text = when {
            alerts > 0 && last.isNotBlank() -> "$alerts pending security alert(s)\n\n$last\n\nTap an alert notification to review the affected app."
            else -> "No pending alerts. Agent Neha will notify you when a new app is installed or a monitored security capability changes."
        }

        val notif = notificationStatus()
        val accessCount = listOf(
            accessibilityEnabled(),
            notificationListenerEnabled(),
            usageAccessGranted(),
            microphoneStatus() == "Enabled"
        ).count { it }
        val setupDone = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SETUP_SEEN, false)
        val ready = notif.startsWith("Enabled") && setupDone
        statusTitle.text = if (ready) "Agent Neha is protecting your device" else "Agent Neha setup"
        statusText.text = buildString {
            append(if (ready) "Ready to monitor security-relevant changes." else "Choose the protections you want to enable.")
            append("\n\n")
            append("Apps scanned: ${packages.size}\n")
            append("Evidence records: ${vault.count()}\n")
            append("Optional access enabled: $accessCount/4\n")
            append("Voice chat microphone: ${microphoneStatus()}\n")
            append("Continuous monitoring: ${if (monitoring) "ON" else "OFF"}")
        }
        setupButton.text = if (setupDone) "Manage protection access" else "Set up protection"
    }

    private fun requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
        } else {
            toast("Microphone access is already enabled")
            refreshUi()
        }
    }

    private fun microphoneStatus(): String =
        if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "Enabled"
        } else {
            "Not enabled"
        }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else {
            toast("Notifications are already enabled or controlled by system settings")
            refreshUi()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshUi()
        if (requestCode == REQUEST_NOTIFICATIONS) {
            toast(
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    "Notifications enabled for Agent Neha"
                } else {
                    "Notifications were not enabled"
                }
            )
        } else if (requestCode == REQUEST_MICROPHONE) {
            toast(
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    "Microphone enabled for Agent Neha voice chat"
                } else {
                    "Microphone access was not enabled"
                }
            )
        }
    }

    private fun notificationStatus(): String {
        return if (SentinelNotifications.notificationsAllowed(this)) "Enabled" else "Not enabled"
    }

    private fun accessibilityStatus(): String = if (accessibilityEnabled()) "Enabled" else "Not enabled"

    private fun notificationListenerStatus(): String = if (notificationListenerEnabled()) "Enabled" else "Not enabled"

    private fun usageAccessStatus(): String = if (usageAccessGranted()) "Enabled" else "Not enabled"

    private fun accessibilityEnabled(): Boolean {
        val manager = getSystemService(android.view.accessibility.AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo?.serviceInfo?.packageName == packageName }
    }

    private fun notificationListenerEnabled(): Boolean {
        val manager = getSystemService(NotificationManager::class.java) ?: return false
        return runCatching {
            manager.isNotificationListenerAccessGranted(ComponentName(this, SentinelNotificationListener::class.java))
        }.getOrDefault(false)
    }

    private fun usageAccessGranted(): Boolean {
        val appOps = getSystemService(AppOpsManager::class.java) ?: return false
        return runCatching {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

    private fun handleReviewIntent(intent: Intent?) {
        val target = intent?.getStringExtra(EXTRA_REVIEW_PACKAGE) ?: return
        intent.removeExtra(EXTRA_REVIEW_PACKAGE)
        window.decorView.post {
            showAppReview(target)
            SentinelNotifications.clearPending(this)
            refreshUi()
        }
    }

    private fun showAppReview(packageName: String) {
        val snapshot = packages.firstOrNull { it.packageName == packageName }
            ?: PackageCollector(this).scanPackage(packageName, true)
        if (snapshot == null) {
            toast("The app is no longer installed")
            return
        }
        val boot = PackageCollector(this).bootReceiverPackages()
        val currentFacts = AccessCollector(this).buildFacts(listOf(snapshot), boot)
        val active = currentFacts.filter { it.state in setOf("ENABLED", "GRANTED", "ACTIVE", "ALLOWED", "DECLARED") }
            .joinToString("\n") { "• ${friendly(it.capability)}" }
            .ifBlank { "• No sensitive capability currently observed" }

        val message = "${snapshot.appLabel}\n${snapshot.packageName}\n\nObserved capabilities:\n$active\n\nImportant: a powerful capability is not by itself proof of malicious intent. SentinelDroid shows the evidence and lets you choose the response."
        AlertDialog.Builder(this)
            .setTitle("Review application")
            .setMessage(message)
            .setNeutralButton("Accessibility settings") { _, _ -> open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }
            .setNegativeButton("App settings") { _, _ -> openAppSettings(packageName) }
            .setPositiveButton("Isolate network") { _, _ -> requestVpnFor(packageName) }
            .show()
    }

    private fun openAppSettings(packageName: String) {
        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName")))
    }

    private fun open(action: String) {
        open(Intent(action))
    }

    private fun open(intent: Intent) {
        runCatching {
            if (intent.resolveActivity(packageManager) != null) startActivity(intent) else throw IllegalStateException("unavailable")
        }.onFailure { toast("This Android settings page is not available on this device") }
    }

    private fun showText(title: String, message: String) {
        val text = TextView(this).apply {
            this.text = message
            textSize = 13f
            setPadding(dp(24), dp(12), dp(24), dp(12))
            setTextIsSelectable(true)
            setTextColor(color(R.color.sentinel_text))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton("Close", null)
            .show()
    }

    private fun friendly(value: String): String = when (value) {
        "ACCESSIBILITY" -> "Accessibility"
        "NOTIFICATION_LISTENER" -> "Notification access"
        "DEVICE_ADMIN" -> "Device admin"
        "DEVICE_OWNER" -> "Device owner"
        "PROFILE_OWNER" -> "Profile owner"
        "USAGE_ACCESS" -> "Usage access"
        "OVERLAY_ACCESS" -> "Overlay access"
        "CAMERA_PERMISSION" -> "Camera"
        "MICROPHONE_PERMISSION" -> "Microphone"
        "LOCATION_PERMISSION" -> "Location"
        "CONTACTS_PERMISSION" -> "Contacts"
        "CALL_LOG_PERMISSION" -> "Call log"
        "SMS_PERMISSION" -> "SMS"
        "PHONE_PERMISSION" -> "Phone"
        "MEDIA_PERMISSION" -> "Media"
        "BOOT_RECEIVER" -> "Boot persistence"
        else -> value
    }

    private fun rounded(fill: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
        setStroke(dp(1), color(R.color.sentinel_line))
    }

    private fun color(id: Int): Int = getColor(id)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun lp(weight: Float = 0f, width: Int = ViewGroup.LayoutParams.MATCH_PARENT, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(width, height, weight).apply { bottomMargin = dp(8) }
    }

    private fun space(height: Int): Space = Space(this).apply { minimumHeight = height }

    private fun escape(value: String) = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_REVIEW_PACKAGE = "review_package"
        private const val REQUEST_VPN = 2001
        private const val REQUEST_GLOBAL_VPN = 2002
        private const val REQUEST_NOTIFICATIONS = 3001
        private const val REQUEST_MICROPHONE = 3002
        private const val PREFS = "sentinel_runtime"
        private const val KEY_SETUP_SEEN = "setup_seen"
        private const val KEY_MONITORING = "background_monitoring"
    }
}
