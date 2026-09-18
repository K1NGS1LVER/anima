package io.agents.anima.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class KnowledgeStore(context: Context, dbName: String = "knowledge_pack.db") {
    companion object {
        const val DB_VERSION = 2
        
        const val CREATE_APP = """
            CREATE TABLE IF NOT EXISTS app (
                package_name TEXT PRIMARY KEY,
                label TEXT NOT NULL,
                version_name TEXT NOT NULL,
                version_code INTEGER NOT NULL
            )
        """
        
        const val CREATE_SCANS = """
            CREATE TABLE IF NOT EXISTS scans (
                id TEXT PRIMARY KEY,
                app_package TEXT NOT NULL,
                started_at TEXT NOT NULL,
                duration_ms INTEGER NOT NULL,
                device_json TEXT NOT NULL,
                coverage_json TEXT NOT NULL,
                understander_json TEXT NOT NULL,
                FOREIGN KEY(app_package) REFERENCES app(package_name)
            )
        """
        
        const val CREATE_SCREENS = """
            CREATE TABLE IF NOT EXISTS screens (
                id TEXT,
                scan_id TEXT,
                name TEXT,
                purpose TEXT,
                kind TEXT,
                signature_json TEXT NOT NULL,
                modes_seen_json TEXT NOT NULL,
                screenshot_path TEXT,
                PRIMARY KEY(id, scan_id),
                FOREIGN KEY(scan_id) REFERENCES scans(id)
            )
        """
        
        const val CREATE_ELEMENTS = """
            CREATE TABLE IF NOT EXISTS elements (
                id TEXT,
                screen_id TEXT,
                scan_id TEXT,
                role TEXT,
                label TEXT,
                semantic TEXT,
                bounds_rel_json TEXT,
                actions_json TEXT,
                leads_to TEXT,
                input_json TEXT,
                PRIMARY KEY(id, screen_id, scan_id),
                FOREIGN KEY(screen_id, scan_id) REFERENCES screens(id, scan_id)
            )
        """

        const val CREATE_JOURNEYS = """
            CREATE TABLE IF NOT EXISTS journeys (
                id TEXT,
                scan_id TEXT,
                name TEXT,
                goal TEXT,
                preconditions_json TEXT,
                steps_json TEXT NOT NULL,
                outcome TEXT,
                replayable INTEGER NOT NULL,
                verified_at_scan TEXT,
                PRIMARY KEY(id, scan_id),
                FOREIGN KEY(scan_id) REFERENCES scans(id)
            )
        """

        const val CREATE_MODEL_CACHE = """
            CREATE TABLE IF NOT EXISTS model_cache (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                cached_json TEXT NOT NULL
            )
        """
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
                if (oldVersion < 2) {
                    db.execSQL(CREATE_MODEL_CACHE)
                }
            }
        }
        
    fun getReadableDatabase(): SQLiteDatabase = helper.readableDatabase
    fun getWritableDatabase(): SQLiteDatabase = helper.writableDatabase

    fun cacheModelResult(id: String, type: String, jsonValue: String) {
        val values = ContentValues().apply {
            put("id", id)
            put("type", type)
            put("cached_json", jsonValue)
        }
        helper.writableDatabase.insertWithOnConflict(
            "model_cache", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getCachedModelResult(id: String): String? {
        helper.readableDatabase.rawQuery("SELECT cached_json FROM model_cache WHERE id = ?", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }
}
