# App icon 替换（正式版 + Mock）

遵循[Git工作流](../git-workflow.md)。本规划替代旧的蓝杯图标方案，不改业务行为。

## 需求与范围

- 用户 2026-11-07 提供两版 1254×1254 设计稿：黑底 "Open HOYI" 作正式 App icon；橙底字标 + "Mock" 徽标作 Mock 构建 icon。
- 只改 launcher icon 资源：`mobile` 模块 main（正式）与 mock flavor 的 adaptive icon、背景色、生成脚本和设计稿留档。
- 不改包名、签名、versionCode、UI 布局、业务逻辑、OpenHOYI Lab（`io.openhoyi.lab`）和网站 favicon；不涉及数据。
- 验收标准：两个 APK 的 launcher icon 分别显示黑/橙设计稿；Mock 与正式版仍可并存；常见遮罩下字标与 "Mock" 徽标完整可读；无残留旧 vector 图标引用。

## 来源、目标、分支

- 来源 `origin/develop@df3b922`，目标 `develop`；工作分支 `feature/app-icons`（新工作树 `openhoyi-app-icons`）。
- PR 目标 develop，必需检查 `branch-policy`、`verify` 通过后 squash 合并；合并后清理本地工作树/分支，远端分支按 GitHub 审核清理。

## 改动文件与步骤

1. `docs/ui/app-icons/`：保留用户原始设计稿（含 sha256）与近似遮罩预览。
2. `scripts/generate_app_icons.py`：按纯色背景抠出前景，生成 5 档密度 adaptive icon 前景 PNG；`--check` 校验提交物。
3. `mobile/src/main/res/...`：`mipmap-anydpi-v26/ic_mobile.xml`、`values/ic_mobile.xml`（#000000 背景）、`mipmap-*/ic_mobile_foreground.png`；删除旧 `drawable/ic_mobile.xml`。
4. `mobile/src/mock/res/...`：同名资源 override（#BC3F02 背景 + 带 Mock 徽标前景）；删除旧 `drawable/ic_mobile.xml`。
5. `mobile/src/main/AndroidManifest.xml`：`android:icon` 从 `@drawable/ic_mobile` 改为 `@mipmap/ic_mobile`。

尺寸映射：设计稿正方形对应 72dp 可见区域，108dp 图层四周各留 18dp 背景出血，内容居中。

## 推送与合并

检查工作树 → 本地 `:mobile:assembleDebug`、`:mobile:assembleMock`、`:mobile:lintDebug` 与 `localization/verify_apk_resources.py` 验证 → 普通推送 `feature/app-icons` → PR 正文含规划/验证/风险/回退并链接本文件 → 必需检查通过 → 合并 develop。

## 验证证据

本地（JDK 17、AGP 8.10.0；`GRADLE_USER_HOME`/`ANDROID_USER_HOME` 指向可写临时目录，未改仓库配置）：

- `python3 scripts/generate_app_icons.py --check` → `Icon outputs are up to date.`
- `./gradlew :mobile:assembleDebug :mobile:assembleMock :mobile:lintDebug` → `BUILD SUCCESSFUL`，产出 `mobile-debug.apk`、`mobile-mock.apk`。
- `python3 localization/verify_apk_resources.py --alpha mobile-debug.apk --mock mobile-mock.apk` → `{"literalRoundTrips": 6, "alpha": {"matchedStrings": 11513}, "mock": {"matchedStrings": 11513}}`。
- `aapt2 dump badging`：两个 APK 的 `application-icon` 均为 `res/mipmap-anydpi-v26/ic_mobile.xml`；包名仍为 `io.openhoyi.mobile` / `io.openhoyi.mobile.mock`。
- 编译后资源：`color/ic_mobile_background` 在 debug APK 为 `#ff000000`、mock APK 为 `#ffbc3f02`（mock 覆盖正确）；APK 内 5 档 `ic_mobile_foreground.png` 与生成文件逐像素一致。
- 还原比对：由生成图层重建 72dp 可见区域并放大到 1254 与设计稿比较，内容 bbox 偏差 ≤3px；不透明内容平均差 1.1（正式）/2.0（Mock），无橙色/黑色残影。
- 遮罩预览：`docs/ui/app-icons/launcher-preview.png`（圆/超椭圆近似）。Mock 徽标最外缘 36.96dp，纯圆遮罩可见半径 36dp，理论最多约 1dp 裁切；平板常见圆角方形遮罩完整显示。

CI 门槛：本 PR 的 `branch-policy`、`verify` 结果（见 PR 检查）。未验证：真机平板安装后的 launcher 实际观感与图标缓存刷新。

## 协议与设备风险

- 不涉及 BLE、协议编码、单位、队列、门禁、持久未确认记录、重试或停止逻辑；不改 versionCode/签名/包名，不卸载数据。仅 launcher 图标资源与 manifest icon 属性。
- 设备侧未在真机平板安装验证，只到 APK 资源与近似遮罩预览；launcher 实际观感、Mock 徽标在纯圆遮罩下最多约 1dp 裁切仍属未验证限制，Mock 与 Debug 同步装机验收按既有规则在后续装机时执行。

## 发布

不发版、不打标签、不发布 APK、不安装。图标随下一次经批准的构建进入；正式版签名与升级边界不变。

## 回退

revert 本 PR 即恢复旧 vector 图标与 manifest 引用，无数据或协议兼容影响；不需要卸载或清数据。

## 未完成与阻塞

无阻塞。真机 launcher 观感待用户确认，必要时以源码后续提交微调映射比例。
