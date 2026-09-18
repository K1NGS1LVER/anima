package io.agents.anima.understand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UnderstandCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `put then get round-trips`() {
        val cache = UnderstandCache(tmp.newFolder())
        cache.put("scr_abc", CachedResponse("cloud_vlm", "{\"name\": \"X\"}"))
        assertEquals("{\"name\": \"X\"}", cache.get("scr_abc")!!.text)
        assertEquals("cloud_vlm", cache.get("scr_abc")!!.backend)
    }

    @Test
    fun `missing keys return null`() {
        val cache = UnderstandCache(tmp.newFolder())
        assertNull(cache.get("scr_nope"))
    }

    @Test
    fun `cache survives process restart`() {
        val dir = tmp.newFolder()
        UnderstandCache(dir).put("scr_def", CachedResponse("on_device_llm", "{\"purpose\": \"Y\"}"))
        // Fresh instance over the same directory == a fresh scan run.
        val reopened = UnderstandCache(dir)
        assertEquals("{\"purpose\": \"Y\"}", reopened.get("scr_def")!!.text)
        assertEquals("on_device_llm", reopened.get("scr_def")!!.backend)
    }

    @Test
    fun `safe against hostile keys and corrupt files`() {
        val cache = UnderstandCache(tmp.newFolder())
        // Hostile key is sanitized into a plain filename; write and read must not throw
        // and must round-trip through the sanitized form.
        cache.put("../evil/key!@#", CachedResponse("heuristic", "{}"))
        assertEquals("{}", cache.get("../evil/key!@#")!!.text)
        // A corrupt file must read as a miss, never throw.
        val file = java.io.File(tmp.root, "scr_bad.json")
        file.parentFile!!.mkdirs()
        file.writeText("not json {")
        assertNull(cache.get("scr_bad"))
    }
}