package io.openhoyi.mobile

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Process
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.KeyStore
import java.util.UUID
import org.json.JSONObject

/** Dedicated synthetic storage only. No default KeyStore/Vault/Session constructors or real owners. */
internal class AiStorageProbeHost(private val activity: Activity, context: Context) {
    private val palette = SettingsVisualStyle(context).palette
    private val status = palette.text("Mock专用存储验收：固定合成内容，无网络。非系统picker。\n启动后点击准备；再次启动此固定入口可恢复本次对象。", TaskProductVisualStyle.body)
    private var busy = false
    private var verified = false
    private var destroyed = false
    private val rootDir get() = File(activity.noBackupFilesDir, AiStorageProbeScope.ROOT)
    private val registry get() = File(rootDir, "registered_run.json")
    val root = ScrollView(context).apply {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(32,32,32,32)
            addView(status)
            fun button(label: String, action: () -> Unit) { addView(Button(context).apply {
                palette.apply(this, TaskProductVisualStyle.body); text = label; minHeight = palette.dp(48)
                setOnClickListener { if (!busy) action() }
            }) }
            button("准备固定合成副本") { work { seed(); "准备并回读通过；可仅结束本测试进程，然后重新打开固定验收入口。" } }
            button("恢复本次合成副本") { work { verify(); verified = true; "AndroidKeyStore解密/附件副本/Session磁盘恢复通过；未知URI与损坏副本已拒绝。非真实picker或用户会话。" } }
            button("仅结束本测试进程") {
                if (verified && !busy && guard()) Process.killProcess(Process.myPid())
                else status.text = "未完成保存回读，禁止结束进程。"
            }
            button("返回") { activity.finish() }
        })
    }
    fun destroy() { destroyed = true }
    private fun guard() = BuildConfig.MOCK_MODE && activity.packageName == "io.openhoyi.mobile.mock" &&
        (activity.application as MobileApplication).offlinePreviewProcessReady
    private fun work(action: () -> String) {
        check(guard()); busy = true; verified = false; status.text = "仅处理本次合成对象…"
        Thread {
            val result = try { action() } catch (_: Exception) { "验收未确认；保留本次对象，不自动重试、不清理未知文件。" }
            activity.runOnUiThread { busy = false; if (!destroyed) status.text = result }
        }.start()
    }
    private fun safeDirectory(file: File) {
        check(!Files.isSymbolicLink(file.toPath()))
        check(file.isDirectory || file.mkdir())
        check(Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS))
    }
    private fun runDir(id: String): File { AiStorageProbeScope.checked(id); return File(rootDir,id).also { check(it.canonicalFile.parentFile == rootDir.canonicalFile); check(Files.isDirectory(it.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(it.toPath())) } }
    private fun registered(): Pair<String,String> {
        safeDirectory(rootDir)
        check(Files.isRegularFile(registry.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(registry.toPath()) && registry.length() in 1..256)
        val o = JSONObject(registry.readText()); check(o.keys().asSequence().toSet() == setOf("schema","run","phase") && o.getInt("schema") == 1)
        val id = AiStorageProbeScope.checked(o.getString("run")); val phase = o.getString("phase"); check(phase in setOf("pending","ready")); runDir(id)
        return id to phase
    }
    private fun key(id: String, kind: String) = AndroidAiKeySource(AiStorageProbeScope.alias(id,kind))
    private fun ownedSubdir(id: String, name: String): File = File(runDir(id),name).also { safeDirectory(it) }
    private fun vault(id: String) = AiAttachmentVault(AndroidAiAttachmentStorage(ownedSubdir(id,"attachments")),key(id,"attachments"),id)
    private fun sessions(id: String) = AiSessionStore(AndroidAiSessionStorage(ownedSubdir(id,"session")),key(id,"session"))
    private fun corruptId(id: String) = UUID.nameUUIDFromBytes("probe-corrupt:$id".toByteArray()).toString()
    private fun seed() {
        check(guard()); safeDirectory(rootDir)
        check(!Files.exists(registry.toPath(), LinkOption.NOFOLLOW_LINKS)) // Existing/unknown runs never replaced.
        val id = UUID.randomUUID().toString(); val dir = File(rootDir,id); check(dir.mkdir())
        listOf("attachments","session").forEach { check(File(dir,it).mkdir()) }
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        check(listOf("attachments","session").none { ks.containsAlias(AiStorageProbeScope.alias(id,it)) })
        check(registry.createNewFile())
        java.io.FileOutputStream(registry).use { it.write(JSONObject().put("schema",1).put("run",id).put("phase","pending").toString().toByteArray()); it.fd.sync() }
        val v = vault(id); val source = AndroidAiAttachmentSource(activity)
        val attachments = mutableListOf<AiAttachment>()
        val sourceDigests = mutableListOf<String>()
        for (path in listOf("/text","/pdf","/image")) source.prepare(Uri.parse("content://${AiStorageProbeScope.AUTHORITY}$path"),attachments).use { sourceDigests += digest(it.bytes); v.save(it); attachments += it.attachment }
        // Separate owned object intentionally damaged; never a user/reference copy.
        val corruptBytes = "Fixed corrupt synthetic copy".toByteArray()
        val corrupt = AiAttachment(corruptId(id),"corrupt.txt","text/plain",corruptBytes.size,AiAttachmentKind.TEXT)
        AiPreparedAttachment(corrupt,corruptBytes).use { v.save(it) }
        val corruptFile = File(File(dir,"attachments"),corrupt.id)
        check(Files.isRegularFile(corruptFile.toPath(),LinkOption.NOFOLLOW_LINKS))
        java.io.RandomAccessFile(corruptFile,"rw").use { it.seek(it.length()-1); val b=it.readByte(); it.seek(it.length()-1); it.writeByte(b.toInt() xor 1); it.fd.sync() }
        val store = sessions(id); check(store.load(id) is AiSessionLoad.Empty)
        check(store.save(AiStorageProbeFixture.session(id, attachments, "Fixed synthetic draft|" + sourceDigests.joinToString("|"))))
        verifyRun(id)
        android.util.AtomicFile(registry).let { f -> val out=f.startWrite(); try { out.write(JSONObject().put("schema",1).put("run",id).put("phase","ready").toString().toByteArray()); out.fd.sync(); f.finishWrite(out) } catch(e:Exception) { f.failWrite(out); throw e } }
        check(registered() == id to "ready"); verified = true
    }
    private fun verify() { check(guard()); val (id,phase)=registered(); check(phase == "ready"); verifyRun(id) }
    private fun digest(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun verifyRun(id: String) {
        val loaded = sessions(id).load(id) as? AiSessionLoad.Ready ?: error("Unavailable")
        AiStorageProbeFixture.verify(loaded.session)
        check(loaded.session.draftAttachments.size == 3)
        val expected = loaded.session.draft.split("|")
        check(expected.size == 4 && expected.first() == "Fixed synthetic draft")
        val v=vault(id)
        loaded.session.draftAttachments.forEachIndexed { index,a ->
            v.load(a).use { check(digest(it.bytes) == expected[index+1]) }
        }
        val text = loaded.session.draftAttachments.first(); v.load(text).use { check(it.bytes.contentEquals("Fixed synthetic coffee document.\nNo user data.\n".toByteArray())) }
        val bad = AiAttachment(corruptId(id),"corrupt.txt","text/plain","Fixed corrupt synthetic copy".toByteArray().size,AiAttachmentKind.TEXT)
        check(runCatching { v.load(bad).close() }.isFailure)
        val source=AndroidAiAttachmentSource(activity)
        check(runCatching { source.prepare(Uri.parse("content://${AiStorageProbeScope.AUTHORITY}/unknown"), emptyList()).close() }.isFailure)
        fun rejected(path: String, expected: AiAttachmentProblem, cancel: Boolean = false) {
            val signal = android.os.CancellationSignal()
            val result = runCatching {
                source.prepare(Uri.parse("content://${AiStorageProbeScope.AUTHORITY}$path"), emptyList(), signal,
                    opened = { if (cancel) signal.cancel() }).close()
            }
            check((result.exceptionOrNull() as? AiAttachmentFailure)?.problem == expected)
        }
        rejected("/short", AiAttachmentProblem.UNAVAILABLE)
        rejected("/wrong-size", AiAttachmentProblem.SIZE)
        rejected("/cancel", AiAttachmentProblem.UNAVAILABLE, cancel = true)
    }
}

/** Pure synthetic fixture reused by JVM checks and Android encrypted disk readback. No IO or requests. */
internal object AiStorageProbeFixture {
    private val messages = kotlinx.serialization.json.Json.parseToJsonElement("""[
        {"role":"user","content":"Fixed tool question"},
        {"role":"assistant","content":null,"reasoning_content":"Fixed synthetic reasoning","tool_calls":[{"id":"probe-call","type":"function","function":{"name":"current_status","arguments":"{}"}}]},
        {"role":"tool","tool_call_id":"probe-call","content":"Fixed synthetic result"},
        {"role":"assistant","content":"Fixed tool answer"}
    ]""").let { it as kotlinx.serialization.json.JsonArray }.map { it as kotlinx.serialization.json.JsonObject }
    fun session(epoch: String, attachments: List<AiAttachment>, draft: String): AiSession {
        val lines = listOf(AiChatLine(true,"Fixed summarized question"), AiChatLine(false,"Fixed summarized answer"),
            AiChatLine(true,"Fixed tool question"), AiChatLine(false,"Fixed tool answer"))
        val protocol = AiProtocolHistory.checked(messages, summary = "Fixed uncertain summary; no authorization.",
            coveredLines = 2, reportedInput = 200000, reportedOutput = 64)
        val inactive = AiSession(epoch, emptyList(), AiProtocolHistory.empty(AiModelPolicy.PRO), "Fixed Pro draft", AiUsageSnapshot(since=1))
        return AiSession(epoch, lines, protocol, draft, AiUsageSnapshot(since=1), attachments, listOf(inactive),
            listOf(AiOcrRecord(attachments.single { it.kind == AiAttachmentKind.DOCUMENT }, AiOcrStatus.IN_FLIGHT)))
    }
    fun verify(session: AiSession) {
        check(session.modelId == AiModelPolicy.FLASH && session.lines.size == 4)
        check(session.protocol.coveredLines == 2 && session.protocol.summary == "Fixed uncertain summary; no authorization.")
        check(session.protocol.reportedInput == 200000L && session.protocol.reportedOutput == 64L)
        check(session.protocol.messages == messages && AiContextPolicy.matches(session.protocol, session.lines))
        check(session.inactive.single().let { it.modelId == AiModelPolicy.PRO && it.draft == "Fixed Pro draft" && it.protocol.messages.isEmpty() })
        check(session.ocr.single().let { it.status == AiOcrStatus.UNKNOWN && it.extracted == null && it.original.id == session.draftAttachments.single { a -> a.kind == AiAttachmentKind.DOCUMENT }.id })
    }
}
