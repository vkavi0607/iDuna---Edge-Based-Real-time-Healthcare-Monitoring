package com.iduna.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "heart_rate_readings")
data class HeartRateReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val bpm: Int,
    val averageBpm: Int,
    val anomalyCode: Int,
    val fingerDetected: Boolean,
)

@Entity(tableName = "alert_events")
data class AlertEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val bpm: Int,
    val anomalyCode: Int,
    val message: String,
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 0,
    val name: String,
    val age: Int,
    val emergencyContact: String,
)

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val notificationsEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val autoSosEnabled: Boolean,
    val bleAutoConnectEnabled: Boolean,
    val darkModeEnabled: Boolean,
)
