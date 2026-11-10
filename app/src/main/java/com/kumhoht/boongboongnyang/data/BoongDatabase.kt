package com.kumhoht.boongboongnyang.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BoongDao {
    // ---- 운행 기록
    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Query("SELECT * FROM trips ORDER BY endAt DESC LIMIT :limit")
    fun observeTrips(limit: Int): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY endAt DESC LIMIT 1")
    fun observeLatestTrip(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE seen = 0 ORDER BY endAt DESC LIMIT 1")
    fun observeUnseenTrip(): Flow<TripEntity?>

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun trip(id: Long): TripEntity?

    @Query("UPDATE trips SET seen = 1 WHERE id = :id")
    suspend fun markSeen(id: Long)

    @Query("UPDATE trips SET seen = 1 WHERE seen = 0")
    suspend fun markAllSeen()

    @Query("SELECT * FROM trips WHERE endAt >= :since")
    suspend fun tripsSince(since: Long): List<TripEntity>

    // ---- 포인트
    @Insert
    suspend fun insertLedger(entry: LedgerEntity)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM ledger")
    fun observeBalance(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM ledger")
    suspend fun balance(): Int

    // ---- 아이템
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItem(item: InventoryEntity)

    @Query("SELECT * FROM inventory")
    fun observeInventory(): Flow<List<InventoryEntity>>

    @Query("SELECT itemId FROM inventory")
    suspend fun ownedIds(): List<String>

    @Upsert
    suspend fun equip(e: EquippedEntity)

    @Query("DELETE FROM equipped WHERE slot = :slot")
    suspend fun unequip(slot: String)

    @Query("SELECT * FROM equipped")
    fun observeEquipped(): Flow<List<EquippedEntity>>

    // ---- 선물상자
    @Insert
    suspend fun insertGiftBox(box: GiftBoxEntity)

    @Query("SELECT COUNT(*) FROM gift_boxes WHERE openedAt IS NULL")
    fun observeClosedBoxCount(): Flow<Int>

    @Query("SELECT * FROM gift_boxes WHERE openedAt IS NULL ORDER BY earnedAt LIMIT 1")
    suspend fun firstClosedBox(): GiftBoxEntity?

    @Query("UPDATE gift_boxes SET openedAt = :at, reward = :reward WHERE id = :id")
    suspend fun openBox(id: Long, at: Long, reward: String)

    // ---- 칭호
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTitle(t: TitleEntity): Long

    @Query("SELECT * FROM titles ORDER BY earnedAt DESC")
    fun observeTitles(): Flow<List<TitleEntity>>

    // ---- kv
    @Upsert
    suspend fun putKv(kv: KvEntity)

    @Query("SELECT value FROM kv WHERE `key` = :key")
    suspend fun getKv(key: String): String?

    @Query("SELECT * FROM kv")
    fun observeKv(): Flow<List<KvEntity>>
}

@Database(
    entities = [
        TripEntity::class, LedgerEntity::class, InventoryEntity::class, EquippedEntity::class,
        GiftBoxEntity::class, TitleEntity::class, KvEntity::class,
    ],
    version = 2,   // 2: 부드러운 운전 거리 Double (0.2km 단위)
    exportSchema = false,
)
abstract class BoongDatabase : RoomDatabase() {
    abstract fun dao(): BoongDao

    companion object {
        fun create(context: Context): BoongDatabase =
            Room.databaseBuilder(context, BoongDatabase::class.java, "boongboongnyang.db")
                // 출시 전 데모 단계라 스키마가 바뀌면 로컬 기록을 새로 만든다
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
