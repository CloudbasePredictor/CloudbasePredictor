package com.cloudbasepredictor.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the SQLCipher passphrase encrypted with an AES-256-GCM key held in the
 * Android Keystore, persisting the ciphertext in a plain [android.content.SharedPreferences]
 * file. This replaces the deprecated `androidx.security.crypto` (Jetpack Security)
 * `EncryptedSharedPreferences`/`MasterKey` approach with a dependency-free one.
 *
 * On first run after an update from the old scheme, the previously persisted
 * passphrase is read via the legacy store and re-encrypted under this scheme,
 * returned unchanged so the existing database keeps opening.
 *
 * If decryption fails irrecoverably (e.g. the Keystore key was invalidated), a
 * fresh passphrase is generated and the old database becomes unreadable, which
 * triggers Room's destructive migration. That is acceptable here because the
 * database only holds a cache, but it is logged rather than silent.
 */
class KeystoreDatabasePassphraseStore(
    private val context: Context,
) : DatabasePassphraseStore {

    override fun getOrCreate(): String =
        readNewSchemePassphrase()
            ?: migrateLegacyPassphrase()?.also { persist(it) }
            ?: generateAndPersist()

    // Keystore/Cipher operations throw a wide range of (often device-specific)
    // checked and unchecked exceptions; catching broadly and regenerating is the
    // intended recovery for this cache passphrase.
    @Suppress("TooGenericExceptionCaught")
    private fun readNewSchemePassphrase(): String? {
        val ciphertext = prefs().getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            decrypt(ciphertext)
        } catch (e: Exception) {
            Timber.w(e, "Failed to decrypt stored passphrase; regenerating (cache DB will be reset)")
            clearKeystoreKey()
            prefs().edit().remove(KEY_CIPHERTEXT).apply()
            null
        }
    }

    /** Reads the passphrase persisted by the legacy Jetpack Security store, if present. */
    @Suppress("TooGenericExceptionCaught")
    private fun migrateLegacyPassphrase(): String? {
        val legacyFile = File(File(context.applicationInfo.dataDir, "shared_prefs"), "$LEGACY_PREFS_NAME.xml")
        if (!legacyFile.exists()) return null
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val legacyPrefs = EncryptedSharedPreferences.create(
                context,
                LEGACY_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            legacyPrefs.getString(LEGACY_KEY_DB_PASSPHRASE, null)
        } catch (e: Exception) {
            Timber.w(e, "Failed to read legacy passphrase; treating as fresh install")
            null
        }
    }

    private fun generateAndPersist(): String {
        val bytes = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(bytes)
        val passphrase = bytes.joinToString("") { "%02x".format(it) }
        persist(passphrase)
        return passphrase
    }

    private fun persist(passphrase: String) {
        prefs().edit().putString(KEY_CIPHERTEXT, encrypt(passphrase)).apply()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey() ?: generateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val blob = Base64.decode(stored, Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = blob.copyOfRange(GCM_IV_BYTES, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    private fun getSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun generateSecretKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun clearKeystoreKey() {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            Timber.w(e, "Failed to delete invalidated Keystore key")
        }
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "db_passphrase_store"
        private const val KEY_CIPHERTEXT = "passphrase_ciphertext"

        private const val LEGACY_PREFS_NAME = "db_encryption_prefs"
        private const val LEGACY_KEY_DB_PASSPHRASE = "db_passphrase"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "db_passphrase_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
        private const val PASSPHRASE_BYTES = 32
    }
}
