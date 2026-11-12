# PR 候选基线与隔离验收（当前，2026-11-12）

- 唯一主树：`/Users/boolean93/workspace/hoyi_app/openhoyi-in-app-update`，`feature/in-app-update`；从最新 `origin/develop` 的 `56e3b9ac4174fa64b9dc7fd1f7637daf902e68b0` 建立。仅迁入旧device-check相对e9dd235b的任务差异，含公共Switch入口接线，无冲突；旧树冻结，不把未合入扫描历史或其他任务改动带入PR。最新基线已含空拨杆/late recovery/framing修复。
- 用户授权无需人工验收批准即可推进上线；Sol负责本地实现/提交/检查，协调者统一推送、通过 `scripts/create-pr` 建PR、独立审查、必需CI、合并及后续发布；Sol本轮不自行push/PR/merge/发布。正式版/签名/版本与既有发布门禁仍有效。
- 只在显式 `-PhoyiUpdateChecks=true` 下新增隔离Mock宿主 `io.openhoyi.mobile.updatefixture` 和专用AndroidTest sourceSet，系统安装目标固定 `io.openhoyi.updatefixture`；不覆盖真实主包/普通Mock/其test provider。真实产品组件通过generic UiActions ports渲染，默认产品仍Live实际身份/资格，不存在产品Intent身份覆盖入口。
- 夹具用真实本地签名APK字节流驱动进度/取消/失败；真实Android apksig及PM解析、生产资格拒绝、FileProvider实际读与拒staging、系统组件和权限往返分别记录。不是公开网络下载或真实生产升级资格证明。未授权未知来源时不改appops/settings/策略、不拨开关，仅验证拒绝和实际系统页返回；正向安装器确认/实际安装明确不宣称通过。
- 复用 `mock-lifecycle.yml` 的原单job，固定boolean `update_checks` 仅手动入口切换API34窄runner，不增加push时skipped job影响develop发布门禁。固定脚本 `python3 scripts/check_update_acceptance.py --sdk "$ANDROID_HOME"` 仅接受fresh QEMU，固定namespace；不接受任意shell/下载URL/包名。CI截图为真实合成帧的原生app可见内容矩形裁切，含Popup，不保存通知栏；每图记录方向/主题/状态及受控输入。
- 基础本地检查：隔离宿主/runner编译 **PASS**（`/tmp/hoyi-in-app-update/fixture-build.log`）；实际APK包名/manifest/无BLE/定位/无旧provider/签名匹配 **PASS**（`fixture-manifests.log`）。fresh-emulator守卫3项、workflow policy6项通过。真实API34运行待协调者推送后调度，未运行本地AVD或平板。
- PR创建前仍须本候选最终完整本地门禁；旧阶段结果仅作历史证据，不冒称新基线已全部通过。统一Switch及曲线删除并行PR合入后，在最终PR前统一同步develop并做受影响检查。

以下为先前实现/软件验证历史记录；其中旧工作树、旧APK哈希、旧设备阻塞不是当前候选的CI运行结论。

# 应用内更新：当前实现与验证（2026-11-12）

> 当前为“用户点击下载 → 应用内真实进度 → 完整校验 → 安全条件下打开系统安装确认”。早期“跳转 Release 页面下载”方案已废止，旧工作树 `openhoyi-auto-update` 仅冻结留档，不用于交付。

- 目标：设置中一行左侧当前版本、右侧检查更新；自动检查开关默认开启。仅在需要时显示结果、真实进度、取消、失败重试和安装入口。B 右上轻提示提供“稍后/下载更新”，下载后在原位显示进度与取消；收起进度卡撤销自动安装意图，但用户已启动的下载可继续。
- 基线/历史实现树：`e9dd235bd9ba008c9ab0612a5487218454cfbd4b`，即 develop `df108928` 与扫描修复 `2886c554` 的本地整合；`/Users/boolean93/workspace/hoyi_app/openhoyi-auto-update-device-check`，分支 `feature/auto-update-device-check`。保留扫描修复行为与差异；`AppSettingsFragment.kt` 的本任务改动仅去掉重复版本行，不宣称扫描涉及文件完全未修改。原树不再双写。
- 规则：[Git 工作流](../git-workflow.md)、[任务模板](task-template.md)。本轮不提交、推送、创建 PR、合并或发布；未来如授权仍经 `scripts/create-pr` 与必需 CI。未改默认版本/签名策略。
- 分工：Astra 独立设计、只读安全复核；Sol 唯一实现与本地验证。Astra 已确认最终软件无剩余代码安全阻断，设备证据不包含在此结论内。

## 当前行为与边界

