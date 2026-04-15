package com.iduna.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.iduna.data.local.entity.AlertEventEntity
import com.iduna.data.local.entity.HeartRateReadingEntity
import com.iduna.data.local.entity.UserProfileEntity
import com.iduna.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: HeartRateReadingEntity)

    @Query(
        """
        SELECT * FROM heart_rate_readings
        ORDER BY timestamp DESC
        LIMIT :limit
        """,
    )
    fun observeLatestReadings(limit: Int): Flow<List<HeartRateReadingEntity>>

    @Query(
        """
        SELECT * FROM heart_rate_readings
        WHERE timestamp >= :start
        ORDER BY timestamp DESC
        """,
    )
    fun observeReadingsFrom(start: Long): Flow<List<HeartRateReadingEntity>>

    @Query(
        """
        SELECT * FROM heart_rate_readings
        WHERE timestamp BETWEEN :start AND :end
        ORDER BY timestamp ASC
        """,
    )
    suspend fun getReadingsBetween(start: Long, end: Long): List<HeartRateReadingEntity>
}

@Dao
interface AlertEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEventEntity)

    @Query(
        """
        SELECT * FROM alert_events
        ORDER BY timestamp DESC
        """,
    )
    fun observeAlerts(): Flow<List<AlertEventEntity>>

    @Query(
        """
        SELECT * FROM alert_events
        WHERE timestamp BETWEEN :start AND :end
        ORDER BY timestamp DESC
        """,
    )
    suspend fun getAlertsBetween(start: Long, end: Long): List<AlertEventEntity>
}

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM user_profile WHERE id = 0")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 0")
    suspend fun getProfile(): UserProfileEntity?
}

@Dao
interface UserSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: UserSettingsEntity)

    @Query("SELECT * FROM user_settings WHERE id = 0")
    fun observeSettings(): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE id = 0")
    suspend fun getSettings(): UserSettingsEntity?
}
