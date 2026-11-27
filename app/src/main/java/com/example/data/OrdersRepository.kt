package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.example.model.Codec
import com.example.model.QuickOrder
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One order row. The full order is kept as JSON so new fields never need a migration. */
@Entity(tableName = "orders", indices = [Index("dateStr")])
data class OrderEntity(
    @PrimaryKey val id: String,
    val dateStr: String,
    val timestamp: Long,
    val json: String
)

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    suspend fun all(): List<OrderEntity>

    @Upsert
    suspend fun upsert(rows: List<OrderEntity>)

    @Query("DELETE FROM orders WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)
}

@Database(entities = [OrderEntity::class], version = 1, exportSchema = false)
abstract class LinklyDatabase : RoomDatabase() {
    abstract fun orders(): OrderDao
}

/**
 * Order history in a Room database (it used to be one big JSON file rewritten on every change).
 * Only orders that changed are written.
 */
class OrdersRepository(private val context: Context) {

    companion object {
        private const val TAG = "OrdersRepository"
        private const val LEGACY_FILE = "orders_history.json"
    }

    private val db: LinklyDatabase by lazy {
        Room.databaseBuilder(context, LinklyDatabase::class.java, "linkly.db").build()
    }

    /** id -> json last written, to skip rows that did not change. */
    private val written = HashMap<String, String>()

    suspend fun load(): List<QuickOrder> {
        migrateLegacyFile()
        val rows = db.orders().all()
        written.clear()
        return rows.mapNotNull { row ->
            try {
                written[row.id] = row.json
                Codec.parseOrder(JSONObject(row.json))
            } catch (e: Exception) {
                Log.e(TAG, "Bad order row ${row.id}", e)
                null
            }
        }
    }

    /** Writes the new list: changed or new orders are upserted, removed ones deleted. */
    suspend fun save(orders: List<QuickOrder>) {
        val changed = ArrayList<OrderEntity>()
        val ids = HashSet<String>(orders.size)
        orders.forEach { o ->
            ids.add(o.id)
            val json = Codec.orderToJson(o).toString()
            if (written[o.id] != json) changed.add(OrderEntity(o.id, o.dateStr, o.timestamp, json))
        }
        val removed = written.keys.filter { it !in ids }
        if (changed.isNotEmpty()) db.orders().upsert(changed)
        if (removed.isNotEmpty()) removed.chunked(500).forEach { db.orders().delete(it) }
        changed.forEach { written[it.id] = it.json }
        removed.forEach { written.remove(it) }
    }

    /** One time: move the old JSON history file into the database. */
    private suspend fun migrateLegacyFile() {
        val file = File(context.filesDir, LEGACY_FILE)
        if (!file.exists()) return
        try {
            val arr = JSONArray(file.readText())
            val rows = (0 until arr.length()).map { i ->
                val o = Codec.parseOrder(arr.getJSONObject(i))
                OrderEntity(o.id, o.dateStr, o.timestamp, Codec.orderToJson(o).toString())
            }
            if (rows.isNotEmpty()) db.orders().upsert(rows)
            file.renameTo(File(context.filesDir, "$LEGACY_FILE.migrated"))
        } catch (e: Exception) {
            Log.e(TAG, "Legacy history migration failed", e)
        }
    }
}
