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
