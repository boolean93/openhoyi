# 萃取实时秤读数

- 需求与范围：固定轻量秤读数，横屏停止键旁、竖屏停止键上；原始新鲜通知，一位小数，保留负值。无本杯可信去皮证据时只称秤读数，不表示净重或目标进度。
- 来源分支/基线提交：develop / 92cf2f9e。
- 工作分支：feature/live-yield-readout；PR目标：develop。
- 改动文件与步骤：纯投影与测试；MobileService 本杯/设备生命周期；两个萃取入口与独立中英文资源。四曲线、轴网格、动画及机器控制不变。
- 推送与合并：协调线程随后授权仅本地提交；本轮不推送、不建 PR。后续遵循 [Git工作流](../git-workflow.md)。
- 验证证据：`ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew :mobile:testMockUnitTest --tests io.openhoyi.mobile.ExtractionScaleReadoutTest :mobile:assembleMock --offline` 最终通过（57s，8 tests / 0 failures / 0 errors）；`python3 localization/android_resources.py --check`、`git diff --check` 通过。初轮编译期间调整签名导致测试与旧产物不匹配，最终串行重编译与单测通过，不复用失败轮结果。实机截图/安装由协调线程另行安排，编译不等于实机验收。
- 协议与设备风险：只读展示，不新增去皮、控制许可、重试或写入；结束只冻结对应杯结束时的新鲜观察，不用曲线或移杯后数据补值。
- 发布：不发版、不改版本、签名或升级路径，不安装、不操作设备。
- 回退：回退本任务代码即可，不改变持久数据或未知记录。
- 独立审查：协调线程安排 Astra 只读复核，最终代码无阻塞；Mock 停止顺序测试不是 Service 调度集成测试。
- 端点选择：App 历史终值由 250ms watchdog 采样，可能晚于首次 idle 后移杯。展示单独捕获 brewing 后首次 idle 的新鲜原始观察，恢复 brewing 时清候选，确认结束后锁定（含缺数），不回填后续通知；本缓存不改变历史或机器结束判定。普通 Mock 没有 BLE idle 回调，stop 后立即 sample 发布端点；synthetic 只接受显式通知，不用时钟补样本。
- 未完成与阻塞：真实屏幕、深浅色、方向与大字体布局仍待设备验收。平板已装未合入的一键开关 Mock（versionCode 91），本轮不得覆盖；无安装、设备控制或硬件结论。独立 Activity 保留兼容展示，当前正常入口跳转 Fragment。

## 本地集成与最小实窗检查续轮

- 续轮授权：在原工作树 merge `origin/develop`，不 rebase/重写原提交；仅构建同 versionCode 91 的 Mock 与 instrumentation，不安装、不操作设备。
- 集成：`origin/develop` = `5ac1a61166a6d0d9538d697f6a7c8fef8e714783`；merge = `2fcad92f7958ca4edbd5de64513cf455990b70eb`，无冲突。`git diff --quiet 693d5ae7 origin/develop` 返回 0，已装 shortcut-toggle 源码与合入后的 develop 整棵树一致，未遗漏一键行为。
- 构建参数：`-PhoyiVersionCode=91 -PhoyiVersionName=0.2.0-local.91-live-yield`，不修改默认版本文件。相关单测为 ExtractionScaleReadoutTest、ShortcutStartPolicyTest、ShortcutStartDispatchTest。
- 最小入口：`liveScaleReadoutChecks=true`，不执行其他矩阵。真实 MainActivity + 普通 Mock Service 开一杯 `factory-v3-002`，调用原停止按钮，检查冻结，截图横屏亮色运行、竖屏暗色运行、竖屏暗色结束。不是硬件验收；第 8 项 JVM 单测仍仅为 runtime/投影顺序测试。
- 数据副作用：新增并保留一杯 Mock 测试历史，开始后即在 app 外部文件目录 `live-scale-window-checks/scope.txt` 写入 shotId 和测试来源；不删除或清除原历史、不改曲线内容。临时选择工厂配方和 dark 键在 finally 精确恢复键是否存在及原值，Activity 请求方向恢复；不改系统旋转或字体。若存在未开杯用户豆准备，直接失败，避免消耗用户准备。测试过程中仅 Mock；确认 hub 为 null，失败清理也只停止本测试明确创建的 shotId。
- 建议执行（仅由协调线程在核验签名、空闲和安装授权后执行）：

