package io.agents.anima.engine

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Layer 1 deterministic fast-path (`dev_plan.md` §2).
 *
 * A GUI agent can only act on what is in front of it. Asking it to "turn off
 * bluetooth" while Anima's own screen is in the foreground is not a planning
 * problem -- there is simply no Bluetooth toggle to find. Rather than reason
 * about that, route the obvious cases deterministically: launch the system
 * screen that owns the setting, then let the perception loop take over.
 *
 * This is the "prioritise system intents before probabilistic vision" idea the
 * plan credits to ClawMobile, and it costs zero LLM calls.
 */
object IntentRouter {

    const val TAG = "AnimaRouter"

    /** A system screen that can service a goal, with a human-readable name. */
    data class Destination(val action: String, val label: String)

    private data class Route(val keywords: List<String>, val destination: Destination)

    // Keywords are matched against the punctuation-stripped goal, so "wi-fi",
    // "Wi‑Fi" and "wifi" all land on the same route.
    private val routes = listOf(
        Route(listOf("wifi", "wireless"), Destination(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi settings")),
        Route(listOf("bluetooth"), Destination(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth settings")),
        Route(listOf("airplane", "flightmode"), Destination(Settings.ACTION_AIRPLANE_MODE_SETTINGS, "Airplane mode settings")),
        Route(listOf("nfc"), Destination(Settings.ACTION_NFC_SETTINGS, "NFC settings")),
        Route(listOf("location", "gps"), Destination(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Location settings")),
        Route(listOf("brightness", "display", "darkmode"), Destination(Settings.ACTION_DISPLAY_SETTINGS, "Display settings")),
        Route(listOf("sound", "volume", "ringtone", "donotdisturb"), Destination(Settings.ACTION_SOUND_SETTINGS, "Sound settings")),
        Route(listOf("battery", "powersaver"), Destination(Settings.ACTION_BATTERY_SAVER_SETTINGS, "Battery saver settings")),
        Route(listOf("mobiledata", "cellular", "datausage"), Destination(Settings.ACTION_DATA_USAGE_SETTINGS, "Data usage settings")),
        Route(listOf("storage"), Destination(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "Storage settings")),
        Route(listOf("accessibility"), Destination(Settings.ACTION_ACCESSIBILITY_SETTINGS, "Accessibility settings")),
    )

    /**
     * The system screen this goal needs, or null when the goal is about an app
     * Anima cannot route to -- in which case the agent works with whatever the
     * user already has on screen, as before.
     */
    fun destinationFor(goal: String): Destination? {
        val flat = HeuristicPlanner.flatten(goal)
        if (flat.isEmpty()) return null
        return routes.firstOrNull { route -> route.keywords.any { flat.contains(it) } }?.destination
    }

    /**
     * @return true when the screen was actually launched.
     *
     * CLEAR_TOP matters more than it looks: Settings is a single task, so
     * running "turn off bluetooth" and then "toggle wifi" back to back would
     * otherwise just re-show the Bluetooth page that is already on top, and the
     * agent would be asked to find a Wi-Fi toggle on the Bluetooth screen.
     */
    fun launch(context: Context, destination: Destination): Boolean = try {
        context.startActivity(
            Intent(destination.action).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
        true
    } catch (e: Exception) {
        // Not every OEM ships every settings screen; fall back to acting on
        // whatever is currently in front of the agent.
        Log.w(TAG, "No activity for ${destination.action}", e)
        false
    }
}
