package io.agents.anima.understand

import io.agents.anima.core.ElementRole
import io.agents.anima.core.InputSpec
import io.agents.anima.core.InputType
import io.agents.anima.core.Journey
import io.agents.anima.core.JourneyStep
import io.agents.anima.core.ScanContext
import io.agents.anima.core.Screen
import io.agents.anima.core.ScreenKind
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.ScreenProfile
import io.agents.anima.core.ScreenUnderstander
import io.agents.anima.core.ToneOfVoice
import io.agents.anima.engine.PrunedNode

/**
 * The floor: no model, no network, always answers, fully deterministic.
 *
 * Exists so a scan never dies because a model is absent. Quality is honest but
 * modest -- it is not tuned to any app, which is deliberate. Every rule here is
 * class- or vocabulary-driven, and the vocabulary is the app's own ids/hints,
 * so the output is stable across rescans of the same app version.
 */
class HeuristicUnderstander : ScreenUnderstander {

    override fun describe(observation: ScreenObservation, screenId: String, context: ScanContext): ScreenProfile {
        val kind = kindOf(observation)
        val inputs = LinkedHashMap<Int, InputSpec>()
        val semantics = LinkedHashMap<Int, String>()
        var primaryLabel = ""
        for (n in observation.nodes) {
            val label = labelOf(n)
            if (label.isNotBlank() && primaryLabel.isBlank()) primaryLabel = label
            semantics[n.id] = semanticOf(n)
            if (Roles.isInputLike(n)) inputs[n.id] = inputSpecOf(n)
        }
        val name = nameOf(observation, kind, primaryLabel)
        return ScreenProfile(
            name = name,
            purpose = purposeOf(kind, primaryLabel),
            kind = kind,
            elementSemantics = semantics,
            inputSpecs = inputs,
        )
    }

    override fun describeJourney(path: List<JourneyStep>, screens: List<Screen>, context: ScanContext): Journey {
        val id = JourneyLanguage.journeyId(path)
        val name = JourneyLanguage.name(path, screens, context)
        val goal = JourneyLanguage.goal(screens, context)
        return Journey(
            id = id,
            name = name,
            goal = goal,
            preconditions = emptyList(),
            steps = path,
            outcome = path.lastOrNull()?.screen,
            replayable = false,
            verifiedAtScan = null,
        )
    }

    override fun toneOfVoice(copy: List<String>, context: ScanContext): ToneOfVoice {
        val register = JourneyLanguage.classifyRegister(copy)
        val examples = copy.filter { it.isNotBlank() }.distinct().take(3).map { truncate(it, 80) }
        return ToneOfVoice(
            register = register,
            summary = "Short, $register copy typical of ${context.appLabel}.",
            examples = examples,
        )
    }

    // -- screen -------------------------------------------------------------------

    internal fun kindOf(o: ScreenObservation): ScreenKind {
        val hay = buildList {
            o.activity?.let { add(slug(it)) }
            add(o.packageName.lowercase())
        }.joinToString(" ")
        if (Regex("""\b(login|sign in|sign up|registration|authenticate|otp|verification|2fa|password)\b""").containsMatchIn(hay)) return ScreenKind.AUTH
        if (Regex("""\b(settings|preferences|config)\b""").containsMatchIn(hay)) return ScreenKind.SETTINGS
        if (Regex("""\b(onboarding|welcome|intro|tour|walkthrough|permission)\b""").containsMatchIn(hay)) return ScreenKind.ONBOARDING

        val inputs = o.nodes.count { Roles.isInputLike(it) }
        if (inputs >= 3) return ScreenKind.FORM
        if (inputs == 2 && o.nodes.any { Roles.roleOf(it) == ElementRole.TOGGLE }) return ScreenKind.FORM
        if (inputs >= 1 && o.nodes.any { Roles.roleOf(it) == ElementRole.BUTTON }) return ScreenKind.FORM

        if (o.nodes.any { it.className.lowercase().contains("recycler") || it.className.lowercase().contains("listview") }) return ScreenKind.LIST
        if (o.nodes.any { it.className.lowercase().contains("dialog") } && o.nodes.size <= 8) return ScreenKind.DIALOG
        return ScreenKind.OTHER
    }

    internal fun nameOf(o: ScreenObservation, kind: ScreenKind, primaryLabel: String): String {
        o.activity?.let { activity ->
            val base = activity.substringAfterLast('.')
            val cleaned = base
                .removeSuffix("Activity")
                .removeSuffix("Screen")
                .removeSuffix("Fragment")
                .removeSuffix("Page")
                .trim()
            if (cleaned.isNotEmpty()) return prettify(cleaned)
        }
        if (primaryLabel.isNotEmpty()) return prettify(primaryLabel)
        return when (kind) {
            ScreenKind.AUTH -> "Sign in"
            ScreenKind.FORM -> "Form"
            ScreenKind.LIST -> "List"
            ScreenKind.DIALOG -> "Dialog"
            ScreenKind.SETTINGS -> "Settings"
            ScreenKind.ONBOARDING -> "Welcome"
            else -> "Screen"
        }
    }

