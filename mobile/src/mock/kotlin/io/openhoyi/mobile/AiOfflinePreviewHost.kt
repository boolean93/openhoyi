package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.TextView

/** Reuses the real shell and pure policies. Dialog actions are explicitly simulations, not acceptance of live IO. */
internal class AiOfflinePreviewHost(private val activity: Activity, private val context: Context, saved: Bundle?) {
    private val visual = SettingsVisualStyle(context)
    private var state = runCatching { AiOfflinePreviewState(saved?.getByteArray("offline-fixture")) }.getOrElse { AiOfflinePreviewState() }
    private var dialog: AlertDialog? = null
    private var notice = ""
    private var cardPreview = activity.intent.getIntExtra("previewCard", -1).takeIf { it in 0..1 } ?: -1
    private fun t(zh:String,en:String) = if(context.resources.configuration.locales[0].language == "zh") zh else en
    val presentation = AiChatPresentation(context,visual,state.session.draft,0,0,
        onBack={activity.finish()},onNewChat={state.reset();cardPreview=-1;notice="";sync()},onKey={},keyEnabled=false,
        onSendOrStop=::send,onAttachment=::samples,onModel=::models,onUsage=::usage,
        onOpenCurve={show(t("曲线卡片","Curve card"),t("仅合成预览，不导航、不启动萃取。","Synthetic preview only; no navigation or extraction."))},
        curveAvailable={card -> AiCurveCardPreviewFixture.cards().any { it == card }},
        onReviewCurve={show(t("修改草稿","Edit draft"),t("仅合成预览，不打开用户编辑器、不保存曲线。","Synthetic preview only; no user editor or curve save."))},
        curveReviewable={card -> AiCurveCardPreviewFixture.cards().any { it == card }},
        onViewCurve={show(t("查看曲线","View curve"),t("仅离线预览，不访问用户曲线库。","Offline preview only; no user library access."))},
        curveLibraryAvailable={card -> AiCurveCardPreviewFixture.cards().any { it == card }})
    val root get() = presentation.root
    init { sync() }
    private fun show(title:String, body:String, positive:String?=null, action:(()->Unit)?=null) {
        dialog?.dismiss()
        dialog=AlertDialog.Builder(context).setTitle(t("离线预览 · ","Offline preview · ")+title)
            .setMessage(t("完全离线模拟，不上传、不收费、不保存用户数据。\n\n","Offline simulation: no upload, billing or user-data storage.\n\n")+body)
            .setNegativeButton(R.string.ai_cancel,null).apply { if(positive!=null)setPositiveButton(positive){_,_->action?.invoke()} }.create().also { d ->
                d.setOnDismissListener { if(dialog===d)dialog=null };d.show();visual.dialog(d)
            }
    }
    private fun choose(title:String, labels:List<String>, action:(Int)->Unit) {
        dialog?.dismiss()
        // Native list rows are inflated lazily and can inherit incompatible theme text colors.
        // Bind every row explicitly through the same app palette/typography, including recycling.
        val rows = object : BaseAdapter() {
            override fun getCount() = labels.size
            override fun getItem(position: Int) = labels[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (convertView as? TextView ?: TextView(context)).apply {
                    visual.palette.apply(this, TaskProductVisualStyle.body)
                    text = labels[position]
                    setSingleLine(false); maxLines = Int.MAX_VALUE; ellipsize = null
                    minHeight = maxOf(minHeight, visual.palette.dp(48))
                    setPadding(visual.palette.dp(24), visual.palette.dp(12), visual.palette.dp(24), visual.palette.dp(12))
                    layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
        }
        dialog=AlertDialog.Builder(context).setTitle(t("离线预览 · ","Offline preview · ")+title).setAdapter(rows){_,n-> action(n)}
            .setNegativeButton(R.string.ai_cancel,null).create().also { d -> d.setOnDismissListener{if(dialog===d)dialog=null};d.show();visual.dialog(d) }
    }
    private fun samples() = choose(t("内置合成场景","Built-in synthetic scenarios"),listOf(
        t("图片元数据（不打开picker）","Image metadata (no picker)"),t("文本附件元数据","Text attachment metadata"),t("PDF元数据 / 两阶段OCR","PDF metadata / two-stage OCR"),
        t("移除预览附件","Remove preview attachments"),t("载入5000字以上合成草稿","Load synthetic draft over 4,000 characters"),t("恢复合成状态（生产codec）","Restore synthetic state (production codec)"),
        t("compact状态（不调用模型）","Compacted state (no model)"),t("曲线确认文案（仅预览）","Curve confirmation wording (preview only)"),t("内置曲线只读边界","Factory curve read-only boundary"),t("合成推荐卡片（离线）","Synthetic recommendation card (offline)"),t("合成修改卡片（离线）","Synthetic edit card (offline)"))) { at ->
        when(at) {
            9,10 -> {cardPreview=at-9;notice="";sync()}
            0,1,2 -> { val kind=listOf(AiAttachmentKind.IMAGE,AiAttachmentKind.TEXT,AiAttachmentKind.DOCUMENT)[at];notice=if(state.add(kind))t("已添加内置元数据；无实际图片解码或文件读取。","Added built-in metadata; no image decoding or file reading.") else t("该模型不支持、已添加或最多3件。","Unsupported by this model, already added, or limit 3.");sync() }
            3 -> {state.removeDraftAttachments();sync()}
            4 -> {state.fixedDraft();notice=t("仅此固定样例可恢复；键盘自由输入不落盘。","Only this fixed fixture is restored; free-typed input is not persisted.");sync()}
            5 -> {state.restoreRoundTrip();notice=t("合成状态已通过生产SessionCodec往返；不代表真实Keystore/磁盘验收。","Synthetic state round-tripped through SessionCodec; no Keystore/disk acceptance claim.");sync()}
            6 -> {state.compactFixture();notice=t("仅构造已压缩状态展示，未调用摘要模型。","Constructed compacted display state only; no summarization call.");sync()}
            7 -> show(t("自定义曲线确认","Custom curve confirmation"),t("仅确认文案预览。未连接生产写入协调器或任何曲线库，不能据此宣称真实新增/编辑通过。\n修改前：93°C / 36g\n修改后：94°C / 38g","Wording preview only. No production write coordinator or curve store is connected. This does not prove actual creation/editing.\nBefore: 93°C / 36g\nAfter: 94°C / 38g"),t("模拟确认（不保存）","Simulate confirm (no save)")){notice=t("模拟确认完成，未保存任何曲线。","Simulated confirmation; no curve saved.");sync()}
            8 -> {check(!AiCurveDetail.validId("factory-001"));show(t("内置曲线只读","Factory curves are read-only"),t("生产AiCurveDetail.validId拒绝factory-001；这是纯ID边界检查，不是生产存储层或100条内置曲线实测。","Production AiCurveDetail.validId rejects factory-001. This is a pure ID-boundary check, not storage or all-100-curve acceptance."))}
        }
    }
    private fun models() = choose(t("独立合成模型会话","Independent synthetic model sessions"),AiModelPolicy.models.map{it.label}) { at ->
        val target=AiModelPolicy.models[at]
        show(target.label,context.getString(R.string.ai_model_switch_body),t("模拟切换","Simulate switch")){state.switch(target.id);notice=t("仅合成会话切换；未验证账户权限。","Synthetic conversation switch only; account availability not checked.");sync()}
    }
    private fun send() {
        when(state.documentStage()) {
            "needs-ocr" -> show(context.getString(R.string.ai_ocr_title),context.getString(R.string.ai_ocr_consent),t("模拟同意解析","Simulate extraction consent")) {
                check(state.beginOcr());sync();choose(t("模拟OCR结果","Simulate OCR outcome"),listOf(t("完整结果","Complete"),t("length不完整","Incomplete length"),t("未知 / 已取消","Unknown / cancelled"))) { at ->
                    state.finishOcr(unknown=at==2,incomplete=at==1);notice=t("不会自动聊天；完整结果请再次点发送。","Chat never starts automatically; tap Send again for complete results.");sync()
                }
            }
            AiOcrStatus.UNKNOWN.name,AiOcrStatus.INCOMPLETE.name,AiOcrStatus.IN_FLIGHT.name -> {notice=t("未知/不完整状态阻止重复解析与聊天；没有自动重试。","Unknown/incomplete state blocks repeated extraction and chat; no automatic retry.");sync(false)}
            else -> show(context.getString(R.string.ai_attachment_send_title),context.getString(R.string.ai_attachment_send_body),t("模拟聊天确认","Simulate chat confirmation")) {
                notice=if(state.sendSynthetic())t("只追加固定合成回答。输入框自由文字未发送、未保存。","Appended a fixed synthetic answer. Free-typed input was not sent or saved.") else t("预览样例已达边界，请新建预览。","Preview fixture limit reached; start a new preview.")
                sync()
            }
        }
    }
    private fun usage() {
        val sample=AiUsageSnapshot(since=1794700800000L,models=listOf(AiModelUsage(state.session.modelId,2,1,1000,120,600,400,1)),searches=1,searchEmpty=1)
        dialog?.dismiss();dialog=AiUsageDialog.create(context,sample,sample).also{it.setTitle(t("离线合成用量（非账单）","Synthetic offline usage (not billing)"));it.show();visual.dialog(it)}
    }
    private fun sync(resetInput:Boolean=true) {
        if(resetInput)presentation.input.setText(state.session.draft)
        val banner=t("隔离离线预览：所有动作均模拟。＋可选场景；不触及真实会话/Key/文件/曲线/网络。","Isolated offline preview: all actions simulated. Use + for scenarios. No real session/key/file/curve/network access.")
        val status=listOf(banner,notice,"OCR: "+state.documentStage(),if(state.session.protocol.coveredLines>0)context.getString(R.string.ai_compacted,state.session.protocol.coveredLines/2) else "").filter{it.isNotBlank()}.joinToString("\n")
        presentation.render(if(cardPreview >= 0) AiCurveCardPreviewFixture.lines(cardPreview) else state.session.lines,state.session.draft,false,status,state.session.draftAttachments,modelId=state.session.modelId)
    }
    fun save(out:Bundle) { out.putByteArray("offline-fixture",state.snapshot()) }
    fun pause() { dialog?.dismiss();dialog=null;presentation.dismissOptions();state.interrupt() }
    fun destroy() { pause() }
}
