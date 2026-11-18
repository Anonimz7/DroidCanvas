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
        ),
        ForeignKey(
            entity = CanvasItem::class,
            parentColumns = ["id"],
            childColumns = ["fromItemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CanvasItem::class,
            parentColumns = ["id"],
            childColumns = ["toItemId"],
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
    val type: String // ConnectionType.name — see ConnectionType.kt
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

    @Query("DELETE FROM edges WHERE fromItemId = :itemId OR toItemId = :itemId")
    suspend fun deleteEdgesForItem(itemId: Int)

    @Query("DELETE FROM edges WHERE boardId = :boardId")
    suspend fun deleteEdgesForBoard(boardId: Int)
}

@Database(entities = [Board::class, CanvasItem::class, Edge::class], version = 10, exportSchema = false)
abstract class DroidCanvasDatabase : RoomDatabase() {
    abstract fun droidCanvasDao(): DroidCanvasDao
    abstract fun edgeDao(): EdgeDao

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

        fun getDatabase(context: Context): DroidCanvasDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DroidCanvasDatabase::class.java,
                    "droid_canvas_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
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
    private val edgeDao: EdgeDao
) {
    val allBoards: Flow<List<Board>> = dao.getAllBoards()

    suspend fun getBoardById(id: Int): Board? = dao.getBoardById(id)

    suspend fun insertBoard(board: Board): Long = dao.insertBoard(board)

    suspend fun updateBoard(board: Board) = dao.updateBoard(board)

    suspend fun deleteBoard(board: Board) = dao.deleteBoard(board)

    fun getItemsForBoard(boardId: Int): Flow<List<CanvasItem>> = dao.getItemsForBoard(boardId)

    suspend fun insertCanvasItem(item: CanvasItem): Long = dao.insertCanvasItem(item)

    suspend fun updateCanvasItem(item: CanvasItem) = dao.updateCanvasItem(item)

    suspend fun deleteCanvasItem(item: CanvasItem) = dao.deleteCanvasItem(item)

    suspend fun getMaxZIndex(boardId: Int): Int? = dao.getMaxZIndex(boardId)

    // Map view edges (fase 1: data layer only, no business logic yet)
    fun getEdgesForBoard(boardId: Int): Flow<List<Edge>> = edgeDao.getEdgesForBoard(boardId)

    suspend fun insertEdge(edge: Edge): Long = edgeDao.insertEdge(edge)

    suspend fun deleteEdge(edge: Edge) = edgeDao.deleteEdge(edge)

    suspend fun deleteEdgesForItem(itemId: Int) = edgeDao.deleteEdgesForItem(itemId)

    suspend fun deleteEdgesForBoard(boardId: Int) = edgeDao.deleteEdgesForBoard(boardId)
}
