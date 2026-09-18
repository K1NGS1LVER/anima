package io.agents.anima.design

import java.util.zip.Inflater

/**
 * A decoded screenshot: ARGB pixels, row-major, no platform types.
 *
 * `android.graphics.Bitmap` is deliberately not used. `:design` must unit-test
 * with no device, and this module's Gradle config sets
 * `unitTests.isReturnDefaultValues = true`, so every `BitmapFactory` call in a
 * JVM test returns null. A decoder that only works on a device would make the
 * colour extractor the one part of this module that cannot be tested — and
 * colour is most of what it does.
 */
class Raster(val width: Int, val height: Int, val pixels: IntArray) {

    init {
        require(pixels.size == width * height) {
            "pixel count ${pixels.size} does not match ${width}x$height"
        }
    }

    /** ARGB at (x, y). Callers clamp; out-of-bounds is a programming error. */
    fun at(x: Int, y: Int): Int = pixels[y * width + x]

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height
}

/**
 * Enough of the PNG spec to read what `adb shell screencap -p` writes, in pure
 * Kotlin over [Inflater] — which exists on both the JVM and Android, so the same
 * code path runs in a unit test and on a phone.
 *
 * Supported: bit depth 8 for greyscale, RGB, greyscale+alpha and RGBA; bit
 * depths 1/2/4/8 for palette images; bit depth 16 by keeping the high byte.
 * Interlaced (Adam7) images are rejected rather than silently mis-decoded —
 * `screencap` does not produce them.
 *
 * DETERMINISM: byte-for-byte arithmetic with no floating point. The same file
 * always decodes to the same pixels.
 */
object Png {

