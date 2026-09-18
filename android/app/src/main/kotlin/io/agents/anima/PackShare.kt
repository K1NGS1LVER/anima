package io.agents.anima

import android.content.Context
import io.agents.anima.core.KnowledgePack
import io.agents.anima.store.PackArchive
import java.io.File

/** App-owned export seam. A completed pack is supplied by the real controller/store integration. */
object PackShare {
    fun export(context: Context, pack: KnowledgePack, images: Map<String, ByteArray>): File {
        val output = File(context.cacheDir, "${pack.app.packageName}-${pack.scan.id}.animapack")
        PackArchive.exportPack(pack, images, output)
        return output
    }
}
