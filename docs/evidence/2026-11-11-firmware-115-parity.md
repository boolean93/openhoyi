# 1.1.5 静态兼容放行依据

用户授权将 1.1.5 显式纳入与 1.1.4 相同的现有控制范围及完整流量曲线。此结论不是实机验收，不扩大命令组或参数范围。

## 来源和复现

只读固件分析会话 `6af48252-8068-8191-b39e-3d17b39980d0` 的最终复核报告及本机 `Documents/Codex/2026-11-11/task-2/evidence/` 中 `manifest.json`、`address-map.json`、`cfg-diff.txt`、`C6_stop_mask.txt` 为输入。

- 1.1.5：71,216 bytes，SHA-256 `5dc4b6088c2bbd8f718387ea7f6a419b368bc9975b6b588bc55ccce2991c5970`。
- 1.1.4：70,892 bytes，SHA-256 `fa869de17b99836d5962d0566e93f543765194be2885265df514cd9a27dfc8aa`。
- 1.1.5 来自旧官方 APK 使用的 `http://www.newcoffeetech.com.cn/ota/hoyi/1.1.5.bin`；重复下载 hash 一致。HTTP 来源没有发布者签名或认证公布 hash，本地 hash 不能作为来源密码学认证；HTTPS 握手失败，旧远端 1.1.4 已 404。

## 静态协议范围

逐项入口支持现有 `01/02/03/04/05/06/09/0A/0C/0D/0E/0F/10/11/14/15/20` 命令及 `40/80/83` 布局复用。启动仍为20字节；低三位为零为停止。空闲/萃取/主设置仍为19/13/13字节，睡眠回读20/15字节；固件版本字段为 `01 15`。

曲线不是只按编号猜测兼容：1.1.4 `0800F6B0–0800F70C` 首段拆分/目标夹限 helper 与 1.1.5 `+0x48` 对应字节相等；状态转换 `0800F70C–0800F72A` 与 `0800F754–0800F772` 字节相等。曲线入口 `0800E384→0800E3C8`，新版首段/闷蒸/后续三段入口 `0800E4D4/0800E76A/0800E802/0800EA22/0800EBAC`，报告未发现新增阶段、连续插值或切段策略变化。

因此完整文档 Ls 和首段流量配置仅精确允许1.1.4、1.1.5；1.1.3新组合仍拒绝，未知/1.1.6拒绝。不放开跟随模式、缺失模式的原始流量或任意参数。启动上下文绑定固件身份，排队中换固件须重新生成许可，即使两版都在白名单。

## 保留边界

C6 在新版部分固件路径被排除（`bic #0x20`），App 仍按 `bits & 0xBFFF` 阻断新控制。身份、新鲜遥测、设置、清醒状态及工作室温度门禁不变。急停仍匹配当前设备/代次并优先发送，不额外要求新鲜空闲或无报警。

OTA、密码、校准、恢复出厂/忽略报警、曲线复制等原禁止组不开放，特别是新版 OTA 引导程序有实质变化。普通 App 温度既有范围75–105/110–145而固件夹96/130、预热96，不因本次许可声称全数值认证。GATT属性、BLE模块密码校验、执行器效果及回读延迟仍待1.1.5实机验证。

## 实现验证

- `./gradlew :protocol-core:test :device-session:test --console=plain`：BUILD SUCCESSFUL；protocol 39,827 checks、32,856条捕获通知回放；Session aggregate103；JUnit83 suites、772 tests、0失败。日志 `/tmp/firmware115-session.log`。
- `./gradlew :mobile:testDebugUnitTest --tests '*CustomFlowCurveTest' --tests '*CustomCurveExecutionLibraryTest' --tests '*FirmwarePresentationTest' --tests '*CurveStageParametersTest' --console=plain`：BUILD SUCCESSFUL；4类20 tests、0失败。日志 `/tmp/firmware115-mobile.log`。
- `git diff --check`：通过。独立Astra只读复核最终源码和证据无已知阻断。
- 新回归包含schema3 Ls/首段flow文档roundtrip与library许可、113新组合拒绝、114/115成功capture和队列barrier后精确启动bytes、116/unknown拒绝；114↔115和115→113/116在派发前撤销；C6阻挡新启动/设置，陈旧空闲加C6仍可急停。
- 旧context未绑定firmware的漏洞由独立审查确认；协调者要求不在共享树临时移除门禁，因此未执行red重现，仅运行最终强断言green。既有禁止命令组、身份和遥测回归随完整session任务执行。

不以静态分析或离线测试替代设备验收；未安装、刷机或操作咖啡机。
