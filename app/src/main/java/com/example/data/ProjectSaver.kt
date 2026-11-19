package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Save/Open project file — Saver (Fase 2, T3).
 *
 * Menulis satu papan jadi arsip `.dc`: satu ZIP berisi
 * `manifest.json` + `images/` (pasangan full + thumb per item).
 * Format v1 sesuai `docs/designs/manifest-schema.md` dan
 * `docs/designs/save-open.md` §4.1 (K1–K6 disetujui, ADR-015).
 *
 * Kelas murni: tanpa dependensi Room, ViewModel, Compose, atau `Context`
 * — hanya `java.io`, `java.util.zip`, dan `org.json`. Entitas
 * `Board`/`CanvasItem`/`Edge` dipakai sebagai input karena itu data class
 * biasa. Pemanggil yang memilih `outputFile` (nanti `cacheDir`, Fase 4)
 * dan yang memetakan data UI ke [SavedStroke]/[CameraState] (Fase 4).
 *
 * Aturan yang ditegakkan di sini (bukan hanya di Open):
 * - K1: kolom mati `Board` (`backgroundType`, `canvasScale`,
 *   `canvasTranslateX/Y`) TIDAK masuk manifest. Kamera Board + Map masuk
 *   dari parameter [boardCamera]/[mapCamera] — bukan dari kolom, bukan
 *   dari SharedPreferences.
 * - K5: values study tidak disimpan sebagai hasil hitungan — hanya kolom
 *   `isValuesEnabled`/`simplicity`/`stopsCount`/`stopsJson` (dihitung
 *   ulang saat Open).
 * - Tiap item mendapat `uuid` baru per Save (`UUID.randomUUID()`); uuid
 *   itu juga stem nama berkas gambar di arsip. Id baris DB tidak pernah
 *   ditulis ke manifest.
 * - Berkas gambar disalin byte-per-byte (tidak di-decode).
 * - Gagal cepat: edge yang merujuk item di luar daftar, type edge yang
 *   tak dikenal, atau berkas sumber yang hilang → [IllegalArgumentException]
 *   sebelum ada byte yang ditulis ke `outputFile`.
 */
data class CameraState(
    val scale: Float,
    val translateX: Float,
    val translateY: Float
)

/**
 * Goresan dalam bentuk yang bisa disimpan — padanan [com.example.ui.DrawingStroke]
 * tanpa dependensi Compose (`DrawingStroke` memakai `Offset`).
 * ViewModel yang memetakan saat wiring (Fase 4).
 */
data class SavedStroke(
    val id: String,
    val color: Int,
    val width: Float,
    val points: List<Pair<Float, Float>>
)

object ProjectSaver {

    const val FORMAT_VERSION = 1

