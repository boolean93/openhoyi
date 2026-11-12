# 应用内更新：PR候选与验收（2026-11-12）

## 当前结论与待办

- 唯一主树 `openhoyi-in-app-update`，分支 `feature/in-app-update`；初始基线 `56e3b9ac4174fa64b9dc7fd1f7637daf902e68b0`。仅迁入旧device-check相对e9dd235b的任务差异，不包含未合入扫描修复；原实现树冻结。基线已含空拨杆、late recovery及framing修复。
- 产品完整本地验证已通过。API34真实运行 [43255773045](https://github.com/boolean93/openhoyi/actions/runs/43255773045) 在 `e5465d06` 成功，取得14张原生窗口截图与实际验签/系统权限页/忙碌门禁报告；Astra已独立复核，无剩余代码安全阻断。正向系统安装确认与实际安装没有执行，不能宣称完成生产升级验收。
- 已合入最新develop `8fb6d2e99d27977f2b8404fba2b85561aea69f4c`（曲线删除与统一Switch #75）；唯一冲突在MobileService注册和startShot包装，保留两方逻辑。更新开关使用 `HoyiSwitch(activity)` 并保留 `visual.switch(toggle)`，直接复用A外观与滑动动效；隔离宿主/runner重编译及实际manifest检查通过（`final-fixture-build.log`、`final-fixture-manifests.log`）；最终PR门禁及CI截图待执行。未混入未合并扫描改动。
- 首批截图的CURRENT状态受控版本与标题不一致，已落实仅fixture修正：CURRENT metadata/identity与BuildConfig一致；AVAILABLE仍用更高的真实签名fixture目标。与最终Switch截图一起验证，不修改产品资格。
- 用户已授权完成后推进上线，无需人工体验批准；Sol负责实现/本地Git、验证及通过 `./scripts/create-pr` 创建PR；协调者统一push并等待精确头提交必需CI，负责合入/跟进既有Develop发布。正式版/RC仍遵守独立签名、版本及发布规则。

## 目标、规则与范围

设置一行左当前版本、右检查更新；自动开关默认开启。仅必要时显示状态、真实下载进度、取消、失败重试及安装入口。右上B提示提供“稍后/下载更新”，主动下载后在原位显示进度。用户点击下载后，只有同一次安全前台流程仍有效才进入系统安装确认；真正安装仍由系统向用户确认。

遵守 [Git工作流](../git-workflow.md) 与 [任务模板](task-template.md)。Astra独立设计和只读复核，Sol唯一实现。PR目标develop，不直接推送保护分支，不改默认包名/签名/版本策略。协议风险仅为只读安全门禁接线，不改变机器命令、队列或恢复记录。回退经代码revert/修复PR，保留用户数据，不卸载清数据或降级。

## 当前行为与边界

- 每日到期自动检查，24h 加 0–2h 持久错峰；普通自动错误 24h 起指数退避。手动无次数/固定冷却，仅合并并发并遵守服务端限流。429/带 Retry-After 的503尊重明确截止；无有效头的429指数退避。关闭自动检查不取消用户手动检查或下载。
- 固定官方 `dev.json` 严格校验，不扩 schema/发布链路。Mock、Debug、本机身份未知不自动检查或取得生产下载资格；诊断手动检查保留。自动提示仅匹配主包、实际本机签名及更高 versionCode；稍后按版本/签名/摘要在本进程去重，重启可重验缓存提醒，不绕过联网期限。
- APK 下载仅在用户明确点击后启动。独立单任务与不可变目标；受限 HTTPS，仅初始本仓库固定附件路径、最多3次跳转及精确 `release-assets.githubusercontent.com` CDN；不传凭据/Cookie/Range，不自动重试。实际收到字节驱动进度；长度上限/精确EOF/声明长度检查，连接/读取/整体超时，取消、断网、空间不足可显式重试。
- 私有 `update-staging` 与 `updates/verified` 分离。完整大小、SHA256、ApkVerifier真实密码学验签、单签名/无轮换回退、包名、精确目标版本、递增本机版本、非debuggable、非split、设备minSDK兼容、索引/实际APK/本机签名三向一致全部通过才成为Ready。每次重新安装动作及未知来源授权返回均重新全量验证。
- FileProvider只映射verified子目录，非exported，只发具体APK的READ URI给显式受信系统组件；不授WRITE、不授sidecar URI。仅 `REQUEST_INSTALL_PACKAGES` + 系统安装确认，无静默安装或无人值守commit。实际安装成功仅据重新读取的本机包身份，不能把打开安装器/返回当成功。
- 用户点击下载签发一次性内存安装租约，绑定目标、代际、Activity与primary Fragment、前台和安全epoch。下载/校验可在后台完成；任何普通暂停、离页/重建、busy、取消、失败或收起进度交互永久撤销自动续装。仍同一安全前台流程才自动打开系统确认；否则保留包供“安装更新”主动恢复。未知来源授权仅允许匹配的ActivityResult流程续行，posted callback还须持有原token对象；旧回调不得启动或撤销新流程。
- 全局门禁复用Service只读状态：萃取/预检/待开始/归零/关键写入与未决恢复记录阻止提示和系统跳转。关键控制入口与被动萃取帧立即撤销租约，不能仅靠轮询遗漏短busy；有候选或有效租约时辅以短周期复核。未改机器命令、协议、队列、重试或未知记录处理。
- 每个交系统的APK先原子持久化 `.handoff` 保留标记；写失败不打开安装器。标记不是verified/已安装凭据，不恢复网络/安装意图。进程重启或新任务不会清理仍被系统引用的包；只在实际同包同签版本达标或用户明确放弃后清理，放弃先撤READ grant。未知/损坏标记和非本任务文件不碰。part只按可证所有权、无在途worker清理。

## 当前软件与API34证据

- `scripts/verify-pr-checks full` **PASS**，日志 `/tmp/hoyi-in-app-update/default-verify.log`，Gradle2m26s。Debug **652**、Mock **659** 项，均0失败/0错误/各1项既有skip，更新相关各44项；双包各3239项资源匹配，release guard最终 **cases=12**。JUnit汇总及归档在同目录 `default-junit-summary.json`、`default-junit-reports.zip`。这是56e3b9ac基线候选结果；最终develop整合后按实际变更补验并保留PR入口检查。
- 显式隔离变体 `:mobile:assembleUpdateFixture :mobile:assembleUpdateFixtureAndroidTest -PhoyiUpdateChecks=true` **PASS**。实际APK独立包名、manifest无BLE/定位/旧provider、匹配签名检查通过；普通Debug/Mock实际APK无fixture Activity、类或APK资产。日志 `fixture-build.log`、`fixture-manifests.log`、`default-apk-isolation.json` 均在上述目录。
- 固定wrapper两路与非法输入、fresh-emulator守卫合计5项及workflow policy6项通过。首次CI43254244870因action逐行执行多行if而在instrumentation前失败；e5465d06已改为单行固定wrapper并在后续成功运行，未靠重跑旧SHA掩盖修复。
- API34成功artifact在 `/tmp/openhoyi-update-acceptance-e5465d06/update-acceptance/`。`runtime-install-safety.json` 证明实际Android apksig验签及PM解析固定隔离包、生产资格拒绝、FileProvider真实READ及staging拒绝、显式系统组件、真实未知来源权限页往返、未授权安装拒绝。`permissionPolicyChanged=false`、`installerConfirmationVerified=false`、`installationPerformed=false`；未改变任何权限策略或实际确认安装。`busy-safety.json` 证明实际隔离MockService的READY待开始门禁及关键操作即时撤租约，无BLE。
- 14张PNG通过instrumentation原生捕获并按应用可见矩形裁切（含Popup，排除通知栏），没有美化合成或私人数据；`manifest.json` 记录方向/主题/状态及受控输入。覆盖横竖明暗的B/真实字节进度，以及设置检查中/当前/可更新/失败、下载取消/失败。Astra已实际看图，最终A样式集成后更新截图；图片不代替验签/安装安全证据。

## 隔离验收边界与剩余限制

- 仅显式 `-PhoyiUpdateChecks=true` 创建宿主 `io.openhoyi.mobile.updatefixture` 及专用androidTest；安装器目标固定 `io.openhoyi.updatefixture`，不得为真实主包或普通Mock。默认Live UiActions仍读取真实身份、生产资格和校验；无普通Intent/产品UI身份覆盖入口。
- 夹具使用真实本地签名APK字节流驱动原生产品UI、进度/取消/失败；受控metadata和installed identity仅为呈现。它不是公开网络下载或生产更新资格证据。运行中真实生产verifier与eligibility明确拒绝隔离身份。
- 复用 `mock-lifecycle.yml` 单job，手动boolean `update_checks` 只选择固定API34 runner；脚本限定fresh QEMU及固定namespace，不接受任意shell/下载URL/包名，不覆盖已有包。未运行本地AVD或继续占用平板。平板此前被使用且数据备份未证明稳定，不把旧raw tar当有效备份；私有备份不上传。
- 不修改appops/settings/系统策略，不自动开启未知来源。API34实际权限未授权，因此仅验证拒绝和真实权限页返回；正向系统安装确认/实际升级及保数据未验。API26/35的JVM验签规则测试不替代这些Android运行版本证据。
- 原Release外跳方案已废止；旧 `openhoyi-auto-update`、`openhoyi-auto-update-device-check` 的基线、APK哈希、656/663项测试和设备预检仅为历史，不作为本PR当前结果。旧树没有继续写入/安装。当前用户流程允许用户下载后同一有效租约打开系统确认，不能再用早期“绝不跳转/无后台下载”描述它。
