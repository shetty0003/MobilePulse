package com.mobilepulse.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mobilepulse.app.data.db.dao.BootRestrictionDao
import com.mobilepulse.app.data.db.dao.ExemptionDao
import com.mobilepulse.app.data.db.dao.LogDao
import com.mobilepulse.app.data.db.dao.RuleDao
import com.mobilepulse.app.data.db.entity.*
import com.mobilepulse.app.data.repository.BatterySampleDao
import com.mobilepulse.app.data.repository.BatterySampleEntity

@Database(
    entities = [
        AutomationRuleEntity::class,
        WhitelistEntity::class,
        IgnoreListEntity::class,
        ActivityLogEntity::class,
        BatterySampleEntity::class,
        BootRestrictionEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class MobilePulseDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun exemptionDao(): ExemptionDao
    abstract fun logDao(): LogDao
    abstract fun batterySampleDao(): BatterySampleDao
    abstract fun bootRestrictionDao(): BootRestrictionDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `boot_restrictions` " +
                    "(`packageName` TEXT NOT NULL, `appName` TEXT NOT NULL, " +
                    "`restrictBackground` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`packageName`))"
                )
            }
        }
    }
}
