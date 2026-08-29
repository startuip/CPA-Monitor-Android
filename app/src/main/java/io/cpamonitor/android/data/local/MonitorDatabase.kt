package io.cpamonitor.android.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "response_cache")
data class CacheEntity(
    @PrimaryKey val cacheKey: String,
    val payload: String,
    val updatedAtMs: Long,
)

@Entity(tableName = "alert_state")
data class AlertStateEntity(
    @PrimaryKey val alertKey: String,
    val active: Boolean,
    val lastNotifiedAtMs: Long,
    val failureCount: Int = 0,
)

@Dao
interface MonitorDao {
    @Query("SELECT * FROM response_cache WHERE cacheKey = :key")
    fun observeCache(key: String): Flow<CacheEntity?>

    @Query("SELECT * FROM response_cache WHERE cacheKey = :key")
    suspend fun cache(key: String): CacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCache(value: CacheEntity)

    @Query("DELETE FROM response_cache")
    suspend fun clearCache()

    @Query("SELECT * FROM alert_state WHERE alertKey = :key")
    suspend fun alert(key: String): AlertStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAlert(value: AlertStateEntity)

    @Query("DELETE FROM alert_state")
    suspend fun clearAlerts()
}

@Database(
    entities = [CacheEntity::class, AlertStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MonitorDatabase : RoomDatabase() {
    abstract fun monitorDao(): MonitorDao
}

