package com.cloudbasepredictor.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cloudbasepredictor.data.local.AppDatabase
import com.cloudbasepredictor.data.local.ForecastCacheDao
import com.cloudbasepredictor.data.local.LaunchSiteCacheDao
import com.cloudbasepredictor.data.local.MIGRATION_1_2
import com.cloudbasepredictor.data.local.SavedPlaceDao
import com.cloudbasepredictor.data.place.FavoritePlacesBackupContract
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FavoritePlacesBackupPreferences

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private const val PREFS_NAME = "db_encryption_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = getOrCreatePassphrase(context)
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

    private fun getOrCreatePassphrase(context: Context): String {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        return prefs.getString(KEY_DB_PASSPHRASE, null) ?: run {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            val generated = bytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString(KEY_DB_PASSPHRASE, generated).apply()
            generated
        }
    }
}
