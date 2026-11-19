package com.example

import com.example.data.Board
import com.example.data.CameraState
import com.example.data.CanvasItem
import com.example.data.Edge
import com.example.data.ProjectSaver
import com.example.data.SavedStroke
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * Fase 2 (T3): ProjectSaver menulis 1 papan jadi arsip `.dc`
 * dan isinya kembali identik saat dibaca ulang.
 *
 * Robolectric diperlukan karena `org.json` di JVM polos hanya stub
 * ("Stub!") — di bawah Robolectric ia implementasi Android yang asli,
 * sama seperti `ExampleRobolectricTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProjectSaverTest {

    private val boardCamera = CameraState(scale = 1.25f, translateX = 100f, translateY = -50f)
    private val mapCamera = CameraState(scale = 0.85f, translateX = 120f, translateY = -40f)

    private fun writeBytes(dir: File, name: String, bytes: ByteArray): File {
        return File(dir, name).apply { writeBytes(bytes) }
    }

    private fun floatOf(json: JSONObject, key: String): Float =
        json.getDouble(key).toFloat()

    private fun assertCameraEquals(expected: CameraState, actual: JSONObject) {
        assertEquals(expected.scale, floatOf(actual, "scale"), 0.001f)
        assertEquals(expected.translateX, floatOf(actual, "translateX"), 0.001f)
        assertEquals(expected.translateY, floatOf(actual, "translateY"), 0.001f)
    }

    private fun assertItemEquals(expected: CanvasItem, actual: JSONObject) {
        assertEquals(expected.posX, floatOf(actual, "posX"), 0.001f)
        assertEquals(expected.posY, floatOf(actual, "posY"), 0.001f)
        assertEquals(expected.width, floatOf(actual, "width"), 0.001f)
        assertEquals(expected.height, floatOf(actual, "height"), 0.001f)
        assertEquals(expected.scale, floatOf(actual, "scale"), 0.001f)
        assertEquals(expected.rotation, floatOf(actual, "rotation"), 0.001f)
        assertEquals(expected.zIndex, actual.getInt("zIndex"))
        assertEquals(expected.isPinned, actual.getBoolean("isPinned"))
        assertEquals(expected.flipHorizontal, actual.getBoolean("flipHorizontal"))
        assertEquals(expected.flipVertical, actual.getBoolean("flipVertical"))
        assertEquals(expected.isValuesEnabled, actual.getBoolean("isValuesEnabled"))
        assertEquals(expected.simplicity, actual.getInt("simplicity"))
        assertEquals(expected.stopsCount, actual.getInt("stopsCount"))
        assertEquals(expected.stopsJson, actual.getString("stopsJson"))
        assertEquals(expected.isGrayscale, actual.getBoolean("isGrayscale"))
        assertEquals(expected.mapX, floatOf(actual, "mapX"), 0.001f)
        assertEquals(expected.mapY, floatOf(actual, "mapY"), 0.001f)
        assertEquals(expected.note, actual.getString("note"))
    }

    @Test
    fun `round-trip satu papan kembali identik`() {
        val tempDir = Files.createTempDirectory("saver-roundtrip").toFile()
        try {
            // Sumber gambar: byte palsu kecil — serializer hanya menyalin
            // byte, tidak decode. Item 2 sengaja .png (uji ekstensi dipertahankan).
            val full1Bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
            val thumb1Bytes = byteArrayOf(9, 8, 7, 6, 5)
            val full2Bytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 4, 5, 6)
            val thumb2Bytes = byteArrayOf(11, 12, 13)
            val full1 = writeBytes(tempDir, "ref_full_1.jpg", full1Bytes)
            val thumb1 = writeBytes(tempDir, "ref_thumb_1.jpg", thumb1Bytes)
            val full2 = writeBytes(tempDir, "ref_full_2.png", full2Bytes)
            val thumb2 = writeBytes(tempDir, "ref_thumb_2.jpg", thumb2Bytes)

            // Kolom mati sengaja diisi nilai bukan-default (K1): nilainya
            // TIDAK BOLEH muncul di manifest, dan kamera di manifest harus
            // dari parameter — bukan dari kolom ini.
            val board = Board(
                id = 7, name = "Papan Uji", createdAt = 1795054800000L,
                backgroundType = "GRAPH",
                canvasScale = 2.5f, canvasTranslateX = 999f, canvasTranslateY = -999f
            )
            val item1 = CanvasItem(
                id = 11, boardId = 7,
                fullPath = full1.absolutePath, thumbPath = thumb1.absolutePath,
                posX = 120f, posY = 80f, width = 320f, height = 240f,
                scale = 1f, rotation = 0f, zIndex = 0,
                isPinned = false, flipHorizontal = true, flipVertical = false,
                isGrayscale = false, isValuesEnabled = true,
                simplicity = 2, stopsCount = 5, stopsJson = "[0.1,0.5,0.9]",
                mapX = 40f, mapY = 24f, note = "Catatan satu"
            )
            val item2 = CanvasItem(
                id = 22, boardId = 7,
                fullPath = full2.absolutePath, thumbPath = thumb2.absolutePath,
                posX = 480f, posY = 80f, width = 320f, height = 240f,
                scale = 0.5f, rotation = 90f, zIndex = 1,
                isPinned = true, flipHorizontal = false, flipVertical = true,
                isGrayscale = true, isValuesEnabled = false,
                simplicity = 0, stopsCount = 3, stopsJson = "",
                mapX = 0f, mapY = 0f, note = ""
            )
            val edge = Edge(id = 5, boardId = 7, fromItemId = 11, toItemId = 22, type = "SUPPORTS")
            val stroke = SavedStroke(
                id = "stroke-1", color = -16777216, width = 8f,
                points = listOf(10f to 20f, 30f to 40f, 50f to 60f)
            )

            val outputFile = File(tempDir, "papan-uji.dc")
            ProjectSaver.save(
                board = board, items = listOf(item1, item2),
                edges = listOf(edge), strokes = listOf(stroke),
                boardCamera = boardCamera, mapCamera = mapCamera,
                appVersion = "0.2.0", outputFile = outputFile
            )

            assertTrue("arsip harus tertulis", outputFile.isFile)

            ZipFile(outputFile).use { zip ->
                val entryNames = zip.entries().asSequence().toList().map { it.name }
                assertEquals("arsip berisi 5 entri", 5, entryNames.size)
                assertTrue(entryNames.contains("manifest.json"))

                val manifest = JSONObject(
                    zip.getInputStream(zip.getEntry("manifest.json"))
                        .bufferedReader(Charsets.UTF_8).readText()
                )

                // --- tingkat atas ---
                assertEquals(1, manifest.getInt("formatVersion"))
                assertEquals("0.2.0", manifest.getString("appVersion"))

                // --- board + kamera (K1) ---
                val boardJson = manifest.getJSONObject("board")
                assertEquals("Papan Uji", boardJson.getString("name"))
                assertEquals(1795054800000L, boardJson.getLong("createdAt"))
                assertCameraEquals(boardCamera, boardJson.getJSONObject("camera"))
                assertCameraEquals(mapCamera, boardJson.getJSONObject("mapCamera"))
                assertFalse("backgroundType (kolom mati) tidak boleh masuk manifest",
                    boardJson.has("backgroundType"))
                assertFalse("canvasScale (kolom mati) tidak boleh masuk manifest",
                    boardJson.has("canvasScale"))
                assertFalse("canvasTranslateX (kolom mati) tidak boleh masuk manifest",
                    boardJson.has("canvasTranslateX"))
                assertFalse("canvasTranslateY (kolom mati) tidak boleh masuk manifest",
                    boardJson.has("canvasTranslateY"))
                // Kamera dari parameter (1.25), BUKAN dari kolom mati (2.5).
                assertEquals(1.25f, floatOf(boardJson.getJSONObject("camera"), "scale"), 0.001f)

                // --- items ---
                val itemsJson = manifest.getJSONArray("items")
                assertEquals(2, itemsJson.length())
                val uuid1 = itemsJson.getJSONObject(0).getString("uuid")
                val uuid2 = itemsJson.getJSONObject(1).getString("uuid")
                assertTrue("uuid tidak boleh kosong", uuid1.isNotBlank())
                assertNotEquals("uuid tiap item harus beda", uuid1, uuid2)
                assertItemEquals(item1, itemsJson.getJSONObject(0))
                assertItemEquals(item2, itemsJson.getJSONObject(1))
                assertEquals("images/${uuid1}_full.jpg",
                    itemsJson.getJSONObject(0).getString("fullFile"))
                assertEquals("images/${uuid1}_thumb.jpg",
                    itemsJson.getJSONObject(0).getString("thumbFile"))
                assertEquals("images/${uuid2}_full.png",
                    itemsJson.getJSONObject(1).getString("fullFile"))
                assertEquals("images/${uuid2}_thumb.jpg",
                    itemsJson.getJSONObject(1).getString("thumbFile"))

                // --- edges: ter-resolve lewat uuid ---
                val edgesJson = manifest.getJSONArray("edges")
                assertEquals(1, edgesJson.length())
                val edgeJson = edgesJson.getJSONObject(0)
                assertEquals(uuid1, edgeJson.getString("from"))
                assertEquals(uuid2, edgeJson.getString("to"))
                assertEquals("SUPPORTS", edgeJson.getString("type"))

                // --- strokes ---
                val strokesJson = manifest.getJSONArray("strokes")
                assertEquals(1, strokesJson.length())
                val strokeJson = strokesJson.getJSONObject(0)
                assertEquals("stroke-1", strokeJson.getString("id"))
                assertEquals(-16777216, strokeJson.getInt("color"))
                assertEquals(8f, floatOf(strokeJson, "width"), 0.001f)
                val pointsJson = strokeJson.getJSONArray("points")
                assertEquals(3, pointsJson.length())
                val expectedPoints = listOf(10f to 20f, 30f to 40f, 50f to 60f)
                for (i in expectedPoints.indices) {
                    val point = pointsJson.getJSONArray(i)
                    assertEquals(expectedPoints[i].first, point.getDouble(0).toFloat(), 0.001f)
                    assertEquals(expectedPoints[i].second, point.getDouble(1).toFloat(), 0.001f)
                }

                // --- byte gambar di arsip identik dengan sumber ---
                assertArrayEquals(full1Bytes,
                    zip.getInputStream(zip.getEntry("images/${uuid1}_full.jpg")).readBytes())
                assertArrayEquals(thumb1Bytes,
                    zip.getInputStream(zip.getEntry("images/${uuid1}_thumb.jpg")).readBytes())
                assertArrayEquals(full2Bytes,
                    zip.getInputStream(zip.getEntry("images/${uuid2}_full.png")).readBytes())
                assertArrayEquals(thumb2Bytes,
                    zip.getInputStream(zip.getEntry("images/${uuid2}_thumb.jpg")).readBytes())
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `papan kosong tetap menulis manifest lengkap`() {
        val tempDir = Files.createTempDirectory("saver-empty").toFile()
        try {
            val board = Board(id = 8, name = "Kosong", createdAt = 1795054800000L)
            val outputFile = File(tempDir, "kosong.dc")
            ProjectSaver.save(
                board = board, items = emptyList(), edges = emptyList(),
                strokes = emptyList(), boardCamera = boardCamera,
                mapCamera = mapCamera, appVersion = "0.2.0", outputFile = outputFile
            )
            ZipFile(outputFile).use { zip ->
                val names = zip.entries().asSequence().toList().map { it.name }
                assertEquals(listOf("manifest.json"), names)
                val manifest = JSONObject(
                    zip.getInputStream(zip.getEntry("manifest.json"))
                        .bufferedReader(Charsets.UTF_8).readText()
                )
                assertEquals(1, manifest.getInt("formatVersion"))
                assertEquals("Kosong", manifest.getJSONObject("board").getString("name"))
                assertEquals(0, manifest.getJSONArray("items").length())
                assertEquals(0, manifest.getJSONArray("edges").length())
                assertEquals(0, manifest.getJSONArray("strokes").length())
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `edge ke item yang tidak ada ditolak sebelum menulis`() {
        val tempDir = Files.createTempDirectory("saver-bad-edge").toFile()
        try {
            val board = Board(id = 9, name = "Papan", createdAt = 0L)
            val outputFile = File(tempDir, "gagal.dc")
            val ex = assertThrows(IllegalArgumentException::class.java) {
                ProjectSaver.save(
                    board = board, items = emptyList(),
                    edges = listOf(Edge(id = 1, boardId = 9, fromItemId = 99, toItemId = 100, type = "SUPPORTS")),
                    strokes = emptyList(), boardCamera = boardCamera,
                    mapCamera = mapCamera, appVersion = "0.2.0", outputFile = outputFile
                )
            }
            assertTrue(ex.message!!.contains("99"))
            assertFalse("tidak boleh ada arsip setengah jadi", outputFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `type edge tak dikenal ditolak sebelum menulis`() {
        val tempDir = Files.createTempDirectory("saver-bad-type").toFile()
        try {
            val item = CanvasItem(
                id = 1, boardId = 9, fullPath = "", thumbPath = "",
                posX = 0f, posY = 0f, width = 1f, height = 1f
            )
            val board = Board(id = 9, name = "Papan", createdAt = 0L)
            val outputFile = File(tempDir, "gagal.dc")
            assertThrows(IllegalArgumentException::class.java) {
                ProjectSaver.save(
                    board = board, items = listOf(item),
                    edges = listOf(Edge(id = 1, boardId = 9, fromItemId = 1, toItemId = 1, type = "BUKAN_TYPE")),
                    strokes = emptyList(), boardCamera = boardCamera,
                    mapCamera = mapCamera, appVersion = "0.2.0", outputFile = outputFile
                )
            }
            assertFalse("tidak boleh ada arsip setengah jadi", outputFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `berkas gambar sumber hilang ditolak sebelum menulis`() {
        val tempDir = Files.createTempDirectory("saver-missing-file").toFile()
        try {
            val item = CanvasItem(
                id = 1, boardId = 9,
                fullPath = File(tempDir, "tidak-ada.jpg").absolutePath,
                thumbPath = File(tempDir, "tidak-ada-thumb.jpg").absolutePath,
                posX = 0f, posY = 0f, width = 1f, height = 1f
            )
            val board = Board(id = 9, name = "Papan", createdAt = 0L)
            val outputFile = File(tempDir, "gagal.dc")
            assertThrows(IllegalArgumentException::class.java) {
                ProjectSaver.save(
                    board = board, items = listOf(item), edges = emptyList(),
                    strokes = emptyList(), boardCamera = boardCamera,
                    mapCamera = mapCamera, appVersion = "0.2.0", outputFile = outputFile
                )
            }
            assertFalse("tidak boleh ada arsip setengah jadi", outputFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
