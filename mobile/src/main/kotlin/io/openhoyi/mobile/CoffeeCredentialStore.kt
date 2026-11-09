package io.openhoyi.mobile

import android.content.Context
import io.openhoyi.session.DeviceState
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

    /** A present but unreadable blob is preserved, including when Android Keystore is temporarily unavailable. */
    fun read(encoded: String?, address: String, key: () -> SecretKey): CoffeeCredentialRead {
        if (encoded == null) return CoffeeCredentialRead.Missing
        return runCatching { decrypt(key(), address, encoded)?.let(CoffeeCredentialRead::Available) }
            .getOrNull() ?: CoffeeCredentialRead.Unavailable
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

interface CoffeeCredentialFailureStore {
    fun read(address: String): Int
    fun write(address: String, count: Int)
}

private class MemoryCoffeeCredentialFailureStore : CoffeeCredentialFailureStore {
    private val counts = mutableMapOf<String, Int>()
    override fun read(address: String) = counts[address] ?: 0
    override fun write(address: String, count: Int) { counts[address] = count }
}

/** Legacy API retained for upgrade checks. Transport failures cannot establish password rejection. */
@Suppress("UNUSED_PARAMETER")
class CoffeeCredentialRetryGate(private val maxFailures: Int = 2,
                                private val store: CoffeeCredentialFailureStore = MemoryCoffeeCredentialFailureStore()) {
    init { require(maxFailures > 0) }
    fun mayUse(address: String): Boolean = true
    fun failed(address: String) = Unit
    fun succeeded(address: String) = Unit
    fun connectionState(address: String, state: DeviceState, remembered: Boolean) = Unit
}

sealed interface CoffeeCredentialRead {
    data class Available(val password: String) : CoffeeCredentialRead {
        override fun toString() = "Available(password=REDACTED)"
    }
    data object Missing : CoffeeCredentialRead
    data object Unavailable : CoffeeCredentialRead
}

enum class RememberedCoffeeConnection {
    STARTED, PASSWORD_REQUIRED, BLOCKED;

    companion object {
        fun attempt(allowed: Boolean, read: () -> CoffeeCredentialRead,
                    connect: (String) -> Boolean): RememberedCoffeeConnection {
            if (!allowed) return BLOCKED
            return when (val credential = read()) {
                CoffeeCredentialRead.Missing -> PASSWORD_REQUIRED
                CoffeeCredentialRead.Unavailable -> BLOCKED
                is CoffeeCredentialRead.Available -> if (connect(credential.password)) STARTED else BLOCKED
            }
        }
    }
}

internal data class PendingCoffeeCredential(val address: String, val password: String, val remembered: Boolean) {
    override fun toString() = "PendingCoffeeCredential(address=$address, password=REDACTED, remembered=$remembered)"
    fun passwordToSave(readyAddress: String?, state: DeviceState): String? = password.takeIf {
        !remembered && state == DeviceState.READY && address.equals(readyAddress, ignoreCase = true)
    }
}

class SharedPreferencesCoffeeFailureStore(context: Context) : CoffeeCredentialFailureStore {
    private val prefs = context.applicationContext.getSharedPreferences("coffee_credential_failures", Context.MODE_PRIVATE)
    override fun read(address: String) = prefs.getInt(address, 0)
    override fun write(address: String, count: Int) {
        val edit = prefs.edit()
        if (count == 0) edit.remove(address) else edit.putInt(address, count)
        edit.commit()
    }
}

class CoffeeCredentialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("coffee_credentials", Context.MODE_PRIVATE)
    private fun keyFor(address: String) = address.uppercase(Locale.ROOT)

    fun read(address: String): String? = (readState(address) as? CoffeeCredentialRead.Available)?.password

    fun readState(address: String): CoffeeCredentialRead = CoffeeCredentialCodec.read(
        prefs.getString(keyFor(address), null), address, ::key)

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
