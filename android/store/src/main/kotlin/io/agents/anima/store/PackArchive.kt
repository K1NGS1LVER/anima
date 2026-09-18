package io.agents.anima.store

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.agents.anima.core.CanonicalJson
import io.agents.anima.core.KnowledgePack
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile

object PackArchive {
    fun exportPack(pack: KnowledgePack, screenImages: Map<String, ByteArray>, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            val packJson = CanonicalJson.toJson(pack).toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("pack.json"))
            zos.write(packJson)
            zos.closeEntry()
            
            for ((screenId, imgBytes) in screenImages) {
                val webpBytes = compressToWebP(imgBytes, 720)
                zos.putNextEntry(ZipEntry("screens/$screenId.webp"))
                zos.write(webpBytes)
                zos.closeEntry()
            }
        }
    }
    
    private fun compressToWebP(imgBytes: ByteArray, maxEdge: Int): ByteArray {
        val bitmap = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size) ?: return imgBytes
        val width = bitmap.width
        val height = bitmap.height
        
        val scale = if (width > height) maxEdge.toFloat() / width else maxEdge.toFloat() / height
        val resized = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
        } else {
            bitmap
        }
        
        val baos = ByteArrayOutputStream()
        // Use WEBP or WEBP_LOSSY depending on API level. Since minSdk=30, WEBP_LOSSY is available.
        resized.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, baos)
        return baos.toByteArray()
    }
    
    fun importPack(inputFile: File): Pair<String, Map<String, ByteArray>> {
        var packJson = ""
        val images = mutableMapOf<String, ByteArray>()
        
        ZipFile(inputFile).use { zip ->
            for (entry in zip.entries()) {
                if (entry.name == "pack.json") {
                    packJson = zip.getInputStream(entry).reader().readText()
                } else if (entry.name.startsWith("screens/") && entry.name.endsWith(".webp")) {
                    val screenId = entry.name.substringAfter("screens/").substringBefore(".webp")
                    images[screenId] = zip.getInputStream(entry).readBytes()
                }
            }
        }
        return Pair(packJson, images)
    }
}