- 每日到期自动检查，24h 加 0–2h 持久错峰；普通自动错误 24h 起指数退避。手动无次数/固定冷却，仅合并并发并遵守服务端限流。429/带 Retry-After 的503尊重明确截止；无有效头的429指数退避。关闭自动检查不取消用户手动检查或下载。
- 固定官方 `dev.json` 严格校验，不扩 schema/发布链路。Mock、Debug、本机身份未知不自动检查或取得生产下载资格；诊断手动检查保留。自动提示仅匹配主包、实际本机签名及更高 versionCode；稍后按版本/签名/摘要在本进程去重，重启可重验缓存提醒，不绕过联网期限。
- APK 下载仅在用户明确点击后启动。独立单任务与不可变目标；受限 HTTPS，仅初始本仓库固定附件路径、最多3次跳转及精确 `release-assets.githubusercontent.com` CDN；不传凭据/Cookie/Range，不自动重试。实际收到字节驱动进度；长度上限/精确EOF/声明长度检查，连接/读取/整体超时，取消、断网、空间不足可显式重试。
- 私有 `update-staging` 与 `updates/verified` 分离。完整大小、SHA256、ApkVerifier真实密码学验签、单签名/无轮换回退、包名、精确目标版本、递增本机版本、非debuggable、非split、设备minSDK兼容、索引/实际APK/本机签名三向一致全部通过才成为Ready。每次重新安装动作及未知来源授权返回均重新全量验证。
- FileProvider只映射verified子目录，非exported，只发具体APK的READ URI给显式受信系统组件；不授WRITE、不授sidecar URI。仅 `REQUEST_INSTALL_PACKAGES` + 系统安装确认，无静默安装或无人值守commit。实际安装成功仅据重新读取的本机包身份，不能把打开安装器/返回当成功。
- 用户点击下载签发一次性内存安装租约，绑定目标、代际、Activity与primary Fragment、前台和安全epoch。下载/校验可在后台完成；任何普通暂停、离页/重建、busy、取消、失败或收起进度交互永久撤销自动续装。仍同一安全前台流程才自动打开系统确认；否则保留包供“安装更新”主动恢复。未知来源授权仅允许匹配的ActivityResult流程续行，posted callback还须持有原token对象；旧回调不得启动或撤销新流程。
- 全局门禁复用Service只读状态：萃取/预检/待开始/归零/关键写入与未决恢复记录阻止提示和系统跳转。关键控制入口与被动萃取帧立即撤销租约，不能仅靠轮询遗漏短busy；有候选或有效租约时辅以短周期复核。未改机器命令、协议、队列、重试或未知记录处理。
- 每个交系统的APK先原子持久化 `.handoff` 保留标记；写失败不打开安装器。标记不是verified/已安装凭据，不恢复网络/安装意图。进程重启或新任务不会清理仍被系统引用的包；只在实际同包同签版本达标或用户明确放弃后清理，放弃先撤READ grant。未知/损坏标记和非本任务文件不碰。part只按可证所有权、无在途worker清理。

## 本地验证结果

检查均针对本任务未提交工作树。本机日志不是远端CI或设备验收证据。

1. `scripts/verify-pr-checks full`：**PASS**，日志 `/tmp/hoyi-auto-update-download-verify.log`。包含全部现行Python/本地化、core/session/Bluetooth/诊断app/Mobile单测、双包D8构建、lint、APK资源与release配置防护；Gradle 2m36s。release guard最终实际输出 `RELEASE_CONFIGURATION_CHECKS_PASSED cases=12`，不是仅前10行PASS计数。
2. 最后handoff持久保护/总体超时修复后，运行受影响检查：
   `./gradlew --no-daemon --console=plain :mobile:testDebugUnitTest :mobile:testMockUnitTest :mobile:assembleDebug :mobile:assembleMock :mobile:lintDebug`
   **PASS**，11m36s（本机多组Java构建并发资源竞争，未干扰其他任务）。日志 `/tmp/hoyi-auto-update-download-final.log`。Debug **656**、Mock **663** 项，均0失败/0错误/1项既有skip；更新相关各**44**项通过，含跨重启handoff保留/匹配已安装清理/未知文件保护。lint 0错误，475 warnings。
3. 真实本地生成的签名、unsigned、签后篡改APK：生产相同 `verifyReleaseApkSignature` 在JVM按平台级别26、35规则验证；正确签名/摘要通过，unsigned/tampered拒绝。fixture密钥仅临时目录生成后销毁。这**不是Android26/35设备运行证据**。脚本 `scripts/prepare_update_apk_fixtures.py`。
4. 最终双包 `localization/verify_apk_resources.py`：**PASS**，各3241项字符串、6项literal round-trip；日志 `/tmp/hoyi-auto-update-download-apk-resources-final.log`。`git diff --check`通过。没有在最后软件修复后重复无关完整矩阵。
5. 纯JVM覆盖受限跳转/真实字节/截断溢出/取消与预存文件保护、单槽与旧回调、每日/手动/关闭语义、授权token与posted旧结果、重启无自动动作、实际已装后旧任务归一化、全局忙碌投影、横竖屏尺寸约束。Android生命周期、PackageManager归档解析、FileProvider授权和系统界面的实际运行仍待验证。

## 本地软件APK（不能用于当前平板覆盖验收）

