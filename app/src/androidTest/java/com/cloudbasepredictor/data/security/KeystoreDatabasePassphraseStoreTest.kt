package com.cloudbasepredictor.data.security

import android.content.Context
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cloudbasepredictor.data.local.AppDatabase
import com.cloudbasepredictor.data.local.MIGRATION_1_2
import com.cloudbasepredictor.data.local.SavedPlaceEntity
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
class KeystoreDatabasePassphraseStoreTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = cleanState()

    @After
    fun tearDown() {
        cleanState()
        context.deleteDatabase(TEST_DB_NAME)
    }

    @Test
    fun getOrCreate_returnsStableNonEmptyValueAcrossInstances() {
        val first = KeystoreDatabasePassphraseStore(context).getOrCreate()
        val second = KeystoreDatabasePassphraseStore(context).getOrCreate()

        assertTrue("passphrase should not be empty", first.isNotEmpty())
        assertEquals("passphrase must be stable across instances", first, second)
    }

    @Test
    fun getOrCreate_migratesLegacyPassphraseUnchanged() {
        val legacy = randomPassphrase()
        seedLegacyPassphrase(legacy)

        val migrated = KeystoreDatabasePassphraseStore(context).getOrCreate()
        assertEquals("legacy passphrase must be returned unchanged", legacy, migrated)

        // A fresh instance now reads from the new scheme and still returns the same value.
        val afterMigration = KeystoreDatabasePassphraseStore(context).getOrCreate()
        assertEquals(legacy, afterMigration)
    }

    @Test
    fun databaseWrittenUnderLegacyPassphrase_opensAfterMigration() {
        val legacy = randomPassphrase()
        seedLegacyPassphrase(legacy)

        // Write a row into the encrypted DB using the legacy passphrase directly.
        val seeded = SavedPlaceEntity(id = "id-1", name = "Test Site", latitude = 1.0, longitude = 2.0)
        val writeDb = openDatabase(legacy)
        try {
            runBlocking { writeDb.savedPlaceDao().upsert(seeded) }
        } finally {
            writeDb.close()
        }

        // The migrated store must produce the same passphrase so the same DB opens.
        val migrated = KeystoreDatabasePassphraseStore(context).getOrCreate()
        assertEquals(legacy, migrated)

        val readDb = openDatabase(migrated)
        try {
            val read = runBlocking { readDb.savedPlaceDao().findById("id-1") }
            assertNotNull("row written under legacy passphrase must survive migration", read)
            assertEquals(seeded, read)
        } finally {
            readDb.close()
        }
    }

    private fun openDatabase(passphrase: String): AppDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase.toByteArray()))
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    private fun seedLegacyPassphrase(passphrase: String) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val prefs = EncryptedSharedPreferences.create(
            context,
            LEGACY_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        prefs.edit().putString(LEGACY_KEY, passphrase).commit()
    }

    private fun cleanState() {
        context.deleteSharedPreferences(NEW_PREFS_NAME)
        context.deleteSharedPreferences(LEGACY_PREFS_NAME)
        // Belt-and-braces: legacy file may linger if deleteSharedPreferences is a no-op.
        File(File(context.applicationInfo.dataDir, "shared_prefs"), "$LEGACY_PREFS_NAME.xml").delete()
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(NEW_KEY_ALIAS)
        }
    }

    private fun randomPassphrase(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TEST_DB_NAME = "passphrase_migration_test.db"
        const val NEW_PREFS_NAME = "db_passphrase_store"
        const val NEW_KEY_ALIAS = "db_passphrase_key"
        const val LEGACY_PREFS_NAME = "db_encryption_prefs"
        const val LEGACY_KEY = "db_passphrase"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
