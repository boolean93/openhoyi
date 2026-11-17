package io.openhoyi.mobile

import android.content.Context
import android.os.Process
import java.security.KeyStore
import java.util.Base64

/**
 * Real Android prefs + Keystore checks, exclusively in a disposable Mock emulator.
 * The runner must verify MOCK_MODE and disposable-emulator consent before constructing this class.
 * seed and verifyAfterRestart must run in different target processes. Only a safe assertion count
 * is returned; neither credentials, ciphertext nor provider exceptions become instrumentation output.
 * Failed commit / system Keystore unavailable remain JVM fake tests, not simulated by damaging Android.
 */
internal class AiAndroidCredentialChecks(context: Context) {
    private val context = context.applicationContext
    init { check(BuildConfig.MOCK_MODE) { "Credential checks require Mock" } }

    /** Leaves this UUID-scoped fixture for the next process; cleans its own fixture on failure. */
    fun seed(id: String): Int {
        validate(id)
        var assertions = 0
        fun expect(condition: Boolean) { check(condition); assertions++ }
        try {
            val prefs = prefs(id)
            expect(prefs.all.isEmpty() && !keyStore().containsAlias(alias(id)) && !keyStore().containsAlias(wrongAlias(id)))
            val storage = storage(id)
            val store = store(id)
            val token = fakeToken(id)
            expect(store.status() == AiKeyStatus.MISSING)
            expect(store.save(token))
            val first = requireNotNull(storage.read())
            val firstEpoch = requireNotNull(store.credentialEpoch())
            expect(validEpoch(firstEpoch) && firstEpoch != token)
            expect(!first.contains(token))
            expect(store.readForRequest() == token)
            expect(store.save(token))
            val second = requireNotNull(storage.read())
            val secondEpoch = requireNotNull(store.credentialEpoch())
            expect(validEpoch(secondEpoch) && secondEpoch != firstEpoch)
            expect(prefs.getString("epoch", null) == secondEpoch)
            expect(first != second)
            expect(!Base64.getDecoder().decode(first).copyOfRange(0, 12)
                .contentEquals(Base64.getDecoder().decode(second).copyOfRange(0, 12)))
            expect(store.status() == AiKeyStatus.SAVED)
            // Test-only non-secret snapshot, so a later target process can verify stable identity.
            expect(prefs.edit().putInt("seedPid", Process.myPid()).putString("seedEpoch", secondEpoch).commit())
            expect(prefs.all.keys == setOf("sealed", "epoch", "seedPid", "seedEpoch"))
            expect(prefs.all.values.filterIsInstance<String>().none { it.contains(token) })
            return assertions
        } catch (_: Exception) {
            runCatching { cleanup(id) }
            throw IllegalStateException("Credential seed check failed")
        }
    }

