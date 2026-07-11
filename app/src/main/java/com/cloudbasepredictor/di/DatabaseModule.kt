package com.cloudbasepredictor.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.cloudbasepredictor.data.forecast.ForecastCacheStore
import com.cloudbasepredictor.data.local.AppDatabase
import com.cloudbasepredictor.data.local.ForecastCacheDao
import com.cloudbasepredictor.data.local.LaunchSiteCacheDao
import com.cloudbasepredictor.data.local.MIGRATION_1_2
import com.cloudbasepredictor.data.local.RoomFavoritePlaceStore
import com.cloudbasepredictor.data.local.RoomForecastCacheStore
import com.cloudbasepredictor.data.local.SavedPlaceDao
import com.cloudbasepredictor.data.map.MapCameraPersistenceContract
import com.cloudbasepredictor.data.map.MapCameraStore
import com.cloudbasepredictor.data.map.SharedPreferencesMapCameraStore
import com.cloudbasepredictor.data.place.FavoritePlaceStore
import com.cloudbasepredictor.data.place.FavoritePlacesBackupContract
import com.cloudbasepredictor.data.security.DatabasePassphraseStore
import com.cloudbasepredictor.data.security.KeystoreDatabasePassphraseStore
import com.cloudbasepredictor.data.storage.KeyValueStorage
import com.cloudbasepredictor.data.storage.SharedPreferencesKeyValueStorage
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FavoritePlacesBackupPreferences

@BindingContainer
object DatabaseModule {

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabasePassphraseStore(
        context: Context,
    ): DatabasePassphraseStore = KeystoreDatabasePassphraseStore(context)

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppDatabase(
        context: Context,
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
    @SingleIn(AppScope::class)
    fun provideForecastCacheStore(
        forecastCacheDao: ForecastCacheDao,
    ): ForecastCacheStore = RoomForecastCacheStore(forecastCacheDao)

    @Provides
    @SingleIn(AppScope::class)
    fun provideFavoritePlaceStore(
        savedPlaceDao: SavedPlaceDao,
    ): FavoritePlaceStore = RoomFavoritePlaceStore(savedPlaceDao)

    @Provides
    fun provideLaunchSiteCacheDao(
        appDatabase: AppDatabase,
    ): LaunchSiteCacheDao = appDatabase.launchSiteCacheDao()

    @Provides
    @SingleIn(AppScope::class)
    fun provideSharedPreferences(
        context: Context,
    ): SharedPreferences =
        context.getSharedPreferences("cloudbase_prefs", Context.MODE_PRIVATE)

    @Provides
    @SingleIn(AppScope::class)
    fun provideKeyValueStorage(
        sharedPreferences: SharedPreferences,
    ): KeyValueStorage = SharedPreferencesKeyValueStorage(sharedPreferences)

    @Provides
    @SingleIn(AppScope::class)
    @FavoritePlacesBackupPreferences
    fun provideFavoritePlacesBackupPreferences(
        context: Context,
    ): SharedPreferences =
        context.getSharedPreferences(FavoritePlacesBackupContract.PREFS_NAME, Context.MODE_PRIVATE)

    @Provides
    @SingleIn(AppScope::class)
    fun provideMapCameraStore(
        context: Context,
    ): MapCameraStore = SharedPreferencesMapCameraStore(
        context.getSharedPreferences(MapCameraPersistenceContract.PREFERENCES_NAME, Context.MODE_PRIVATE),
    )
}
