# 关于页手动检查 Develop 更新（阶段 3）

- 需求与范围：仅在设置/关于页新增 Develop 测试版小卡片，手动检查、明确兼容性状态并跳转官方浏览器下载或发布页。不后台轮询，不自动下载/安装，不添加安装权限。
- 来源/基线：`develop` / `0346941f`（阶段 2 更新索引已合入并上线）。工作分支 `feature/about-release-update`，PR 目标 `develop`。
- 遵守 [Git 工作流](../git-workflow.md) 与 [任务模板](task-template.md)；当前工作树独立，不修改阶段 2 工作树。
- 分工：Sol 实现 parser/policy、HTTP/lifecycle、UI、本地化与测试；Astra 独立复核交互、安全和失败边界。没有设备/安装授权，不启动 AVD，不触碰真机。

## 已确认验收与实现步骤

1. 关于页卡片初始标题「Develop 测试版」、手动检查按钮；初始不联网。点击只发一个请求，加载中禁重复，明确网络/格式错误且允许重试，不把失败称为最新。
2. 固定索引 `https://boolean93.github.io/openhoyi/updates/dev.json`；HTTPS、5 秒连接/读超时、10 秒单次 UI 超时回调（不承诺 socket 严格在 10 秒退出）、64 KiB 响应上限、拒绝 HTTP 重定向；严格验证现有 schema、JSON 整数、包名、证书/哈希格式和固定 GitHub 仓库精确 URL。
3. 使用 PackageManager 读取本机实际包名、versionCode、debuggable flag、当前实际签名证书 SHA-256，不用 BuildConfig 假定 Release 可覆盖升级。非调试主包只有同签名且更高版本才显示「发现新测试版 / 前往下载」。同版和本机更高明确区分，仅查看发布页。
4. Debug 保留手动检查，说明仅查看公开测试版、不判断覆盖升级；Mock 说明主包不覆盖 Mock；Release 签名不同明确不能直接覆盖，只能查看发布页。身份无法读取也不判断可升级。
5. 仅 ACTION_VIEW 浏览器打开受信任 URL，处理无浏览器。没有 APK 下载器/安装器、后台任务、权限弹窗或机器控制。
6. 请求仅由点击发起；离开/暂停、视图销毁时置取消标记、interrupt Future 并使迟到结果失效（socket 由 worker 的 finally 关闭，避免主线程 disconnect）。重新进入可手动重试，不恢复过期请求。成功后打开浏览器返回仍可重新检查。
7. 新增中英文原文与 catalog 快照，使用既有生成器生成英文资源。复用关于页视觉和可访问状态播报。

## 验证计划

- 纯 parser/policy JVM 测试覆盖真实 v1 fixture、所有身份/版本状态、错误 JSON 整数类型、恶意链接、坏 schema。
- 生命周期 controller JVM 测试覆盖初始零请求、重复点、成功/失败重试、暂停/销毁迟到回调、重进以及同步完成边界。
- HTTP JVM fixture 测试超时配置、禁止重定向、响应上限、HTTP 错误与连接关闭；不需要真实网络或设备。
- 相关测试与完整非 AVD `scripts/verify-pr-checks full`（JVM/unit/build/lint/localization APK checks/release guard）；实际结果交付时记录，不把计划当通过。
- 真实 UI 与外部浏览器手动验收未做，必须明确说明；不为补证据启动 AVD 或安装到真机。

## 风险、发布与回退

无 BLE 协议、命令编码/单位/队列/停止、数据库或未知记录变更；读取实际证书只在本地比较，不上传本机身份。应用不下载/验签 APK，不宣称完成安装/数据保留或源码溯源认证。

暂不提交/推送，等协调者/Astra 审核；后续普通推送、统一 `./scripts/create-pr` 与精确 HEAD 必需 CI，通过授权流程合并。不人工发布 APK、不打标签、不控制设备；既有 Develop 自动预发布规则不变。回退走 revert/修复 PR，不卸载、不清数据、不降级 APK 或删除公开索引。

## 实施过程与验证记录

- 相关 JVM 首轮：`./gradlew --no-daemon --console=plain :mobile:testDebugUnitTest --tests '*ReleaseUpdate*'`，15 tests 通过（parser/policy 6、controller 5、HTTP 4）。
- 完整检查首轮未通过，未冒称成功：3 个既有本地化数量快照仍为 998/997，新加 18 条资源后实际为 1016/1015；已仅更新对应数量断言，保留原有完整性/格式门禁。另一次在编译开始后添加 no-cache 设置，较新的 HTTP 断言读取了旧 main 类快照；冻结全部代码/测试后重跑完整验证，最终结果另记。
- Astra 只读首轮与单次 UI 超时补充复核：无阻塞问题。UI 取消只更新状态/中断 Future，不在主线程 disconnect；worker 负责 finally 关闭连接。5 秒连接/读超时、耗时检查及单次 Handler 超时是失败恢复机制，不承诺 DNS/socket 恰在 10 秒退出。
- 未取得 Android 真实 UI、外部浏览器打开/无浏览器提示或真机运行证据；没有启动 AVD、安装 APK 或操作任何设备。单测、编译及独立代码审查不替代这项限制。
- 冻结源码后最终完整本地验证：`JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=$HOME/Library/Android/sdk scripts/verify-pr-checks full` exit 0。Debug 622 / Mock 629 tests 均 0 failures、0 errors（各保留 1 个既有 skipped）；新增 15 tests 全通过。完整 Gradle `BUILD SUCCESSFUL`，191 tasks；APK 本地化 alpha/mock 各 3205 strings，6 literal roundtrips；隔离临时签名 release APK fixture 与 12 项 signing guard 通过，无安装。
- 协调者已在最终完整检查通过后授权普通提交/推送与 `./scripts/create-pr --draft`；不授权本实现者合并，远端精确 HEAD 必需 CI 由协调者继续核验。
