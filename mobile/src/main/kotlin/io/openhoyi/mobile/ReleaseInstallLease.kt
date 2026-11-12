package io.openhoyi.mobile

/** Ephemeral user intent only. Never serialized and never resurrected after an interruption. */
internal class ReleaseInstallLease(private val clock: () -> Long) {
    data class Token(val generation: Long, val target: String, val owner: Any, val page: Any?, val safetyEpoch: Long)
    private var token: Token? = null
    private var permission: Long? = null
    private var permissionDeadline = 0L
    fun begin(value: Token) { token = value; permission = null }
    fun revoke() { token = null; permission = null }
    fun snapshot() = token
    fun owns(value: Token) = token === value
    val active get() = token != null
    val awaitingPermission get() = permission != null
    fun matches(generation: Long, target: String, owner: Any, page: Any?, safetyEpoch: Long): Boolean =
        token?.let { it.generation == generation && it.target == target && it.owner === owner &&
            it.page === page && it.safetyEpoch == safetyEpoch } == true
    fun ownerResumed(owner: Any, page: Any?) {
        if (token?.let { it.owner !== owner || it.page !== page } == true) revoke()
    }
    fun paused() { if (!awaitingPermission) revoke() }
    fun pageLeft(page: Any, destroyed: Boolean = false) {
        if (token?.page === page && (destroyed || !awaitingPermission)) revoke()
    }
    fun requestPermission(id: Long): Boolean {
        if (token == null || permission != null) return false
        permission = id; permissionDeadline = clock() + 120_000
        return true
    }
    fun permissionMatches(id: Long) = permission == id
    fun permissionResult(id: Long): Boolean {
        if (permission != id) return false // A retired callback cannot revoke a newer user intent.
        if (clock() > permissionDeadline) { revoke(); return false }
        permission = null // A result can be consumed once; a second callback revokes the lease.
        return token != null
    }
    fun consume(): Boolean {
        if (token == null || permission != null) return false
        revoke(); return true
    }
}

internal fun releaseUpdateKey(update: ReleaseUpdate) = "${update.versionCode}:${update.signerSha256}:${update.apkSha256}:${update.apkSize}"
