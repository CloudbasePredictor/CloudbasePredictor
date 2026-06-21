package com.cloudbasepredictor.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.cloudbasepredictor.data.local.AppDatabase
import com.cloudbasepredictor.data.local.ForecastCacheDao
import com.cloudbasepredictor.data.local.LaunchSiteCacheDao
import com.cloudbasepredictor.data.local.MIGRATION_1_2
import com.cloudbasepredictor.data.local.SavedPlaceDao
import com.cloudbasepredictor.data.place.FavoritePlacesBackupContract
import com.cloudbasepredictor.data.security.DatabasePassphraseStore
import com.cloudbasepredictor.data.security.KeystoreDatabasePassphraseStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FavoritePlacesBackupPreferences

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabasePassphraseStore(
        @ApplicationContext context: Context,
    ): DatabasePassphraseStore = KeystoreDatabasePassphraseStore(context)

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        passphraseStore: DatabasePassphraseStore,
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = passphraseStore.getOrCreate()
        val factory = SupportOpenHelperFactory(passphrase.toByteArray())
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "cloudbase_predictor.db",
        )
            .openHelperFactory(factory)
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideSavedPlaceDao(
        appDatabase: AppDatabase,
    ): SavedPlaceDao = appDatabase.savedPlaceDao()

    @Provides
    fun provideForecastCacheDao(
        appDatabase: AppDatabase,
    ): ForecastCacheDao = appDatabase.forecastCacheDao()

    @Provides
    fun provideLaunchSiteCacheDao(
        appDatabase: AppDatabase,
    ): LaunchSiteCacheDao = appDatabase.launchSiteCacheDao()

    @Provides
    @Singleton
    fun provideSharedPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences =
        context.getSharedPreferences("cloudbase_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    @FavoritePlacesBackupPreferences
    fun provideFavoritePlacesBackupPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences =
        context.getSharedPreferences(FavoritePlacesBackupContract.PREFS_NAME, Context.MODE_PRIVATE)
}
