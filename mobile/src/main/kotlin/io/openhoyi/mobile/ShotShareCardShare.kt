package io.openhoyi.mobile

import android.app.Activity
import android.app.Dialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ShotShareCardFileProvider : FileProvider()

internal object ShotShareCardShare {
    private const val DIRECTORY = "shot-share"
    private const val STALE_MS = 24 * 60 * 60 * 1000L
    private val running = AtomicBoolean()
    private val disk = Executors.newSingleThreadExecutor { Thread(it, "shot-share-export") }

    // Pass only the share card bitmap, not the dialog chrome or any BLE/AI state.
    @MainThread
    fun launch(
        activity: Activity,
        bitmap: Bitmap,
        origin: Dialog,
        encoder: Executor = disk,
        isCurrent: () -> Boolean = { true },
    ) {
        fun mayPresent() = !activity.isFinishing && !activity.isDestroyed && origin.isShowing &&
            origin.window?.decorView?.hasWindowFocus() == true && isCurrent()
        if (!mayPresent() || !running.compareAndSet(false, true)) return
        val app = activity.applicationContext
        encoder.execute {
            val result = runCatching {
                require(!bitmap.isRecycled)
                val directory = File(app.cacheDir, DIRECTORY)
                check(directory.isDirectory || directory.mkdirs())
                val cutoff = System.currentTimeMillis() - STALE_MS
                directory.listFiles()?.forEach {
                    if (it.isFile && it.name.startsWith("OpenHOYI-") && it.extension == "png" && it.lastModified() in 1 until cutoff) {
                        it.delete()
                    }
                }
                val file = File.createTempFile("OpenHOYI-", ".png", directory)
                try {
                    file.outputStream().buffered().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    file to FileProvider.getUriForFile(app, "${app.packageName}.shot-share", file)
                } catch (failure: Exception) {
                    file.delete()
                    throw failure
                }
            }
            activity.runOnUiThread {
                running.set(false)
                if (!mayPresent()) {
                    // No chooser/grant was created. Remove only this invocation's own file.
                    result.getOrNull()?.first?.let { file -> disk.execute { file.delete() } }
                    return@runOnUiThread
                }
                result.fold(onSuccess = { (file, uri) ->
                    val title = activity.getString(R.string.shot_share_sheet_title)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newUri(activity.contentResolver, title, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { activity.startActivity(Intent.createChooser(send, title)) }
                        .onFailure {
                            disk.execute { file.delete() }
                            Toast.makeText(activity, R.string.shot_share_no_target, Toast.LENGTH_SHORT).show()
                        }
                }, onFailure = {
                    Toast.makeText(activity, R.string.shot_share_failed, Toast.LENGTH_SHORT).show()
                })
            }
        }
    }
}
