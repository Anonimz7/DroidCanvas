package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Board
import com.example.data.CameraState
import com.example.data.CanvasItem
import com.example.data.DroidCanvasDatabase
import com.example.data.Edge
import com.example.data.ProjectOpenException
import com.example.data.ProjectOpener
import com.example.data.ProjectSaver
import com.example.data.SavedStroke
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Fase 3 (T4): `ProjectOpener` membuka arsip `.droidcanvas` jadi papan BARU
 * — validasi → salin → satu transaksi Room — dan menolak arsip yang tidak
 * valid tanpa meninggalkan papan setengah jadi / berkas yatim.
 *
 * Robolectric diperlukan karena `org.json` + Room di JVM polos hanya stub;
 * sama pola dengan `ProjectSaverTest` / `ExampleRobolectricTest`.
 * Room in-memory jadi target transaksi (belum ada preseden Room di test
 * repo ini — lihat `docs/designs/save-open.md` §5.1).
 *
 * Catatan: nama test backtick di file ini sengaja ASCII — nama fun masuk ke
 * nama berkas class (`ProjectOpenerTest$<nama fun>$…class`), dan di build
 * dengan locale POSIX nama non-ASCII membuat kotlinc gagal dengan
 * `InvalidPathException`. Jangan tambahkan em dash/emoji di nama test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProjectOpenerTest {

    private lateinit var database: DroidCanvasDatabase
    private lateinit var workDir: File
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DroidCanvasDatabase::class.java).build()
        workDir = Files.createTempDirectory("opener-test").toFile()
        filesDir = File(workDir, "filesDir").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        database.close()
        workDir.deleteRecursively()
    }

    // ------------------------------------------------------------------
    // Round-trip penuh
    // ------------------------------------------------------------------

    @Test
    fun `round-trip penuh - papan, item, edge, goresan dan kamera kembali identik dengan id baru`() {
        val srcDir = File(workDir, "src").apply { mkdirs() }
        val full1Bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
        val thumb1Bytes = byteArrayOf(9, 8, 7, 6, 5)
        val full2Bytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 4, 5, 6)
        val thumb2Bytes = byteArrayOf(11, 12, 13)
        val full1 = writeBytes(srcDir, "ref_full_1.jpg", full1Bytes)
        val thumb1 = writeBytes(srcDir, "ref_thumb_1.jpg", thumb1Bytes)
        val full2 = writeBytes(srcDir, "ref_full_2.png", full2Bytes) // ekstensi beda
        val thumb2 = writeBytes(srcDir, "ref_thumb_2.jpg", thumb2Bytes)

        // Kolom mati Board sengaja diisi nilai bukan-default (K1): nilainya
        // TIDAK BOLEH ikut terbawa — kolom mati dibiarkan default (aturan 4).
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
        val boardCamera = CameraState(scale = 1.25f, translateX = 100f, translateY = -50f)
        val mapCamera = CameraState(scale = 0.85f, translateX = 120f, translateY = -40f)

        val archive = File(workDir, "papan-uji.droidcanvas")
        ProjectSaver.save(
            board = board, items = listOf(item1, item2),
            edges = listOf(edge), strokes = listOf(stroke),
            boardCamera = boardCamera, mapCamera = mapCamera,
            appVersion = "0.2.0", outputFile = archive
        )

        // uuid arsip (stem nama berkas di dalam ZIP) — dipakai untuk membuktikan
        // nama berkas salinan TIDAK memakai uuid arsip (aturan keras 3).
        val (uuid1, uuid2) = ZipFile(archive).use { zip ->
            val manifest = JSONObject(
                zip.getInputStream(zip.getEntry("manifest.json"))
                    .bufferedReader(Charsets.UTF_8).readText()
            )
            val items = manifest.getJSONArray("items")
            items.getJSONObject(0).getString("uuid") to
                items.getJSONObject(1).getString("uuid")
        }

        val result = runBlocking { ProjectOpener.open(archive, filesDir, database) }

        // --- papan BARU (id baru), nama + createdAt sama, kolom mati default ---
        assertNotEquals("boardId harus baru", board.id, result.boardId)
        assertTrue("boardId harus dari autoGenerate", result.boardId > 0)
        val boards = runBlocking { database.droidCanvasDao().getAllBoards().first() }
        assertEquals(1, boards.size)
        val saved = boards.single()
        assertEquals(result.boardId, saved.id)
        assertEquals("Papan Uji", saved.name)
        assertEquals(1795054800000L, saved.createdAt)
        assertEquals("SLATE", saved.backgroundType)   // kolom mati → default (K1)
        assertEquals(1.0, saved.canvasScale.toDouble(), 0.001)
        assertEquals(0.0, saved.canvasTranslateX.toDouble(), 0.001)
        assertEquals(0.0, saved.canvasTranslateY.toDouble(), 0.001)

        // --- kamera dikembalikan ke pemanggil (aturan keras 6) ---
        assertCameraEquals(boardCamera, result.boardCamera)
        assertCameraEquals(mapCamera, result.mapCamera)

        // --- items: semua field identik, id + berkas baru ---
        val items = runBlocking { database.droidCanvasDao().getItemsForBoard(result.boardId).first() }
        assertEquals(2, items.size)
        val opened1 = items[0]
        val opened2 = items[1]
        assertItemFields(item1, opened1)
        assertItemFields(item2, opened2)
        assertNotEquals("id item harus baru", item1.id, opened1.id)
        assertNotEquals("id item harus baru", item2.id, opened2.id)
        assertEquals(result.boardId, opened1.boardId)
        assertEquals(result.boardId, opened2.boardId)

        val openedFull1 = File(opened1.fullPath)
        val openedThumb1 = File(opened1.thumbPath)
        val openedFull2 = File(opened2.fullPath)
        val openedThumb2 = File(opened2.thumbPath)
        for (f in listOf(openedFull1, openedThumb1, openedFull2, openedThumb2)) {
            assertTrue("berkas salinan harus ada: ${f.name}", f.isFile)
        }
        assertArrayEquals(full1Bytes, openedFull1.readBytes())
        assertArrayEquals(thumb1Bytes, openedThumb1.readBytes())
        assertArrayEquals(full2Bytes, openedFull2.readBytes())
        assertArrayEquals(thumb2Bytes, openedThumb2.readBytes())
        // Ekstensi ikut terbawa (item 2 sengaja .png).
        assertTrue(openedFull2.name.endsWith(".png"))
        // Nama berkas memakai UUID baru, bukan uuid arsip (aturan keras 3).
        for (f in listOf(openedFull1, openedThumb1, openedFull2, openedThumb2)) {
            assertTrue("prefiks harus ref_full_/ref_thumb_: ${f.name}",
                f.name.startsWith("ref_full_") || f.name.startsWith("ref_thumb_"))
            assertFalse("tidak boleh memakai uuid arsip: ${f.name}", f.name.contains(uuid1))
            assertFalse("tidak boleh memakai uuid arsip: ${f.name}", f.name.contains(uuid2))
        }

        // --- edge ter-resolve ke id BARU ---
        val edges = runBlocking { database.edgeDao().getEdgesForBoard(result.boardId).first() }
        assertEquals(1, edges.size)
        val openedEdge = edges.single()
        assertEquals(result.boardId, openedEdge.boardId)
        assertEquals(opened1.id, openedEdge.fromItemId)
        assertEquals(opened2.id, openedEdge.toItemId)
        assertNotEquals("edge tidak boleh memakai id item arsip", 11, openedEdge.fromItemId)
        assertEquals("SUPPORTS", openedEdge.type)

        // --- goresan: format teks sama dengan saveDrawingStrokes ---
        val strokesFile = File(filesDir, "drawing_strokes_${result.boardId}.txt")
        assertTrue("berkas goresan harus tertulis", strokesFile.isFile)
        val expectedStrokes = "stroke-1|-16777216|${8f}|${10f},${20f};${30f},${40f};${50f},${60f}"
        assertEquals(expectedStrokes, strokesFile.readText())

        // --- isi filesDir: 4 berkas gambar + 1 berkas goresan, tidak lebih ---
        val copied = filesDir.list()!!.toList()
        assertEquals("salinan + goresan saja: $copied", 5, copied.size)
    }

    // ------------------------------------------------------------------
    // Penolakan — setiap kasus: jumlah papan tetap + tidak ada berkas tersalin
    // ------------------------------------------------------------------

    @Test
    fun `versi format tak dikenal ditolak dengan pesan yang menyebut versi`() {
        val archive = File(workDir, "v2.droidcanvas")
        writeArchive(
            archive,
            manifestWithItem(formatVersion = 2),
            mapOf("images/u1_full.jpg" to byteArrayOf(1), "images/u1_thumb.jpg" to byteArrayOf(2))
        )
        assertOpenFails(archive, "2")
    }

    @Test
    fun `zip korup ditolak`() {
        val archive = File(workDir, "korup.droidcanvas")
        archive.writeBytes(ByteArray(32) { it.toByte() }) // byte sampah, bukan ZIP
        assertOpenFails(archive, "ZIP")
    }

    @Test
    fun `gambar yang dirujuk manifest tidak ada di arsip ditolak`() {
        val archive = File(workDir, "tanpa-gambar.droidcanvas")
        writeArchive(archive, manifestWithItem()) // tanpa entri images/
        assertOpenFails(archive, "u1_full.jpg")
    }

    @Test
    fun `edge dengan ujung uuid yang tidak ada ditolak`() {
        val archive = File(workDir, "edge-ujung-hilang.droidcanvas")
        val edges = JSONArray().put(
            JSONObject().apply {
                put("from", "u1"); put("to", "ghost"); put("type", "PLAIN")
            }
        )
        writeArchive(
            archive,
            manifestWithItem(edges = edges),
            mapOf("images/u1_full.jpg" to byteArrayOf(1), "images/u1_thumb.jpg" to byteArrayOf(2))
        )
        assertOpenFails(archive, "ghost")
    }

    @Test
    fun `edge dengan type tak dikenal ditolak`() {
        val archive = File(workDir, "edge-type-salah.droidcanvas")
        val edges = JSONArray().put(
            JSONObject().apply {
                put("from", "u1"); put("to", "u1"); put("type", "NOPE")
            }
        )
        writeArchive(
            archive,
            manifestWithItem(edges = edges),
            mapOf("images/u1_full.jpg" to byteArrayOf(1), "images/u1_thumb.jpg" to byteArrayOf(2))
        )
        assertOpenFails(archive, "NOPE")
    }

    // ------------------------------------------------------------------
    // Kompatibilitas pra-Map
    // ------------------------------------------------------------------

    @Test
    fun `arsip pra-Map terbuka bersih - tanpa edge, map 0, note kosong, mapCamera default`() {
        val archive = File(workDir, "pra-map.droidcanvas")
        val manifest = manifestWithItem() // tanpa edges/strokes/mapCamera; item tanpa mapX/mapY/note
        writeArchive(
            archive,
            manifest,
            mapOf("images/u1_full.jpg" to byteArrayOf(1), "images/u1_thumb.jpg" to byteArrayOf(2))
        )

        val result = runBlocking { ProjectOpener.open(archive, filesDir, database) }

        val boards = runBlocking { database.droidCanvasDao().getAllBoards().first() }
        assertEquals(1, boards.size)
        assertEquals("Papan Uji", boards.single().name)
        assertEquals(1795054800000L, boards.single().createdAt)

        val items = runBlocking { database.droidCanvasDao().getItemsForBoard(result.boardId).first() }
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(0.0, item.mapX.toDouble(), 0.001)
        assertEquals(0.0, item.mapY.toDouble(), 0.001)
        assertEquals("", item.note)

        val edges = runBlocking { database.edgeDao().getEdgesForBoard(result.boardId).first() }
        assertTrue("arsip pra-Map harus terbuka tanpa edge", edges.isEmpty())

        // mapCamera hilang = default 1/0/0 (bukan galat).
        assertEquals(1.0, result.mapCamera.scale.toDouble(), 0.001)
        assertEquals(0.0, result.mapCamera.translateX.toDouble(), 0.001)
        assertEquals(0.0, result.mapCamera.translateY.toDouble(), 0.001)
        // camera board tetap dari manifest.
        assertEquals(2.0, result.boardCamera.scale.toDouble(), 0.001)

        val strokesFile = File(filesDir, "drawing_strokes_${result.boardId}.txt")
        assertTrue(strokesFile.isFile)
        assertEquals("", strokesFile.readText())
    }

    // ------------------------------------------------------------------
    // T7: buka berkas yang sama dua kali (Fase 5)
    // ------------------------------------------------------------------

    @Test
    fun `buka berkas yang sama dua kali - dua papan baru tanpa tabrakan id atau berkas`() {
        val srcDir = File(workDir, "src").apply { mkdirs() }
        val fullBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
        val thumbBytes = byteArrayOf(9, 8, 7, 6, 5)
        val full = writeBytes(srcDir, "ref_full_1.jpg", fullBytes)
        val thumb = writeBytes(srcDir, "ref_thumb_1.jpg", thumbBytes)
        val board = Board(id = 7, name = "Papan Uji", createdAt = 1795054800000L)
        val item = CanvasItem(
            id = 11, boardId = 7,
            fullPath = full.absolutePath, thumbPath = thumb.absolutePath,
            posX = 120f, posY = 80f, width = 320f, height = 240f,
            scale = 1f, rotation = 0f, zIndex = 0,
            isPinned = false, flipHorizontal = false, flipVertical = false,
            isGrayscale = false, isValuesEnabled = false,
            simplicity = 0, stopsCount = 3, stopsJson = "",
            mapX = 40f, mapY = 24f, note = "Catatan satu"
        )
        val stroke = SavedStroke(
            id = "stroke-1", color = -16777216, width = 8f,
            points = listOf(10f to 20f, 30f to 40f)
        )
        val archive = File(workDir, "papan-uji.droidcanvas")
        ProjectSaver.save(
            board = board, items = listOf(item), edges = emptyList(),
            strokes = listOf(stroke),
            boardCamera = CameraState(scale = 1.25f, translateX = 100f, translateY = -50f),
            mapCamera = CameraState(scale = 0.85f, translateX = 120f, translateY = -40f),
            appVersion = "0.2.0", outputFile = archive
        )

        val result1 = runBlocking { ProjectOpener.open(archive, filesDir, database) }
        val result2 = runBlocking { ProjectOpener.open(archive, filesDir, database) }

        // Dua papan baru, id berbeda.
        assertNotEquals("boardId kedua harus berbeda", result1.boardId, result2.boardId)
        assertEquals(2, boardCount())

        val items1 = runBlocking { database.droidCanvasDao().getItemsForBoard(result1.boardId).first() }
        val items2 = runBlocking { database.droidCanvasDao().getItemsForBoard(result2.boardId).first() }
        assertEquals(1, items1.size)
        assertEquals(1, items2.size)
        val opened1 = items1.single()
        val opened2 = items2.single()
        assertNotEquals("id item harus berbeda", opened1.id, opened2.id)
        assertNotEquals("berkas full tidak boleh sama", opened1.fullPath, opened2.fullPath)
        assertNotEquals("berkas thumb tidak boleh sama", opened1.thumbPath, opened2.thumbPath)
        assertArrayEquals(fullBytes, File(opened1.fullPath).readBytes())
        assertArrayEquals(fullBytes, File(opened2.fullPath).readBytes())
        assertArrayEquals(thumbBytes, File(opened1.thumbPath).readBytes())
        assertArrayEquals(thumbBytes, File(opened2.thumbPath).readBytes())
        assertItemFields(item, opened1)
        assertItemFields(item, opened2)

        // Goresan tertulis untuk kedua papan, isi sama.
        val expectedStrokes = "stroke-1|-16777216|${8f}|${10f},${20f};${30f},${40f}"
        for (boardId in listOf(result1.boardId, result2.boardId)) {
            val strokesFile = File(filesDir, "drawing_strokes_$boardId.txt")
            assertTrue(strokesFile.isFile)
            assertEquals(expectedStrokes, strokesFile.readText())
        }

        // Isi filesDir: 2 x (2 gambar + 1 goresan), tidak lebih.
        assertEquals(6, filesDir.list()!!.size)
    }

    // ------------------------------------------------------------------
    // T8: papan besar 20 gambar (Fase 5) — kebenaran isi di skala besar;
    // ANR + values study terhitung ulang tetap uji perangkat (Fase 7).
    // ------------------------------------------------------------------

    @Test
    fun `papan besar 20 gambar - semua item, edge dan goresan kembali utuh`() {
        val srcDir = File(workDir, "src").apply { mkdirs() }
        val expectedItems = ArrayList<CanvasItem>(20)
        val expectedBytes = HashMap<String, ByteArray>(40)
        for (i in 0 until 20) {
            val fullBytes = ByteArray(16) { (i * 7 + it).toByte() }
            val thumbBytes = ByteArray(8) { (i * 3 + it).toByte() }
            val full = writeBytes(srcDir, "ref_full_$i.jpg", fullBytes)
            val thumb = writeBytes(srcDir, "ref_thumb_$i.jpg", thumbBytes)
            expectedBytes["full-$i"] = fullBytes
            expectedBytes["thumb-$i"] = thumbBytes
            expectedItems.add(
                CanvasItem(
                    id = 100 + i, boardId = 7,
                    fullPath = full.absolutePath, thumbPath = thumb.absolutePath,
                    posX = (i * 10).toFloat(), posY = 80f, width = 320f, height = 240f,
                    scale = 1f, rotation = 0f, zIndex = i,
                    isPinned = i % 2 == 0, flipHorizontal = false, flipVertical = false,
                    isGrayscale = i % 3 == 0, isValuesEnabled = false,
                    simplicity = i % 5, stopsCount = 3, stopsJson = "",
                    mapX = (i * 5).toFloat(), mapY = 0f, note = if (i == 0) "Pertama" else ""
                )
            )
        }
        val edge = Edge(id = 5, boardId = 7, fromItemId = 100, toItemId = 101, type = "RELATED_TO")
        val stroke1 = SavedStroke(
            id = "stroke-1", color = -16777216, width = 8f,
            points = listOf(10f to 20f, 30f to 40f)
        )
        val stroke2 = SavedStroke(
            id = "stroke-2", color = -65536, width = 4f,
            points = listOf(1f to 2f, 3f to 4f, 5f to 6f)
        )
        val board = Board(id = 7, name = "Papan Besar", createdAt = 1795054800000L)
        val archive = File(workDir, "papan-besar.droidcanvas")
        ProjectSaver.save(
            board = board, items = expectedItems, edges = listOf(edge),
            strokes = listOf(stroke1, stroke2),
            boardCamera = CameraState(scale = 1f, translateX = 0f, translateY = 0f),
            mapCamera = CameraState(scale = 1f, translateX = 0f, translateY = 0f),
            appVersion = "0.2.0", outputFile = archive
        )

        val result = runBlocking { ProjectOpener.open(archive, filesDir, database) }

        val items = runBlocking { database.droidCanvasDao().getItemsForBoard(result.boardId).first() }
            .sortedBy { it.zIndex }
        assertEquals(20, items.size)
        for (i in 0 until 20) {
            val opened = items[i]
            assertItemFields(expectedItems[i], opened)
            assertEquals(result.boardId, opened.boardId)
            assertArrayEquals(expectedBytes["full-$i"], File(opened.fullPath).readBytes())
            assertArrayEquals(expectedBytes["thumb-$i"], File(opened.thumbPath).readBytes())
        }

        val edges = runBlocking { database.edgeDao().getEdgesForBoard(result.boardId).first() }
        assertEquals(1, edges.size)
        assertEquals(items[0].id, edges.single().fromItemId)
        assertEquals(items[1].id, edges.single().toItemId)
        assertEquals("RELATED_TO", edges.single().type)

        val strokesFile = File(filesDir, "drawing_strokes_${result.boardId}.txt")
        assertTrue(strokesFile.isFile)
        val expectedStrokes =
            "stroke-1|-16777216|${8f}|${10f},${20f};${30f},${40f}\n" +
                "stroke-2|-65536|${4f}|${1f},${2f};${3f},${4f};${5f},${6f}"
        assertEquals(expectedStrokes, strokesFile.readText())

        // Isi filesDir: 40 gambar + 1 goresan, tidak lebih.
        assertEquals(41, filesDir.list()!!.size)
    }

    // ------------------------------------------------------------------
    // Pembantu
    // ------------------------------------------------------------------

    private fun boardCount(): Int =
        runBlocking { database.droidCanvasDao().getAllBoards().first().size }

    /**
     * Open harus gagal sebagai [ProjectOpenException] dengan pesan yang
     * menyebut sebabnya (K6); jumlah papan tetap dan tidak ada berkas
     * tersalin (aturan keras 7).
     */
    private fun assertOpenFails(archive: File, mustMention: String) {
        val before = boardCount()
        val thrown = try {
            runBlocking { ProjectOpener.open(archive, filesDir, database) }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull("Open harus gagal untuk ${archive.name}", thrown)
        assertTrue(
            "harus ProjectOpenException, ditemukan: $thrown",
            thrown is ProjectOpenException
        )
        val message = thrown!!.message.orEmpty()
        assertTrue("pesan harus menyebut sebabnya ('$mustMention'), ditemukan: '$message'",
            message.contains(mustMention))
        assertFalse("pesan tidak boleh generik", message.contains("terjadi kesalahan"))
        assertEquals("jumlah papan tidak boleh berubah", before, boardCount())
        assertTrue(
            "tidak boleh ada berkas tersalin: ${filesDir.list()?.toList()}",
            filesDir.list()!!.isEmpty()
        )
    }

    private fun assertCameraEquals(expected: CameraState, actual: CameraState) {
        assertEquals(expected.scale.toDouble(), actual.scale.toDouble(), 0.001)
        assertEquals(expected.translateX.toDouble(), actual.translateX.toDouble(), 0.001)
        assertEquals(expected.translateY.toDouble(), actual.translateY.toDouble(), 0.001)
    }

    /** Semua field item kecuali id / boardId / path berkas (yang memang baru). */
    private fun assertItemFields(expected: CanvasItem, actual: CanvasItem) {
        assertEquals(expected.posX.toDouble(), actual.posX.toDouble(), 0.001)
        assertEquals(expected.posY.toDouble(), actual.posY.toDouble(), 0.001)
        assertEquals(expected.width.toDouble(), actual.width.toDouble(), 0.001)
        assertEquals(expected.height.toDouble(), actual.height.toDouble(), 0.001)
        assertEquals(expected.scale.toDouble(), actual.scale.toDouble(), 0.001)
        assertEquals(expected.rotation.toDouble(), actual.rotation.toDouble(), 0.001)
        assertEquals(expected.zIndex, actual.zIndex)
        assertEquals(expected.isPinned, actual.isPinned)
        assertEquals(expected.flipHorizontal, actual.flipHorizontal)
        assertEquals(expected.flipVertical, actual.flipVertical)
        assertEquals(expected.isValuesEnabled, actual.isValuesEnabled)
        assertEquals(expected.simplicity, actual.simplicity)
        assertEquals(expected.stopsCount, actual.stopsCount)
        assertEquals(expected.stopsJson, actual.stopsJson)
        assertEquals(expected.isGrayscale, actual.isGrayscale)
        assertEquals(expected.mapX.toDouble(), actual.mapX.toDouble(), 0.001)
        assertEquals(expected.mapY.toDouble(), actual.mapY.toDouble(), 0.001)
        assertEquals(expected.note, actual.note)
    }

    private fun writeBytes(dir: File, name: String, bytes: ByteArray): File =
        File(dir, name).apply { writeBytes(bytes) }

    /** Menulis arsip ZIP kecil: `manifest.json` + entri gambar yang diberikan. */
    private fun writeArchive(
        file: File,
        manifest: JSONObject,
        images: Map<String, ByteArray> = emptyMap()
    ) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((name, bytes) in images) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun cameraJson(scale: Double = 2.0, tx: Double = 0.0, ty: Double = 0.0): JSONObject =
        JSONObject().apply {
            put("scale", scale)
            put("translateX", tx)
            put("translateY", ty)
        }

    private fun itemJson(uuid: String): JSONObject = JSONObject().apply {
        put("uuid", uuid)
        put("posX", 10); put("posY", 20); put("width", 320); put("height", 240)
        put("scale", 1); put("rotation", 0); put("zIndex", 0)
        put("isPinned", false); put("flipHorizontal", false); put("flipVertical", false)
        put("isValuesEnabled", false); put("simplicity", 0)
        put("stopsCount", 3); put("stopsJson", "")
        put("isGrayscale", false)
        // Sengaja TANPA mapX/mapY/note (uji kompatibilitas pra-Map).
        put("fullFile", "images/${uuid}_full.jpg")
        put("thumbFile", "images/${uuid}_thumb.jpg")
    }

    /** Manifest minimal valid berisi 1 item (`u1`); `edges`/`strokes`/`mapCamera` opsional. */
    private fun manifestWithItem(formatVersion: Any = 1, edges: JSONArray? = null): JSONObject =
        JSONObject().apply {
            put("formatVersion", formatVersion)
            put("appVersion", "0.2.0")
            put("board", JSONObject().apply {
                put("name", "Papan Uji")
                put("createdAt", 1795054800000L)
                put("camera", cameraJson()) // scale 2.0 — dipakai assert pra-Map
            })
            put("items", JSONArray().put(itemJson("u1")))
            if (edges != null) put("edges", edges)
        }
}
