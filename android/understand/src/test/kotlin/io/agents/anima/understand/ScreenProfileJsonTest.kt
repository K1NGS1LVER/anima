package io.agents.anima.understand

import io.agents.anima.core.InputType
import io.agents.anima.core.ScreenKind
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.UiMode
import io.agents.anima.engine.PrunedNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenProfileJsonTest {

    private val o = ScreenObservation(
        "com.example.bank", "com.example.bank.ui.PayActivity",
        listOf(
            PrunedNode(2, "android.widget.EditText"),
            PrunedNode(7, "android.widget.Button"),
        ),
        null, 1080 to 2400, UiMode.LIGHT,
    )

    private val valid = """{
        "name": "Send Money",
        "purpose": "Collects the amount and a note to send money.",
        "kind": "form",
        "elements": {"7": "Starts the transfer"},
        "inputs": {"2": {"type": "amount", "required": true, "hint": "Enter amount", "max_length": 10}}
    }"""

    @Test
    fun `valid model response becomes a ScreenProfile`() {
        val p = ScreenProfileJson.parse(valid, o)!!
        assertEquals("Send Money", p.name)
        assertEquals(ScreenKind.FORM, p.kind)
        assertEquals("Starts the transfer", p.elementSemantics.getValue(7))
        assertEquals(InputType.AMOUNT, p.inputSpecs.getValue(2).type)
        assertEquals(true, p.inputSpecs.getValue(2).required)
        assertEquals("Enter amount", p.inputSpecs.getValue(2).hint)
        assertEquals(10, p.inputSpecs.getValue(2).maxLength)
    }

    @Test
    fun `degenerate response without purpose falls back to null`() {
        assertNull(ScreenProfileJson.parse("{\"name\": \"Only a name\"}", o))
        assertNull(ScreenProfileJson.parse("garbage", o))
        assertNull(ScreenProfileJson.parse("", o))
    }

    @Test
    fun `unknown kind repairs to OTHER, unknown input type drops that input`() {
        val p = ScreenProfileJson.parse(
            """{
                "name": "Weird",
                "purpose": "Hard to classify.",
                "kind": "blorp",
                "inputs": {
                    "2": {"type": "rainbow", "required": false},
                    "7": {"type": "otp", "required": false}
                }
            }""", o
        )!!
        assertEquals(ScreenKind.OTHER, p.kind)
        assertTrue(2 !in p.inputSpecs)
        assertEquals(InputType.OTP, p.inputSpecs.getValue(7).type)
    }

    @Test
    fun `element ids not on the screen are ignored`() {
        val p = ScreenProfileJson.parse(
            """{
                "name": "X",
                "purpose": "Should not invent ids.",
                "kind": "other",
                "elements": {"99": "never existed"},
                "inputs": {"99": {"type": "text"}}
            }""", o
        )!!
        assertTrue(p.elementSemantics.isEmpty())
        assertTrue(p.inputSpecs.isEmpty())
    }

    @Test
    fun `journey name and tone parse and reject garbage`() {
        val j = ScreenProfileJson.parseJourney("{\"name\": \"Transfer\", \"goal\": \"Send money\"}")
        assertEquals("Transfer", j!!.name)
        assertNull(ScreenProfileJson.parseJourney("{\"name\": \"no goal\"}"))
        assertNull(ScreenProfileJson.parseJourney("nope"))

        val t = ScreenProfileJson.parseTone("{\"register\": \"friendly\", \"summary\": \"Warm and direct.\", \"examples\": [\"Hi there\", \"All good\"]}")
        assertEquals("friendly", t!!.register)
        assertEquals(2, t.examples.size)
        assertNull(ScreenProfileJson.parseTone("{\"register\": \"aggressive\", \"summary\": \"x\"}"))
    }
}