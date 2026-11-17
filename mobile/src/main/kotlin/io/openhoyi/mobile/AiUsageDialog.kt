package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.Context
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

internal object AiUsageDialog {
    fun create(context: Context, session: AiUsageSnapshot, device: AiUsageSnapshot): AlertDialog {
        fun section(title: Int, value: AiUsageSnapshot): String = buildString {
            append(context.getString(title)); append('\n')
            append(context.getString(R.string.ai_usage_since, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value.since)))); append('\n')
            if (value.models.isEmpty()) append(context.getString(R.string.ai_usage_none))
            else value.models.forEachIndexed { index, model ->
                if (index > 0) append('\n')
                if (!model.hasReportedUsage) append(context.getString(R.string.ai_usage_model_unknown, model.model, model.requests))
                else append(context.getString(R.string.ai_usage_model, model.model, model.requests, model.input, model.output, model.input + model.output))
            }
            append('\n')
            val cacheRate = value.cacheHitRate
            if (cacheRate == null) append(context.getString(R.string.ai_usage_cache_unknown))
            else append(context.getString(R.string.ai_usage_cache, NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 }.format(cacheRate), value.cacheReported, value.requests))
            if (value.unknownChat > 0) { append('\n'); append(context.getString(R.string.ai_usage_missing, value.unknownChat)) }
            append('\n'); append(context.getString(R.string.ai_usage_search, value.searches, value.unknownSearch))
            if (value.incomplete) { append('\n'); append(context.getString(R.string.ai_usage_incomplete)) }
        }
        return AlertDialog.Builder(context).setTitle(R.string.ai_usage_title)
            .setMessage(section(R.string.ai_usage_session, session) + "\n\n" + section(R.string.ai_usage_device, device) + "\n\n" + context.getString(R.string.ai_usage_note))
            .setPositiveButton(R.string.ai_usage_close, null).create()
    }
}
