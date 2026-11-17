package io.openhoyi.mobile

import java.util.UUID

/** All identifiers originate internally; exported Activity extras cannot select a path or key. */
internal object AiStorageProbeScope {
    const val ROOT = "ai_offline_storage_probe_v1"
    const val AUTHORITY = "io.openhoyi.mobile.mock.ai_storage_probe"
    val paths = setOf("/text", "/pdf", "/image", "/short", "/wrong-size", "/cancel")
    fun checked(id: String): String {
        require(id.matches(Regex("[a-f0-9-]{36}")) && UUID.fromString(id).toString() == id)
        return id
    }
    fun alias(id: String, type: String): String {
        checked(id); require(type in setOf("attachments", "session"))
        return "io.openhoyi.mobile.mock.storage_probe.$id.$type"
    }
    fun accepts(authority: String?, path: String?, query: String?, fragment: String?) =
        authority == AUTHORITY && path in paths && query == null && fragment == null
}
