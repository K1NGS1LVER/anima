package io.agents.anima.understand

import io.agents.anima.core.ElementRole
import io.agents.anima.core.InputType
import io.agents.anima.engine.PrunedNode

/**
 * Class-based role and input classification. This is the signal that keeps the
 * "no roles and no labels" case alive: an accessibility node that exposes
 * neither a resource-id nor any text still names its own widget class
 * (`android.widget.EditText`, `android.widget.Switch`, ...), and the class is
 * exactly the role a heuristic can trust.
 */
object Roles {

    fun roleOf(n: PrunedNode): ElementRole {
        val cls = n.className.lowercase()
        return when {
            cls.contains("edittext") || cls.contains("autocompletetextview") ||
                cls.contains("textinputedittext") || cls.contains("numberpicker") -> ElementRole.INPUT
            cls.contains("switch") || cls.contains("checkbox") || cls.contains("toggle") ||
                cls.contains("radiobutton") || cls.contains("compoundbutton") -> ElementRole.TOGGLE
            cls.contains("button") || cls.contains("combobox") || cls.contains("spinner") ||
                cls.contains("fab") || cls.contains("imagebutton") || cls.contains("textbutton") -> ElementRole.BUTTON
            cls.contains("tab") -> ElementRole.TAB
            cls.contains("list") || cls.contains("recycler") || cls.contains("adapterview") -> ElementRole.LIST
            cls.contains("image") || cls.contains("drawable") -> ElementRole.IMAGE
            cls.contains("textview") || cls.contains("autosize") -> ElementRole.TEXT
            n.clickable -> ElementRole.BUTTON
            else -> ElementRole.TEXT
        }
    }

    /** Whether a node is a text-entry input, decided purely from its class. */
    fun isInputLike(n: PrunedNode): Boolean =
        n.className.lowercase().contains("edittext") ||
            n.className.lowercase().contains("autocompletetextview") ||
            n.className.lowercase().contains("textinputedittext")

    /**
     * Classifies an input's data type with no roles and no labels. The floor is
     * `text`; anything more precise comes from the id/hint/label vocabulary the
     * app developer actually chose, which is stable across scans.
     */
    fun inputTypeOf(n: PrunedNode): InputType {
        val hay = listOf(n.text, n.contentDesc, n.resourceId)
            .filterNotNull()
            .joinToString(" ")
            .lowercase()

        // A 4-8 digit string stands alone as an OTP/code field ("Enter 6-digit code"),
        // and pure-pin fields are classified this way too.
        if (Regex("""\b(otp|one[- ]?time|verification code|verify code|security code|sms code|\d[- ]?digit code)\b""").containsMatchIn(hay)) return InputType.OTP
        if (hay.matches(Regex("""[\d ]{4,8}"""))) return InputType.OTP

        if (Regex("""\b(pass|password|passcode|pwd|pin code|new password|confirm password|current password)\b""").containsMatchIn(hay)) return InputType.PASSWORD
        if (Regex("""\b(pass\w*|pin)\b""").containsMatchIn(hay)) return InputType.PASSWORD

        if (hay.contains("@") || Regex("""\b(e-?mail|mail address|mail id)\b""").containsMatchIn(hay)) return InputType.EMAIL
        if (Regex("""\b(phone|mobile|telephone|contact number|cell)\b""").containsMatchIn(hay)) return InputType.PHONE
        if (Regex("""\b(amount|balance|price|payment|transfer|deposit|withdraw|top[- ]?up|₹|rs\.?|\$|€|£)\b""").containsMatchIn(hay)) return InputType.AMOUNT
        if (Regex("""\b(date|dob|birth|birthday|expiry|expiration|valid till|dd[/]mm|mm[/]dd|yyyy)\b""").containsMatchIn(hay)) return InputType.DATE
        return InputType.TEXT
    }
}