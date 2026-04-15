package com.iduna.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.iduna.data.local.dao.AlertEventDao
import com.iduna.data.local.dao.HeartRateDao
import com.iduna.data.local.dao.UserProfileDao
import com.iduna.data.local.dao.UserSettingsDao
import com.iduna.data.local.entity.AlertEventEntity
import com.iduna.data.local.entity.HeartRateReadingEntity
import com.iduna.data.local.entity.UserProfileEntity
import com.iduna.data.local.entity.UserSettingsEntity

@Database(
    entities = [
        HeartRateReadingEntity::class,
        AlertEventEntity::class,
        UserProfileEntity::class,
        UserSettingsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class IdunaDatabase : RoomDatabase() {
    abstract fun heartRateDao(): HeartRateDao
    abstract fun alertEventDao(): AlertEventDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        fun create(context: Context): IdunaDatabase = Room.databaseBuilder(
            context = context,
            klass = IdunaDatabase::class.java,
            name = "iduna.db",
        ).fallbackToDestructiveMigration().build()
    }
}
