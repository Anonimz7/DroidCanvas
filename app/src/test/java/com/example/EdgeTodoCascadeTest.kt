package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Board
import com.example.data.CanvasItem
import com.example.data.DroidCanvasDatabase
import com.example.data.DroidCanvasRepository
import com.example.data.Edge
import com.example.data.EdgeEndpointKind
import com.example.data.Todo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Todo T5 (P5 opsi (a)): hapus berantai edge pindah dari FK database ke
 * repository (transaksi) — FK ke canvas_items dibuang di migrasi 11-12
 * karena FK SQLite tidak bisa bersyarat. Test ini membuktikan tidak ada
 * garis yatim DAN tidak ada garis yang ikut terhapus karena id gambar dan
 * id todo bisa sama angkanya (urutan autoGenerate terpisah).
 *
 * Robolectric + Room in-memory, pola ProjectOpenerTest. Nama test backtick
 * sengaja ASCII (lihat catatan di ProjectOpenerTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EdgeTodoCascadeTest {

    private lateinit var database: DroidCanvasDatabase
    private lateinit var repository: DroidCanvasRepository
    private var boardId: Int = 0

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, DroidCanvasDatabase::class.java).build()
        repository = DroidCanvasRepository(
            database.droidCanvasDao(), database.edgeDao(), database.todoDao(), database
        )
        boardId = repository.insertBoard(Board(name = "Test")).toInt()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertItem(): Int = repository.insertCanvasItem(
        CanvasItem(boardId = boardId, fullPath = "/tmp/full.jpg", thumbPath = "/tmp/thumb.jpg", posX = 0f, posY = 0f, width = 100f, height = 100f)
    ).toInt()

    private suspend fun insertTodo(text: String): Int = repository.insertTodo(
        Todo(boardId = boardId, text = text)
    ).toInt()

    private suspend fun edges(): List<Edge> = repository.getEdgesForBoard(boardId).first()

    @Test
    fun `hapus gambar menghapus garisnya tapi garis todo utuh`() = runBlocking {
        val item1 = insertItem()
        val item2 = insertItem()
        val todo1 = insertTodo("A")
        val todo2 = insertTodo("B")
        repository.insertEdge(Edge(boardId = boardId, fromItemId = item1, toItemId = item2, type = "RELATED_TO"))
        repository.insertEdge(Edge(boardId = boardId, fromItemId = todo1, toItemId = todo2, type = "SUPPORTS", fromKind = EdgeEndpointKind.TODO, toKind = EdgeEndpointKind.TODO))
        repository.insertEdge(Edge(boardId = boardId, fromItemId = item1, toItemId = todo1, type = "USED_IN", toKind = EdgeEndpointKind.TODO))
        assertEquals(3, edges().size)

        repository.deleteCanvasItem(CanvasItem(id = item1, boardId = boardId, fullPath = "", thumbPath = "", posX = 0f, posY = 0f, width = 0f, height = 0f))

        val remaining = edges()
        assertEquals(1, remaining.size)
        assertEquals(EdgeEndpointKind.TODO, remaining[0].fromKind)
        assertEquals(EdgeEndpointKind.TODO, remaining[0].toKind)
    }

    @Test
    fun `hapus todo menghapus garisnya tapi garis gambar utuh`() = runBlocking {
        val item1 = insertItem()
        val item2 = insertItem()
        val todo1 = insertTodo("A")
        val todo2 = insertTodo("B")
        repository.insertEdge(Edge(boardId = boardId, fromItemId = item1, toItemId = item2, type = "RELATED_TO"))
        repository.insertEdge(Edge(boardId = boardId, fromItemId = todo1, toItemId = todo2, type = "SUPPORTS", fromKind = EdgeEndpointKind.TODO, toKind = EdgeEndpointKind.TODO))
        repository.insertEdge(Edge(boardId = boardId, fromItemId = item1, toItemId = todo1, type = "USED_IN", toKind = EdgeEndpointKind.TODO))
        assertEquals(3, edges().size)

        repository.deleteTodo(Todo(id = todo1, boardId = boardId, text = "A"))

        val remaining = edges()
        assertEquals(1, remaining.size)
        assertEquals(EdgeEndpointKind.ITEM, remaining[0].fromKind)
        assertEquals(EdgeEndpointKind.ITEM, remaining[0].toKind)
    }

    @Test
    fun `hapus papan menghapus semua garis`() = runBlocking {
        val item1 = insertItem()
        val todo1 = insertTodo("A")
        repository.insertEdge(Edge(boardId = boardId, fromItemId = item1, toItemId = todo1, type = "USED_IN", toKind = EdgeEndpointKind.TODO))
        assertEquals(1, edges().size)

        repository.deleteBoard(Board(id = boardId, name = "Test"))

        assertTrue(edges().isEmpty())
    }

    @Test
    fun `id gambar dan id todo yang sama angka tidak saling menghapus`() = runBlocking {
        // Item pertama dan todo pertama sama-sama id 1 (urutan terpisah).
        val item1 = insertItem()
        val todo1 = insertTodo("A")
        val todo2 = insertTodo("B")
        assertEquals(item1, todo1)
        repository.insertEdge(Edge(boardId = boardId, fromItemId = todo1, toItemId = todo2, type = "SUPPORTS", fromKind = EdgeEndpointKind.TODO, toKind = EdgeEndpointKind.TODO))
        repository.insertEdge(Edge(boardId = boardId, fromItemId = item1, toItemId = todo1, type = "USED_IN", toKind = EdgeEndpointKind.TODO))
        assertEquals(2, edges().size)

        // Hapus gambar #1: garis todo-todo harus tetap ada.
        repository.deleteCanvasItem(CanvasItem(id = item1, boardId = boardId, fullPath = "", thumbPath = "", posX = 0f, posY = 0f, width = 0f, height = 0f))

        val remaining = edges()
        assertEquals(1, remaining.size)
        assertEquals(EdgeEndpointKind.TODO, remaining[0].fromKind)
        assertEquals(todo1, remaining[0].fromItemId)
        assertEquals(todo2, remaining[0].toItemId)
    }

    @Test
    fun `sisip edge ke todo tidak melanggar FK dan kind tersimpan`() = runBlocking {
        val item1 = insertItem()
        val todo1 = insertTodo("A")
        repository.insertEdge(Edge(boardId = boardId, fromItemId = item1, toItemId = todo1, type = "REFERENCES", toKind = EdgeEndpointKind.TODO))

        val saved = edges()
        assertEquals(1, saved.size)
        assertEquals(EdgeEndpointKind.ITEM, saved[0].fromKind)
        assertEquals(EdgeEndpointKind.TODO, saved[0].toKind)
        assertEquals(item1, saved[0].fromItemId)
        assertEquals(todo1, saved[0].toItemId)
    }
}
