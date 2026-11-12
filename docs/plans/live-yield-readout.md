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
