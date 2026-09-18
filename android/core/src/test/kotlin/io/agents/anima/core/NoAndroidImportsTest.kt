package io.agents.anima.core

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class NoAndroidImportsTest {
    @Test
    fun testNoAndroidImports() {
        val coreDir = File("src/main/kotlin")
        if (!coreDir.exists()) return // skip if run from different context
        
        coreDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val content = file.readText()
            assertTrue(!content.contains("import android."), "File ${file.name} contains android imports")
        }
    }
}