两包均 `versionCode=1`、`versionName=0.1.0`，仅构建检查产物；不是已装91的安全覆盖包，不作为“可安全体验包”发布或上传。源码保留扫描修复，但尚未整合/核实其他任务的空拨杆修复 `3e210610` 是否已装，因此不能重新构建91后直接覆盖。

- Debug：`mobile/build/outputs/apk/debug/mobile-debug.apk`，SHA256 `e19fe59a705cad5d55b5a6b2d88e295411285d59786c4b0d35ac27edbed1ea90`。
- Mock：`mobile/build/outputs/apk/mock/mobile-mock.apk`，SHA256 `30b57c3f611aa0722f4e16c34129ce0b3bfa722d0eec4c5e3cb97e421bed08a3`。
- 本地身份/哈希记录 `/tmp/hoyi-auto-update-device-check/local-software-apks.json`。本地产物验签成功不等于当前已装身份已重新确认，也不等于覆盖验收。

## 设备状态、剩余阻塞与后续安全方案

- 用户判定非萃取的规则沿用：不在曲线页、咖啡机未连接或曲线结束均视为非萃取，不再额外泛泛以“机器空闲未知”阻塞。设备是否被占用、最新功能不能覆盖丢失、有效备份，是独立前提。
- 最后一次获准只读预检：前台 `io.openhoyi.mobile/.MainActivity`，不是系统桌面，近期用户活动未能可靠确认。立即停止，没有继续读取APK身份、安装、启动/切换、截图或控制机器。记录 `/tmp/hoyi-auto-update-device-check/latest-read-only-preflight/summary.json`，不持续轮询。
- 先前应用数据备份因前后业务文件哈希变化而失败；只有首份raw tar，未持久保存二次读数，不能证明变化仅日志，也不能当有效备份。私有备份目录不上传Library。未强停真包、卸载或清数据。
- 尚无新实窗截图，未用旧布局图/生成图冒充；未做Android实际apksig、权限往返、系统安装器或Mock四状态实窗。生产Mock资格未放宽，且本轮未增加任何产品可访问的测试身份入口。
- 父线程已认可未来安装器验证仅用全新隔离目标 `io.openhoyi.updatefixture`，不得以主包或现有Mock为installer target；真实UI通过androidTest隔离fixture驱动并明确标注，不能冒称生产更新资格/网络验收。恢复前先确认无人使用，取得已装最新源码（含扫描/空拨杆修复）及稳定业务备份，再同源码同签同版本覆盖Debug/Mock；实际安装前先报告协调者，避免并行设备任务。
- 发布：无发版授权，不发布APK/标签。回退为回退本任务代码；保留业务/恢复记录，不卸载清数据，不以降级替代恢复。安装器打开或下载记录不改变机器控制或数据迁移规则。

## 统一 Switch 公共入口接线（2026-11-12）

更新页开关已调用现有 `SettingsVisualStyle.switch(toggle)`，不增加单页轨道或动画，不修改公共样式实现。后续独立样式分支#75的A外观及动效在HoyiSwitch构造中实现，不能仅凭公共入口调用宣称自动继承；待#75合入、最终同步develop后还须将本页构造替换为HoyiSwitch(activity)，继续调用visual.switch，并更新最终截图。本树当前不复制该分支代码，也不宣称已有A动效。此局部接线只做Debug编译与既有更新开关回归，不重复完整矩阵或设备操作。上方双包哈希对应此前的软件构建产物，本轮不为这条接线重建或分发APK。

本次 `:mobile:compileDebugKotlin :mobile:testDebugUnitTest --tests io.openhoyi.mobile.ReleaseUpdateCoordinatorTest` 通过（43s），既有11项更新协调/开关测试0失败；日志 `/tmp/hoyi-auto-update-switch-hook.log`。完整软件回归JUnit证据已保存在 `/tmp/hoyi-auto-update-device-check/full-final-junit-reports.zip`，没有设备操作或新APK分发。

## 当前并行样式说明

首个CI候选42e4bab2仅包含公共Switch入口接线，不能视为统一A样式成品；#75真正样式在HoyiSwitch，尚待合入后统一同步并改构造。首轮截图仅作功能/安全/旧样式证据，最终用户截图须基于集成后的样式重新获取。

## 首轮CI调度修复

42e4bab2 的 API34 手动运行43254244870已完成宿主构建和QEMU启动，但android-emulator-runner逐行执行多行script，shell if被拆开报语法错，尚未运行instrumentation、无实窗证据。入口已改为单行固定wrapper `bash scripts/check_mock_or_update_acceptance.sh`；wrapper仅接受true/false，固定派发两个脚本。本地stub验证两路与非法输入，绝不运行设备；后续须推送新SHA重新dispatch，不rerun旧SHA冒称修复。

当前56e3b9ac基线候选42e4bab2的默认产品完整本地verify已通过（Gradle2m26s、双包各3239资源匹配、12项release guard）；默认Debug/Mock实际APK已检查无UpdateAcceptance类/Activity/fixture APK资产。该结果不替代待运行的API34 runtime验签/系统页/截图。
