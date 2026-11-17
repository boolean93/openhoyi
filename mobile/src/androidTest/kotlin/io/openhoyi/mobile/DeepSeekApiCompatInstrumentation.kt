package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle

/** No network, KeyStore or local business data. Only forces the vendor SDK list-merge branch. */
class DeepSeekApiCompatInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { start() }
    override fun onStart() {
        val outcome = if (BuildConfig.MOCK_MODE && targetContext.packageName == "io.openhoyi.mobile.mock")
            DeepSeekApiCompatFixture.run() else "WRONG_TARGET"
        @Suppress("DEPRECATION") val code = targetContext.packageManager.getPackageInfo(targetContext.packageName, 0).versionCode
        val line = "api=${Build.VERSION.SDK_INT} app=$code result=$outcome noNetwork=true noCredentials=true"
        finish(Activity.RESULT_OK, Bundle().apply { putString("compat", line); putString("stream", "DEEPSEEK_API_COMPAT $line\n") })
    }
}
