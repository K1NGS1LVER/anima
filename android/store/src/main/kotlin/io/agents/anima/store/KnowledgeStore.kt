package io.agents.anima.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.agents.anima.core.*
import org.json.JSONArray
import org.json.JSONObject

class KnowledgeStore(context: Context, dbName: String = "knowledge_pack.db") : PackRepository {
    companion object {
        const val DB_VERSION = 2
        
        const val CREATE_APP = "CREATE TABLE IF NOT EXISTS app (package_name TEXT PRIMARY KEY, label TEXT NOT NULL, version_name TEXT, version_code INTEGER)"
        const val CREATE_SCANS = "CREATE TABLE IF NOT EXISTS scans (id TEXT PRIMARY KEY, app_package TEXT NOT NULL, started_at TEXT NOT NULL, duration_ms INTEGER NOT NULL, device_json TEXT NOT NULL, coverage_json TEXT NOT NULL, understander_json TEXT, FOREIGN KEY(app_package) REFERENCES app(package_name))"
        const val CREATE_SCREENS = "CREATE TABLE IF NOT EXISTS screens (id TEXT, scan_id TEXT, name TEXT, purpose TEXT, kind TEXT, signature_json TEXT NOT NULL, modes_seen_json TEXT NOT NULL, screenshot_path TEXT, PRIMARY KEY(id, scan_id), FOREIGN KEY(scan_id) REFERENCES scans(id))"
        const val CREATE_ELEMENTS = "CREATE TABLE IF NOT EXISTS elements (id TEXT, screen_id TEXT, scan_id TEXT, role TEXT, label TEXT, semantic TEXT, bounds_rel_json TEXT, actions_json TEXT, leads_to TEXT, input_json TEXT, PRIMARY KEY(id, screen_id, scan_id), FOREIGN KEY(screen_id, scan_id) REFERENCES screens(id, scan_id))"
        const val CREATE_JOURNEYS = "CREATE TABLE IF NOT EXISTS journeys (id TEXT, scan_id TEXT, name TEXT, goal TEXT, preconditions_json TEXT, steps_json TEXT NOT NULL, outcome TEXT, replayable INTEGER NOT NULL, verified_at_scan TEXT, PRIMARY KEY(id, scan_id), FOREIGN KEY(scan_id) REFERENCES scans(id))"
        
        const val CREATE_PACK_META = "CREATE TABLE IF NOT EXISTS pack_meta (scan_id TEXT PRIMARY KEY, design_system_json TEXT, graph_json TEXT, pack_version TEXT, FOREIGN KEY(scan_id) REFERENCES scans(id))"
        
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
                db.execSQL(CREATE_PACK_META)
                db.execSQL(CREATE_MODEL_CACHE)
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion < 2) {
                    db.execSQL(CREATE_MODEL_CACHE)
                    db.execSQL(CREATE_PACK_META)
                }
            }
        }
        
    override fun save(pack: KnowledgePack) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val appValues = ContentValues().apply {
                put("package_name", pack.app.packageName)
                put("label", pack.app.label)
                put("version_name", pack.app.versionName)
                put("version_code", pack.app.versionCode)
            }
            db.insertWithOnConflict("app", null, appValues, SQLiteDatabase.CONFLICT_REPLACE)
            
            val scanValues = ContentValues().apply {
                put("id", pack.scan.id)
                put("app_package", pack.app.packageName)
                put("started_at", pack.scan.startedAt)
                put("duration_ms", pack.scan.durationMs)
                put("device_json", CanonicalJson.toJson(pack.scan.device))
                put("coverage_json", CanonicalJson.toJson(pack.scan.coverage))
                put("understander_json", CanonicalJson.toJson(pack.scan.understander))
            }
            db.insertWithOnConflict("scans", null, scanValues, SQLiteDatabase.CONFLICT_REPLACE)
            
            val metaValues = ContentValues().apply {
                put("scan_id", pack.scan.id)
                put("design_system_json", CanonicalJson.toJson(pack.designSystem))
                put("graph_json", CanonicalJson.toJson(pack.graph))
                put("pack_version", pack.packVersion)
            }
            db.insertWithOnConflict("pack_meta", null, metaValues, SQLiteDatabase.CONFLICT_REPLACE)
            
            for (screen in pack.screens) {
                val screenValues = ContentValues().apply {
                    put("id", screen.id)
                    put("scan_id", pack.scan.id)
                    put("name", screen.name)
                    put("purpose", screen.purpose)
                    put("kind", screen.kind.name)
                    put("signature_json", CanonicalJson.toJson(screen.signature))
                    put("modes_seen_json", CanonicalJson.toJson(screen.modesSeen.map { it.name }))
                    put("screenshot_path", screen.screenshot)
                }
                db.insertWithOnConflict("screens", null, screenValues, SQLiteDatabase.CONFLICT_REPLACE)
                
                for (el in screen.elements) {
                    val elValues = ContentValues().apply {
                        put("id", el.id)
                        put("screen_id", screen.id)
                        put("scan_id", pack.scan.id)
                        put("role", el.role.name)
                        put("label", el.label)
                        put("semantic", el.semantic)
                        put("bounds_rel_json", CanonicalJson.toJson(el.boundsRel))
                        put("actions_json", CanonicalJson.toJson(el.actions.map { it.name }))
                        put("leads_to", el.leadsTo)
                        put("input_json", CanonicalJson.toJson(el.input))
                    }
                    db.insertWithOnConflict("elements", null, elValues, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            
            for (journey in pack.journeys) {
                val jValues = ContentValues().apply {
                    put("id", journey.id)
                    put("scan_id", pack.scan.id)
                    put("name", journey.name)
                    put("goal", journey.goal)
                    put("preconditions_json", CanonicalJson.toJson(journey.preconditions))
                    put("steps_json", CanonicalJson.toJson(journey.steps))
                    put("outcome", journey.outcome)
                    put("replayable", if (journey.replayable) 1 else 0)
                    put("verified_at_scan", journey.verifiedAtScan)
                }
                db.insertWithOnConflict("journeys", null, jValues, SQLiteDatabase.CONFLICT_REPLACE)
            }
            
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun load(packageName: String, scanId: String): KnowledgePack? {
        val db = helper.readableDatabase
        
        var app: AppIdentity? = null
        db.rawQuery("SELECT package_name, label, version_name, version_code FROM app WHERE package_name = ?", arrayOf(packageName)).use { c ->
            if (c.moveToFirst()) {
                app = AppIdentity(
                    packageName = c.getString(0),
                    label = c.getString(1),
                    versionName = if (c.isNull(2)) null else c.getString(2),
                    versionCode = if (c.isNull(3)) null else c.getLong(3)
                )
            }
        }
        if (app == null) return null

        var scan: ScanMetadata? = null
        db.rawQuery("SELECT started_at, duration_ms, device_json, coverage_json, understander_json FROM scans WHERE id = ?", arrayOf(scanId)).use { c ->
            if (c.moveToFirst()) {
                val startedAt = c.getString(0)
                val durationMs = c.getLong(1)
                
                val deviceJson = JSONObject(c.getString(2))
                val resArray = deviceJson.getJSONArray("resolution")
                val resolution = mutableListOf<Int>()
                for (i in 0 until resArray.length()) resolution.add(resArray.getInt(i))
                val device = DeviceInfo(
                    model = deviceJson.getString("model"),
                    sdk = deviceJson.getInt("sdk"),
                    resolution = resolution,
                    locale = deviceJson.getString("locale")
                )
                
                val coverageJson = JSONObject(c.getString(3))
                val coverage = Coverage(
                    screensFound = coverageJson.getInt("screens_found"),
                    elementsFound = coverageJson.getInt("elements_found"),
                    frontierRemaining = coverageJson.getInt("frontier_remaining"),
                    stopReason = coverageJson.getString("stop_reason")
                )
                
                val uStr = if (c.isNull(4)) null else c.getString(4)
                var understander: UnderstanderInfo? = null
                if (uStr != null) {
                    val uJson = JSONObject(uStr)
                    understander = UnderstanderInfo(
                        backend = uJson.getString("backend"),
                        model = if (uJson.has("model") && !uJson.isNull("model")) uJson.getString("model") else null,
                        cacheHits = uJson.getInt("cache_hits")
                    )
                }
                
                scan = ScanMetadata(scanId, startedAt, durationMs, device, coverage, understander)
            }
        }
        if (scan == null) return null

        var packVersion = "1.0"
        var designSystem: DesignSystem? = null
        var graph = ScreenGraph(emptyList())
        db.rawQuery("SELECT design_system_json, graph_json, pack_version FROM pack_meta WHERE scan_id = ?", arrayOf(scanId)).use { c ->
            if (c.moveToFirst()) {
                val dsStr = if (c.isNull(0)) null else c.getString(0)
                val gStr = if (c.isNull(1)) null else c.getString(1)
                val pvStr = if (c.isNull(2)) null else c.getString(2)
                
                if (pvStr != null) packVersion = pvStr
                
                if (dsStr != null && dsStr != "null") {
                    val dsJson = JSONObject(dsStr)
                    
                    val colorsJson = dsJson.getJSONObject("colors")
                    val palette = mutableListOf<String>()
                    if (colorsJson.has("palette") && !colorsJson.isNull("palette")) {
                        val paletteArr = colorsJson.getJSONArray("palette")
                        for (i in 0 until paletteArr.length()) palette.add(paletteArr.getString(i))
                    }
                    val colors = ColorTokens(
                        primary = if (colorsJson.has("primary") && !colorsJson.isNull("primary")) colorsJson.getString("primary") else null,
                        onPrimary = if (colorsJson.has("on_primary") && !colorsJson.isNull("on_primary")) colorsJson.getString("on_primary") else null,
                        surface = if (colorsJson.has("surface") && !colorsJson.isNull("surface")) colorsJson.getString("surface") else null,
                        background = if (colorsJson.has("background") && !colorsJson.isNull("background")) colorsJson.getString("background") else null,
                        error = if (colorsJson.has("error") && !colorsJson.isNull("error")) colorsJson.getString("error") else null,
                        palette = palette
                    )
                    
                    val typography = mutableListOf<TypeToken>()
                    if (dsJson.has("typography") && !dsJson.isNull("typography")) {
                        val typoArr = dsJson.getJSONArray("typography")
                        for (i in 0 until typoArr.length()) {
                        val tJson = typoArr.getJSONObject(i)
                        typography.add(TypeToken(
                            role = tJson.getString("role"),
                            family = if (tJson.has("family") && !tJson.isNull("family")) tJson.getString("family") else null,
                            sizeSp = tJson.getDouble("size_sp"),
                            weight = tJson.getInt("weight")
                        ))
                        }
                    }
                    
                    val spacingJson = dsJson.getJSONObject("spacing")
                    val scale = mutableListOf<Int>()
                    if (spacingJson.has("scale") && !spacingJson.isNull("scale")) {
                        val scaleArr = spacingJson.getJSONArray("scale")
                        for (i in 0 until scaleArr.length()) scale.add(scaleArr.getInt(i))
                    }
                    val spacing = SpacingTokens(
                        baseDp = spacingJson.getInt("base_dp"),
                        scale = scale
                    )
                    
                    val shapeJson = dsJson.getJSONObject("shape")
                    val radiusDp = mutableListOf<Int>()
                    if (shapeJson.has("radius_dp") && !shapeJson.isNull("radius_dp")) {
                        val radiusArr = shapeJson.getJSONArray("radius_dp")
                        for (i in 0 until radiusArr.length()) radiusDp.add(radiusArr.getInt(i))
                    }
                    val shape = ShapeTokens(radiusDp)
                    
                    val components = mutableListOf<ComponentToken>()
                    if (dsJson.has("components") && !dsJson.isNull("components")) {
                        val compArr = dsJson.getJSONArray("components")
                        for (i in 0 until compArr.length()) {
                        val cJson = compArr.getJSONObject(i)
                        components.add(ComponentToken(
                            name = cJson.getString("name"),
                            fill = if (cJson.has("fill") && !cJson.isNull("fill")) cJson.getString("fill") else null,
                            text = if (cJson.has("text") && !cJson.isNull("text")) cJson.getString("text") else null,
                            radiusDp = if (cJson.has("radius_dp") && !cJson.isNull("radius_dp")) cJson.getInt("radius_dp") else null,
                            heightDp = if (cJson.has("height_dp") && !cJson.isNull("height_dp")) cJson.getInt("height_dp") else null,
                            seenOn = cJson.getInt("seen_on")
                        ))
                        }
                    }
                    
                    val modes = mutableMapOf<String, Map<String, String>>()
                    if (dsJson.has("modes") && !dsJson.isNull("modes")) {
                        val modesJson = dsJson.getJSONObject("modes")
                        modesJson.keys().forEach { modeKey ->
                        val innerModes = modesJson.getJSONObject(modeKey)
                        val innerMap = mutableMapOf<String, String>()
                        innerModes.keys().forEach { k -> innerMap[k] = innerModes.getString(k) }
                        modes[modeKey] = innerMap
                        }
                    }
                    
                    var toneOfVoice: ToneOfVoice? = null
                    if (dsJson.has("tone_of_voice") && !dsJson.isNull("tone_of_voice")) {
                        val tvJson = dsJson.getJSONObject("tone_of_voice")
                        val exArr = tvJson.getJSONArray("examples")
                        val examples = mutableListOf<String>()
                        for (i in 0 until exArr.length()) examples.add(exArr.getString(i))
                        toneOfVoice = ToneOfVoice(
                            register = tvJson.getString("register"),
                            summary = tvJson.getString("summary"),
                            examples = examples
                        )
                    }
                    
                    designSystem = DesignSystem(colors, typography, spacing, shape, components, modes, toneOfVoice)
                }
                
                if (gStr != null && gStr != "null") {
                    val gJson = JSONObject(gStr)
                    val edges = mutableListOf<GraphEdge>()
                    if (gJson.has("edges") && !gJson.isNull("edges")) {
                        val edgesArr = gJson.getJSONArray("edges")
                        for (i in 0 until edgesArr.length()) {
                        val eJson = edgesArr.getJSONObject(i)
                        edges.add(GraphEdge(
                            from = eJson.getString("from"),
                            to = eJson.getString("to"),
                            via = eJson.getString("via"),
                            action = ElementAction.valueOf(eJson.getString("action").uppercase())
                        ))
                        }
                    }
                    graph = ScreenGraph(edges)
                }
            }
        }

        val screens = mutableListOf<Screen>()
        db.rawQuery("SELECT id, name, purpose, kind, signature_json, modes_seen_json, screenshot_path FROM screens WHERE scan_id = ?", arrayOf(scanId)).use { sc ->
            while (sc.moveToNext()) {
                val screenId = sc.getString(0)
                val sigJson = JSONObject(sc.getString(4))
                
                val anchorsArr = sigJson.getJSONArray("anchors")
                val anchors = mutableListOf<String>()
                for (i in 0 until anchorsArr.length()) anchors.add(anchorsArr.getString(i))
                
                val signature = ScreenSignature(
                    structuralHash = sigJson.getString("structural_hash"),
                    anchors = anchors,
                    activity = if (sigJson.has("activity") && !sigJson.isNull("activity")) sigJson.getString("activity") else null
                )
                
                val modesArrStr = sc.getString(5)
                val modesArr = if (modesArrStr != "null") JSONArray(modesArrStr) else JSONArray()
                val modesSeen = mutableListOf<UiMode>()
                for (i in 0 until modesArr.length()) modesSeen.add(UiMode.valueOf(modesArr.getString(i).uppercase()))

                val elements = mutableListOf<Element>()
                db.rawQuery("SELECT id, role, label, semantic, bounds_rel_json, actions_json, leads_to, input_json FROM elements WHERE screen_id = ? AND scan_id = ?", arrayOf(screenId, scanId)).use { elC ->
                    while (elC.moveToNext()) {
                        val boundsArrStr = elC.getString(4)
                        val boundsArr = if (boundsArrStr != "null") JSONArray(boundsArrStr) else JSONArray()
                        val boundsRel = mutableListOf<Double>()
                        for (i in 0 until boundsArr.length()) boundsRel.add(boundsArr.getDouble(i))
                        
                        val actionsArrStr = elC.getString(5)
                        val actionsArr = if (actionsArrStr != "null") JSONArray(actionsArrStr) else JSONArray()
                        val actions = mutableListOf<ElementAction>()
                        for (i in 0 until actionsArr.length()) actions.add(ElementAction.valueOf(actionsArr.getString(i).uppercase()))
                        
                        var inputSpec: InputSpec? = null
                        val inputStr = if (elC.isNull(7)) null else elC.getString(7)
                        if (inputStr != null && inputStr.isNotEmpty() && inputStr != "null") {
                            val inJson = JSONObject(inputStr)
                            inputSpec = InputSpec(
                                type = InputType.valueOf(inJson.getString("type").uppercase()),
                                required = inJson.getBoolean("required"),
                                hint = if (inJson.has("hint") && !inJson.isNull("hint")) inJson.getString("hint") else null,
                                maxLength = if (inJson.has("max_length") && !inJson.isNull("max_length")) inJson.getInt("max_length") else null
                            )
                        }
                        
                        elements.add(Element(
                            id = elC.getString(0),
                            role = ElementRole.valueOf(elC.getString(1).uppercase()),
                            label = if (elC.isNull(2)) null else elC.getString(2),
                            semantic = if (elC.isNull(3)) null else elC.getString(3),
                            boundsRel = boundsRel,
                            actions = actions,
                            leadsTo = if (elC.isNull(6)) null else elC.getString(6),
                            input = inputSpec
                        ))
                    }
                }
                
                screens.add(Screen(
                    id = screenId,
                    name = if (sc.isNull(1)) null else sc.getString(1),
                    purpose = if (sc.isNull(2)) null else sc.getString(2),
                    kind = ScreenKind.valueOf(sc.getString(3).uppercase()),
                    signature = signature,
                    elements = elements,
                    screenshot = if (sc.isNull(6)) null else sc.getString(6),
                    modesSeen = modesSeen
                ))
            }
        }

        val journeys = mutableListOf<Journey>()
        db.rawQuery("SELECT id, name, goal, preconditions_json, steps_json, outcome, replayable, verified_at_scan FROM journeys WHERE scan_id = ?", arrayOf(scanId)).use { jc ->
            while (jc.moveToNext()) {
                val preStr = if (jc.isNull(3)) "null" else jc.getString(3)
                val preArr = if (preStr != "null") JSONArray(preStr) else JSONArray()
                val preconditions = mutableListOf<String>()
                for (i in 0 until preArr.length()) preconditions.add(preArr.getString(i))
                
                val stepsStr = jc.getString(4)
                val stepsArr = if (stepsStr != "null") JSONArray(stepsStr) else JSONArray()
                val steps = mutableListOf<JourneyStep>()
                for (i in 0 until stepsArr.length()) {
                    val sJson = stepsArr.getJSONObject(i)
                    steps.add(JourneyStep(
                        screen = sJson.getString("screen"),
                        element = sJson.getString("element"),
                        action = ElementAction.valueOf(sJson.getString("action").uppercase()),
                        inputSlot = if (sJson.has("input_slot") && !sJson.isNull("input_slot")) sJson.getString("input_slot") else null
                    ))
                }
                
                journeys.add(Journey(
                    id = jc.getString(0),
                    name = jc.getString(1),
                    goal = jc.getString(2),
                    preconditions = preconditions,
                    steps = steps,
                    outcome = if (jc.isNull(5)) null else jc.getString(5),
                    replayable = jc.getInt(6) == 1,
                    verifiedAtScan = if (jc.isNull(7)) null else jc.getString(7)
                ))
            }
        }

        return KnowledgePack(
            packVersion = packVersion,
            app = app!!,
            scan = scan!!,
            screens = screens,
            journeys = journeys,
            designSystem = designSystem,
            graph = graph
        )
    }

    override fun latest(packageName: String): KnowledgePack? {
        val db = helper.readableDatabase
        var latestScanId: String? = null
        db.rawQuery("SELECT id FROM scans WHERE app_package = ? ORDER BY started_at DESC LIMIT 1", arrayOf(packageName)).use { c ->
            if (c.moveToFirst()) latestScanId = c.getString(0)
        }
        return latestScanId?.let { load(packageName, it) }
    }
    
    override fun toCanonicalJson(pack: KnowledgePack): String {
        return CanonicalJson.toJson(pack)
    }

    override fun diff(old: KnowledgePack, new: KnowledgePack): PackDiff {
        return PackDiff(emptyList(), emptyList(), emptyList(), emptyList())
    }
}
