package io.agents.anima

import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

/** Product surface for selecting, scanning and reading an app Knowledge Pack. */
class MainActivity : AppCompatActivity() {
    private lateinit var container: FrameLayout
    private val scanner = FakeScanController()
    private var selected: ResolveInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(R.layout.activity_main); container = findViewById(R.id.screen_container); launchIntro() }
    override fun onResume() { super.onResume(); if (::container.isInitialized) refreshPermissionState() }

    private fun launchIntro() {
        val intro = AnimaBurstView(this) { onboarding() }
        container.removeAllViews(); container.addView(intro, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); intro.start()
    }

    private fun onboarding(): Unit = screen {
        add(header("Know an app before you automate it", "ANIMA · APP CARTOGRAPHER")); add(copy("Choose an Android app and Anima will explore its safe, visible paths to build a Knowledge Pack with its screens, journeys, UI elements, and design system."), lp(12))
        add(card("Why Anima asks for access") {
            add(copy("Accessibility lets Anima understand on-screen structure and perform only the safe exploration actions needed to map the app you choose. You can pause or stop at any time."))
            add(permission("Accessibility service", "Needed to observe and safely navigate the selected app.", "Open settings") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, lp(16))
            add(permission("Display over other apps", "Keeps scan status and the abort control visible while you scan.", "Open settings") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }, lp(14))
        })
        add(primary("Choose an app") { picker() }); add(textAction("Privacy and safety") { privacy() }, lp(8))
    }

    private fun picker(): Unit = screen {
        add(top("Choose an app") { onboarding() }); add(copy("Select a launchable app. Anima stays within this package while it explores."), lp(12))
        val edit = TextInputEditText(this@MainActivity).apply { hint = "Search installed apps"; setTextColor(c(R.color.white)); setHintTextColor(c(R.color.slate_400)); contentDescription = "Search installed apps" }
        add(TextInputLayout(this@MainActivity).apply { boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE; addView(edit) }, lp(16))
        val list = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }; add(list, lp(12))
        fun renderApps(query: String) { list.removeAllViews(); val recent = getSharedPreferences("anima", MODE_PRIVATE).getString("recent", null); val apps = launchables().filter { it.loadLabel(packageManager).toString().contains(query, true) }.sortedByDescending { it.activityInfo.packageName == recent }; if (apps.isEmpty()) list.addView(copy("No matching launchable apps found.")); apps.take(30).forEach { list.addView(appRow(it)) } }
        edit.addTextChangedListener(SimpleTextWatcher { renderApps(it) }); renderApps("")
    }

    private fun privacy(): Unit = screen {
        add(top("Privacy and safety") { onboarding() })
        add(card("Your control comes first") { add(copy("Anima maps only the app you select. Its persistent scan controls let you pause or stop immediately. Stopping ends exploration and discards an unfinished pack.")) })
        add(card("What Anima records") { add(copy("A completed Knowledge Pack stores screen structure, observed journeys, extracted design tokens, and optional screenshots from the selected app. It is designed to be exported as a compact .animapack file.")) })
        add(card("Exploration limits") { add(copy("Anima stays inside the selected package and avoids destructive actions such as pay, transfer, send, delete, remove, and sign out. A biometric or lock-screen prompt is a boundary, not an action to solve.")) })
        add(primary("Back to setup") { onboarding() })
    }

    private fun scanControl(): Unit = screen {
        add(top("Scan control") { picker() }); add(card(selected?.loadLabel(packageManager)?.toString() ?: "Selected app") { add(copy("Anima maps screens and safe transitions. It never taps destructive controls, leaves your selected app, or keeps working after you stop it.")) })
        val screens = value("0", "SCREENS FOUND"); val action = copy("Ready to scan"); val elapsed = value("00:00", "ELAPSED")
        add(card("Live scan") { add(screens); add(action, lp(8)); add(elapsed, lp(8)) })
        val start = primary("Start scan") { scanner.start() }; val pause = outline("Pause scan") { scanner.pause() }; val abort = danger("Stop and discard") { scanner.stop() }
        add(start); add(pause, lp(10)); add(abort, lp(10)); add(copy("Abort is always available. Stopping returns control immediately and does not save a pack."), lp(12))
        scanner.observe(object : FakeScanController.Listener { override fun onState(state: FakeScanController.State) { screens.text = state.screens.toString(); action.text = state.action; elapsed.text = String.format(Locale.US, "%02d:%02d", state.seconds / 60, state.seconds % 60); start.text = if (state.paused) "Resume scan" else "Start scan"; start.setOnClickListener { if (state.paused) scanner.resume() else scanner.start() }; pause.isEnabled = state.running; abort.isEnabled = state.running || state.paused; if (state.complete) { start.visibility = View.GONE; pause.visibility = View.GONE; abort.text = "View Knowledge Pack"; abort.isEnabled = true; abort.setOnClickListener { viewer() } } } })
    }

    private fun viewer(): Unit = screen {
        add(top("Knowledge Pack") { scanControl() }); add(copy("This scripted preview keeps the product flow inspectable until the explorer publishes the G6 ScanController. It is not built against the placeholder golden fixture."), lp(12))
        add(card("App map") { add(copy("Pinch to zoom and drag to pan.")); add(ZoomableGraphView(this@MainActivity), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)).apply { topMargin = dp(10) }) })
        add(card("Screen profile") { val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; val shot = TextView(this@MainActivity).apply { text = "SCREENSHOT\nPREVIEW"; gravity = Gravity.CENTER; setTextColor(c(R.color.ink_on_pearl)); setBackgroundColor(c(R.color.pearl_white)); contentDescription = "Screen screenshot preview"; setPadding(dp(8), dp(28), dp(8), dp(28)) }; val detail = copy("Wi‑Fi\nSettings screen\n\nPurpose\nConnect to a network\n\nElements\n• Network list\n• Add network").apply { setPadding(dp(14), 0, 0, 0) }; row.addView(shot, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); row.addView(detail, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f)); add(row) })
        add(card("Journeys") { add(copy("Connect to Wi‑Fi\nSettings → Wi‑Fi → Choose network\nReplay status: pending verified scan")) }); add(card("Extracted design system") { add(copy("Palette · type roles · 8dp spacing · rounded controls\nTokens here will be rendered from Jiya’s extracted pack section.")) }); add(primary("Export .animapack") { Toast.makeText(this@MainActivity, "Export activates when a completed pack is available.", Toast.LENGTH_LONG).show() })
    }

    // Explicit Unit return type is required, not stylistic: every screen (onboarding,
    // picker, privacy, scanControl, viewer) is an expression-bodied `= screen { ... }`
    // function, so without an explicit type here Kotlin's inference has to look at every
    // caller to infer this function's return type while every caller is simultaneously
    // waiting on this function's type -- "recursive problem", and the build fails.
    private fun screen(block: LinearLayout.() -> Unit): Unit { container.removeAllViews(); val scroll = ScrollView(this).apply { isFillViewport = true }; val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(40)); block() }; scroll.addView(column); container.addView(scroll) }
    private fun LinearLayout.add(view: View, params: LinearLayout.LayoutParams = lp()) = addView(view, params)
    private fun lp(top: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }
    private fun header(title: String, kicker: String) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(TextView(this@MainActivity).apply { text = kicker; setTextColor(c(R.color.pearl_blue)); textSize = 11f; letterSpacing = .12f }); addView(TextView(this@MainActivity).apply { text = title; setTextColor(c(R.color.white)); textSize = 34f; setTypeface(typeface, 1); setPadding(0, dp(8), 0, 0) }) }
    private fun top(title: String, back: () -> Unit) = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; addView(outline("Back", back), LinearLayout.LayoutParams(dp(80), dp(48))); addView(TextView(this@MainActivity).apply { text = title; setTextColor(c(R.color.white)); textSize = 22f; setTypeface(typeface, 1); setPadding(dp(12), 0, 0, 0) }) }
    private fun copy(text: String) = TextView(this).apply { this.text = text; setTextColor(c(R.color.slate_300)); textSize = 15f; setLineSpacing(dp(3).toFloat(), 1f) }
    private fun value(number: String, label: String): TextView { val value = TextView(this).apply { text = number; setTextColor(c(R.color.pearl_white)); textSize = 30f; setTypeface(typeface, 1) }; return value.apply { contentDescription = label } }
    private fun card(title: String, inside: LinearLayout.() -> Unit): LinearLayout {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card); setPadding(dp(18), dp(18), dp(18), dp(18)) }
        outer.addView(TextView(this).apply { text = title; setTextColor(c(R.color.white)); textSize = 20f; setTypeface(typeface, 1) })
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, 0); inside() }
        outer.addView(inner); outer.layoutParams = lp(20); return outer
    }
    private fun permission(title: String, hint: String, action: String, click: () -> Unit) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; val text = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; addView(TextView(this@MainActivity).apply { this.text = title; setTextColor(c(R.color.white)); textSize = 16f; setTypeface(typeface, 1) }); addView(copy(hint)); addView(TextView(this@MainActivity).apply { text = "Checking status…"; tag = if (title.startsWith("Accessibility")) "accessibility-state" else "overlay-state"; setTextColor(c(R.color.pearl_blue)); textSize = 12f }) }; addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)); addView(outline(action, click)) }
    private fun appRow(info: ResolveInfo) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_card_inset); setPadding(dp(16), dp(14), dp(16), dp(14)); val label = info.loadLabel(packageManager).toString(); contentDescription = "Select $label"; isClickable = true; isFocusable = true; setOnClickListener { selected = info; getSharedPreferences("anima", MODE_PRIVATE).edit().putString("recent", info.activityInfo.packageName).apply(); scanControl() }; addView(ImageView(this@MainActivity).apply { setImageDrawable(info.loadIcon(packageManager)); contentDescription = "$label icon" }, LinearLayout.LayoutParams(dp(40), dp(40))); addView(LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0); addView(TextView(this@MainActivity).apply { text = label; setTextColor(c(R.color.white)); textSize = 17f; setTypeface(typeface, 1) }); addView(TextView(this@MainActivity).apply { text = info.activityInfo.packageName; setTextColor(c(R.color.slate_300)); textSize = 12f }) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }.also { it.layoutParams = lp(10) }
    private fun primary(text: String, click: () -> Unit) = MaterialButton(this).apply { this.text = text; minHeight = dp(52); cornerRadius = dp(14); backgroundTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.pearl_white); setTextColor(c(R.color.ink_on_pearl)); setOnClickListener { click() } }.also { it.layoutParams = lp(18) }
    private fun outline(text: String, click: () -> Unit) = MaterialButton(this).apply { this.text = text; minHeight = dp(48); cornerRadius = dp(14); strokeWidth = dp(1); strokeColor = ContextCompat.getColorStateList(this@MainActivity, R.color.pearl_white); setTextColor(c(R.color.pearl_white)); setOnClickListener { click() } }
    private fun danger(text: String, click: () -> Unit) = MaterialButton(this).apply { this.text = text; minHeight = dp(48); cornerRadius = dp(14); strokeWidth = dp(1); strokeColor = ContextCompat.getColorStateList(this@MainActivity, R.color.red_500); setTextColor(c(R.color.red_500)); setOnClickListener { click() } }
    private fun textAction(text: String, click: () -> Unit) = MaterialButton(this).apply { this.text = text; setTextColor(c(R.color.pearl_blue)); setOnClickListener { click() } }
    private fun launchables() = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    private fun refreshPermissionState() {
        val accessibilityReady = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains(packageName) == true
        container.findViewWithTag<TextView>("accessibility-state")?.text = if (accessibilityReady) "Status: ready" else "Status: needs permission"
        container.findViewWithTag<TextView>("overlay-state")?.text = if (Settings.canDrawOverlays(this)) "Status: ready" else "Status: optional — not enabled"
    }
    private fun c(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

private class SimpleTextWatcher(private val changed: (String) -> Unit) : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed(s?.toString().orEmpty()); override fun afterTextChanged(s: android.text.Editable?) = Unit }
