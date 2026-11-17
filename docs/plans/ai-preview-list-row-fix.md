# Mock136 合成预览列表可见性修复

- 真实发现：135同签名-r安装成功，模型弹窗不再BadToken，但03与04稳定截图均显示模型项白底不可见；XML存在节点不等于可见/可操作，未盲点或报切换通过。134/135原APK及源码不改。
- 来源/工作/目标：既有develop来源feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；未来目标develop。按[Git工作流](../git-workflow.md)/[模板](task-template.md)保留工作树，不PR/push/合入。Sol唯一writer/Gradle/本次合成设备操作者，Astra前置方案/终审。
- 最小方案/文件：只AiOfflinePreviewHost.choose从native setItems改BaseAdapter，每次getView含回收行用visual.palette.apply(body)明确ink与统一SP字体；wrap_content+48dp minimum、上下padding、多行、不省略；不改shared样式/全局theme/回调/真实模型流程/owner guard。沿用135 Activity-base窗口context。
- 验证：最小assembleMock+lintMock，复用wrapper5项state/fence/JVM功能证据，不重复52/13矩阵。关键是安装后稳定截图真实看见Flash/Pro、实际点击并回返；只合成preview，再PDF确认取消或未知阻止、codec/usage/curve取消等连贯最小流程，不机械全组合或长输入。
- 候选：先确认136未占，Gradle属性hoyiVersionCode=136、hoyiVersionName=0.1.0-local.ai-features-136，不改共享version文件，包名io.openhoyi.mobile.mock、signer须与132..135一致。freeze允许清单源码/archive/SHA/diff/签名/构建lint。
- 本次授权：root明确转达当前设备自135安装后始终为本任务合成preview、无真实chat数据活动，可在持续确知条件下同签名-r136继续此测试修复，无需重复弃聊确认；若用户新活动则停止。仅Mock，绝不卸载/清数据/读Key或正文/收费API/正式App/机器/CU/系统安全权限。
- 体验边界：目前preview是Android真实渲染的合成UI，不接真实picker/Keystore/生产curve coordinator；native确认文案取消不能升级为真实写链验收。字号/语言使用现有配置和palette，真实关键区域至少48dp需截图/实际测量证据，不只minHeight。
- 发布/回退：非公开Mock候选、不标签/Release；保留134/135不可变APK与失败截图，不以降级/卸载/清记录回退；未知状态/持久数据不删除。
- 剩余：真实模型/OCR/搜索、文件provider/Keystore/机器与完整字号矩阵不在本次范围。必要小闭环失败立即记录，不用不可见节点代替视觉通过。
