package com.project011.lifehealthplanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        UserProfileEntity::class,
        HealthRecordEntity::class,
        MedicationEntity::class,
        LifeGoalEntity::class,
        ConstraintEntity::class,
        PlanEntity::class,
        PlanItemEntity::class,
        CheckInEntity::class,
        RiskAlertEntity::class,
        CalendarLinkEntity::class,
        AiConsentReceiptEntity::class,
        BackupManifestEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun build(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyManager(context).getOrCreatePassphrase()
            return Room.databaseBuilder(context, AppDatabase::class.java, "life-health.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
