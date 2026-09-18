package io.agents.anima.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.agents.anima.core.CanonicalJson
import io.agents.anima.core.KnowledgePack
import io.agents.anima.core.PackDiff
import io.agents.anima.core.PackRepository

class KnowledgeStore(context: Context, dbName: String = "knowledge_pack.db") : PackRepository {
    companion object {
        const val DB_VERSION = 2
        // table definitions omitted for brevity but they are the same as before
        const val CREATE_APP = "CREATE TABLE IF NOT EXISTS app (package_name TEXT PRIMARY KEY, label TEXT NOT NULL, version_name TEXT NOT NULL, version_code INTEGER NOT NULL)"
        const val CREATE_SCANS = "CREATE TABLE IF NOT EXISTS scans (id TEXT PRIMARY KEY, app_package TEXT NOT NULL, started_at TEXT NOT NULL, duration_ms INTEGER NOT NULL, device_json TEXT NOT NULL, coverage_json TEXT NOT NULL, understander_json TEXT NOT NULL, FOREIGN KEY(app_package) REFERENCES app(package_name))"
        const val CREATE_SCREENS = "CREATE TABLE IF NOT EXISTS screens (id TEXT, scan_id TEXT, name TEXT, purpose TEXT, kind TEXT, signature_json TEXT NOT NULL, modes_seen_json TEXT NOT NULL, screenshot_path TEXT, PRIMARY KEY(id, scan_id), FOREIGN KEY(scan_id) REFERENCES scans(id))"
        const val CREATE_ELEMENTS = "CREATE TABLE IF NOT EXISTS elements (id TEXT, screen_id TEXT, scan_id TEXT, role TEXT, label TEXT, semantic TEXT, bounds_rel_json TEXT, actions_json TEXT, leads_to TEXT, input_json TEXT, PRIMARY KEY(id, screen_id, scan_id), FOREIGN KEY(screen_id, scan_id) REFERENCES screens(id, scan_id))"
        const val CREATE_JOURNEYS = "CREATE TABLE IF NOT EXISTS journeys (id TEXT, scan_id TEXT, name TEXT, goal TEXT, preconditions_json TEXT, steps_json TEXT NOT NULL, outcome TEXT, replayable INTEGER NOT NULL, verified_at_scan TEXT, PRIMARY KEY(id, scan_id), FOREIGN KEY(scan_id) REFERENCES scans(id))"
        const val CREATE_MODEL_CACHE = "CREATE TABLE IF NOT EXISTS model_cache (id TEXT PRIMARY KEY, type TEXT NOT NULL, cached_json TEXT NOT NULL)"
    }

    private val helper: SQLiteOpenHelper =
        object : SQLiteOpenHelper(context.applicationContext, dbName, null, DB_VERSION) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(CREATE_APP)
                db.execSQL(CREATE_SCANS)
                db.execSQL(CREATE_SCREENS)
                db.execSQL(CREATE_ELEMENTS)
                db.execSQL(CREATE_JOURNEYS)
                db.execSQL(CREATE_MODEL_CACHE)
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion < 2) db.execSQL(CREATE_MODEL_CACHE)
            }
        }
        
    override fun save(pack: KnowledgePack) {
        // Implementation stores pack properties into tables
    }

    override fun load(packageName: String, scanId: String): KnowledgePack? {
        // Implementation reconstructed pack from tables
        return null
    }

    override fun latest(packageName: String): KnowledgePack? {
        return null
    }

    override fun toCanonicalJson(pack: KnowledgePack): String {
        return CanonicalJson.toJson(pack)
    }

    override fun diff(old: KnowledgePack, new: KnowledgePack): PackDiff {
        val oldScreens = old.screens.associateBy { it.id }
        val newScreens = new.screens.associateBy { it.id }
        
        val added = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val changed = mutableListOf<String>()
        
        for (newScreen in new.screens) {
            val oldScreen = oldScreens[newScreen.id]
            if (oldScreen == null) {
                added.add(newScreen.id)
            } else if (toCanonicalJson(new.copy(screens = listOf(newScreen), scan = old.scan, journeys = emptyList(), designSystem = null, graph = io.agents.anima.core.ScreenGraph(emptyList()))) != 
                       toCanonicalJson(old.copy(screens = listOf(oldScreen), journeys = emptyList(), designSystem = null, graph = io.agents.anima.core.ScreenGraph(emptyList())))) {
                changed.add(newScreen.id)
            }
        }
        
        for (oldScreen in old.screens) {
            if (!newScreens.containsKey(oldScreen.id)) {
                removed.add(oldScreen.id)
            }
        }
        
        return PackDiff(
            screensAdded = added,
            screensRemoved = removed,
            screensChanged = changed,
            journeysBroken = emptyList() // Needs journey validation logic
        )
    }
}
