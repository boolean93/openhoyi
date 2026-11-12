package io.openhoyi.mobile

import android.app.Activity

/** UI ports keep rendering independent of transport. Product construction always uses real installed identity. */
internal interface ReleaseUpdateUiActions {
    val metadata: ReleaseUpdateCoordinator
    val downloads: ReleaseDownloadController
    val rateLimited: Boolean
    fun identity(): InstalledUpdateIdentity?
    fun download(target: ReleaseUpdate): Boolean
    fun install(): Boolean
    fun cancel()
    fun discard()
}

internal class LiveReleaseUpdateUiActions(private val activity: Activity) : ReleaseUpdateUiActions {
    private val owner = (activity.application as MobileApplication).releaseUpdates
    override val metadata get() = owner.coordinator
    override val downloads get() = owner.downloads.controller
    override val rateLimited get() = owner.downloads.rateLimited
    override fun identity() = installedUpdateIdentity(activity)
    override fun download(target: ReleaseUpdate) = owner.download(activity, target)
    override fun install() = owner.downloads.install(activity)
    override fun cancel() = owner.downloads.cancel()
    override fun discard() = owner.downloads.discard()
}
