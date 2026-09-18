package io.agents.anima.engine

/**
 * Pure-Kotlin reimplementation of Python's `difflib.SequenceMatcher(None, a, b).ratio()`.
 *
 * There is no JVM stdlib equivalent, and the Matcher's fuzzy scores must agree with the Python
 * runtime digit for digit, so the Ratcliff/Obershelp algorithm is ported directly from CPython's
 * `difflib` (`find_longest_match` + the recursive block walk of `get_matching_blocks`).
 *
 * ratio = 2 * M / T, where T is the combined length of both strings and M is the total number of
 * matched characters across all longest-matching blocks.
 *
 * Autojunk is *not* implemented: CPython only engages it for sequences of length >= 200 and our
 * inputs are UI labels, so the plain recursive implementation reproduces Python's output.
 *
 * Verified against CPython 3:
 *   ratio("wifi", "wifi")        == 1.0
 *   ratio("wi-fi", "wifi")       == 0.8888888888888888
 *   ratio("settings", "setting") == 0.9333333333333333
 *   ratio("allow", "deny")       == 0.0
 *   ratio("", "")                == 1.0
 */
object TextRatio {

    fun ratio(a: String, b: String): Double {
        val total = a.length + b.length
        if (total == 0) return 1.0 // Python returns 1.0 for two empty sequences.
        return 2.0 * totalMatches(a, b) / total.toDouble()
    }

    /** Total size of all matching blocks, equivalent to sum(triple.size for triple in blocks). */
    private fun totalMatches(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0

        // b2j: character -> ascending list of indices in b (CPython's __chain_b).
        val b2j = HashMap<Char, MutableList<Int>>()
        for (j in b.indices) {
            b2j.getOrPut(b[j]) { ArrayList() }.add(j)
        }

        var matched = 0
        // Explicit stack instead of recursion; the summed size is order-independent.
        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, a.length, 0, b.length))
        while (stack.isNotEmpty()) {
            val range = stack.removeLast()
            val alo = range[0]
            val ahi = range[1]
            val blo = range[2]
            val bhi = range[3]

            val best = longestMatch(a, b2j, alo, ahi, blo, bhi)
            val i = best[0]
            val j = best[1]
            val size = best[2]
            if (size == 0) continue

            matched += size
            if (alo < i && blo < j) stack.addLast(intArrayOf(alo, i, blo, j))
            if (i + size < ahi && j + size < bhi) stack.addLast(intArrayOf(i + size, ahi, j + size, bhi))
        }
        return matched
    }

    /**
     * Port of CPython's `SequenceMatcher.find_longest_match` without junk handling.
     * Returns [i, j, size] — the earliest-in-a (then earliest-in-b) longest common block.
     */
    private fun longestMatch(
        a: String,
        b2j: Map<Char, List<Int>>,
        alo: Int,
        ahi: Int,
        blo: Int,
        bhi: Int,
    ): IntArray {
        var besti = alo
        var bestj = blo
        var bestsize = 0

        var j2len = HashMap<Int, Int>()
        for (i in alo until ahi) {
            val newj2len = HashMap<Int, Int>()
            val indices = b2j[a[i]] ?: emptyList()
            for (j in indices) {
                if (j < blo) continue
                if (j >= bhi) break
                val k = (j2len[j - 1] ?: 0) + 1
                newj2len[j] = k
                if (k > bestsize) {
                    besti = i - k + 1
                    bestj = j - k + 1
                    bestsize = k
                }
            }
            j2len = newj2len
        }
        return intArrayOf(besti, bestj, bestsize)
    }
}
