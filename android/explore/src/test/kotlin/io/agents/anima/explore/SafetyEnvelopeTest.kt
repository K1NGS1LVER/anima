package io.agents.anima.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deny-list is the one piece of this project where being wrong is not a
 * worse pack but a real consequence for a real person.
 */
class SafetyEnvelopeTest {

    @Test
    fun spellingAndPunctuationDoNotGetPastIt() {
        // A real device lesson, recorded the hard way: `"wifi" !in "Wi-Fi"`
        // broke every match in the planner until labels were flattened. A
        // deny-list that only catches one spelling of "Log out" is decoration.
        for (label in listOf("Log out", "log-out", "LOGOUT", "Log Out", "log  out")) {
            assertTrue("'$label' must be denied", SafetyEnvelope.isDestructiveLabel(label))
        }
    }

    @Test
    fun itRefusesTheThingsThatCannotBeUndone() {
        for (label in listOf(
            "Delete account", "Pay now", "Transfer", "Withdraw", "Close account",
            "Factory reset", "Uninstall", "Emergency call", "Block user",
        )) {
            assertTrue("'$label' must be denied", SafetyEnvelope.isDestructiveLabel(label))
        }
    }

    @Test
    fun itDoesNotRefuseForwardMotion() {
        // The brief explicitly asks the crawler to get through login, OTP and
        // KYC gates. A deny-list that also blocks Continue and Verify would make
        // the crawler safe and useless -- it would never see a screen behind a
        // sign-in, which on a fintech app is every screen worth mapping.
        for (label in listOf("Continue", "Next", "Submit", "Sign in", "Verify", "OK", "Got it", "Skip")) {
            assertFalse("'$label' must be allowed", SafetyEnvelope.isDestructiveLabel(label))
        }
    }

    @Test
    fun aDangerousPhraseIsCaughtInsideALongerSentence() {
        assertTrue(SafetyEnvelope.isDestructiveLabel("Delete account permanently"))
        assertTrue(SafetyEnvelope.isDestructiveLabel("This cannot be undone"))
        // But an ordinary sentence that merely contains a short verb is not
        // condemned, or the crawler would refuse half of a normal app.
        assertFalse(SafetyEnvelope.isDestructiveLabel("Payments and transfers"))
    }

    @Test
    fun itLooksAtEveryLabelANodeCarries() {
        // Buttons in the wild routinely have an icon and no text, a
        // content-description and no text, or only a resource-id that gives the
        // game away. Checking one of the three is checking none of them.
        assertTrue(
            "content-desc alone must be enough",
            SafetyEnvelope.isDestructive(node(1, null, 0, 0, 100, 100).copy(contentDesc = "Delete")),
        )
        assertTrue(
            "resource-id alone must be enough",
            SafetyEnvelope.isDestructive(
                node(1, null, 0, 0, 100, 100).copy(resourceId = "com.example:id/btn_logout"),
            ),
        )
        assertFalse(SafetyEnvelope.isDestructive(node(1, "Accounts", 0, 0, 100, 100)))
    }

    @Test
    fun aConsentDialogIsNotTheSameAsBeingLost() {
        // The permission controller appears over the target app on first launch.
        // Treating it as "we left the app" would trigger the recovery ladder --
        // back, home, relaunch -- which dismisses the dialog by restarting, and
        // then it appears again on the next launch, forever.
        val target = "com.example.bank"
        assertEquals(SafetyEnvelope.Location.ON_TARGET, SafetyEnvelope.locate(target, target))
        assertEquals(
            SafetyEnvelope.Location.SYSTEM_DIALOG,
            SafetyEnvelope.locate("com.android.permissioncontroller", target),
        )
        assertEquals(
            SafetyEnvelope.Location.OFF_TARGET,
            SafetyEnvelope.locate("com.android.chrome", target),
        )
        assertEquals(SafetyEnvelope.Location.UNREADABLE, SafetyEnvelope.locate(null, target))
    }
}
