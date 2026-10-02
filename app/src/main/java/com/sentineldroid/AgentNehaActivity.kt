package com.sentineldroid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.drawable.GradientDrawable

/**
 * Agent-first security screen.
 *
 * The user talks to Neha instead of navigating a large settings dashboard.
 * Safe operations such as scanning and reading telemetry are launched from
 * the same conversation path; security intervention remains outside this UI.
 */
class AgentNehaActivity : Activity(), NehaVoiceController.Callbacks {
    private lateinit var messagesBox: LinearLayout
    private lateinit var input: EditText
    private lateinit var micButton: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var voice: NehaVoiceController

    private var scanRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = NehaVoiceController(this, this)
        buildUi()
        addNeha(
            "Hi, I'm Neha. Just tell me what you need. " +
                "I can check your current conditions, run a full scan, explain recent activity, or guide setup."
        )

        intent.getStringExtra("neha_prompt")?.let { prompt ->
            window.decorView.post { runCommand(prompt) }
        }
    }

    override fun onDestroy() {
        voice.destroy()
        super.onDestroy()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.sentinel_bg))
        }

        root.addView(buildHeader(), lp())
        root.addView(buildAgentCard(), lp())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }
        messagesBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(16))
        }
        scroll.addView(messagesBox)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(buildComposer(), lp())
        setContentView(root)
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(8))
        }

        header.addView(TextView(this).apply {
            text = "N"
            textSize = 22f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.sentinel_bg))
            background = rounded(color(R.color.sentinel_primary), dp(18))
        }, LinearLayout.LayoutParams(dp(46), dp(46)))

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        title.addView(TextView(this).apply {
            text = "Neha Assistant"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.sentinel_text))
        }, lp())
        title.addView(TextView(this).apply {
            text = "SentinelDroid • ready"
            textSize = 12f
            setTextColor(color(R.color.sentinel_primary))
        }, lp())
        header.addView(title, lp(1f, 0, ViewGroup.LayoutParams.WRAP_CONTENT))

        header.addView(smallButton("Close") { finish() })
        return header
    }

    private fun buildAgentCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = rounded(color(R.color.sentinel_surface), dp(22))
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }

        card.addView(TextView(this).apply {
            text = "ASK NEHA — SHE HANDLES THE WORKFLOW"
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            setTextColor(color(R.color.sentinel_primary))
        }, lp())

        statusText = TextView(this).apply {
            text = "Tap the microphone or use a quick action"
            textSize = 12.5f
            gravity = Gravity.CENTER
            setTextColor(color(R.color.sentinel_muted))
            setPadding(0, dp(3), 0, dp(12))
        }
        card.addView(statusText, lp())

        micButton = Button(this).apply {
            text = "🎙"
            textSize = 25f
            isAllCaps = false
            contentDescription = "Speak to Agent Neha"
            setTextColor(color(R.color.sentinel_bg))
            background = rounded(color(R.color.sentinel_primary), dp(44))
            setOnClickListener { toggleListening() }
        }
        card.addView(micButton, LinearLayout.LayoutParams(dp(92), dp(92)))

        progressBar = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        card.addView(progressBar, lp())

        val quickActions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
        }

        quickActions.addView(actionRow("Current conditions", "How is my device right now?", "Check") {
            runCommand("Neha, tell me the current conditions")
        }, lp())
        quickActions.addView(actionRow("Full device scan", "Check apps, capabilities and evidence", "Scan") {
            runCommand("Neha, scan my device")
        }, lp())
        quickActions.addView(actionRow("What changed?", "Review recent security activity", "Review") {
            runCommand("Neha, what changed recently?")
        }, lp())
        quickActions.addView(actionRow("Protect my device", "See only the setup steps that matter", "Guide") {
            runCommand("Neha, help me protect my phone")
        }, lp())

        card.addView(quickActions)
        return card
    }

    private fun buildComposer(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.sentinel_surface), dp(20))
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }

        input = EditText(this).apply {
            hint = "Ask Neha anything…"
            textSize = 15f
            setSingleLine(true)
            setTextColor(color(R.color.sentinel_text))
            setHintTextColor(color(R.color.sentinel_muted))
            background = rounded(color(R.color.sentinel_surface_2), dp(14))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnEditorActionListener { _, _, _ ->
                sendText()
                true
            }
        }
        box.addView(input, lp(1f, 0, ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(smallButton("Send") { sendText() }, LinearLayout.LayoutParams(dp(74), dp(48)).apply {
            leftMargin = dp(8)
        })

        val more = smallButton("More") {
            startActivity(Intent(this, SecurityCenterActivity::class.java))
        }
        box.addView(more, LinearLayout.LayoutParams(dp(72), dp(48)).apply {
            leftMargin = dp(8)
        })
        return box
    }

    private fun actionRow(title: String, description: String, actionText: String, action: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.sentinel_surface_2), dp(15))
            setPadding(dp(12), dp(8), dp(8), dp(8))
        }

        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        textBox.addView(TextView(this).apply {
            text = title
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color(R.color.sentinel_text))
        }, lp())
        textBox.addView(TextView(this).apply {
            text = description
            textSize = 11.5f
            setTextColor(color(R.color.sentinel_muted))
        }, lp())
        row.addView(textBox, lp(1f, 0, ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(smallButton(actionText) { action() })

        val params = lp()
        params.bottomMargin = dp(7)
        row.layoutParams = params
        return row
    }

    private fun sendText() {
        val text = input.text.toString().trim()
        if (text.isBlank()) return
        input.text.clear()
        runCommand(text)
    }

    private fun runCommand(text: String) {
        addUser(text)
        when (NehaConversationEngine.commandFor(text)) {
            NehaConversationEngine.Command.SCAN -> startScan()
            NehaConversationEngine.Command.CONDITIONS -> speakResult(NehaConversationEngine.currentConditions(this))
            NehaConversationEngine.Command.RECENT_ACTIVITY -> speakResult(NehaConversationEngine.recentActivity(this))
            NehaConversationEngine.Command.SETUP -> speakResult(NehaConversationEngine.setupGuide(this))
            NehaConversationEngine.Command.HELP,
            NehaConversationEngine.Command.CHAT -> speakResult(NehaConversationEngine.reply(this, text))
        }
    }

    private fun startScan() {
        if (scanRunning) {
            speakResult("I'm already scanning the device. I'll post the results here when the checks finish.")
            return
        }

        scanRunning = true
        progressBar.visibility = View.VISIBLE
        micButton.isEnabled = false
        statusText.text = "Neha is working…"
        addNeha("I'm on it. I'll collect the platform evidence, compare capabilities, check recent changes, and then summarize the result here.")

        NehaScanController(this).start(object : NehaScanController.Callback {
            override fun onStep(text: String) {
                runOnUiThread {
                    statusText.text = text
                }
            }

            override fun onFinished(result: NehaScanController.Result) {
                runOnUiThread {
                    scanRunning = false
                    progressBar.visibility = View.GONE
                    micButton.isEnabled = true
                    statusText.text = "Scan complete"
                    val report = buildScanReport(result)
                    addNeha(report)
                    voice.speak(report)
                }
            }

            override fun onFailed(error: Throwable) {
                runOnUiThread {
                    scanRunning = false
                    progressBar.visibility = View.GONE
                    micButton.isEnabled = true
                    statusText.text = "Scan stopped safely"
                    val message = "I couldn't finish the scan. ${error.message ?: "Android returned an unknown error."}"
                    addNeha(message)
                    voice.speak(message)
                }
            }
        })
    }

    private fun buildScanReport(result: NehaScanController.Result): String {
        val health = result.telemetry
        val blindSpots = health.blindSpots().size
        val riskLine = "Green ${result.green} • Yellow ${result.yellow} • Orange ${result.orange} • Red ${result.red}"
        val protection = if (health.monitoring) "background monitoring is on" else "background monitoring is off"

        return buildString {
            append("Scan complete ✅\n")
            append("• Apps checked: ${result.appCount}\n")
            append("• Capability facts: ${result.factCount}\n")
            append("• Risk zones: $riskLine\n")
            append("• Pending alerts: ${result.pendingAlerts}\n")
            append("• Evidence records: ${result.evidenceCount}\n")
            append("• Telemetry: ${if (blindSpots == 0) "all configured sources available" else "$blindSpots source(s) limited"}\n")
            append("• Protection: $protection\n\n")
            append("I only report what the device evidence supports. Open Security Center for the detailed app-by-app view.")
        }
    }

    private fun speakResult(text: String) {
        addNeha(text)
        voice.speak(text)
    }

    private fun toggleListening() {
        if (!hasMicrophonePermission()) {
            requestMicrophonePermission()
            return
        }

        if (voice.isSpeaking()) {
            voice.stopSpeaking()
        }

        micButton.isEnabled = false
        voice.startListening()
    }

    override fun onListeningChanged(listening: Boolean) {
        runOnUiThread {
            if (!scanRunning) micButton.isEnabled = true
            micButton.text = if (listening) "■" else "🎙"
            if (!scanRunning) {
                statusText.text = if (listening) "Listening…" else "Tap the microphone or use a quick action"
            }
        }
    }

    override fun onTextRecognized(text: String) {
        runOnUiThread { runCommand(text) }
    }

    override fun onSpeechStarted() {
        runOnUiThread {
            if (!scanRunning) statusText.text = "Neha is speaking…"
        }
    }

    override fun onSpeechStopped() {
        runOnUiThread {
            if (!scanRunning) statusText.text = "Tap the microphone or use a quick action"
        }
    }

    override fun onVoiceError(message: String) {
        runOnUiThread {
            if (!scanRunning) micButton.isEnabled = true
            micButton.text = "🎙"
            statusText.text = message
        }
    }

    private fun addUser(text: String) = addBubble(text, true)

    private fun addNeha(text: String) = addBubble(text, false)

    private fun addBubble(text: String, user: Boolean) {
        val bubble = TextView(this).apply {
            this.text = text
            textSize = 14.5f
            setTextColor(color(R.color.sentinel_text))
            setPadding(dp(15), dp(11), dp(15), dp(11))
            background = rounded(
                if (user) color(R.color.sentinel_primary_dark) else color(R.color.sentinel_surface_2),
                dp(18)
            )
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (user) Gravity.END else Gravity.START
            setPadding(0, dp(2), 0, dp(6))
        }
        row.addView(bubble, LinearLayout.LayoutParams(dp(310), ViewGroup.LayoutParams.WRAP_CONTENT))
        messagesBox.addView(row)
        messagesBox.post { (messagesBox.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun hasMicrophonePermission(): Boolean {
        return Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MIC) {
            statusText.text = if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                "Microphone ready — tap the microphone"
            } else {
                "Microphone access was not granted"
            }
        }
    }

    private fun smallButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(color(R.color.sentinel_primary))
        background = rounded(color(R.color.sentinel_surface_2), dp(13))
        setPadding(dp(9), 0, dp(9), 0)
        minimumWidth = 0
        minHeight = dp(42)
        setOnClickListener { action() }
    }

    private fun rounded(fill: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
        setStroke(dp(1), color(R.color.sentinel_line))
    }

    private fun color(id: Int): Int = getColor(id)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun lp(
        weight: Float = 0f,
        width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(width, height, weight).apply {
            bottomMargin = dp(6)
        }
    }

    companion object {
        private const val REQUEST_MIC = 4101
    }
}
