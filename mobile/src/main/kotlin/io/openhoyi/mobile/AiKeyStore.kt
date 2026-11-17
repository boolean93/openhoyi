package io.openhoyi.mobile

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Only ciphertext is persisted. Never expose a saved key back to a view, Bundle, log or export. */
internal object AiKeyCodec {
    private val aad = "openhoyi:deepseek:api-key:v1".toByteArray(Charsets.UTF_8)
    fun valid(value: String) = value.length in 8..512 && value.all { it.code in 33..126 }
    fun encrypt(key: SecretKey, value: String): String {
        require(valid(value))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        require(cipher.iv.size == 12)
        val plain = value.toByteArray(Charsets.UTF_8)
        return try { Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(plain)) }
        finally { plain.fill(0) }
    }
    fun decrypt(key: SecretKey, value: String): String {
        require(value.length <= 1024)
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        val plain = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        return try { String(plain, Charsets.UTF_8).also { require(valid(it)) } } finally { plain.fill(0) }
    }
}

internal enum class AiKeyStatus { MISSING, SAVED, UNAVAILABLE }

/** Deliberately ciphertext-only, including the test seam. */
internal interface AiCiphertextStorage {
    fun contains(): Boolean
    fun read(): String?
    fun write(ciphertext: String?): Boolean
    fun credentialEpoch(): String = error("Credential epoch unavailable")
}

internal interface AiKeySource {
    fun existing(): SecretKey?
    fun create(): SecretKey
    /** Called only by explicit save after a typed permanent-invalidation failure, never by a read. */
    fun replacePermanentlyInvalidated(): SecretKey = throw IllegalStateException()
}

internal class AiKeyStore internal constructor(
    private val storage: AiCiphertextStorage,
    private val keys: AiKeySource,
) {
    constructor(context: Context) : this(PreferencesAiCiphertextStorage(context), AndroidAiKeySource())

    fun status(): AiKeyStatus = synchronized(mutationLock) {
        // Status must not decrypt or expose the stored value, even when it is damaged.
        runCatching {
            when {
                failedWrite.get() -> AiKeyStatus.UNAVAILABLE
                !storage.contains() -> AiKeyStatus.MISSING
                keys.existing() != null -> AiKeyStatus.SAVED
                else -> AiKeyStatus.UNAVAILABLE
            }
        }.getOrDefault(AiKeyStatus.UNAVAILABLE)
    }
    /** Non-secret identity, never derived from or obtained by decrypting the API key. */
    fun credentialEpoch(): String? = synchronized(mutationLock) {
        if (failedWrite.get()) return@synchronized null
        runCatching { storage.credentialEpoch() }.getOrElse { failedWrite.set(true); null }
    }
    fun revision(): Long = generation.get()
    fun observeMutation(observer: () -> Unit): () -> Unit {
        synchronized(observers) { observers += observer }
        return { synchronized(observers) { observers -= observer } }
    }
    fun readForRequest(expectedRevision: Long = revision()): String = synchronized(mutationLock) {
        try {
            if (failedWrite.get() || expectedRevision != revision()) throw AiFailure(AiProblem.KEY)
            val blob = storage.read() ?: throw AiFailure(AiProblem.KEY)
            // A read never creates/replaces an alias or rewrites damaged ciphertext.
            AiKeyCodec.decrypt(keys.existing() ?: throw AiFailure(AiProblem.KEY), blob)
        } catch (_: Exception) { throw AiFailure(AiProblem.KEY) }
    }
    fun save(value: String): Boolean = mutate {
        require(AiKeyCodec.valid(value))
        val sealed = try {
            AiKeyCodec.encrypt(keys.existing() ?: keys.create(), value)
        } catch (failure: Exception) {
            // Never infer permanent loss from error text, an ordinary InvalidKeyException or bad ciphertext.
            if (generateSequence<Throwable>(failure) { it.cause }.take(8)
                    .none { it is KeyPermanentlyInvalidatedException }) throw failure
            // The user supplied the replacement value in this explicit save. Retry exactly once.
            AiKeyCodec.encrypt(keys.replacePermanentlyInvalidated(), value)
        }
        // Storage failures must not trigger alias replacement, even if their cause looks like a key error.
        storage.write(sealed)
    }
    fun delete(): Boolean = mutate { storage.write(null) }
    private fun mutate(write: () -> Boolean): Boolean {
        val saved = synchronized(mutationLock) {
            generation.incrementAndGet()
            runCatching(write).getOrDefault(false).also { failedWrite.set(!it) }
        }
        // Process-wide fence also covers new store instances after a failed SharedPreferences commit.
        // A failed commit may already have changed its in-memory value. Only a successful mutation clears it.
        synchronized(observers) { observers.toList() }.forEach { runCatching(it) }
        return saved
    }
    private companion object {
        val mutationLock = Any()
        val generation = AtomicLong()
        val failedWrite = AtomicBoolean()
        val observers = mutableSetOf<() -> Unit>()
    }
}

/** Read-only bridge for pre-epoch credentials. Never derives identity from secret bytes.
 * Explicit save/delete still rotates the stored epoch. Downgrade-and-change-key is unsupported.
 */
internal object AiCredentialEpochPolicy {
    const val LEGACY = "00000000-0000-0000-0000-000000000000"
    fun resolve(stored: String?): String {
        if (stored == null) return LEGACY
        require(stored.matches(Regex("[a-f0-9-]{36}")))
        return stored
    }
}

internal class PreferencesAiCiphertextStorage(context: Context, name: String = "ai_credentials_v1") : AiCiphertextStorage {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun contains(): Boolean = prefs.contains("sealed")
    override fun read(): String? = prefs.getString("sealed", null)
    override fun credentialEpoch(): String = AiCredentialEpochPolicy.resolve(prefs.getString("epoch", null))
    override fun write(ciphertext: String?): Boolean = prefs.edit().run {
        putString("epoch", java.util.UUID.randomUUID().toString())
        if (ciphertext == null) remove("sealed") else putString("sealed", ciphertext)
        commit()
    }
}

internal class AndroidAiKeySource(private val alias: String = ALIAS) : AiKeySource {
    override fun existing(): SecretKey? = KeyStore.getInstance("AndroidKeyStore").run {
        load(null); getKey(alias, null) as? SecretKey
    }
    override fun create(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).setKeySize(256).build())
        generateKey()
    }
    override fun replacePermanentlyInvalidated(): SecretKey {
        // This source owns one AI alias; never enumerate, clear, or modify another credential namespace.
        KeyStore.getInstance("AndroidKeyStore").run { load(null); deleteEntry(alias) }
        return create()
    }
    private companion object { const val ALIAS = "io.openhoyi.mobile.deepseek_api_key.v1" }
}
