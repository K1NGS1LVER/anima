package io.agents.anima.engine

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * On-device SQLite skill library.
 *
 * The schema is byte-identical to `SkillDB._init_db` in `anima.py`, so a database (or an exported
 * `skills.json`) is portable in both directions between the desktop runtime and the phone.
 */
class SkillStore(context: Context, dbName: String = DB_NAME) : SkillRepository {

    companion object {
        const val TAG = "AnimaSkillStore"
        const val DB_NAME = "anima_skills.db"
        const val DB_VERSION = 1
        const val TABLE = "skills"

        const val CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS skills (
                intent TEXT PRIMARY KEY,
                steps_json TEXT NOT NULL,
                success_count INTEGER DEFAULT 0,
                failure_count INTEGER DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """
    }

    private val helper: SQLiteOpenHelper =
        object : SQLiteOpenHelper(context.applicationContext, dbName, null, DB_VERSION) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(CREATE_SQL)
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // Schema is append-only for now; the CREATE IF NOT EXISTS above is sufficient.
                db.execSQL(CREATE_SQL)
            }
        }

    override fun get(intent: String): Skill? {
        val clean = intent.trim().lowercase()
        return try {
            helper.readableDatabase.rawQuery(
                "SELECT steps_json, success_count, failure_count FROM $TABLE WHERE intent = ?",
                arrayOf(clean),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                Skill(
                    intent = clean,
                    steps = SkillJson.stepsFromJson(cursor.getString(0)),
                    successCount = cursor.getInt(1),
                    failureCount = cursor.getInt(2),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "get('$clean') failed", e)
            null
        }
    }

    override fun save(skill: Skill) {
        try {
            val values = ContentValues().apply {
                put("intent", skill.intent.trim().lowercase())
                put("steps_json", SkillJson.stepsToJson(skill.steps))
                put("success_count", skill.successCount)
                put("failure_count", skill.failureCount)
            }
            // Equivalent to SQLite's INSERT OR REPLACE.
            helper.writableDatabase.insertWithOnConflict(
                TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE,
            )
        } catch (e: Exception) {
            Log.e(TAG, "save('${skill.intent}') failed", e)
        }
    }

    override fun allSkills(): List<Skill> {
        val out = ArrayList<Skill>()
        try {
            helper.readableDatabase.rawQuery(
                "SELECT intent, steps_json, success_count, failure_count FROM $TABLE",
                null,
            ).use { cursor -> out.addAll(readAll(cursor)) }
        } catch (e: Exception) {
            Log.e(TAG, "allSkills() failed", e)
        }
        return out
    }

    override fun findMatch(userGoal: String): Pair<Skill?, Map<String, String>> {
        val clean = userGoal.trim().lowercase()

        val exact = get(clean)
        if (exact != null) return Pair(exact, emptyMap())

        val templates = try {
            helper.readableDatabase.rawQuery(
                "SELECT intent, steps_json, success_count, failure_count FROM $TABLE WHERE intent LIKE ?",
                arrayOf("%{%}%"),
            ).use { cursor -> readAll(cursor) }
        } catch (e: Exception) {
            Log.e(TAG, "findMatch('$clean') template lookup failed", e)
            emptyList()
        }

        // First matching template wins, in plain result-set order.
        for (skill in templates) {
            val extracted = TemplateMatcher.match(skill.intent, clean) ?: continue
            return Pair(skill, extracted)
        }
        return Pair(null, emptyMap())
    }

    /** Deletes every compiled skill. Used by the "forget everything" developer action. */
    fun clear() {
        try {
            helper.writableDatabase.delete(TABLE, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "clear() failed", e)
        }
    }

    fun exportJson(): String = SkillsJsonIo.export(this)

    fun importJson(json: String?): Int = SkillsJsonIo.import(this, json)

    fun close() {
        try {
            helper.close()
        } catch (e: Exception) {
            Log.w(TAG, "close() failed", e)
        }
    }

    private fun readAll(cursor: Cursor): List<Skill> {
        val out = ArrayList<Skill>()
        while (cursor.moveToNext()) {
            out.add(
                Skill(
                    intent = cursor.getString(0),
                    steps = SkillJson.stepsFromJson(cursor.getString(1)),
                    successCount = cursor.getInt(2),
                    failureCount = cursor.getInt(3),
                )
            )
        }
        return out
    }
}
