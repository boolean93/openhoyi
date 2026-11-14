# 本地预览包升级 DEV

- 需求：`local` Debug 可在应用内检查、下载并交给系统覆盖安装 DEV；不改变正式 DEV 的版本比较，不卸载或清数据。
- 来源：develop / ac7c2109；工作分支 `fix/local-dev-update`；PR 目标 develop。遵守 [Git 工作流](../git-workflow.md)，通过 `scripts/create-pr`、精确头检查后合入。
- 实现：从已安装 PackageManager 身份读取 versionName；只有 `-local.` 命名的 Debug 属于本地预览。它允许同 versionCode 的 DEV 替换或更高版本升级。其他 Debug、Mock 不变，低 versionCode 永不放行；包名、证书、下载哈希、APK 非 Debug、安装确认及运行中保护不变。
- 自动检查仍沿用每天一次、可关闭与安全时机提示；检查不自动下载，不自动安装。
- 同号替换尚未完成时不能因 versionCode 相等清掉下载状态；沿用“已安装非 Debug 才确认完成”的恢复/清理逻辑。
- 本地构建继续沿用平板已装 versionCode，同签名覆盖，用 `-PhoyiVersionName=0.2.0-local.<code>-<purpose>` 区分预览。不抬高本地版本号去抢 DEV 号；签名必须与已装包及公开 DEV 一致，不能以卸载、降级开关或替换密钥解决冲突。
- 验证：策略、自动检查、下载后的 APK 身份、同号安装完成识别；完整本地门禁及 CI。真实平板只读核验当前身份，不安装或操作机器。
- 发布：跟随既有 Develop 自动发布，不人工发稳定版；回退走代码 revert PR，保留全部用户数据。
- 证据边界：平板当前 91 / `0.2.0-local.91-dev104`，debuggable，安装证书 SHA-256 为 c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54，与既有 DEV 身份一致；此读取不等于覆盖安装实测。
- 聚焦回归：ReleaseUpdate、ReleaseApk、ReleaseDownloadController 的 Debug JVM 测试通过；覆盖同号 local 保留下载状态/文件、切为非 Debug 后才清理，以及未知命名、Mock、签名和降级保护。Astra 独立审查实现无阻断。
