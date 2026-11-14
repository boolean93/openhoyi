package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.TextView
import java.io.File

/** Display-only Mock probe: no preferences, service state or hardware commands are changed. */
class ManualPendingRecipeTitleChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(BuildConfig.MOCK_MODE && targetContext.packageName == "io.openhoyi.mobile.mock")
            var failure: Throwable? = null
            runOnMainSync {
                try {
                    val folder = File(targetContext.getExternalFilesDir(null), "manual-pending-title").apply { mkdirs() }
                    for (size in AppFontSize.entries) for (dark in listOf(false, true))
                        for (language in listOf(AppLanguage.CHINESE, AppLanguage.ENGLISH)) {
                            val context = AppLanguageContext.wrap(targetContext, language, dark, size)
                            val style = TaskProductVisualStyle(context)
                            val main = style.text("", TaskProductVisualStyle.Type(18, 500, 26)).apply {
                                maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END
                            }
                            val title = ManualPendingRecipeTitle(main, style)
                            val name = context.getString(R.string.extraction_manual_label)
                            val pending = title.getChildAt(1) as TextView
                            for (width in listOf(112, 190, 320)) {
                                val px=(width*context.resources.displayMetrics.density).toInt()
                                fun layout() {
                                    title.measure(View.MeasureSpec.makeMeasureSpec(px,View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                                    title.layout(0,0,title.measuredWidth,title.measuredHeight)
                                }
                                title.show(name,null); layout()
                                val ordinaryHeight=title.height
                                check(pending.visibility==View.GONE)
                                title.show(name,"Long user recipe · 很长的自定义曲线名称".repeat(4)); layout()
                                check(main.lineCount==1 && pending.lineCount==1)
                                check(pending.visibility==View.VISIBLE && pending.height>0 && pending.bottom<=title.height)
                                check(pending.layout.getEllipsisCount(0)>0)
                                val bitmap=Bitmap.createBitmap(title.width,title.height,Bitmap.Config.ARGB_8888)
                                Canvas(bitmap).also { it.drawColor(style.surface); title.draw(it) }
                                File(folder,"${language}-${size}-${dark}-${width}.png").outputStream().use {
                                    bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                                }; bitmap.recycle()
                                title.show(name,null); layout()
                                check(title.height==ordinaryHeight && main.maxLines==1)
                            }
                        }
                } catch(error:Throwable) { failure=error }
            }
            failure?.let { throw it }
            result.putString("stream","MANUAL_PENDING_TITLE_PASSED languages=2 themes=2 sizes=3 widths=3 noHardware=true noPreferences=true\n")
            finish(Activity.RESULT_OK,result)
        } catch(error:Throwable) {
            result.putString("stream",error.stackTraceToString()); finish(Activity.RESULT_CANCELED,result)
        }
    }
}
