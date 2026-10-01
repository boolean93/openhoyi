package io.openhoyi.mobile

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.util.IdentityHashMap
import java.util.UUID

/** Reports all app pages, including readers that never bind the device service.
 * Android saves state before destroying a configuration-changing instance. */
internal class AppVisibilityCallbacks(private val visibility: AppVisibility) : Application.ActivityLifecycleCallbacks {
    private val owners = IdentityHashMap<Activity, String>()
    private fun owner(activity: Activity): String = owners.getOrPut(activity) { UUID.randomUUID().toString() }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        owners[activity] = savedInstanceState?.getString(OWNER)?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString()
    }
    override fun onActivityStarted(activity: Activity) {
        visibility.started(owner(activity))
        if (BuildConfig.MOCK_MODE) android.util.Log.i("OpenHoyiLifecycle", "started:${activity.javaClass.simpleName}")
    }
    override fun onActivityStopped(activity: Activity) {
        visibility.stopped(owner(activity), activity.isChangingConfigurations)
        if (BuildConfig.MOCK_MODE && activity.isChangingConfigurations)
            android.util.Log.i("OpenHoyiLifecycle", "recreating:${activity.javaClass.simpleName}")
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        outState.putString(OWNER, owner(activity))
    }
    override fun onActivityDestroyed(activity: Activity) {
        owners.remove(activity)?.let { visibility.destroyed(it, activity.isChangingConfigurations) }
        if (BuildConfig.MOCK_MODE && activity.isChangingConfigurations)
            android.util.Log.i("OpenHoyiLifecycle", "destroyed:${activity.javaClass.simpleName}:configuration")
    }
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit

    private companion object { const val OWNER = "io.openhoyi.mobile.visibility.owner" }
}
