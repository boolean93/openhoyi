package io.openhoyi.mobile

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URI
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

internal fun releaseApkUrlAllowed(value: URI, initial: URI): Boolean {
    if (value.scheme != "https" || value.port !in setOf(-1, 443) || value.userInfo != null || value.fragment != null) return false
    if (initial.host == "gitee.com") {
        val filename = initial.path.substringAfterLast('/')
        return when (value.host) {
            "gitee.com" -> value.rawQuery == null && (value.rawPath == initial.rawPath ||
                Regex("/boolean93/openhoyi/attach_files/[0-9]+/download/" + Regex.escape(filename)).matches(value.rawPath))
            "foruda.gitee.com" -> setOf(filename, filename.lowercase(Locale.ROOT)).any {
                Regex("/attach_file/[0-9]+/" + Regex.escape(it)).matches(value.rawPath)
            }
            else -> false
        }
    }
    return when (value.host) {
        "github.com" -> value.path == initial.path && value.rawQuery == null
        "release-assets.githubusercontent.com" -> value.path.startsWith("/github-production-release-asset/")
        else -> false
    }
}

/** A single explicit attempt. Redirect credentials and signed query strings are never logged. */
internal class ReleaseApkTransfer(
    private val open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
    private val clock: () -> Long = System::nanoTime,
) {
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }
    fun download(target: ReleaseUpdate, part: File, progress: (Long) -> Unit) = downloadFrom(target, target.downloadUrl, part, progress)
    fun downloadFrom(target: ReleaseUpdate, url: String, part: File, progress: (Long) -> Unit) {
        require(target.apkSize in 1..256L * 1024 * 1024)
        val initial = URI(url)
        val filename = URI(target.downloadUrl).path.substringAfterLast('/')
        require(url == target.downloadUrl || (url in target.mirrorDownloadUrls &&
            url == "https://gitee.com/boolean93/openhoyi/releases/download/dev-${target.versionCode}/$filename"))
        require(initial.scheme == "https" && initial.host in setOf("github.com", "gitee.com") && initial.port == -1 &&
            initial.userInfo == null && initial.rawQuery == null && initial.fragment == null &&
            initial.path == "/boolean93/openhoyi/releases/download/dev-${target.versionCode}/" + initial.path.substringAfterLast('/') &&
            initial.path.substringAfterLast('/').matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.apk")))
        val started = clock()
        fun active() {
            if (cancelled || Thread.currentThread().isInterrupted || clock() - started > 30L * 60 * 1_000_000_000)
                throw InterruptedIOException("Download interrupted")
        }
        var address = initial
        var redirects = 0
        var complete = false
        var createdByThisAttempt = false
        try {
            while (true) {
                active()
                require(releaseApkUrlAllowed(address, initial))
                val connection = open(address.toURL())
                try {
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 15_000
                    connection.setRequestProperty("Accept-Encoding", "identity")
                    connection.setRequestProperty("Accept", "application/octet-stream")
                    val code = connection.responseCode
                    active()
                    if (code in setOf(301, 302, 303, 307, 308)) {
                        if (++redirects > 3) throw IOException("Too many download redirects")
                        val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect")
                        address = address.resolve(location)
                        if (!releaseApkUrlAllowed(address, initial)) throw IOException("Download redirect rejected")
                        continue
                    }
                    if (code != 200) throw ReleaseUpdateHttpException(code, connection.getHeaderField("Retry-After"))
                    if (connection.contentEncoding?.let { it != "identity" } == true) throw IOException("Encoded download rejected")
                    if (connection.contentLengthLong >= 0 && connection.contentLengthLong != target.apkSize)
                        throw IOException("Download length differs")
                    part.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
                    if (!part.createNewFile()) throw IOException("Download file already exists")
                    createdByThisAttempt = true
                    var received = 0L
                    var lastProgress = clock()
                    progress(0)
                    connection.inputStream.use { input ->
                        FileOutputStream(part).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                active()
                                val count = input.read(buffer)
                                if (count == -1) break
                                if (count == 0) continue
                                if (received + count > target.apkSize) throw IOException("Download too large")
                                output.write(buffer, 0, count); received += count
                                val now = clock()
                                if (now - lastProgress >= 250_000_000 || received == target.apkSize) {
                                    progress(received); lastProgress = now
                                }
                            }
                            active()
                            if (received != target.apkSize) throw IOException("Download truncated")
                            output.fd.sync()
                        }
                    }
                    complete = true
                    return
                } finally { connection.disconnect() }
            }
        } finally {
            if (createdByThisAttempt && !complete) part.delete() // This worker may only delete its unique part.
        }
    }
}
