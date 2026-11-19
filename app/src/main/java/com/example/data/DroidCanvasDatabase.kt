package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "boards")
data class Board(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val backgroundType: String = "SLATE",
    val canvasScale: Float = 1f,
    val canvasTranslateX: Float = 0f,
    val canvasTranslateY: Float = 0f
)

@Entity(
    tableName = "canvas_items",
    foreignKeys = [
        ForeignKey(
            entity = Board::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["boardId"])]
)
data class CanvasItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val boardId: Int,
    val fullPath: String,     // private filesDir path to original image
    val thumbPath: String,    // private filesDir path to downsampled thumbnail
    val posX: Float,          // X coordinate in canvas space
    val posY: Float,          // Y coordinate in canvas space
    val width: Float,         // native/initial width
    val height: Float,        // native/initial height
    val scale: Float = 1f,    // visual scale factor
    val rotation: Float = 0f,  // in degrees
    val zIndex: Int = 0,      // ordering
    val isPinned: Boolean = false,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val isGrayscale: Boolean = false,
    val isValuesEnabled: Boolean = false,
    val simplicity: Int = 0,
    val stopsCount: Int = 3,
    val stopsJson: String = "",
    val mapX: Float = 0f,       // X coordinate of the node in Map view (own layout, K2)
    val mapY: Float = 0f,       // Y coordinate of the node in Map view (own layout, K2)
    val note: String = ""       // Per-item note shown in Map view (K6, O2)
)

// Map view: a typed connection between two items of the same board.
// Jenis ujung edge (T5, P5 opsi (a)): satu tabel `edges` untuk gambar dan
// todo; kolom ini yang membedakan ujung ITEM (id → canvas_items) dari ujung
// TODO (id → todos). FK ke canvas_items DIBUANG di migrasi 11→12 — FK
// SQLite tidak bisa bersyarat, jadi id todo di kolom ber-FK akan ditolak
// database; hapus berantai pindah ke repository (transaksi, lihat
// DroidCanvasRepository.deleteCanvasItem/deleteTodo).
object EdgeEndpointKind {
    const val ITEM = "ITEM"
    const val TODO = "TODO"
}

// The edge stores only from/to/type (K5); the curve geometry is derived
// from the node positions at render time, never stored.
@Entity(
    tableName = "edges",
    foreignKeys = [
        ForeignKey(
            entity = Board::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["boardId"]),
        Index(value = ["fromItemId"]),
        Index(value = ["toItemId"])
    ]
)
data class Edge(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val boardId: Int,
    val fromItemId: Int,
    val toItemId: Int,
    val type: String, // ConnectionType.name — see ConnectionType.kt
    val fromKind: String = EdgeEndpointKind.ITEM,
    val toKind: String = EdgeEndpointKind.ITEM
)

