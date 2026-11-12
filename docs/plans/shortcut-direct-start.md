# 首页本地快捷槽：可选直接开始

- 需求与范围（当前方案）：首页快捷区标题旁增加轻量「一键萃取」开关，与设置页共享 `shortcut_start/direct_start`，默认关闭；关闭时点击快捷槽只导航，开启时本次点击尝试启动。可随时关闭。移除原首次弹窗及配置完成状态，不读写或删除遗留键，保留现有明确选择。机内拨杆卡片、普通导航、普通开始操作不变。
- 来源分支/基线提交：develop / `bc078c89`（已纳入 #63、#64）。
- 工作分支：`feature/shortcut-direct-start`；PR 目标 `develop`。
- 改动步骤：首页标题内开关与设置同源同步；仅保留 direct_start 偏好；同步导航后尝试一次，未绑定、工作室温度不符或其他安全阻挡仅留在萃取页；可撤销的短时许可贯穿真实/Mock 最终派发及既有去皮预检。
- 安全验收：精确绑定源槽、曲线和完整解析参数、秤模式、服务/设备、设置；遵循咖啡馆/工作室现有模式门禁，仅工作室依赖预热及新鲜校正温度；离页、后台、重建、偏好/选择改变或超时后许可永久失效；任意开关操作先撤销许可，关闭再开启不复活旧请求；存储失败与解除抑制也通知所有观察者，两页保持相同安全状态；不保存自动启动标志，不在绑定/恢复/达温后重试，不自动预热。不把已经发送或结果未知的请求当作未发。
- 推送与合并：本轮仅本地实现和验证，不推送、不建 PR、不合并。后续另行授权并遵守 [Git 工作流](../git-workflow.md)，使用普通分支推送及 `scripts/create-pr`，等待必需 CI。
- 验证证据：聚焦 JVM 测试、Mock 构建和相关编译；添加 Mock 实窗测试入口。本实现任务不操作设备；协调任务依据后续设备授权负责双包覆盖及 Mock 窗口验证，不创建/启动 AVD。实际命令和结果补充在交付。
- 协议与设备风险：不新增报文、不放宽门禁，复用服务启动和去皮预检。新增许可只进一步收紧派发条件；没有真实停水/硬件验收证据。
- 发布：不发版、不更改源码默认版本/签名。本地双包按已装版本通过 `-PhoyiVersionCode=91 -PhoyiVersionName=0.2.0-local.91-shortcut-toggle` 构建；安装和设备检查由协调任务执行，不发起真实机器萃取。
- 回退：回退本任务代码；新偏好为独立可忽略字段，不迁移/删除历史，不卸载、不清数据、不删除未知记录。
- 未完成与阻塞：新开关方案生产代码独立初审、本地验证和横竖屏 Mock 实窗验证已通过。成功出水未做实窗，真实机器萃取/停止及硬件验收未执行。

## 原首次提示方案验证历史（已由新开关方案替代）

以下构建、哈希、截图和设备记录属于旧方案，保留追溯，不作为新开关方案验收。

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
  成功。mobile 44 项（含 Lever/Recovery）、device-session 56 项筛选单测通过，session 可执行检查 103 项通过，三包构建与 lintDebug 通过。复用既有队列检查；没有运行无关全量矩阵。
