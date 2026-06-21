package com.cloudbasepredictor.data.security

/**
 * Provides the SQLCipher database passphrase.
 *
 * The passphrase is the only thing that lets an app update open the existing
 * encrypted cache database; if it changes, Room falls back to a destructive
 * migration and the database is wiped. Implementations must therefore return a
 * stable value across process restarts and app updates for a given install.
 */
interface DatabasePassphraseStore {

    /**
     * Returns the existing passphrase, migrating it from a legacy store if
     * necessary, or generates and persists a new one on first use.
     */
    fun getOrCreate(): String
}
