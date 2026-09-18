package io.agents.anima.understand

import io.agents.anima.core.InputType
import io.agents.anima.core.ScreenKind
import io.agents.anima.core.ScanContext
import io.agents.anima.core.ScreenObservation
import io.agents.anima.core.UiMode
import io.agents.anima.engine.PrunedNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * The thin end-to-end slice: a real model reply travelling the whole chain --
 * LocalLlm → real HTTP loopback → strict parse gate → durable file cache →
 * verbatim rescan reuse. No device, no cloud, no mocks for the transport.
 */
class EndToEndTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val ctx = ScanContext("com.example.bank", "Example Bank", emptyList())

    private val obs = ScreenObservation(
        "com.example.bank", "com.example.bank.ui.TransferActivity",
        listOf(
            PrunedNode(1, "android.widget.EditText", text = null, contentDesc = "Amount"),
            PrunedNode(2, "android.widget.Button", text = "Send", clickable = true),
        ),
        null, 1080 to 2400, UiMode.LIGHT,
    )

    /** D4's hard case: no text, no content-desc, no resource-id, no roles -- only classes. */
    private val noSignalObs = ScreenObservation(
        "com.example.bank", "com.example.bank.ui.TransferActivity",
        listOf(
            PrunedNode(1, "android.widget.EditText"),
            PrunedNode(2, "android.widget.EditText"),
            PrunedNode(3, "android.widget.Button", clickable = true),
        ),
        null, 1080 to 2400, UiMode.LIGHT,
    )

    /** Minimal HTTP/1.1 echo over a real loopback socket; enough for the two clients. */
    private class FakeEndpoint : AutoCloseable {
        val server = ServerSocket(0)
        private val thread = Thread {
            try {
                while (!server.isClosed) handle(server.accept())
            } catch (_: Exception) {
                // Closing the socket ends the accept loop.
            }
        }
        val hits = AtomicInteger(0)
        val lastTemperature = AtomicReference<Double?>(null)
        val payload = AtomicReference<String?>(
            """{"name":"Send money","purpose":"Collects an amount and sends it.","kind":"form","elements":{"2":"Taps to send"},"inputs":{"1":{"type":"amount","required":true}}}"""
        )

        init {
            thread.isDaemon = true
            thread.start()
        }

        private fun handle(socket: Socket) {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val out = s.getOutputStream()
                var length = 0
                var expectsContinue = false
                while (true) {
                    val line = reader.readLine() ?: return
                    if (line.isEmpty()) break
                    if (line.startsWith("Content-Length:")) length = line.substringAfter(':').trim().toInt()
                    if (line.startsWith("Expect:")) expectsContinue = true
                }
                if (expectsContinue) {
                    // HttpURLConnection withholds the body until this interim response;
                    // without it the exchange deadlocks past the read timeout.
                    out.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray())
                    out.flush()
                }
                val buf = StringBuilder()
                while (buf.length < length) {
                    val part = reader.read()
                    if (part < 0) break
                    buf.append(part.toChar())
                }
                val body = buf.toString()
                lastTemperature.set((org.json.JSONObject(body).opt("temperature") as? Number)?.toDouble())
                hits.incrementAndGet()
                val reply = payload.get() ?: "{}"
                val code = if (payload.get() == null) 503 else 200
                val envelope = org.json.JSONObject()
                    .put("choices", org.json.JSONArray().put(
                        org.json.JSONObject().put("message", org.json.JSONObject().put("role", "assistant").put("content", reply))
                    ))
                    .toString()
                val bytes = envelope.toByteArray(Charsets.UTF_8)
                out.write(
                    ("HTTP/1.1 $code OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray()
                )
                out.write(bytes)
                out.flush()
            }
        }

        val url: String get() = "http://127.0.0.1:${server.localPort}/v1/chat/completions"

        override fun close() = server.close()
    }

    @Test
    fun `model output flows through http parse gate and cache, byte-identical on rescan`() {
        FakeEndpoint().use { ep ->
            val hybrid = HybridUnderstander(
                listOf(LocalLlm(endpointUrl = ep.url)),
                UnderstandCache(tmp.newFolder()),
            )

            val first = hybrid.describe(obs, "scr_transfer", ctx)

            assertEquals("on_device_llm", hybrid.lastBackend)
            assertEquals("Send money", first.name)
            assertEquals(ScreenKind.FORM, first.kind)
            assertEquals(InputType.AMOUNT, first.inputSpecs.getValue(1).type)
            assertTrue("Taps to send" in first.elementSemantics.values)
            assertEquals("temperature 0 must travel on the real wire", 0.0, ep.lastTemperature.get()!!, 0.0)
            assertEquals(1, ep.hits.get())

            // A rescan of the same screen id, even with drifted content, reuses the
            // first run's exact bytes: no second network call.
            val drifted = obs.copy(nodes = listOf(
                PrunedNode(1, "android.widget.EditText", text = null, contentDesc = "drifted"),
                PrunedNode(2, "android.widget.Button", text = "Send", clickable = true),
            ))
            val second = hybrid.describe(drifted, "scr_transfer", ctx)

            assertEquals(1, ep.hits.get())
            assertEquals(1, hybrid.cacheHits)
            assertEquals("Send money", second.name)
            assertEquals("Taps to send", second.elementSemantics.getValue(2))
        }
    }

    @Test
    fun `garbage from a live backend degrades to heuristic, scan never fails`() {
        FakeEndpoint().use { ep ->
            ep.payload.set("this is not JSON")
            val hybrid = HybridUnderstander(listOf(LocalLlm(endpointUrl = ep.url)))
            val p = hybrid.describe(obs, "scr_broken", ctx)
            assertEquals("heuristic", hybrid.lastBackend)
            assertEquals(InputType.AMOUNT, p.inputSpecs.getValue(1).type)
            assertEquals(ScreenKind.FORM, p.kind)
        }
    }

    @Test
    fun `journey and tone complete over the same live endpoint`() {
        FakeEndpoint().use { ep ->
            val jReply = """{"name":"Send money","goal":"Transfer funds to a payee"}"""
            val tReply = """{"register":"formal","summary":"Courteous and precise.","examples":["Please verify","Thank you"]}"""
            val hybrid = HybridUnderstander(listOf(LocalLlm(endpointUrl = ep.url)))
            ep.payload.set(jReply)
            val j = hybrid.describeJourney(emptyList(), emptyList(), ctx)
            assertEquals("Send money", j.name)
            ep.payload.set(tReply)
            val t = hybrid.toneOfVoice(listOf("Please verify your details."), ctx)
            assertEquals("formal", t.register)
            assertEquals(2, t.examples.size)
        }
    }

    /**
     * D5 over a genuinely dead wire: the endpoint accepts then immediately closes
     * the socket with no HTTP at all. The transport fails mid-handshake, the
     * backend returns null, and the scan must still produce a profile -- plus the
     * D4 hard-case screen (no labels, no roles, only classes) comes out correctly
     * typed and purpose-correct on that degraded path.
     */
    @Test
    fun `a dead endpoint degrades to heuristic and the no-signal screen still gets typed fields`() {
        ServerSocket(0).use { dead ->
            val thread = Thread {
                try {
                    while (!dead.isClosed) dead.accept().use { it.close() }
                } catch (_: Exception) {
                    // Closing the socket ends the accept loop.
                }
            }
            thread.isDaemon = true
            thread.start()

            val hybrid = HybridUnderstander(
                listOf(LocalLlm(endpointUrl = "http://127.0.0.1:${dead.localPort}/v1/chat/completions", timeoutMs = 1500)),
                UnderstandCache(tmp.newFolder()),
            )

            val p = hybrid.describe(noSignalObs, "scr_forgot_my_signal", ctx)

            assertEquals("heuristic", hybrid.lastBackend)
            assertEquals(ScreenKind.FORM, p.kind)
            assertEquals("Collects input to submit.", p.purpose)
            assertEquals("Transfer", p.name)
            assertEquals(InputType.TEXT, p.inputSpecs.getValue(1).type)
            assertEquals(InputType.TEXT, p.inputSpecs.getValue(2).type)
            assertEquals("A field for text entry.", p.elementSemantics.getValue(1))
        }
    }
}