- 新增验证包括：默认安全偏好、失败进程级抑制/重试、单次 claim、4999/5000ms、时钟回退、永久撤销、内容 A→B→A、咖啡馆低温可开始而工作室需新点击、去皮期间取消、已提交未知结果不被误清、真实 Session/GATT 持队列后撤销无机器开始报文。
- 两主包 `aapt2 dump badging` 均为 versionCode 91 / `0.2.0-local.91-shortcut`；`apksigner verify --print-certs` 通过，均为原 Android Debug 证书 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`。
- Debug APK SHA-256：`7037505abe456045d5bdff2ae5e790a783f227dcaf94aa913f660ce5b0f5605b`。
- Mock APK SHA-256：`ce4522cccdc1ea2367f7ad2d6cf98948c5afc838c8f55a1dac3f140dbbbd9dee`。
- Mock instrumentation APK SHA-256：`61ba3bde2f54c21ff011a3b5a7fb6980e0d87b397fae3536fa3ebd966d8d8363`。
- 本实现任务未操作设备、未安装、未做真实萃取。协调任务已使用 `shortcutDirectStartChecks=true` 完成平板 Mock 横屏安全阻挡流程，并报告双包覆盖后原有业务文件 SHA 保持一致。首次提示 24dp 横向留白修复后将重跑并验证 400dp 竖屏；测试以温度阻挡确保不创建新萃取记录，成功出水路径仍仅有离线测试，不冒充实窗或真实硬件验收。

- 首次提示左右 24dp、上下 8dp 留白修复后，同 v91 参数增量构建 Debug / Mock / MockAndroidTest 全部成功（1m11s）。此后仅布局留白变化，安全逻辑未变；下列协调任务实窗记录以最终 APK 为准。

## 原方案集成与设备验证历史（已替代）

- 已基于 #63 (`0f3cee6a`) rebase，保留首卡静态背景/幂等更新和 service process journal 修正。集成后三包、lint 与上述筛选测试重新通过。
- 平板真实 Android 窗口，仅运行 Mock：原1600×2560横屏及900×1600（400dp）竖屏均输出 `SHORTCUT_DIRECT_WINDOW_PASSED`。取消不保存；勾选记忆；本次查看不启动；工作室冷机只导航；重建不重放；设置关闭；无BLE/无shot。
- 截图验收发现并修复对话框留白与设置目录旧坐标定位。新增viewport完整可见断言，手动滚动后不拉回及连续目录最后一次生效检查；最终两种尺寸均通过。截图人工确认新设置开关在页面顶部且无截断。
- 安装前确认真实包无进程、设备空闲，备份双包及数据。同源码/签名/versionCode91双包覆盖，每次安装前后业务文件SHA一致；测试恢复原槽位、选择、偏好及内存fixture，检查历史和自定义曲线文件未变。未卸载、清数据、发真实机器命令。
- 测试结束恢复原物理尺寸及横屏rotation3。独立Astra对安全链路、偏好失败保护和设置导航最终复核无阻断。


## 当前开关方案与本地验证

- 首页透明紧凑开关位于快捷区标题旁，保留 48dp 点击区域，横竖屏标题与图例自适应；设置同名开关使用同一偏好。
- 首页 tag：`product/home/shortcuts/direct-start`；设置 tag：`product/settings/shortcuts/direct-start`。
- 没有首次弹窗或配置完成标志。`direct_start` 缺失即关闭；只修改该键，不读取/写入/清理旧遗留键，不删除用户数据。
- 两页可见时订阅偏好与进程安全状态，暂停/销毁解绑，恢复时刷新；程序刷新不触发写入。解绑本身不撤销正常 Home→萃取的新请求。
- 保留原一次性启动许可与安全门禁。新增共享偏好修订号及操作前撤销，覆盖关闭再开启、跨实例失败同步与恢复、移除观察者无副作用的 JVM 测试。
- Astra 对新生产实现初审无阻断。`git diff --check`、本地资源/翻译校验及 2 项 scale architecture 检查通过。
- 同 v91 / `0.2.0-local.91-shortcut-toggle` 已构建 Debug、Mock、MockAndroidTest 并通过 `lintDebug`；mobile 筛选 47 项（Shortcut/CustomShortcut/Studio/Direct/Lever/Recovery）、device-session Queued 56 项及 session 可执行 103 项检查通过。
- 首轮 Gradle 的 lint 分析触发本机 JDK17.0.7 ARM64 C1 JIT OSR 内部错误（`assembler_aarch64.hpp:267`），不是源码编译错误。保持源码不变，仅本轮命令添加 `--max-workers=1 --no-parallel '-Dorg.gradle.jvmargs=-Xmx1536m -XX:-UseOnStackReplacement'`，1m31s 重试成功；未修改全局 JDK、项目配置或安全门禁。崩溃生成物已移出工作树，未提交。
- 两主包 `aapt2 dump badging` 均为 versionCode 91 / `0.2.0-local.91-shortcut-toggle`，`apksigner verify --print-certs` 通过，原 Android Debug 证书 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`。
- Debug APK SHA-256：`952139d7fcb767a0ad81be4dfb855f565d4fd16c29830e9d556509e753b0f79c`。
- Mock APK SHA-256：`1a9476ae2b8accab3087959284975d37ccf06f6a0467117ab7bf7d0c6b99f6c1`。
- Mock instrumentation APK SHA-256：`1eaef2a0a8dd559d314a00fad83df3faaf368d13dbcb14e8c5444032352748b7`。

## 当前开关方案最终设备验收

- 协调任务使用本节上述新开关版本 APK 完成横屏及竖屏 Mock 实窗验证，全部通过：`defaultOff/homeToggle/settingsSync/coldNoStart/recreateNoReplay/noBle/noShot`。
- 首次进入默认关闭，无旧弹窗；关闭时点槽只导航。首页开关与设置共享同一状态，双向操作同步；工作室低温不发启动，重建不重放，无 BLE/新萃取记录。
- 已人工查看横竖屏截图，快捷区标题与轻量「一键萃取」开关完整可见，设置开关同步正常。目录定位、完整 viewport、手动滚动不拉回与连续目录最后一次生效检查保留。
- 原签名、同 versionCode91 的 Debug/Mock 双包均覆盖安装，安装前后业务文件 SHA 完全一致。测试结束恢复原物理尺寸与横屏 rotation3，未卸载或清数据。
- 此验收是实际 Mock 窗口与数据保留证据，不包含真实机器命令。成功出水只由离线控制器/队列测试覆盖，未做实窗成功出水或真实硬件萃取/停止验收。
- 上述旧提示方案历史保留追溯，不作为新开关界面的交付截图。
