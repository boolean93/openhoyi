# 扫描提示与设备角色筛选修复

- 需求与范围：Android 11 及以下定位关闭时准确提示；通用扫描显示咖啡机和秤，设备更换仅显示对应角色。
- 来源分支/基线提交：develop / 0346941ff3de201761e00da66b246d6455290c2f。
- 工作分支：fix/scan-ui-guidance；PR 目标 develop，遵守 [Git 工作流](../git-workflow.md)。
- 改动：区分用户发起通用扫描与权限/蓝牙回调续扫；角色过滤独立于异步扫描结果。定位关闭提示“开启定位”。
- 验证：JVM 角色转换、源代码接线回归、双语资源检查、Debug/Mock 编译、lint；Astra 独立复核。结果待补充。
- 协议与设备风险：不改变 BLE 扫描器、协议、连接/萃取保护、不向机器写入。不将 Mock 验收当作硬件验证。
- 发布：未授权本轮 PR/发布，报告后再决定；不安装真实包、不操作真实咖啡机。
- 回退：回退本次 UI 和资源提交，不涉及持久数据。
- 未完成：本地验证与独立复核；Mock 设备验收需先协调。

## 本地验证与独立复核

- `:mobile:testDebugUnitTest --tests io.openhoyi.mobile.DeviceScanSelectionTest :mobile:assembleDebug :mobile:assembleMock :mobile:lintDebug` 通过（1m58s），新增 5 项测试 0 失败。测试是纯筛选状态/源码接线回归，不是实际 Android 权限弹窗验收。
- `python3 -m unittest discover -s localization/tests` 25 项通过；`validate_catalog.py` 与 `android_resources.py --check` 通过；`git diff --check` 通过。
- Astra 独立只读复核无阻塞：更换仍仅切换显示角色，不自动重扫，非回归；全新实例默认全量，不可将残留角色问题当成新安装现场根因。官方 app 可扫描的反馈仍需实际版本/系统/权限与扫描页面证据来定位。
- Mock 实窗验收前核实：平板在系统桌面、真实 MobileService 未运行；原 Debug SHA256 `952139d7fcb767a0ad81be4dfb855f565d4fd16c29830e9d556509e753b0f79c` 对应一键开关最终包（源码与 #66 一致）；原 Mock `8c76bc95129cfe39a06bc4d695e5848b53246534ce8da8f0f47a6918ce17c241` 对应最后 raw readout 居中版，其变更均包含于 #69。当前基线已包含 #66–#70，未移除原未合入功能。两包原证书均与新产物一致，备份原 APK 和 files/shared_prefs，不清数据。
- 按父目录较新本地安装规则沿用 versionCode 91，构建同源码 Debug/Mock/testAPK。只允许启动 Mock 验列表，不启动真实包/咖啡机；定位关闭分支只报告代码和单测覆盖。

## 同源码双包安装与 Mock 实窗结果

- 同源码版本 `0.2.0-local.91-scan-ui`（versionCode 91）Debug、Mock、MockAndroidTest 构建通过，1m51s。实际 DEX class_defs 与本模块编译类核对：Debug 744 / Mock 760，无缺失。
- Debug SHA256 `4f0feed7794ea8ab68e13776ea551d55b6a0ab5868efad52424ddb62cf92a7f9`；Mock `cb01f955dea0185380e4508417f87bcb39158e3cd3e4088f92ecc98975fc3467`；testAPK `e6da359f59b6323edee06cff16bbe35d50653671966079a1aefea9fb3784905b`。三包证书均为原 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`。
- 平板 M2105K81AC 上三次 `adb install -r` 均 Success，两主包读回版本一致。安装前后两包 files/shared_prefs 逐文件字节比较完全一致，未卸载或清数据。
- 只启动 Mock 设置页，真实 active-tree 与截图验证四步 PASS：秤更换→通用扫描显示两类；咖啡机更换仅显示 HOYI；秤更换仅显示 BOOKOO；通用扫描再次显示两类。截图/树保存在本地 `build/scan-evidence/`。未点击连接/断开/休眠/萃取，不启动真实包、不发 BLE 指令。结束返回原系统桌面，未更改系统方向/字体。
- Mock 列表是立即生成，故这不属于真实异步 BLE 或系统权限窗口验收；旧系统定位分支仍为代码条件/资源测试证据。
- 尚未推送、创建 PR 或发布。报告后等待下一步。