    private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < SIGNATURE.size) return false
        return SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }
    }

    /** Decodes [bytes], or throws [IllegalArgumentException] describing what it could not read. */
    fun decode(bytes: ByteArray): Raster {
        require(isPng(bytes)) { "not a PNG: bad signature" }

        var width = 0
        var height = 0
        var bitDepth = 0
        var colorType = -1
        var palette: IntArray? = null
        var transparency: ByteArray? = null
        val idat = ByteArrayBuilder()

        var pos = SIGNATURE.size
        while (pos + 8 <= bytes.size) {
            val length = readInt(bytes, pos)
            require(length >= 0) { "chunk length overflows a signed int at $pos" }
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val dataStart = pos + 8
            require(dataStart + length + 4 <= bytes.size) { "truncated $type chunk" }

            when (type) {
                "IHDR" -> {
                    width = readInt(bytes, dataStart)
                    height = readInt(bytes, dataStart + 4)
                    bitDepth = bytes[dataStart + 8].toInt() and 0xFF
                    colorType = bytes[dataStart + 9].toInt() and 0xFF
                    val interlace = bytes[dataStart + 12].toInt() and 0xFF
                    require(width > 0 && height > 0) { "IHDR declares ${width}x$height" }
                    require(interlace == 0) { "interlaced PNG is not supported" }
                }
                "PLTE" -> {
                    val entries = length / 3
                    palette = IntArray(entries) { i ->
                        val o = dataStart + i * 3
                        argb(255, bytes[o].toInt() and 0xFF, bytes[o + 1].toInt() and 0xFF, bytes[o + 2].toInt() and 0xFF)
                    }
                }
                "tRNS" -> transparency = bytes.copyOfRange(dataStart, dataStart + length)
                "IDAT" -> idat.append(bytes, dataStart, length)
                "IEND" -> pos = bytes.size
            }
            pos = dataStart + length + 4
        }

        require(colorType >= 0) { "no IHDR chunk" }
        require(idat.size > 0) { "no IDAT data" }

        val channels = channelsFor(colorType)
        val raw = inflate(idat.toByteArray(), expectedRawSize(width, height, channels, bitDepth))
        val unfiltered = unfilter(raw, width, height, channels, bitDepth)
        return toRaster(unfiltered, width, height, colorType, bitDepth, palette, transparency)
    }

    private fun channelsFor(colorType: Int): Int = when (colorType) {
        0 -> 1  // greyscale
        2 -> 3  // truecolour
        3 -> 1  // palette index
        4 -> 2  // greyscale + alpha
        6 -> 4  // truecolour + alpha
        else -> throw IllegalArgumentException("unsupported PNG colour type $colorType")
    }

    private fun expectedRawSize(width: Int, height: Int, channels: Int, bitDepth: Int): Int {
        val bitsPerRow = width.toLong() * channels * bitDepth
        val bytesPerRow = (bitsPerRow + 7) / 8
        return ((bytesPerRow + 1) * height).toInt()
    }

    private fun inflate(data: ByteArray, expected: Int): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArray(expected)
            var written = 0
            while (written < expected && !inflater.finished()) {
                val n = inflater.inflate(out, written, expected - written)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                written += n
            }
            require(written == expected) { "IDAT inflated to $written bytes, expected $expected" }
            return out
        } finally {
            inflater.end()
        }
    }

    /** Reverses the per-scanline filters in place, returning rows without their filter byte. */
    private fun unfilter(raw: ByteArray, width: Int, height: Int, channels: Int, bitDepth: Int): ByteArray {
        val bitsPerPixel = channels * bitDepth
        val bytesPerPixel = maxOf(1, bitsPerPixel / 8)
        val bytesPerRow = ((width.toLong() * bitsPerPixel + 7) / 8).toInt()
        val out = ByteArray(bytesPerRow * height)

        var src = 0
        for (y in 0 until height) {
            val filter = raw[src].toInt() and 0xFF
            src++
            val rowStart = y * bytesPerRow
            val prevStart = rowStart - bytesPerRow

            for (i in 0 until bytesPerRow) {
                val x = raw[src + i].toInt() and 0xFF
                val a = if (i >= bytesPerPixel) out[rowStart + i - bytesPerPixel].toInt() and 0xFF else 0
                val b = if (y > 0) out[prevStart + i].toInt() and 0xFF else 0
                val c = if (y > 0 && i >= bytesPerPixel) out[prevStart + i - bytesPerPixel].toInt() and 0xFF else 0
                val value = when (filter) {
                    0 -> x
                    1 -> x + a
                    2 -> x + b
                    3 -> x + (a + b) / 2
                    4 -> x + paeth(a, b, c)
                    else -> throw IllegalArgumentException("unknown PNG filter $filter on row $y")
                }
                out[rowStart + i] = (value and 0xFF).toByte()
            }
            src += bytesPerRow
        }
        return out
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = Math.abs(p - a)
        val pb = Math.abs(p - b)
        val pc = Math.abs(p - c)
        return if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
    }

    private fun toRaster(
        rows: ByteArray,
        width: Int,
        height: Int,
        colorType: Int,
        bitDepth: Int,
        palette: IntArray?,
        transparency: ByteArray?,
    ): Raster {
        val pixels = IntArray(width * height)
        val channels = channelsFor(colorType)
        val bytesPerRow = ((width.toLong() * channels * bitDepth + 7) / 8).toInt()

        for (y in 0 until height) {
            val rowStart = y * bytesPerRow
            for (x in 0 until width) {
                pixels[y * width + x] = when (colorType) {
                    3 -> {
                        val table = requireNotNull(palette) { "palette image with no PLTE chunk" }
                        val index = readIndex(rows, rowStart, x, bitDepth)
                        require(index < table.size) { "palette index $index out of range" }
                        val alpha = transparency?.getOrNull(index)?.let { it.toInt() and 0xFF } ?: 255
                        (table[index] and 0x00FFFFFF) or (alpha shl 24)
                    }
                    0 -> {
                        val g = sample(rows, rowStart, x, 0, 1, bitDepth)
                        argb(255, g, g, g)
                    }
                    2 -> argb(
                        255,
                        sample(rows, rowStart, x, 0, 3, bitDepth),
                        sample(rows, rowStart, x, 1, 3, bitDepth),
                        sample(rows, rowStart, x, 2, 3, bitDepth),
                    )
                    4 -> {
                        val g = sample(rows, rowStart, x, 0, 2, bitDepth)
                        argb(sample(rows, rowStart, x, 1, 2, bitDepth), g, g, g)
                    }
                    6 -> argb(
                        sample(rows, rowStart, x, 3, 4, bitDepth),
                        sample(rows, rowStart, x, 0, 4, bitDepth),
                        sample(rows, rowStart, x, 1, 4, bitDepth),
                        sample(rows, rowStart, x, 2, 4, bitDepth),
                    )
                    else -> throw IllegalArgumentException("unsupported colour type $colorType")
                }
            }
        }
        return Raster(width, height, pixels)
    }

    /** One 8-bit sample. Bit depth 16 keeps the high byte: the low byte is below display precision. */
    private fun sample(rows: ByteArray, rowStart: Int, x: Int, channel: Int, channels: Int, bitDepth: Int): Int =
        when (bitDepth) {
            8 -> rows[rowStart + x * channels + channel].toInt() and 0xFF
            16 -> rows[rowStart + (x * channels + channel) * 2].toInt() and 0xFF
            else -> throw IllegalArgumentException("bit depth $bitDepth is only supported for palette images")
        }

    /** A palette index, which may be packed several to a byte at depths below 8. */
    private fun readIndex(rows: ByteArray, rowStart: Int, x: Int, bitDepth: Int): Int = when (bitDepth) {
        8 -> rows[rowStart + x].toInt() and 0xFF
        4 -> (rows[rowStart + x / 2].toInt() shr (if (x % 2 == 0) 4 else 0)) and 0x0F
        2 -> (rows[rowStart + x / 4].toInt() shr ((3 - x % 4) * 2)) and 0x03
        1 -> (rows[rowStart + x / 8].toInt() shr (7 - x % 8)) and 0x01
        else -> throw IllegalArgumentException("unsupported palette bit depth $bitDepth")
    }

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun readInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)

    private fun ByteArray.getOrNull(index: Int): Byte? = if (index in indices) this[index] else null

    /** Grows geometrically so a screenshot's many IDAT chunks do not copy quadratically. */
    private class ByteArrayBuilder {
        private var buffer = ByteArray(64 * 1024)
        var size = 0
            private set

        fun append(source: ByteArray, offset: Int, length: Int) {
            if (size + length > buffer.size) {
                var capacity = buffer.size
                while (capacity < size + length) capacity *= 2
                buffer = buffer.copyOf(capacity)
            }
            System.arraycopy(source, offset, buffer, size, length)
            size += length
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)
    }
}