    /**
     * Menulis arsip `.dc` untuk satu papan ke [outputFile].
     *
     * @param board papan yang disimpan; hanya `name` dan `createdAt` yang
     *   masuk manifest (kolom mati dikecualikan, K1).
     * @param items semua item papan (urutannya dipertahankan di manifest).
     * @param edges garis Map; `fromItemId`/`toItemId` harus merujuk ke
     *   item di [items], `type` harus salah satu nama [ConnectionType].
     * @param strokes goresan papan (boleh kosong).
     * @param boardCamera kamera Board saat Save (dari ViewModel/prefs).
     * @param mapCamera kamera Map saat Save (dari ViewModel/prefs).
     * @param appVersion versi aplikasi penulis, mis. `BuildConfig.VERSION_NAME`.
     * @param outputFile berkas tujuan; direktori induk dibuat bila belum ada.
     * @throws IllegalArgumentException bila edge merujuk item yang tidak ada,
     *   type edge tak dikenal, atau berkas gambar sumber tidak ada.
     */
    fun save(
        board: Board,
        items: List<CanvasItem>,
        edges: List<Edge>,
        strokes: List<SavedStroke>,
        boardCamera: CameraState,
        mapCamera: CameraState,
        appVersion: String,
        outputFile: File
    ) {
        // Satu uuid baru per item — juga stem nama berkas di arsip.
        val uuidByItemId = LinkedHashMap<Int, String>(items.size)
        val entries = items.map { item ->
            val uuid = UUID.randomUUID().toString()
            uuidByItemId[item.id] = uuid
            uuid to item
        }

        // Todo-edges BELUM disimpan (T5): manifest belum punya todos[]
        // (T6), jadi garis ke/dari todo tidak bisa dirujuk di arsip —
        // lewati saja supaya Save tidak gagal (fail-fast di bawah akan
        // menolaknya karena id todo bukan id gambar); T6 menutup gap ini
        // (todos[] + kind di manifest). Todo sendiri juga belum disimpan.
        val itemEdges = edges.filter { it.fromKind == EdgeEndpointKind.ITEM && it.toKind == EdgeEndpointKind.ITEM }

        // Gagal cepat, sebelum ada byte yang ditulis.
        for (edge in itemEdges) {
            require(uuidByItemId.containsKey(edge.fromItemId)) {
                "Edge ${edge.id} merujuk ke item ${edge.fromItemId} yang tidak ada di papan '${board.name}'"
            }
            require(uuidByItemId.containsKey(edge.toItemId)) {
                "Edge ${edge.id} merujuk ke item ${edge.toItemId} yang tidak ada di papan '${board.name}'"
            }
            require(ConnectionType.entries.any { it.name == edge.type }) {
                "Edge ${edge.id} punya type tak dikenal: '${edge.type}'"
            }
        }
        for ((_, item) in entries) {
            require(File(item.fullPath).isFile) {
                "Berkas gambar item ${item.id} tidak ada: ${item.fullPath}"
            }
            require(File(item.thumbPath).isFile) {
                "Berkas thumbnail item ${item.id} tidak ada: ${item.thumbPath}"
            }
        }

        val manifest = buildManifest(
            board, entries, itemEdges, strokes,
            boardCamera, mapCamera, appVersion, uuidByItemId
        )

        outputFile.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((uuid, item) in entries) {
                val ext = extensionOf(item.fullPath)
                writeFileEntry(zip, "images/${uuid}_full.$ext", File(item.fullPath))
                writeFileEntry(zip, "images/${uuid}_thumb.jpg", File(item.thumbPath))
            }
        }
    }

    private fun buildManifest(
        board: Board,
        entries: List<Pair<String, CanvasItem>>,
        edges: List<Edge>,
        strokes: List<SavedStroke>,
        boardCamera: CameraState,
        mapCamera: CameraState,
        appVersion: String,
        uuidByItemId: Map<Int, String>
    ): JSONObject {
        val manifest = JSONObject()
        manifest.put("formatVersion", FORMAT_VERSION)
        manifest.put("appVersion", appVersion)

        val boardJson = JSONObject()
        boardJson.put("name", board.name)
        boardJson.put("createdAt", board.createdAt)
        boardJson.put("camera", cameraJson(boardCamera))
        boardJson.put("mapCamera", cameraJson(mapCamera))
        manifest.put("board", boardJson)

        val itemsJson = JSONArray()
        for ((uuid, item) in entries) {
            val itemJson = JSONObject()
            itemJson.put("uuid", uuid)
            itemJson.put("posX", item.posX)
            itemJson.put("posY", item.posY)
            itemJson.put("width", item.width)
            itemJson.put("height", item.height)
            itemJson.put("scale", item.scale)
            itemJson.put("rotation", item.rotation)
            itemJson.put("zIndex", item.zIndex)
            itemJson.put("isPinned", item.isPinned)
            itemJson.put("flipHorizontal", item.flipHorizontal)
            itemJson.put("flipVertical", item.flipVertical)
            itemJson.put("isValuesEnabled", item.isValuesEnabled)
            // simplicity adalah Int di entitas (0-100) — ditulis sebagai integer.
            itemJson.put("simplicity", item.simplicity)
            itemJson.put("stopsCount", item.stopsCount)
            itemJson.put("stopsJson", item.stopsJson)
            itemJson.put("isGrayscale", item.isGrayscale)
            itemJson.put("mapX", item.mapX)
            itemJson.put("mapY", item.mapY)
            itemJson.put("note", item.note)
            itemJson.put("fullFile", "images/${uuid}_full.${extensionOf(item.fullPath)}")
            itemJson.put("thumbFile", "images/${uuid}_thumb.jpg")
            itemsJson.put(itemJson)
        }
        manifest.put("items", itemsJson)

        val edgesJson = JSONArray()
        for (edge in edges) {
            val edgeJson = JSONObject()
            edgeJson.put("from", uuidByItemId.getValue(edge.fromItemId))
            edgeJson.put("to", uuidByItemId.getValue(edge.toItemId))
            edgeJson.put("type", edge.type)
            edgesJson.put(edgeJson)
        }
        manifest.put("edges", edgesJson)

        val strokesJson = JSONArray()
        for (stroke in strokes) {
            val strokeJson = JSONObject()
            strokeJson.put("id", stroke.id)
            strokeJson.put("color", stroke.color)
            strokeJson.put("width", stroke.width)
            val pointsJson = JSONArray()
            for ((x, y) in stroke.points) {
                val pointJson = JSONArray()
                pointJson.put(x)
                pointJson.put(y)
                pointsJson.put(pointJson)
            }
            strokeJson.put("points", pointsJson)
            strokesJson.put(strokeJson)
        }
        manifest.put("strokes", strokesJson)

        return manifest
    }

    private fun cameraJson(camera: CameraState): JSONObject {
        return JSONObject().apply {
            put("scale", camera.scale)
            put("translateX", camera.translateX)
            put("translateY", camera.translateY)
        }
    }

    private fun writeFileEntry(zip: ZipOutputStream, entryName: String, source: File) {
        zip.putNextEntry(ZipEntry(entryName))
        source.inputStream().use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    /**
     * Ekstensi berkas sumber, huruf kecil; tanpa ekstensi → "jpg"
     * (sama dengan default [ImageStorageHelper]).
     */
    private fun extensionOf(path: String): String {
        val name = File(path).name
        val dot = name.lastIndexOf('.')
        val ext = if (dot in 1 until name.length - 1) name.substring(dot + 1) else ""
        return if (ext.isEmpty()) "jpg" else ext.lowercase()
    }
}
