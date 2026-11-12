# 首页本地快捷槽：可选直接开始

- 需求与范围：首页五个本地快捷槽首次点击显示「查看曲线 / 开始萃取」和默认不勾选的「下次点击直接开始萃取」；设置可修改。机内拨杆卡片、普通导航、普通开始操作不变。取消无副作用。
- 来源分支/基线提交：develop / `12f76324`。
- 工作分支：`feature/shortcut-direct-start`；PR 目标 `develop`。
- 改动步骤：增加独立本地偏好、首次提示和设置开关；同步导航后尝试一次，未绑定、工作室温度不符或其他安全阻挡仅留在萃取页；可撤销的短时许可贯穿真实/Mock 最终派发及既有去皮预检。
- 安全验收：精确绑定源槽、曲线和完整解析参数、秤模式、服务/设备、设置；遵循咖啡馆/工作室现有模式门禁，仅工作室依赖预热及新鲜校正温度；离页、后台、重建、偏好/选择改变或超时后许可永久失效；不保存自动启动标志，不在绑定/恢复/达温后重试，不自动预热。不把已经发送或结果未知的请求当作未发。
- 推送与合并：本轮仅本地实现和验证，不推送、不建 PR、不合并。后续另行授权并遵守 [Git 工作流](../git-workflow.md)，使用普通分支推送及 `scripts/create-pr`，等待必需 CI。
- 验证证据：聚焦 JVM 测试、Mock 构建和相关编译；添加 Mock 实窗测试入口。本实现任务不操作设备；协调任务依据后续设备授权负责双包覆盖及 Mock 窗口验证，不创建/启动 AVD。实际命令和结果补充在交付。
- 协议与设备风险：不新增报文、不放宽门禁，复用服务启动和去皮预检。新增许可只进一步收紧派发条件；没有真实停水/硬件验收证据。
- 发布：不发版、不更改源码默认版本/签名。本地双包按已装版本通过 `-PhoyiVersionCode=91 -PhoyiVersionName=0.2.0-local.91-shortcut` 构建；安装和设备检查由协调任务执行，不发起真实机器萃取。
- 回退：回退本任务代码；新偏好为独立可忽略字段，不迁移/删除历史，不卸载、不清数据、不删除未知记录。
- 未完成与阻塞：实现及本地验证完成。协调任务已完成平板 Mock 横屏首次提示/取消/偏好/工作室阻挡/重建/设置流程；首次提示留白修复后将重验及补 400dp 竖屏。成功出水未做实窗，真实机器萃取/停止及硬件验收未执行。

## 本地实现验证

- `git diff --check`、`python3 scripts/git_policy.py branch`：通过。
- `python3 -m unittest discover -s scripts -p test_scale_architecture.py`：2 项通过。
- `python3 localization/android_resources.py --check` 与 `python3 localization/validate_catalog.py`：通过。
- JDK 17、Android SDK 35，运行：
  ```sh
  ./gradlew --no-daemon --console=plain \
    -PhoyiVersionCode=91 -PhoyiVersionName=0.2.0-local.91-shortcut \
    :mobile:testMockUnitTest --tests '*ShortcutStart*' --tests '*CustomShortcut*' \
      --tests '*StudioStartGate*' --tests '*DirectExtractionStart*' \
    :mobile:assembleDebug :mobile:assembleMock :mobile:assembleMockAndroidTest \
    :mobile:lintDebug :device-session:test --tests '*Queued*'
  ```
  成功。mobile 24 项、device-session 56 项筛选单测通过，session 可执行检查 103 项通过，三包构建与 lintDebug 通过。复用既有队列检查；没有运行无关全量矩阵。
- 新增验证包括：默认安全偏好、失败进程级抑制/重试、单次 claim、4999/5000ms、时钟回退、永久撤销、内容 A→B→A、咖啡馆低温可开始而工作室需新点击、去皮期间取消、已提交未知结果不被误清、真实 Session/GATT 持队列后撤销无机器开始报文。
- 两主包 `aapt2 dump badging` 均为 versionCode 91 / `0.2.0-local.91-shortcut`；`apksigner verify --print-certs` 通过，均为原 Android Debug 证书 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`。
- Debug APK SHA-256：`dbf0070f7ffad7465db1b92292ac0e957210a90c4fada54481a65cd44a1d1958`。
- Mock APK SHA-256：`296ba39afa430cee05ec7849a4e777546dbaeecc6dbdf02ff62dc07861c31b44`。
- Mock instrumentation APK SHA-256：`101a45ad671ed7aa7ac4384075b8378876d2fb922dacde49b5cca81bae083114`。
- 本实现任务未操作设备、未安装、未做真实萃取。协调任务已使用 `shortcutDirectStartChecks=true` 完成平板 Mock 横屏安全阻挡流程，并报告双包覆盖后原有业务文件 SHA 保持一致。首次提示 24dp 横向留白修复后将重跑并验证 400dp 竖屏；测试以温度阻挡确保不创建新萃取记录，成功出水路径仍仅有离线测试，不冒充实窗或真实硬件验收。

- 首次提示左右 24dp、上下 8dp 留白修复后，同 v91 参数增量构建 Debug / Mock / MockAndroidTest 全部成功（1m11s）。此后仅布局留白变化，安全逻辑未变；下列协调任务实窗记录以最终 APK 为准。
