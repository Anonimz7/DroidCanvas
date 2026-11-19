package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Gagal membuka arsip — pesannya menyebut sebabnya (K6), bukan
 * "terjadi kesalahan".
 */
class ProjectOpenException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Hasil Open (aturan keras 6): papan BARU + kamera Board + kamera Map.
 *
 * Opener TIDAK menyentuh SharedPreferences — penyimpanan kamera
 * (`board_scale/trans_x/trans_y_$boardId`, `map_scale/trans_x/trans_y_$boardId`)
 * adalah urusan ViewModel (Fase 4, sisi Save sudah ada di `saveProject()`).
 */
data class OpenedProject(
    val boardId: Int,
    val boardCamera: CameraState,
    val mapCamera: CameraState
)

/**
 * Save/Open project file — Opener (Fase 3, T4).
 *
 * Membuka arsip `.dc` yang ditulis [ProjectSaver]: validasi penuh
 * → salin gambar ke `filesDir` → insert Board + items + edges dalam SATU
 * transaksi Room → tulis `drawing_strokes_<boardId>.txt`. Format manifest v1
 * mengikuti `docs/designs/manifest-schema.md`; alur mengikuti
 * `docs/designs/save-open.md` §4.2 (K1–K6, ADR-015).
 *
 * Keputusan implementasi (panduan Fase 3 §5.1, aturan 2 — "putuskan
 * caranya"): kelas ini menerima objek [DroidCanvasDatabase] dan memakai
 * `withTransaction` (room-ktx) untuk transaksi lintas-DAO. Alasannya:
 * repository saat ini tidak memegang objek database dan `@Insert` per-DAO
 * tidak bisa bertransaksi lintas DAO. Konsekuensinya repository dan
 * `MainActivity` tidak perlu diubah di fase ini.
 *
 * Kepuraian kelas (aturan keras 1): tanpa `Context`, ViewModel, atau
 * Compose — hanya `java.io`, `java.util.zip`, `org.json`, entitas data Room,
 * dan `DroidCanvasDatabase` sebagai target transaksi. JSON pakai `org.json`
 * (sama seperti Saver). Fungsi utama [open] `suspend`; I/O berkas di
 * [Dispatchers.IO].
 *
 * Aturan yang ditegakkan (skema §7 — 8 aturan validasi, K6):
 * 1. ZIP valid + `manifest.json` ada di root arsip.
 * 2. `manifest.json` JSON valid.
 * 3. `formatVersion` == [ProjectSaver.FORMAT_VERSION] (konstanta dipakai
 *    ulang, tidak diduplikasi) — pesan gagal menyebut versi yang ditemukan.
 * 4. Field wajib ada + tipe benar (integer/number/string/boolean/object/array).
 * 5. `uuid` item unik.
 * 6. `fullFile`/`thumbFile` ada di arsip, stem-nya `uuid` item
 *    (`<uuid>_full.<ekstensi>`, `<uuid>_thumb.<ekstensi>`), path relatif
 *    (nilai yang diawali `/` atau mengandung `..` ditolak).
 * 7. Tiap edge merujuk `uuid` yang ada + `type` salah satu dari 8
 *    [ConnectionType].
 * 8. `points` goresan tidak kosong dan tiap elemen pasangan angka [x, y].
 *
 * Kompatibilitas pra-Map (skema §8, aturan keras 4): arsip tanpa
 * `edges`/`mapX`/`mapY`/`note`/`mapCamera` terbuka bersih — tanpa edge,
 * `mapX`/`mapY` = 0, `note` = `""`, `mapCamera` default 1/0/0. Field
 * `mapCamera` yang hilang BUKAN galat. Board baru dibuat dengan `name` +
 * `createdAt` dari manifest; kolom mati `Board` dibiarkan default (K1).
 *
 * Aturan keras 3 (id selalu baru): `boardId` + `id` item dari
 * `autoGenerate`; nama berkas salinan memakai UUID baru
 * (`UUID.randomUUID()`), BUKAN uuid arsip — membuka arsip yang sama dua
 * kali (T7) tidak pernah menabrak nama berkas. (Catatan: `save-open.md`
 * §4.2 menyebut `ref_full_<newId>`; id DB tidak diketahui sebelum insert,
 * jadi nama berkas memakai UUID baru — maknanya sama: id baru, bukan uuid
 * arsip.)
 *
 * Aturan keras 5 (goresan): `drawing_strokes_<newBoardId>.txt` ditulis
 * dengan format teks yang SAMA seperti `DroidCanvasViewModel.saveDrawingStrokes`
 * (`id|color|width|x,y;x,y;…` per baris) supaya `loadDrawingStrokes` bisa
 * membacanya kembali.
 *
 * Aturan keras 7 (bersih saat gagal): validasi penuh dulu, sebelum menyalin
 * apa pun. Bila langkah apa pun gagal setelah penyalinan dimulai, berkas
 * yang sudah disalin dihapus; transaksi Room me-rollback papan — tidak ada
 * papan setengah jadi DAN tidak ada berkas yatim.
 */
