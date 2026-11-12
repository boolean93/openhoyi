# 曲线删除：离线升级边界与实窗验收准备

## 状态与边界

功能源码为 `a56481270e1025f254c61c4e0c3f76ea4941ea54`。协调会话转达：独立 Astra 已审查该提交，无阻断。本次补充只改测试与文档，不改生产代码或 APK，不推送、不建 PR、不合并、不发版。

**平板等待统一调度，本次没有执行任何 ADB、安装、旋转、截图、Mock 萃取或设备命令，也未启动 AVD。** 现有 Mock APK/哈希沿用 [实现交接](curve-delete-handoff.md)；默认 versionCode=1 不能直接用于共享平板覆盖。

## 离线兼容验证

`CurveStoreUpgradeCompatibilityTest` 使用真实临时文件和 `AtomicDocumentStorage`。旧读取器/写入器 fixture 取自基线 `df108928b2f1aa88f3ba662c60320101cfde0359` 的 `CustomCurveStore.kt`，仅重命名类并改为 internal，未重写旧格式校验。还原类名后已逐字比对基线源码；原文件 SHA256 为 `8c1fb456118177e06d31a10535507628ef21282b9792040e2785bb07d06a05e5`。

| 场景 | 验证范围 |
| --- | --- |
| 基线写入 v1，新版打开、编辑 | editable/只读 document 保留；打开不改文件；未归档时仍写 v1，真实旧读取器可继续读 |
| 新版删除并重新打开 v2 | 保留历史名称，正常列表不可用，关联快捷槽仍为显式空槽 |
| 旧读取器打开 v2 | 抛错拒绝，原始文件字节及写入次数不变；随后新版仍可打开 |
| 同 ID 重导恢复全部 document | 空槽元数据仍需保留，文件仍为 v2；旧读取器仍拒绝，不能将重导宣称为降级迁移 |

命令：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home \
ANDROID_HOME=/Users/boolean93/Library/Android/sdk \
./gradlew :mobile:testMockUnitTest \
  --tests 'io.openhoyi.mobile.CurveStoreUpgradeCompatibilityTest' --offline
```

结果：`BUILD SUCCESSFUL in 48s`，新增 3 项测试全部通过、0 failures、0 errors。此次运行仅过滤新增兼容测试；此前 29 项功能回归未重复执行，生产代码与 Astra 已审查版本相同。原始日志 `/tmp/curve-delete-upgrade.log`，JUnit XML `mobile/build/test-results/testMockUnitTest/TEST-io.openhoyi.mobile.CurveStoreUpgradeCompatibilityTest.xml`。

这些是文件/读取器级证据，**未验证 Android PackageManager 覆盖安装、签名、实际升级 UI 或真实设备行为**。现有 `scripts/check_mock_upgrade.py` 限定 QEMU，验证同源码版本号 1→2，不覆盖历史 v1→本功能 v2；本轮未运行，也未绕过其限制。

## 回退风险结论

当前没有通用降级迁移实现。只要仍有归档或空槽元数据，旧读取器就不兼容；即使所有曲线都重导恢复，空槽标记也可能要求继续保存 v2。进程内最近一次撤销，只能恢复对应操作，不能保证全库回到 v1。

需要回退功能时，应提供保留 v2 读取、归档和空槽安全语义的前向修复。不能直接 revert 整项功能后安装旧 APK，不能修改 storeVersion/丢弃元数据冒充迁移，也不能卸载清数据。若以后确需降级，迁移必须作为独立工作验证历史名称、槽引用及期间新增内容的保留；当前尚未实现和验收。

## 待协调后的最小实窗验收

设备窗口由协调会话统一安排。取得窗口后先核对连接设备、无其他验收/真实萃取占用、已装 Mock/Debug 版本与原签名；按当前规则用同源码、与已装对应的 versionCode 构建双包，保留原数据。安装和操作范围以协调授权为准，不生成或调用实际咖啡机/电子秤命令。

| 代表场景 | 可观察通过标准 | 证据 |
| --- | --- | --- |
| 横屏：自建曲线绑定快捷槽后删除、撤销 | 删除仅在详情「…」；确认含名称、历史保留及槽清空提示；列表退出详情；空槽为「选择曲线」且不启动；撤销恢复原 ID，新绑定不被覆盖 | 菜单/确认/删除后首页/撤销四个关键状态截图；记录操作时间和曲线 ID |
| 竖屏：导入不可执行/不可编辑 document 后删除 | 仍可发现「删除曲线」；确认和撤销可触达，文字与按钮不裁切；列表/最近/上次曲线正确刷新；工厂曲线无删除 | 竖屏菜单、确认、删除后列表与首页截图 |
| Mock 使用中与保留数据 | 在获准的 Mock 合成流程中，相关启动/去皮/萃取曲线不可删且有短原因，无关曲线可删；已有关联历史名称/样本和首页拨杆快照不变 | Mock 状态与提示截图；同一测试记录前后 ID/名称、样本可见性、拨杆名称/预览比对 |
| 删除后重启 Mock | 已删曲线仍不在正常列表，快捷槽仍空，历史名称保留；撤销入口不跨进程保留 | 重启前后截图；明确只验证 Mock，不作为硬件或升级安装证据 |

只使用本次明确建立/标记的测试资料；不清未知历史、不删除未知记录。前一状态失败即保留现场并报告，不自动重发、重装或清数据。横竖屏的正常流程与只读 document 各覆盖一次即可，不扩展完整组合矩阵。
