package io.agents.anima.core

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `:core` must stay free of `android.*`.
 *
 * This is not style. The entire engine — pruning, matching, the pack schema,
 * the stable-ID hashing — is unit-tested on a plain JVM with no emulator and no
 * device, which is the only reason five people can develop against it in
 * parallel during a sprint. One `import android.util.Log` added for a quick
 * debug line would end that quietly, and nobody would notice until CI needed an
 * emulator.
 *
 * `org.json` is the one deliberate exception: a real JVM artifact here
 * (declared `compileOnly`) and the platform's copy on device.
 *
 * Note the assertion on the working directory. An earlier revision of this test
 * opened with `if (!coreDir.exists()) return`, which turns a guard test into
 * one that reports success precisely when it cannot do its job — green forever,
 * teaching nobody anything. If the source tree is not where this expects it,
 * that is a fact worth failing on.
 */
class NoAndroidImportsTest {

    @Test
    fun coreSourcesImportNothingFromAndroid() {
        val root = File("src/main/kotlin")
        assertTrue(
            "expected the module directory as the working directory, got ${File(".").absolutePath}",
            root.isDirectory,
        )

        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .withIndex()
                    // Match the import statement, not the substring: a string
                    // literal or a comment mentioning android.* is harmless.
                    .filter { (_, line) -> line.trimStart().startsWith("import android") }
                    .map { (index, line) -> "${file.path}:${index + 1}  ${line.trim()}" }
            }
            .toList()

        assertTrue(
            "these :core files import from android.*, which breaks JVM-only testing:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