// Todo berbasis kanvas (langkah 6, T1; R1 2026-11-19, K11): satu todo =
// SATU JUDUL + daftar baris (TodoItem) di bawahnya — bukan lagi 1 entri.
// Judul cuma kepala (tidak bisa dicentang); yang dicentang tiap baris.
// Posisi Board (posX/posY) + posisi Map (mapX/mapY) mengikuti pola
// CanvasItem. Kartu todo diisi manual (blank todo, K3).
@Entity(
    tableName = "todos",
    foreignKeys = [
        ForeignKey(
            entity = Board::class,
            parentColumns = ["id"],
            childColumns = ["boardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["boardId"])]
)
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val boardId: Int,
    val title: String = "",
    val posX: Float = 0f,       // X coordinate in canvas space (Board)
    val posY: Float = 0f,       // Y coordinate in canvas space (Board)
    val mapX: Float = 0f,       // X coordinate of the node in Map view (own layout, like CanvasItem.mapX)
    val mapY: Float = 0f,       // Y coordinate of the node in Map view (own layout, like CanvasItem.mapY)
    val createdAt: Long = System.currentTimeMillis()
)

// Satu baris di bawah judul todo (R1, K11): teks + centang sendiri.
// Milik satu Todo (FK CASCADE — hapus judul ikut menghapus barisnya);
// urutan tampil = createdAt (baris baru selalu di bawah).
@Entity(
    tableName = "todo_items",
    foreignKeys = [
        ForeignKey(
            entity = Todo::class,
            parentColumns = ["id"],
            childColumns = ["todoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["todoId"])]
)
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val todoId: Int,
    val text: String = "",
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface DroidCanvasDao {
    @Query("SELECT * FROM boards ORDER BY createdAt ASC")
    fun getAllBoards(): Flow<List<Board>>

    @Query("SELECT * FROM boards WHERE id = :id LIMIT 1")
    suspend fun getBoardById(id: Int): Board?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoard(board: Board): Long

    @Update
    suspend fun updateBoard(board: Board)

    @Delete
    suspend fun deleteBoard(board: Board)

    @Query("SELECT * FROM canvas_items WHERE boardId = :boardId ORDER BY zIndex ASC")
    fun getItemsForBoard(boardId: Int): Flow<List<CanvasItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCanvasItem(item: CanvasItem): Long

    @Update
    suspend fun updateCanvasItem(item: CanvasItem)

    @Delete
    suspend fun deleteCanvasItem(item: CanvasItem)

    @Query("SELECT MAX(zIndex) FROM canvas_items WHERE boardId = :boardId")
    suspend fun getMaxZIndex(boardId: Int): Int?
}

@Dao
interface EdgeDao {
    @Query("SELECT * FROM edges WHERE boardId = :boardId")
    fun getEdgesForBoard(boardId: Int): Flow<List<Edge>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdge(edge: Edge): Long

    @Delete
    suspend fun deleteEdge(edge: Edge)

    // Hapus berantai eksplisit (T5): FK ke canvas_items/todos sudah tidak
    // ada (opsi (a)), jadi penghapusan WAJIB dibatasi jenis ujungnya — id
    // gambar dan id todo punya urutan autoGenerate sendiri dan bisa sama
    // angkanya; tanpa pembatasan kind, hapus gambar bisa ikut menghapus
    // garis todo (dan sebaliknya).
    @Query("DELETE FROM edges WHERE (fromKind = 'ITEM' AND fromItemId = :itemId) OR (toKind = 'ITEM' AND toItemId = :itemId)")
    suspend fun deleteEdgesForItem(itemId: Int)

    @Query("DELETE FROM edges WHERE (fromKind = 'TODO' AND fromItemId = :todoId) OR (toKind = 'TODO' AND toItemId = :todoId)")
    suspend fun deleteEdgesForTodo(todoId: Int)

    @Query("DELETE FROM edges WHERE boardId = :boardId")
    suspend fun deleteEdgesForBoard(boardId: Int)
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE boardId = :boardId ORDER BY createdAt ASC")
    fun getTodosForBoard(boardId: Int): Flow<List<Todo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: Todo): Long

    @Update
    suspend fun updateTodo(todo: Todo)

    @Delete
    suspend fun deleteTodo(todo: Todo)

    @Query("DELETE FROM todos WHERE boardId = :boardId")
    suspend fun deleteTodosForBoard(boardId: Int)

    // Baris di bawah judul (R1, K11): milik satu todo; urutan tampil =
    // createdAt (baris baru di bawah). Subquery supaya aliran mengikuti
    // papan aktif, bukan satu todo saja.
    @Query("SELECT * FROM todo_items WHERE todoId IN (SELECT id FROM todos WHERE boardId = :boardId) ORDER BY createdAt ASC")
    fun getTodoItemsForBoard(boardId: Int): Flow<List<TodoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodoItem(item: TodoItem): Long

    @Update
    suspend fun updateTodoItem(item: TodoItem)

    @Delete
    suspend fun deleteTodoItem(item: TodoItem)
}

@Database(entities = [Board::class, CanvasItem::class, Edge::class, Todo::class, TodoItem::class], version = 13, exportSchema = false)
abstract class DroidCanvasDatabase : RoomDatabase() {
    abstract fun droidCanvasDao(): DroidCanvasDao
    abstract fun edgeDao(): EdgeDao
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: DroidCanvasDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boards ADD COLUMN backgroundType TEXT NOT NULL DEFAULT 'SLATE'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE boards ADD COLUMN canvasScale REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE boards ADD COLUMN canvasTranslateX REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE boards ADD COLUMN canvasTranslateY REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE canvas_items ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE canvas_items ADD COLUMN flipHorizontal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE canvas_items ADD COLUMN flipVertical INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE canvas_items ADD COLUMN isGrayscale INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE canvas_items ADD COLUMN isValuesEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE canvas_items ADD COLUMN simplicity INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE canvas_items ADD COLUMN stopsCount INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE canvas_items ADD COLUMN stopsJson TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create a temporary table with the exact new schema
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `canvas_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `boardId` INTEGER NOT NULL, 
                        `fullPath` TEXT NOT NULL, 
                        `thumbPath` TEXT NOT NULL, 
                        `posX` REAL NOT NULL, 
                        `posY` REAL NOT NULL, 
                        `width` REAL NOT NULL, 
                        `height` REAL NOT NULL, 
                        `scale` REAL NOT NULL, 
                        `rotation` REAL NOT NULL, 
                        `zIndex` INTEGER NOT NULL, 
                        `isPinned` INTEGER NOT NULL, 
                        `flipHorizontal` INTEGER NOT NULL, 
                        `flipVertical` INTEGER NOT NULL, 
                        `isValuesEnabled` INTEGER NOT NULL, 
                        `simplicity` INTEGER NOT NULL, 
                        `stopsCount` INTEGER NOT NULL, 
                        `stopsJson` TEXT NOT NULL, 
                        FOREIGN KEY(`boardId`) REFERENCES `boards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                // Copy the data from the old table
                db.execSQL("""
                    INSERT INTO `canvas_items_new` (
                        id, boardId, fullPath, thumbPath, posX, posY, width, height, scale, rotation, zIndex, isPinned, flipHorizontal, flipVertical, isValuesEnabled, simplicity, stopsCount, stopsJson
                    ) SELECT 
                        id, boardId, fullPath, thumbPath, posX, posY, width, height, scale, rotation, zIndex, isPinned, flipHorizontal, flipVertical, isValuesEnabled, simplicity, stopsCount, stopsJson
                    FROM `canvas_items`
                """.trimIndent())

                // Drop the old table
                db.execSQL("DROP TABLE `canvas_items`")

                // Rename the new table
                db.execSQL("ALTER TABLE `canvas_items_new` RENAME TO `canvas_items`")

                // Recreate the index
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_canvas_items_boardId` ON `canvas_items` (`boardId`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE canvas_items ADD COLUMN isGrayscale INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Map view (fase 1): one migration for all schema changes — 3 new
        // CanvasItem columns (mapX/mapY/note) + the edges table. New rows get
        // mapX/mapY = 0 and note = '' (same defaults as the entity), so
        // pre-existing boards open unchanged and keep their data.
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE canvas_items ADD COLUMN mapX REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE canvas_items ADD COLUMN mapY REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE canvas_items ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `edges` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `boardId` INTEGER NOT NULL,
                        `fromItemId` INTEGER NOT NULL,
                        `toItemId` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        FOREIGN KEY(`boardId`) REFERENCES `boards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`fromItemId`) REFERENCES `canvas_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`toItemId`) REFERENCES `canvas_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_edges_boardId` ON `edges` (`boardId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_edges_fromItemId` ON `edges` (`fromItemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_edges_toItemId` ON `edges` (`toItemId`)")
            }
        }

        // Todo berbasis kanvas (langkah 6, T1): tabel baru `todos` saja —
        // tidak ada kolom baru di tabel lain. Tabel kosong saat migrasi
        // (todo pertama dibuat setelah update), jadi tidak ada data yang
        // perlu dipindahkan.
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `todos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `boardId` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `done` INTEGER NOT NULL,
                        `posX` REAL NOT NULL,
                        `posY` REAL NOT NULL,
                        `mapX` REAL NOT NULL,
                        `mapY` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`boardId`) REFERENCES `boards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_boardId` ON `todos` (`boardId`)")
            }
        }

        // Todo berbasis kanvas (langkah 6, T5, P5 opsi (a)): edge boleh
        // menunjuk todo — tambah kolom penanda jenis ujung + buang dua FK ke
        // canvas_items (FK SQLite tidak bisa bersyarat). SQLite tidak bisa
        // menambah/menghapus FK pakai ALTER, jadi tabel dibuat ulang: salin
        // semua baris (kind lama = ITEM semua), buang tabel lama, ganti
        // nama, buat ulang indeks. FK boardId TETAP (hapus papan tetap
        // menghapus garisnya di database).
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `edges_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `boardId` INTEGER NOT NULL,
                        `fromItemId` INTEGER NOT NULL,
                        `toItemId` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `fromKind` TEXT NOT NULL DEFAULT 'ITEM',
                        `toKind` TEXT NOT NULL DEFAULT 'ITEM',
                        FOREIGN KEY(`boardId`) REFERENCES `boards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `edges_new` (id, boardId, fromItemId, toItemId, type, fromKind, toKind)
                    SELECT id, boardId, fromItemId, toItemId, type, 'ITEM', 'ITEM' FROM `edges`
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS `edges`")
                db.execSQL("ALTER TABLE `edges_new` RENAME TO `edges`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_edges_boardId` ON `edges` (`boardId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_edges_fromItemId` ON `edges` (`fromItemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_edges_toItemId` ON `edges` (`toItemId`)")
            }
        }

        // Todo jadi judul + daftar baris (R1, K11): `todos` dibuat ulang
        // tanpa kolom `done` (judul tidak bisa dicentang) dan `text` jadi
        // `title`; tabel baru `todo_items` menampung baris-barisnya.
        // Baris todo LAMA dibuang (keputusan user 2026-11-19: "biarin aja
        // toh nanti direset" — tidak dimigrasi). DROP aman: tidak ada tabel
        // lain yang ber-FK ke `todos` (FK edge ke todo sudah dibuang T5).
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `todos`")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `todos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `boardId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `posX` REAL NOT NULL,
                        `posY` REAL NOT NULL,
                        `mapX` REAL NOT NULL,
                        `mapY` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`boardId`) REFERENCES `boards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todos_boardId` ON `todos` (`boardId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `todo_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `todoId` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `done` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`todoId`) REFERENCES `todos`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_todoId` ON `todo_items` (`todoId`)")
            }
        }

        fun getDatabase(context: Context): DroidCanvasDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DroidCanvasDatabase::class.java,
                    "droid_canvas_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class DroidCanvasRepository(
    private val dao: DroidCanvasDao,
    private val edgeDao: EdgeDao,
    private val todoDao: TodoDao,
    private val database: DroidCanvasDatabase
) {
    val allBoards: Flow<List<Board>> = dao.getAllBoards()

    suspend fun getBoardById(id: Int): Board? = dao.getBoardById(id)

    suspend fun insertBoard(board: Board): Long = dao.insertBoard(board)

    suspend fun updateBoard(board: Board) = dao.updateBoard(board)

    suspend fun deleteBoard(board: Board) = dao.deleteBoard(board)

    fun getItemsForBoard(boardId: Int): Flow<List<CanvasItem>> = dao.getItemsForBoard(boardId)

    suspend fun insertCanvasItem(item: CanvasItem): Long = dao.insertCanvasItem(item)

    suspend fun updateCanvasItem(item: CanvasItem) = dao.updateCanvasItem(item)

    // Hapus gambar + garisnya dalam SATU transaksi (T5): FK ke canvas_items
    // sudah tidak ada (opsi (a)), jadi repository yang menjamin tidak ada
    // garis yatim — pemanggil tidak perlu mengingat memanggil
    // deleteEdgesForItem sendiri.
    suspend fun deleteCanvasItem(item: CanvasItem) = database.withTransaction {
        edgeDao.deleteEdgesForItem(item.id)
        dao.deleteCanvasItem(item)
    }

    suspend fun getMaxZIndex(boardId: Int): Int? = dao.getMaxZIndex(boardId)

    // Map view edges (fase 1: data layer only, no business logic yet)
    fun getEdgesForBoard(boardId: Int): Flow<List<Edge>> = edgeDao.getEdgesForBoard(boardId)

    suspend fun insertEdge(edge: Edge): Long = edgeDao.insertEdge(edge)

    suspend fun deleteEdge(edge: Edge) = edgeDao.deleteEdge(edge)

    suspend fun deleteEdgesForItem(itemId: Int) = edgeDao.deleteEdgesForItem(itemId)

    suspend fun deleteEdgesForBoard(boardId: Int) = edgeDao.deleteEdgesForBoard(boardId)

    // Todo berbasis kanvas (langkah 6, T1: data layer only)
    fun getTodosForBoard(boardId: Int): Flow<List<Todo>> = todoDao.getTodosForBoard(boardId)

    suspend fun insertTodo(todo: Todo): Long = todoDao.insertTodo(todo)

    suspend fun updateTodo(todo: Todo) = todoDao.updateTodo(todo)

    // Hapus todo + garisnya dalam SATU transaksi (T5) — cermin
    // deleteCanvasItem di atas; garis ke/dari todo ikut hilang (O3 untuk
    // todo). Baris-baris todo ikut hilang via FK CASCADE di `todo_items`.
    // Hapus papan tidak perlu ini: FK boardId di `edges` tetap ada.
    suspend fun deleteTodo(todo: Todo) = database.withTransaction {
        edgeDao.deleteEdgesForTodo(todo.id)
        todoDao.deleteTodo(todo)
    }

    suspend fun deleteTodosForBoard(boardId: Int) = todoDao.deleteTodosForBoard(boardId)

    // Baris todo (R1, K11): thin pass-throughs, seperti todos di atas.
    fun getTodoItemsForBoard(boardId: Int): Flow<List<TodoItem>> = todoDao.getTodoItemsForBoard(boardId)

    suspend fun insertTodoItem(item: TodoItem): Long = todoDao.insertTodoItem(item)

    suspend fun updateTodoItem(item: TodoItem) = todoDao.updateTodoItem(item)

    suspend fun deleteTodoItem(item: TodoItem) = todoDao.deleteTodoItem(item)
}