```sh
adb -s 192.168.31.201:5555 shell am instrument -w -r \
  -e liveScaleReadoutChecks true \
  io.openhoyi.mobile.mock.test/io.openhoyi.mobile.BrewAudioInstrumentation
adb -s 192.168.31.201:5555 pull \
  /sdcard/Android/data/io.openhoyi.mobile.mock/files/live-scale-window-checks \
  /tmp/live-yield-window-evidence
```

本实现线程未执行上述设备命令。安装保留数据且须覆盖包含一键开关与重量读数的集成 APK，不能换回旧源码 APK。运行会开启一个 Mock 应用窗口并暂时旋转该 Activity；主题和当前曲线选择在结束时恢复。若进程被强制杀死，finally 不能保证执行，协调线程应根据运行前备份核对两项偏好，不清应用数据。

### 续轮构建结果（未运行设备）

- `:mobile:testMockUnitTest` 上述三个过滤类共 26 项通过（8 + 11 + 7，0 failures/errors）；`:mobile:assembleMock :mobile:assembleMockAndroidTest` 同次构建通过，2m30s。仅既有 deprecated API 警告。
- Mock APK SHA256：`ad96ad478db0b30ce891bf918701bd811e2754d950712c57004a217758620add`。
- instrumentation APK SHA256：`b4cf9556655beb8ed1643be743b44936918ed88a617b7e1a725e0b9efa9b78ae`。
- 两包签名 SHA256 均为 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`，与协调线程备份的已装 `installed-shortcut-mock.apk` 一致。Mock 包名 `io.openhoyi.mobile.mock`、versionCode 91，已本地 aapt2 核对。
- 本线程未安装、未运行 instrumentation；截图与实际冻结仍待协调线程执行。`git diff --check` 通过。

### 安装失败纠正与物理旋转检查（续）

- 首次增量 Mock APK `ad96...` 安装后在 Application.attachBaseContext 缺 `AppLanguagePreference` 定义，测试未开始。JVM tests / assemble 成功不保证最终 DEX 包完整。协调线程恢复旧 APK 保留数据；实现线程未改业务源码，只对本 worktree 输出 clean/no-build-cache/rerun-tasks。
- 干净 Mock SHA256 `f602d51910a34cae85df6ae5fac9c70214034618596f45f2602b83d03dc87ecb`；原 testAPK `b4cf...`。两包版本/签名一致。实际 DEX class_defs 核验 Mock 754 个、instrumentation 341 个本模块编译类缺失 0；旧 APK 所有 io.openhoyi 类缺失 0；dexdump 独立确认 AppLanguagePreference/Storage/Card 的定义。
- 协调线程随后报告真实 Mock 运行/停止/冻结端到端 PASSED，保留测试杯 `1779ad32...`。但 portrait 截图为 1282x1600、内容裁断，不计入视觉验收。MIUI ignore-orientation 导致 Activity 请求 portrait 仅兼容 letterbox。
- 新测试只修改 androidTest：备份 system user_rotation/accelerometer_rotation，Activity UNSPECIFIED 后设物理旋转（portrait=0 / landscape=3，自动旋转=0）。finally 恢复原值并回读验证；本设备已知原值 3 / 0，但测试使用动态读取值，不硬编码恢复。
- 新 capture 必须同时满足物理 display realSize、root/decor 完整可见、读数 rect 在屏内，PNG 尺寸必须精确匹配 physical（本设备 landscape=2560x1600 / portrait=1600x2560）；拒绝 1282x1600。首图前保留 3.5 秒观察曲线。下次最多再开一杯明确记录 shotId 的 Mock 测试杯。
- 测试执行会暂时改变这两个系统旋转键，不再只是 Activity 请求方向；主题与当前选择仍精确恢复。若进程被强制终止，协调线程需核对并恢复运行前值，不清应用数据。业务 APK 保持 f602...，只需更新 testAPK，无需重装业务包。

### 物理方向实窗复验结果（协调线程执行）

- run3 `LIVE_SCALE_WINDOW_PASSED`：真实普通 Mock Service 开始 → 原停止按钮 → 结束冻结全链路通过；不是硬件验收。
- 第二测试杯 `e8bb4b55-942d-4120-8f29-e7a3f2b5cb0a`，首次测试杯 `1779ad32...` 及原历史均保留，不清数据。
- 截图物理尺寸已正确：landscape 2560x1600，portrait 1600x2560；协调线程保存于 `/tmp/live-yield-evidence/live-scale-window-checks`。system user_rotation=3、accelerometer_rotation=0 已回读恢复。
- 新 testAPK SHA256 `e9e3554d246cd5f4779749f3fe38e124ad7350fc230740fc7e099b80b2afe569`；业务 Mock 仍为 `f602d51910a34cae85df6ae5fac9c70214034618596f45f2602b83d03dc87ecb`，此轮仅覆盖 testAPK。实际 DEX class_defs 检查无缺类；编译 1m51s 通过。
- 功能与物理截图验证通过；portrait 标题/数字间距的独立视觉审查由 Astra 继续，尚不冒称最终视觉验收通过。本提交仅测试、计划与构建失败记录，不修改业务 UI。

### 最后居中小修与 run4 结果

- Astra 图审要求的小修仅两处：readout 主、次两行固定 180dp 居中，数字位仍固定 112dp；结束截图额外等待 3 秒让完成 Toast 消失。四指标、四曲线、坐标网格、动画及真实机器控制未改。
- 最后干净构建（clean / no-build-cache / rerun-tasks）通过，2m17s；ExtractionScaleReadoutTest 8 项通过、0 failures/errors。Mock 754 个和 instrumentation 341 个本模块编译类均有实际 DEX 定义；旧 APK 的 io.openhoyi 类无缺失。两包签名与原已装包一致。
- 最终 Mock SHA256：`8c76bc95129cfe39a06bc4d695e5848b53246534ce8da8f0f47a6918ce17c241`。
- 最终 testAPK SHA256：`e715d44a53d9a93b1660c717c2fdc1f6aa80a327fc0fc6f03e5ad19ada426d56`。
- 协调线程执行 run4 PASS：真实 Mock 运行/原停止按钮/结束冻结通过；最终 portrait 1600x2560 图中标签与 3.9g 组合居中，结束时秤读数说明完整、无 Toast 遮挡。landscape 截图 2560x1600。system user_rotation=3、accelerometer_rotation=0 回读恢复。
- 最后测试杯 `2edd16d6-7127-4c3f-9f85-2e41fe33b0d3`；run2/run3/run4 三杯 QA 记录全部保留（run3=`e8bb4b55-942d-4120-8f29-e7a3f2b5cb0a`，run2=`1779ad32...`），原有记录全部保留，未清数据。
- 本实现线程未操作设备；最终截图由协调线程核看。最终验证范围至此结束，不再扩矩阵；没有真实机器萃取/硬件验收、推送、PR 或发布结论。

## 本杯确认归零后的只读净重展示续轮

- 协调线程已确认实现：只读暴露 Controller 本次 serial / 成功有序预去皮 baseline / 时间；accepted App start 绑定同 hub、controller、serial、scaleConnectionEpoch 与秤地址。有效同会话新鲜样本显示 `raw-baseline` 为「出液重」，不加入控制补偿；其余仍「秤读数」。不改去皮、开泵、停止控制条件和调用顺序。
- 断连/换秤/手动 tare epoch 变化本杯永久撤销净重资格，新鲜重连仅恢复原始读数；下一杯重新建立，passive/普通 Mock 不推断证明。结束冻结端点当时的值与来源名称，不因后续设备变化重命名。
- 先相关 Controller/纯投影测试，不安装；交协调线程安排 Astra 独立复核。用户后续准许创建 PR、尚未准合入；复核后遵循 scripts/create-pr 本地检查，未经协调不自行创建 PR 或合并。本节为后续授权更新，不覆盖前几轮历史验证事实。

### 只读净重本地验证与交付边界

- 相关验证最终通过：ExtractionDisplayBaselineTest 4、ProtocolTargetWeightTest 13、ExtractionScaleReadoutTest 14，共 31 项，0 failures/errors；最终命令耗时 59s。资源一致性及 git diff --check 通过。独立审查由协调线程安排，报告本杯 baseline/epoch/冻结/事件顺序无阻断。
- 最新「出液重」路径仅做本地 JVM 验证，没有安装或真实秤/机器验收。平板仍安装此前 raw readout 的 `8c76...` 包；前述 run4 不能作为新 baseline 路径硬件证据。历史/样本继续原始秤读数，不因本次展示净重而回写或标成已校准。
- Controller 仅增加只读证明和展示字段失效清理，不改变原控制条件、协议命令、调用顺序或补偿逻辑。无目标、passive、普通 Mock 和全局 tare 状态仍 raw；未成功 start 不发布净重证明；同杯 ownership 失配永久降 raw，冻结端点保留当时名称。
- 用户已准仅创建 PR 到 develop，不合入。提交前 fetch 已核 develop 更新为 `daac2cf7`（#67 已合入），需 merge 后对最终 HEAD 运行 scripts/create-pr 完整门禁；不以旧基线检查代替。