object ProjectOpener {

    /**
     * Membuka satu arsip `.dc` menjadi papan BARU di [database].
     *
     * @param input berkas arsip (ZIP + `manifest.json`).
     * @param filesDir direktori berkas internal aplikasi (`filesDir`) tempat
     *   gambar disalin dan `drawing_strokes_<boardId>.txt` ditulis.
     * @param database target transaksi Room (Board + items + edges).
     * @return [OpenedProject] berisi id papan baru + kedua kamera dari
     *   manifest.
     * @throws ProjectOpenException arsip ditolak (pesan menyebut sebabnya)
     *   atau gagal dibuka; tidak ada papan baru dan tidak ada berkas yatim.
     */
    suspend fun open(input: File, filesDir: File, database: DroidCanvasDatabase): OpenedProject {
        val staged = mutableListOf<File>()
        try {
            val stagedProject = readAndStage(input, filesDir, staged)
            return insertAll(stagedProject, filesDir, database, staged)
        } catch (e: ProjectOpenException) {
            discard(staged)
            throw e
        } catch (e: Exception) {
            discard(staged)
            throw ProjectOpenException("Gagal membuka arsip: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    // ------------------------------------------------------------------
    // Tahap 1: validasi penuh + salin gambar (belum menyentuh database)
    // ------------------------------------------------------------------

    private data class StagedProject(
        val parsed: ParsedProject,
        val items: List<StagedItem>
    )

    private suspend fun readAndStage(
        input: File,
        filesDir: File,
        staged: MutableList<File>
    ): StagedProject = withContext(Dispatchers.IO) {
        if (!input.isFile) {
            throw ProjectOpenException("Berkas arsip tidak ada: ${input.name}")
        }
        try {
            ZipFile(input).use { zip ->
                val parsed = parseAndValidate(zip)
                // Validasi sudah selesai — baru sekarang menyalin (aturan 1).
                val items = copyImages(zip, parsed, filesDir, staged)
                StagedProject(parsed, items)
            }
        } catch (e: ProjectOpenException) {
            throw e
        } catch (e: ZipException) {
            throw ProjectOpenException("Bukan arsip ZIP yang valid: ${e.message}", e)
        } catch (e: JSONException) {
            throw ProjectOpenException("manifest.json bukan JSON valid: ${e.message}", e)
        } catch (e: IOException) {
            throw ProjectOpenException("Gagal membaca arsip: ${e.message}", e)
        }
    }

    private fun parseAndValidate(zip: ZipFile): ParsedProject {
        // Aturan 1: manifest.json harus ada di root arsip.
        val manifestEntry = zip.getEntry("manifest.json")
            ?: throw ProjectOpenException("manifest.json tidak ada di root arsip")
        val manifestText = try {
            zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            throw ProjectOpenException("Gagal membaca manifest.json: ${e.message}", e)
        }
        // Aturan 2: harus JSON valid.
        val manifest = try {
            JSONObject(manifestText)
        } catch (e: JSONException) {
            throw ProjectOpenException("manifest.json bukan JSON valid: ${e.message}", e)
        }

        // Aturan 3: formatVersion harus 1 — pesan menyebut versi yang ditemukan.
        val formatVersion = reqInt(manifest, "formatVersion", "formatVersion")
        if (formatVersion != ProjectSaver.FORMAT_VERSION) {
            throw ProjectOpenException(
                "Versi format tidak dikenal: $formatVersion " +
                    "(aplikasi ini hanya mendukung ${ProjectSaver.FORMAT_VERSION})"
            )
        }
        reqString(manifest, "appVersion", "appVersion") // informasi saja

        val boardJson = reqObject(manifest, "board", "board")
        val name = reqString(boardJson, "name", "board.name")
        val createdAt = reqLong(boardJson, "createdAt", "board.createdAt")
        val boardCamera = readCamera(reqObject(boardJson, "camera", "board.camera"), "board.camera")
        // Kompatibilitas pra-Map: mapCamera hilang = default 1/0/0, bukan galat.
        val mapCameraJson = optObject(boardJson, "mapCamera", "board.mapCamera")
        val mapCamera = if (mapCameraJson == null) {
            CameraState(scale = 1f, translateX = 0f, translateY = 0f)
        } else {
            readCamera(mapCameraJson, "board.mapCamera")
        }

        val itemsJson = reqArray(manifest, "items", "items")
        val uuids = HashSet<String>(itemsJson.length())
        val items = ArrayList<ParsedItem>(itemsJson.length())
        for (i in 0 until itemsJson.length()) {
            items.add(readItem(reqObject(itemsJson, i, "items[$i]"), "items[$i]", uuids, zip))
        }

        // Kompatibilitas pra-Map: edges/strokes hilang = kosong.
        val edgesJson = optArray(manifest, "edges", "edges") ?: JSONArray()
        val edges = ArrayList<ParsedEdge>(edgesJson.length())
        for (i in 0 until edgesJson.length()) {
            val path = "edges[$i]"
            val edgeJson = reqObject(edgesJson, i, path)
            val from = reqString(edgeJson, "from", "$path.from")
            val to = reqString(edgeJson, "to", "$path.to")
            val type = reqString(edgeJson, "type", "$path.type")
            // Aturan 7: kedua ujung harus merujuk uuid item yang ada.
            if (!uuids.contains(from)) {
                throw ProjectOpenException(
                    "Edge $path merujuk uuid 'from' yang tidak ada di manifest: '$from'"
                )
            }
            if (!uuids.contains(to)) {
                throw ProjectOpenException(
                    "Edge $path merujuk uuid 'to' yang tidak ada di manifest: '$to'"
                )
            }
            // Aturan 7: type harus salah satu dari 8 ConnectionType.
            if (ConnectionType.entries.none { it.name == type }) {
                throw ProjectOpenException(
                    "Edge $path punya type tak dikenal: '$type' " +
                        "(harus salah satu dari ${ConnectionType.entries.size} ConnectionType)"
                )
            }
            edges.add(ParsedEdge(from, to, type))
        }

        val strokesJson = optArray(manifest, "strokes", "strokes") ?: JSONArray()
        val strokes = ArrayList<ParsedStroke>(strokesJson.length())
        for (i in 0 until strokesJson.length()) {
            val path = "strokes[$i]"
            val strokeJson = reqObject(strokesJson, i, path)
            val pointsJson = reqArray(strokeJson, "points", "$path.points")
            // Aturan 8: points tidak kosong dan tiap elemen pasangan angka.
            if (pointsJson.length() < 1) {
                throw ProjectOpenException("Field '$path.points' tidak boleh kosong")
            }
            val points = ArrayList<Pair<Float, Float>>(pointsJson.length())
            for (p in 0 until pointsJson.length()) {
                val pointPath = "$path.points[$p]"
                val pointJson = reqArray(pointsJson, p, pointPath)
                if (pointJson.length() != 2) {
                    throw ProjectOpenException(
                        "Field '$pointPath' harus pasangan [x, y], " +
                            "ditemukan ${pointJson.length()} angka"
                    )
                }
                points.add(
                    reqNumber(pointJson, 0, "$pointPath[0]") to
                        reqNumber(pointJson, 1, "$pointPath[1]")
                )
            }
            strokes.add(
                ParsedStroke(
                    id = reqString(strokeJson, "id", "$path.id"),
                    color = reqInt(strokeJson, "color", "$path.color"),
                    width = reqNumber(strokeJson, "width", "$path.width"),
                    points = points
                )
            )
        }

        return ParsedProject(name, createdAt, boardCamera, mapCamera, items, edges, strokes)
    }

    private fun readItem(
        itemJson: JSONObject,
        path: String,
        uuids: MutableSet<String>,
        zip: ZipFile
    ): ParsedItem {
        val uuid = reqString(itemJson, "uuid", "$path.uuid")
        if (uuid.isBlank()) {
            throw ProjectOpenException("Field '$path.uuid' tidak boleh kosong")
        }
        // Aturan 5: uuid unik antar item.
        if (!uuids.add(uuid)) {
            throw ProjectOpenException("uuid item duplikat: '$uuid'")
        }
        val fullFile = reqString(itemJson, "fullFile", "$path.fullFile")
        val thumbFile = reqString(itemJson, "thumbFile", "$path.thumbFile")
        // Aturan 6: path relatif, stem = uuid, dan entri ada di arsip.
        checkRelativePath(fullFile, "$path.fullFile")
        checkRelativePath(thumbFile, "$path.thumbFile")
        checkImageEntry(zip, fullFile, uuid, "full", "$path.fullFile")
        checkImageEntry(zip, thumbFile, uuid, "thumb", "$path.thumbFile")

        return ParsedItem(
            uuid = uuid,
            posX = reqNumber(itemJson, "posX", "$path.posX"),
            posY = reqNumber(itemJson, "posY", "$path.posY"),
            width = reqNumber(itemJson, "width", "$path.width"),
            height = reqNumber(itemJson, "height", "$path.height"),
            scale = reqNumber(itemJson, "scale", "$path.scale"),
            rotation = reqNumber(itemJson, "rotation", "$path.rotation"),
            zIndex = reqInt(itemJson, "zIndex", "$path.zIndex"),
            isPinned = reqBoolean(itemJson, "isPinned", "$path.isPinned"),
            flipHorizontal = reqBoolean(itemJson, "flipHorizontal", "$path.flipHorizontal"),
            flipVertical = reqBoolean(itemJson, "flipVertical", "$path.flipVertical"),
            isValuesEnabled = reqBoolean(itemJson, "isValuesEnabled", "$path.isValuesEnabled"),
            simplicity = reqInt(itemJson, "simplicity", "$path.simplicity"),
            stopsCount = reqInt(itemJson, "stopsCount", "$path.stopsCount"),
            stopsJson = reqString(itemJson, "stopsJson", "$path.stopsJson"),
            isGrayscale = reqBoolean(itemJson, "isGrayscale", "$path.isGrayscale"),
            // Kompatibilitas pra-Map: field Map opsional dengan default.
            mapX = optNumber(itemJson, "mapX", "$path.mapX", 0f),
            mapY = optNumber(itemJson, "mapY", "$path.mapY", 0f),
            note = optString(itemJson, "note", "$path.note", ""),
            fullFile = fullFile,
            thumbFile = thumbFile
        )
    }

    private fun readCamera(json: JSONObject, path: String): CameraState = CameraState(
        scale = reqNumber(json, "scale", "$path.scale"),
        translateX = reqNumber(json, "translateX", "$path.translateX"),
        translateY = reqNumber(json, "translateY", "$path.translateY")
    )

    private fun checkRelativePath(path: String, fieldPath: String) {
        if (path.isBlank()) {
            throw ProjectOpenException("Field '$fieldPath' tidak boleh kosong")
        }
        if (path.startsWith("/") || path.startsWith("\\") || path.contains('\\')) {
            throw ProjectOpenException("Field '$fieldPath' harus path relatif di dalam arsip: '$path'")
        }
        if (path.split('/').any { it == ".." }) {
            throw ProjectOpenException("Field '$fieldPath' tidak boleh mengandung '..': '$path'")
        }
    }

    private fun checkImageEntry(
        zip: ZipFile,
        entryPath: String,
        uuid: String,
        kind: String,
        fieldPath: String
    ) {
        val base = entryPath.substringAfterLast('/')
        val prefix = "${uuid}_$kind."
        if (!base.startsWith(prefix) || base.length == prefix.length) {
            throw ProjectOpenException(
                "Field '$fieldPath' harus menunjuk '$prefix<ekstensi>' untuk uuid '$uuid', " +
                    "ditemukan '$entryPath'"
            )
        }
        if (zip.getEntry(entryPath) == null) {
            throw ProjectOpenException("Berkas '$entryPath' ($fieldPath) tidak ada di dalam arsip")
        }
    }

    /**
     * Menyalin tiap pasangan full+thumb ke `filesDir` dengan nama berkas
     * `ref_full_<uuid baru>.<ekstensi>` / `ref_thumb_<uuid baru>.jpg` —
     * UUID baru per Open (aturan keras 3), bukan uuid arsip. Setiap berkas
     * dicatat di [staged] SEBELUM ditulis supaya ikut terhapus bila gagal.
     */
    private fun copyImages(
        zip: ZipFile,
        parsed: ParsedProject,
        filesDir: File,
        staged: MutableList<File>
    ): List<StagedItem> {
        filesDir.mkdirs()
        return parsed.items.map { item ->
            val token = UUID.randomUUID().toString()
            val fullDest = File(filesDir, "ref_full_$token.${extensionOf(item.fullFile)}")
            val thumbDest = File(filesDir, "ref_thumb_$token.jpg")
            copyEntry(zip, item.fullFile, fullDest, staged)
            copyEntry(zip, item.thumbFile, thumbDest, staged)
            StagedItem(item, fullDest, thumbDest)
        }
    }

    private fun copyEntry(zip: ZipFile, entryPath: String, dest: File, staged: MutableList<File>) {
        val entry = zip.getEntry(entryPath)
            ?: throw ProjectOpenException("Berkas '$entryPath' tidak ada di dalam arsip")
        staged.add(dest)
        try {
            zip.getInputStream(entry).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: IOException) {
            throw ProjectOpenException("Gagal menyalin '$entryPath': ${e.message}", e)
        }
    }

    // ------------------------------------------------------------------
    // Tahap 2: satu transaksi Room (Board + items + edges) + berkas goresan
    // ------------------------------------------------------------------

    private suspend fun insertAll(
        stagedProject: StagedProject,
        filesDir: File,
        database: DroidCanvasDatabase,
        staged: MutableList<File>
    ): OpenedProject = database.withTransaction {
        val dao = database.droidCanvasDao()
        val edgeDao = database.edgeDao()
        val parsed = stagedProject.parsed

        // Aturan keras 3/4: id selalu baru dari autoGenerate; nama +
        // createdAt dari manifest; kolom mati Board dibiarkan default (K1).
        val boardId = dao.insertBoard(
            Board(name = parsed.name, createdAt = parsed.createdAt)
        ).toInt()

        // Peta uuid arsip → id DB baru, dibangun selagi insert item.
        val newIdByUuid = HashMap<String, Int>(stagedProject.items.size)
        for (stagedItem in stagedProject.items) {
            val item = stagedItem.item
            val newId = dao.insertCanvasItem(
                CanvasItem(
                    boardId = boardId,
                    fullPath = stagedItem.fullDest.absolutePath,
                    thumbPath = stagedItem.thumbDest.absolutePath,
                    posX = item.posX,
                    posY = item.posY,
                    width = item.width,
                    height = item.height,
                    scale = item.scale,
                    rotation = item.rotation,
                    zIndex = item.zIndex,
                    isPinned = item.isPinned,
                    flipHorizontal = item.flipHorizontal,
                    flipVertical = item.flipVertical,
                    isGrayscale = item.isGrayscale,
                    isValuesEnabled = item.isValuesEnabled,
                    simplicity = item.simplicity,
                    stopsCount = item.stopsCount,
                    stopsJson = item.stopsJson,
                    mapX = item.mapX,
                    mapY = item.mapY,
                    note = item.note
                )
            ).toInt()
            newIdByUuid[item.uuid] = newId
        }

        // Edge di-insert dengan id HASIL pemetaan, dalam transaksi yang sama.
        for (edge in parsed.edges) {
            edgeDao.insertEdge(
                Edge(
                    boardId = boardId,
                    fromItemId = newIdByUuid.getValue(edge.from),
                    toItemId = newIdByUuid.getValue(edge.to),
                    type = edge.type
                )
            )
        }

        // Aturan keras 5: format teks sama dengan saveDrawingStrokes.
        val strokesFile = File(filesDir, "drawing_strokes_$boardId.txt")
        staged.add(strokesFile)
        withContext(Dispatchers.IO) {
            strokesFile.writeText(strokesText(parsed.strokes))
        }

        OpenedProject(boardId, parsed.boardCamera, parsed.mapCamera)
    }

    /**
     * Sama persis dengan `DroidCanvasViewModel.saveDrawingStrokes`: satu
     * baris per goresan `id|color|width|x,y;x,y;…`, baris digabung `\n`.
     */
    private fun strokesText(strokes: List<ParsedStroke>): String =
        strokes.joinToString("\n") { stroke ->
            val pointsStr = stroke.points.joinToString(";") { "${it.first},${it.second}" }
            "${stroke.id}|${stroke.color}|${stroke.width}|$pointsStr"
        }

    /**
     * Bersih saat gagal (aturan keras 7): hapus berkas yang sudah/tengah
     * disalin. Transaksi Room sudah me-rollback papan dengan sendirinya.
     */
    private fun discard(files: List<File>) {
        for (file in files) {
            try {
                if (file.exists()) file.delete()
            } catch (_: IOException) {
                // best-effort — jangan menutupi sebab kegagalan yang asli
            }
        }
    }

    // ------------------------------------------------------------------
    // Parser ketat (aturan 4: field wajib ada + tipe benar)
    // ------------------------------------------------------------------

    private data class ParsedProject(
        val name: String,
        val createdAt: Long,
        val boardCamera: CameraState,
        val mapCamera: CameraState,
        val items: List<ParsedItem>,
        val edges: List<ParsedEdge>,
        val strokes: List<ParsedStroke>
    )

    private data class ParsedItem(
        val uuid: String,
        val posX: Float,
        val posY: Float,
        val width: Float,
        val height: Float,
        val scale: Float,
        val rotation: Float,
        val zIndex: Int,
        val isPinned: Boolean,
        val flipHorizontal: Boolean,
        val flipVertical: Boolean,
        val isValuesEnabled: Boolean,
        val simplicity: Int,
        val stopsCount: Int,
        val stopsJson: String,
        val isGrayscale: Boolean,
        val mapX: Float,
        val mapY: Float,
        val note: String,
        val fullFile: String,
        val thumbFile: String
    )

    private data class ParsedEdge(val from: String, val to: String, val type: String)

    private data class ParsedStroke(
        val id: String,
        val color: Int,
        val width: Float,
        val points: List<Pair<Float, Float>>
    )

    private data class StagedItem(val item: ParsedItem, val fullDest: File, val thumbDest: File)

    private fun rawOf(json: JSONObject, key: String, path: String): Any {
        val value = json.opt(key)
            ?: throw ProjectOpenException("Field wajib '$path' hilang")
        if (value === JSONObject.NULL) {
            throw ProjectOpenException("Field wajib '$path' bernilai null")
        }
        return value
    }

    private fun rawOf(array: JSONArray, index: Int, path: String): Any {
        val value = array.opt(index)
            ?: throw ProjectOpenException("Elemen '$path' hilang")
        if (value === JSONObject.NULL) {
            throw ProjectOpenException("Elemen '$path' bernilai null")
        }
        return value
    }

    private fun typeName(value: Any): String = when (value) {
        is String -> "string"
        is Boolean -> "boolean"
        is JSONObject -> "object"
        is JSONArray -> "array"
        else -> "number"
    }

    private fun reqString(json: JSONObject, key: String, path: String): String =
        when (val value = rawOf(json, key, path)) {
            is String -> value
            else -> throw ProjectOpenException(
                "Field '$path' harus string, ditemukan ${typeName(value)}"
            )
        }

    private fun reqBoolean(json: JSONObject, key: String, path: String): Boolean =
        when (val value = rawOf(json, key, path)) {
            is Boolean -> value
            else -> throw ProjectOpenException(
                "Field '$path' harus boolean, ditemukan ${typeName(value)}"
            )
        }

    private fun reqInt(json: JSONObject, key: String, path: String): Int =
        toInt(rawOf(json, key, path), path)

    private fun reqInt(array: JSONArray, index: Int, path: String): Int =
        toInt(rawOf(array, index, path), path)

    private fun toInt(value: Any, path: String): Int = when (value) {
        is Int -> value
        is Long -> value.toInt()
        else -> throw ProjectOpenException(
            "Field '$path' harus integer, ditemukan ${typeName(value)}"
        )
    }

    private fun reqLong(json: JSONObject, key: String, path: String): Long =
        when (val value = rawOf(json, key, path)) {
            is Int -> value.toLong()
            is Long -> value
            else -> throw ProjectOpenException(
                "Field '$path' harus integer, ditemukan ${typeName(value)}"
            )
        }

    private fun reqNumber(json: JSONObject, key: String, path: String): Float =
        toNumber(rawOf(json, key, path), path)

    private fun reqNumber(array: JSONArray, index: Int, path: String): Float =
        toNumber(rawOf(array, index, path), path)

    private fun toNumber(value: Any, path: String): Float = when (value) {
        is Int -> value.toFloat()
        is Long -> value.toFloat()
        is Double -> value.toFloat()
        else -> throw ProjectOpenException(
            "Field '$path' harus number, ditemukan ${typeName(value)}"
        )
    }

    private fun reqObject(json: JSONObject, key: String, path: String): JSONObject =
        when (val value = rawOf(json, key, path)) {
            is JSONObject -> value
            else -> throw ProjectOpenException(
                "Field '$path' harus object, ditemukan ${typeName(value)}"
            )
        }

    private fun reqObject(array: JSONArray, index: Int, path: String): JSONObject =
        when (val value = rawOf(array, index, path)) {
            is JSONObject -> value
            else -> throw ProjectOpenException(
                "Elemen '$path' harus object, ditemukan ${typeName(value)}"
            )
        }

    private fun reqArray(json: JSONObject, key: String, path: String): JSONArray =
        when (val value = rawOf(json, key, path)) {
            is JSONArray -> value
            else -> throw ProjectOpenException(
                "Field '$path' harus array, ditemukan ${typeName(value)}"
            )
        }

    private fun reqArray(array: JSONArray, index: Int, path: String): JSONArray =
        when (val value = rawOf(array, index, path)) {
            is JSONArray -> value
            else -> throw ProjectOpenException(
                "Elemen '$path' harus array, ditemukan ${typeName(value)}"
            )
        }

    private fun optObject(json: JSONObject, key: String, path: String): JSONObject? {
        val value = json.opt(key) ?: return null
        if (value === JSONObject.NULL) return null // tidak ada → default
        return value as? JSONObject ?: throw ProjectOpenException(
            "Field '$path' harus object, ditemukan ${typeName(value)}"
        )
    }

    private fun optArray(json: JSONObject, key: String, path: String): JSONArray? {
        val value = json.opt(key) ?: return null
        if (value === JSONObject.NULL) return null // tidak ada → default
        return value as? JSONArray ?: throw ProjectOpenException(
            "Field '$path' harus array, ditemukan ${typeName(value)}"
        )
    }

    private fun optString(json: JSONObject, key: String, path: String, default: String): String {
        val value = json.opt(key) ?: return default
        if (value === JSONObject.NULL) return default
        return value as? String ?: throw ProjectOpenException(
            "Field '$path' harus string, ditemukan ${typeName(value)}"
        )
    }

    private fun optNumber(json: JSONObject, key: String, path: String, default: Float): Float {
        val value = json.opt(key) ?: return default
        if (value === JSONObject.NULL) return default
        return toNumber(value, path)
    }

    /** Ekstensi berkas arsip, huruf kecil; tanpa ekstensi → "jpg". */
    private fun extensionOf(entryPath: String): String =
        File(entryPath).extension.lowercase().ifEmpty { "jpg" }
}
