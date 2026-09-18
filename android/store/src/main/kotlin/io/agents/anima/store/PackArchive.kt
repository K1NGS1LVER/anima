package io.agents.anima.store

import io.agents.anima.core.CanonicalJson
import io.agents.anima.core.KnowledgePack
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipFile

object PackArchive {
    fun exportPack(pack: KnowledgePack, screenImages: Map<String, ByteArray>, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            // Write pack.json
            val packJson = CanonicalJson.toJson(pack).toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("pack.json"))
            zos.write(packJson)
            zos.closeEntry()
            
            // Write WebP screenshots
            for ((screenId, webpBytes) in screenImages) {
                zos.putNextEntry(ZipEntry("screens/$screenId.webp"))
                zos.write(webpBytes)
                zos.closeEntry()
            }
        }
    }
    
    // In actual implementation, importPack would use Gson or similar to parse pack.json.
    // For this task, we will define the interface.
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
