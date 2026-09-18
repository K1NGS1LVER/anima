package io.agents.anima.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `:core` must stay free of `android.*`.
 *
 * This is not style. The entire engine — pruning, matching, the pack schema —
 * is unit-tested on a plain JVM with no emulator and no device, which is the
 * only reason five people can develop against it in parallel during a sprint.
 * One `import android.util.Log` added for a quick debug line would end that
 * quietly, and nobody would notice until CI needed an emulator.
 *
 * `org.json` is the one deliberate exception: it is a real JVM artifact here
 * (declared `compileOnly`) and the platform's copy on device.
 */
class NoAndroidImportsTest {

    @Test
    fun coreSourcesImportNothingFromAndroid() {
        val root = File("src/main/kotlin")
        assertTrue("expected the module directory as CWD, got ${File(".").absolutePath}", root.isDirectory)

        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .withIndex()
                    .filter { (_, line) -> line.trimStart().startsWith("import android") }
                    .map { (index, line) -> "${file.path}:${index + 1}  $line" }
            }
            .toList()

        assertTrue(
            "these :core files import from android.*, which breaks JVM-only testing:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
