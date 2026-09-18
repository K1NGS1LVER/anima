package io.agents.anima.explore

import io.agents.anima.engine.HeuristicPlanner
import io.agents.anima.engine.PrunedNode

/**
 * What an unattended crawler is never allowed to touch.
 *
 * This is the real risk in the project. Everything else fails by producing a
 * worse pack; this fails by moving somebody's money, deleting their data, or
 * signing them out of the app we are supposed to be mapping. An agent that
 * fails visibly is fine. An agent that quietly does something irreversible that
 * nobody asked for is not, and it is the one outcome we cannot apologise our
 * way out of in front of judges.
 *
 * Deny-list first because it is auditable and costs nothing. Model-assisted
 * classification is a second layer, not a replacement: a model that is wrong
 * once in two hundred calls is wrong several times per scan.
 */
object SafetyEnvelope {

    /**
     * Labels the crawler refuses to tap.
     *
     * Matched against the label with punctuation and spacing stripped -- the
     * same normalization the planner learned the hard way on a real device,
     * where `"wifi" !in "Wi-Fi"` broke every match. So "Log out", "log-out" and
     * "LOGOUT" are one entry here.
     *
     * Note what is deliberately absent: Continue, Next, Submit, Sign in, Verify,
     * OK. Those are how you get through a login or OTP gate, which the brief
     * explicitly asks the crawler to traverse. The line is irreversibility, not
     * forward motion.
     */
    val DESTRUCTIVE: Set<String> = setOf(
        // Money
        "pay", "paynow", "buy", "buynow", "purchase", "checkout", "placeorder",
        "send", "sendmoney", "transfer", "transfernow", "withdraw", "topup",
        "subscribe", "upgradeplan", "addcard", "confirmpayment", "confirmtransfer",
        // Destruction
        "delete", "deleteaccount", "deleteall", "remove", "removeall", "erase",
        "clearall", "cleardata", "reset", "factoryreset", "format", "uninstall",
        "deactivate", "closeaccount", "cancelsubscription", "cancelorder",
        // Losing the session, which ends the scan and may need a human to undo
        "logout", "signout", "logoff", "switchaccount", "forgetdevice",
        // Reaching the outside world
        "call", "callnow", "dial", "sos", "emergency", "emergencycall",
        "report", "reportuser", "block", "blockuser", "share", "sharevia",
    )

    /**
     * Substrings that condemn a label even inside a longer sentence, because
     * these phrasings are only ever attached to the irreversible thing.
     */
    val DESTRUCTIVE_FRAGMENTS: List<String> = listOf(
        "deleteaccount", "closeaccount", "confirmpayment", "confirmtransfer",
        "permanentlydelete", "cannotbeundone", "logout", "signout",
    )

    /**
     * Packages the crawler may legitimately pass through without being
     * considered lost: the permission controller and installer own the consent
     * dialogs that appear over any app on first launch.
     */
    val TRANSIENT_SYSTEM_PACKAGES: Set<String> = setOf(
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.android.systemui",
    )

    /** True when tapping [node] could do something the user cannot undo. */
    fun isDestructive(node: PrunedNode): Boolean {
        val labels = listOfNotNull(node.text, node.contentDesc, node.resourceId?.substringAfterLast('/'))
        return labels.any { isDestructiveLabel(it) }
    }

    fun isDestructiveLabel(label: String?): Boolean {
        val flat = HeuristicPlanner.flatten(label)
        if (flat.isEmpty()) return false
        if (flat in DESTRUCTIVE) return true
        return DESTRUCTIVE_FRAGMENTS.any { flat.contains(it) }
    }

    /**
     * Where the crawler is relative to where it should be.
     *
     * A system consent dialog is not "lost" -- it belongs to the target app's
     * first launch and dismissing it is part of the scan. The launcher, a
     * browser, or another app entirely is lost, and the recovery ladder runs.
     */
    fun locate(currentPackage: String?, targetPackage: String): Location = when {
        currentPackage == null -> Location.UNREADABLE
        currentPackage == targetPackage -> Location.ON_TARGET
        currentPackage in TRANSIENT_SYSTEM_PACKAGES -> Location.SYSTEM_DIALOG
        else -> Location.OFF_TARGET
    }

    enum class Location { ON_TARGET, SYSTEM_DIALOG, OFF_TARGET, UNREADABLE }
}