    /** Always removes only this fixture, even when a check fails. */
    fun verifyAfterRestart(id: String): Int {
        validate(id)
        var assertions = 0
        fun expect(condition: Boolean) { check(condition); assertions++ }
        try {
            val prefs = prefs(id)
            val seedPid = prefs.getInt("seedPid", -1)
            expect(seedPid > 0 && seedPid != Process.myPid())
            val storage = storage(id)
            val store = store(id)
            val token = fakeToken(id)
            expect(prefs.all.keys == setOf("sealed", "epoch", "seedPid", "seedEpoch"))
            val seededEpoch = requireNotNull(prefs.getString("seedEpoch", null))
            val restoredEpoch = requireNotNull(store.credentialEpoch())
            expect(validEpoch(seededEpoch) && restoredEpoch == seededEpoch)
            expect(prefs.getString("epoch", null) == restoredEpoch && restoredEpoch != token)
            expect(store.status() == AiKeyStatus.SAVED)
            expect(store.readForRequest() == token)
            val original = requireNotNull(storage.read())
            expect(!original.contains(token))

            val damagedBytes = Base64.getDecoder().decode(original)
            damagedBytes[damagedBytes.lastIndex] = (damagedBytes.last().toInt() xor 1).toByte()
            val damaged = Base64.getEncoder().encodeToString(damagedBytes)
            expect(storage.write(damaged))
            safeKeyFailure { store.readForRequest() }; assertions++
            expect(storage.read() == damaged)
            expect(storage.write(original))

            // The second fixture alias contains genuinely different AndroidKeyStore AES material.
            val wrong = AndroidAiKeySource(wrongAlias(id))
            expect(wrong.existing() == null)
            wrong.create()
            safeKeyFailure { AiKeyStore(storage, wrong).readForRequest() }; assertions++
            expect(storage.read() == original)

            keyStore().deleteEntry(alias(id))
            expect(store.status() == AiKeyStatus.UNAVAILABLE)
            safeKeyFailure { store.readForRequest() }; assertions++
            expect(!keyStore().containsAlias(alias(id)))
            expect(storage.read() == original)

            // Only explicit reconfiguration can recreate the missing fixture alias.
            expect(store.save(token))
            expect(store.readForRequest() == token)
            var notifications = 0
            val unsubscribe = store.observeMutation { notifications++ }
            try {
                val old = store.revision()
                val replacement = fakeToken(java.util.UUID.randomUUID().toString())
                expect(store.save(replacement))
                safeKeyFailure { store.readForRequest(old) }; assertions++
                expect(store.readForRequest() == replacement)
                expect(notifications == 1)
                val beforeDelete = store.revision()
                val beforeDeleteEpoch = requireNotNull(store.credentialEpoch())
                expect(store.delete())
                val deletedEpoch = requireNotNull(store.credentialEpoch())
                expect(validEpoch(deletedEpoch) && deletedEpoch != beforeDeleteEpoch)
                expect(prefs.all.keys == setOf("epoch", "seedPid", "seedEpoch"))
                safeKeyFailure { store.readForRequest(beforeDelete) }; assertions++
                safeKeyFailure { store.readForRequest() }; assertions++
                expect(store.status() == AiKeyStatus.MISSING)
                expect(storage.read() == null)
                expect(notifications == 2)
            } finally { unsubscribe() }
            return assertions
        } catch (_: Exception) {
            throw IllegalStateException("Credential verification check failed")
        } finally {
            cleanup(id)
        }
    }

    /** May be called independently by the runner after interruption. Never accepts arbitrary paths. */
    fun cleanup(id: String): Int {
        validate(id)
        var failures = 0
        // Independent attempts ensure a prefs failure does not leave both fixture keys behind.
        if (!runCatching {
            val fixture = prefs(id)
            fixture.edit().clear().commit() && fixture.all.isEmpty()
        }.getOrDefault(false)) failures++
        if (!runCatching { context.deleteSharedPreferences(prefsName(id)) }.getOrDefault(false)) failures++
        listOf(alias(id), wrongAlias(id)).forEach { name ->
            if (runCatching { keyStore().apply { deleteEntry(name); check(!containsAlias(name)) } }.isFailure) failures++
        }
        check(failures == 0) { "Credential fixture cleanup failed" }
        return 3
    }

    private fun validEpoch(value: String): Boolean = runCatching {
        java.util.UUID.fromString(value).toString() == value
    }.getOrDefault(false)

    private fun safeKeyFailure(read: () -> String) {
        val error = runCatching(read).exceptionOrNull()
        check(error is AiFailure && error.problem == AiProblem.KEY && error.message == "KEY" && error.cause == null)
    }
    private fun store(id: String) = AiKeyStore(storage(id), AndroidAiKeySource(alias(id)))
    private fun storage(id: String) = PreferencesAiCiphertextStorage(context, prefsName(id))
    private fun prefs(id: String) = context.getSharedPreferences(prefsName(id), Context.MODE_PRIVATE)
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun fakeToken(id: String) = "fake-sentinel-$id"
    private fun prefsName(id: String) = "ai_credential_fixture_$id"
    private fun alias(id: String) = "io.openhoyi.mobile.test.ai_credentials.$id"
    private fun wrongAlias(id: String) = "${alias(id)}.wrong"
    private fun validate(id: String) {
        check(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) {
            "Invalid credential fixture identifier"
        }
    }
}