    internal fun purposeOf(kind: ScreenKind, primaryLabel: String): String {
        return when (kind) {
            ScreenKind.AUTH -> "Asks the user to authenticate and start a session."
            ScreenKind.FORM -> "Collects input to submit."
            ScreenKind.LIST -> "Shows a scrollable list to pick from."
            ScreenKind.DIALOG -> "Asks a short question or confirms a choice."
            ScreenKind.SETTINGS -> "Lets the user change app or device preferences."
            ScreenKind.ONBOARDING -> "Introduces the app and its permissions before use."
            ScreenKind.DETAIL -> "Shows details of one item."
            ScreenKind.OTHER -> if (primaryLabel.isNotBlank())
                "The \"$primaryLabel\" screen." else "A screen of the app."
        }
    }

    // -- elements -----------------------------------------------------------------

    internal fun labelOf(n: PrunedNode): String {
        val text = n.text?.trim()
        if (!text.isNullOrEmpty()) return text
        val desc = n.contentDesc?.trim()
        if (!desc.isNullOrEmpty()) return desc
        return n.resourceId?.substringAfterLast('/')?.replace('_', ' ') ?: ""
    }

    internal fun semanticOf(n: PrunedNode): String {
        val role = Roles.roleOf(n)
        val label = labelOf(n)
        return when (role) {
            ElementRole.BUTTON -> if (label.isNotBlank()) "Taps \"$label\"." else "A tappable button."
            ElementRole.INPUT -> "A field for ${if (label.isNotBlank()) "\"$label\"" else "text entry"}."
            ElementRole.TOGGLE -> "Toggles ${if (label.isNotBlank()) "\"$label\"" else "a switch"}."
            ElementRole.LINK -> "Opens a link."
            ElementRole.TEXT -> if (label.isNotBlank()) "Shows \"${truncate(label, 60)}\"." else "Static text."
            ElementRole.IMAGE -> "An image."
            ElementRole.LIST -> "A scrollable list."
            ElementRole.LIST_ITEM -> if (label.isNotBlank()) "An item \"$label\"." else "A list item."
            ElementRole.TAB -> "A tab."
            ElementRole.NAV -> "Navigation."
        }
    }

    internal fun inputSpecOf(n: PrunedNode): InputSpec {
        val label = labelOf(n)
        val hint = label.takeUnless { it.isBlank() }
        return InputSpec(
            type = Roles.inputTypeOf(n),
            required = label.contains("*") || label.contains("required", ignoreCase = true),
            hint = hint,
            maxLength = null,
        )
    }

    private fun prettify(s: String): String {
        val spaced = s.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        return spaced.replaceFirstChar { it.uppercaseChar() }
    }

    /** `com.x.LoginActivity` -> `login activity`, splitting camelCase so \b vocab matches. */
    private fun slug(s: String): String {
        val base = s.substringAfterLast('.').trim()
        return base
            .removeSuffix("Activity").removeSuffix("Screen").removeSuffix("Fragment").removeSuffix("Page")
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .lowercase()
    }

    private fun truncate(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max - 1) + "…"
}

/** Pure, unit-testable journey/tone helpers shared by heuristic + hybrid paths. */
object JourneyLanguage {

    fun journeyId(path: List<JourneyStep>): String =
        "jr_" + Sha.sha256(path.joinToString("|") { "${it.screen}:${it.element}:${it.action.wire}" }).take(8)

    fun name(path: List<JourneyStep>, screens: List<Screen>, context: ScanContext): String {
        val last = path.lastOrNull() ?: return "Open ${context.appLabel}"
        val lastId = last.screen
        val lastScreen = screens.firstOrNull { it.id == lastId }
        val firstScreen = screens.firstOrNull()
        if (screens.size >= 2 && firstScreen != null && lastScreen != null) {
            val a = firstScreen.name ?: "Start"
            val b = lastScreen.name ?: "End"
            if (a != b) return "$a → $b"
        }
        return lastScreen?.name ?: "Journey in ${context.appLabel}"
    }

    fun goal(screens: List<Screen>, context: ScanContext): String {
        screens.lastOrNull()?.purpose?.takeIf { it.isNotBlank() }?.let {
            return it.take(160)
        }
        return "Reach the end of the flow in ${context.appLabel}."
    }

    /** Classifies copy into one of the four registers the schema declares. */
    fun classifyRegister(copy: List<String>): String {
        var exclamations = 0
        var formal = 0
        var casual = 0
        var terse = 0
        var total = 0
        for (line in copy) {
            val s = line.trim()
            if (s.isEmpty()) continue
            total++
            if ('!' in s) exclamations++
            if (Regex("""(?i)\b(please|kindly|thank you|do not hesitate|we are (glad|happy)|view details|terms & conditions)\b""").containsMatchIn(s)) formal++
            if (Regex("""(?i)\b(yo|hey|awesome|great job|gotta|wanna|thx|np|haha|nice one|cool)\b""").containsMatchIn(s)) casual++
            if (s.split(Regex("""\s+""")).size <= 4) terse++
        }
        if (total == 0) return "formal"
        return when {
            exclamations.toDouble() / total > 0.3 -> "playful"
            casual > formal -> "friendly"
            formal > casual -> "formal"
            terse.toDouble() / total >= 0.6 && formal == 0 && casual == 0 -> "terse"
            else -> "friendly"
        }
    }
}

/** SHA-256 hex, stdlib-only so it behaves identically on JVM and device. */
object Sha {
    fun sha256(s: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}