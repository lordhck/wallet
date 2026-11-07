package com.lordhck.btcwallet

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.Key
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// Keeps the seed encrypted twice: with a key from the PIN, then with a key
// that never leaves this phone. Key stretching is slow, so call create and
// open off the main thread.
object Vault {
    private const val PREFS = "vault"
    private const val SALT = "salt"
    private const val DATA = "data"
    private const val ALIAS = "wallet"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val ROUNDS = "rounds"
    private const val LENGTH = "length"
    private const val FAILS = "fails"
    private const val UNTIL = "until"
    private const val FREE_TRIES = 5

    // Stored with each vault, so changing this never locks out an existing one
    private const val ITERATIONS = 50_000
    private const val FIRST_ITERATIONS = 300_000

    fun exists(context: Context): Boolean = prefs(context).contains(DATA)

    fun pinLength(context: Context): Int = prefs(context).getInt(LENGTH, 6)

    // Seconds left before another PIN can be tried
    fun lockedFor(context: Context): Long {
        val left = prefs(context).getLong(UNTIL, 0L) - System.currentTimeMillis()
        return if (left > 0) left / 1000 + 1 else 0
    }

    fun create(context: Context, pin: String, words: List<String>) {
        Debug.note("vault saved, ${words.size} words, PIN length ${pin.length}")
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val inner = seal(pinKey(pin, salt, ITERATIONS), words.joinToString(" ").toByteArray())
        prefs(context).edit()
            .putInt(ROUNDS, ITERATIONS)
            .putInt(LENGTH, pin.length)
            .putString(SALT, encode(salt))
            .putString(DATA, encode(seal(deviceKey(), inner)))
            .commit()
    }

    // The words, or null if the PIN is wrong
    fun open(context: Context, pin: String): List<String>? {
        if (lockedFor(context) > 0) {
            Debug.note("PIN attempt refused: locked out")
            return null
        }
        val prefs = prefs(context)
        val salt = decode(prefs.getString(SALT, null) ?: return null)
        val data = decode(prefs.getString(DATA, null) ?: return null)
        val rounds = prefs.getInt(ROUNDS, FIRST_ITERATIONS)
        return try {
            val words = String(unseal(pinKey(pin, salt, rounds), unseal(deviceKey(), data))).split(" ")
            prefs.edit().remove(FAILS).remove(UNTIL).apply()
            Debug.note("PIN correct")
            words
        } catch (e: AEADBadTagException) {
            miss(context)
            null
        }
    }

    // After 5 misses each one locks for longer, from 30 seconds up to about an hour
    private fun miss(context: Context) {
        val prefs = prefs(context)
        val fails = prefs.getInt(FAILS, 0) + 1
        Debug.note("PIN wrong, miss $fails")
        val edit = prefs.edit().putInt(FAILS, fails)
        if (fails >= FREE_TRIES) {
            val wait = 30_000L shl minOf(fails - FREE_TRIES, 7)
            edit.putLong(UNTIL, System.currentTimeMillis() + wait)
        }
        edit.commit()
    }

    // False if the old PIN is wrong
    fun changePin(context: Context, old: String, new: String): Boolean {
        val words = open(context, old) ?: return false
        create(context, new, words)
        return true
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().commit()
        KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private fun pinKey(pin: String, salt: ByteArray, rounds: Int): Key {
        val spec = PBEKeySpec(pin.toCharArray(), salt, rounds, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun deviceKey(): Key {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return store.getKey(ALIAS, null) as SecretKey? ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun seal(key: Key, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM).apply { init(Cipher.ENCRYPT_MODE, key) }
        return cipher.iv + cipher.doFinal(plain)
    }

    private fun unseal(key: Key, data: ByteArray): ByteArray {
        val iv = GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES)
        val cipher = Cipher.getInstance(AES_GCM).apply { init(Cipher.DECRYPT_MODE, key, iv) }
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }
}
