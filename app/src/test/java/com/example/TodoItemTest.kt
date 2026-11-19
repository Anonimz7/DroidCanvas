package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.Board
import com.example.data.DroidCanvasDatabase
import com.example.data.DroidCanvasRepository
import com.example.data.Todo
import com.example.data.TodoItem
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
 * R1 (K11): satu todo = satu judul + daftar baris TodoItem. Baris hidup di
 * tabel todo_items sendiri (FK todoId CASCADE) — hapus todo menghapus
 * baris-barisnya, hapus baris hanya menghapus baris itu. Urutan baris =
 * createdAt (menaik).
 *
 * Robolectric + Room in-memory, pola EdgeTodoCascadeTest. Nama test
 * backtick sengaja ASCII (lihat catatan di ProjectOpenerTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TodoItemTest {

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

    private suspend fun insertTodo(title: String): Int = repository.insertTodo(
        Todo(boardId = boardId, title = title)
    ).toInt()

    private suspend fun insertItem(todoId: Int, text: String): Int = repository.insertTodoItem(
        TodoItem(todoId = todoId, text = text)
    ).toInt()

    private suspend fun itemsOf(todoId: Int): List<TodoItem> =
        repository.getTodoItemsForBoard(boardId).first().filter { it.todoId == todoId }

    @Test
    fun `sisip todo lalu dua baris keduanya terbaca urut masuk`() = runBlocking {
        val todo = insertTodo("Belanja")
        insertItem(todo, "Beras")
        insertItem(todo, "Minyak")

        val items = itemsOf(todo)
        assertEquals(2, items.size)
        assertEquals("Beras", items[0].text)
        assertEquals("Minyak", items[1].text)
        assertTrue(items.all { !it.done })
    }

    @Test
    fun `ubah teks baris tersimpan`() = runBlocking {
        val todo = insertTodo("Belanja")
        val item = insertItem(todo, "Beras")

        repository.updateTodoItem(TodoItem(id = item, todoId = todo, text = "Beras merah"))
        assertEquals("Beras merah", itemsOf(todo)[0].text)
    }

    @Test
    fun `centang baris tersimpan`() = runBlocking {
        val todo = insertTodo("Belanja")
        val item = insertItem(todo, "Beras")

        repository.updateTodoItem(TodoItem(id = item, todoId = todo, text = "Beras", done = true))
        assertTrue(itemsOf(todo)[0].done)
    }

    @Test
    fun `hapus baris hanya menghapus baris itu`() = runBlocking {
        val todo = insertTodo("Belanja")
        val item1 = insertItem(todo, "Beras")
        insertItem(todo, "Minyak")

        repository.deleteTodoItem(TodoItem(id = item1, todoId = todo, text = "Beras"))

        val items = itemsOf(todo)
        assertEquals(1, items.size)
        assertEquals("Minyak", items[0].text)
    }

    @Test
    fun `hapus todo menghapus barisnya tapi baris todo lain utuh`() = runBlocking {
        val todo1 = insertTodo("Belanja")
        val todo2 = insertTodo("Kerja")
        insertItem(todo1, "Beras")
        insertItem(todo2, "Laporan")

        repository.deleteTodo(Todo(id = todo1, boardId = boardId, title = "Belanja"))

        assertTrue(itemsOf(todo1).isEmpty())
        assertEquals(1, itemsOf(todo2).size)
        assertEquals("Laporan", itemsOf(todo2)[0].text)
    }
}
