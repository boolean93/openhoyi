package io.openhoyi.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The encrypted blob is bound to its Bluetooth address; plaintext never enters preferences. */
object CoffeeCredentialCodec {
    fun encrypt(key: SecretKey, address: String, password: String): String {
        require(password.matches(Regex("[0-9]{6}")))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(address.uppercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(password.toByteArray(Charsets.US_ASCII))
        val iv = cipher.iv
        require(iv.size in 12..16)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(iv.size.toByte()) + iv + sealed)
    }

    fun decrypt(key: SecretKey, address: String, encoded: String): String? = runCatching {
        require(encoded.length <= 256)
        val bytes = Base64.getUrlDecoder().decode(encoded)
        require(bytes.isNotEmpty())
        val ivSize = bytes[0].toInt() and 0xff
        require(ivSize in 12..16 && bytes.size >= 1 + ivSize + 16)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(1, 1 + ivSize)))
        cipher.updateAAD(address.uppercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        String(cipher.doFinal(bytes.copyOfRange(1 + ivSize, bytes.size)), Charsets.US_ASCII)
            .takeIf { it.matches(Regex("[0-9]{6}")) }
    }.getOrNull()
}

/** A transport failure never deletes a valid credential. Prompt again after two silent failures. */
class CoffeeCredentialRetryGate(private val maxFailures: Int = 2) {
    init { require(maxFailures > 0) }
    private val failures = mutableMapOf<String, Int>()
    private fun key(address: String) = address.uppercase(Locale.ROOT)
    fun mayUse(address: String): Boolean = (failures[key(address)] ?: 0) < maxFailures
    fun failed(address: String) { failures[key(address)] = (failures[key(address)] ?: 0) + 1 }
    fun succeeded(address: String) { failures.remove(key(address)) }
}

class CoffeeCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("coffee_credentials", Context.MODE_PRIVATE)
    private fun keyFor(address: String) = address.uppercase(Locale.ROOT)

    fun read(address: String): String? {
        val encoded = prefs.getString(keyFor(address), null) ?: return null
        val password = runCatching { CoffeeCredentialCodec.decrypt(key(), address, encoded) }.getOrNull()
        if (password == null) prefs.edit().remove(keyFor(address)).apply()
        return password
    }

    fun save(address: String, password: String): Boolean = runCatching {
        prefs.edit().putString(keyFor(address), CoffeeCredentialCodec.encrypt(key(), address, password)).commit()
    }.getOrDefault(false)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return generator.generateKey()
    }

    private companion object { const val ALIAS = "io.openhoyi.mobile.coffee_password.v1" }
}
