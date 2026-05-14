package com.mobilepulse.app.di

import android.content.Context
import androidx.room.Room
import com.mobilepulse.app.data.db.MobilePulseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): MobilePulseDatabase =
        Room.databaseBuilder(ctx, MobilePulseDatabase::class.java, "mobilepulse.db")
            .addMigrations(MobilePulseDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideRuleDao(db: MobilePulseDatabase)              = db.ruleDao()
    @Provides fun provideExemptionDao(db: MobilePulseDatabase)         = db.exemptionDao()
    @Provides fun provideLogDao(db: MobilePulseDatabase)               = db.logDao()
    @Provides fun provideBatterySampleDao(db: MobilePulseDatabase)     = db.batterySampleDao()
    @Provides fun provideBootRestrictionDao(db: MobilePulseDatabase)   = db.bootRestrictionDao()
}
