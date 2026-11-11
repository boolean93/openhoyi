# 完整曲线编辑：官方 App 与固件证据

日期：2026-11-11。离线分析，不代表真机萃取验收。本轮不安装、不操作咖啡机。

## 为什么不能只移除灰色编辑按钮

旧自定义文档只记录有效阶段的目标、水量、温度、目标液重、总量，不能无损表达官方流量曲线。它缺少独立控制位、首段模式、两个计时字段及未启用阶段槽。旧 `FLOW_RAW` 不能靠目标大小猜测单位。

新完整文档须保留这些字段；旧 schema 1/2 的解析、指纹与压力执行边界保持兼容。没有完整参数的旧 `FLOW_RAW` 不自动升级为可执行曲线，用户须从原始完整曲线重新复制。

## 真实模式与字段

| 字段 | 官方含义与编码 | 编辑规则 |
| --- | --- | --- |
| `variableFlowLogic` / `chart` | byte1 bit6，全局压力/流量模式 | 后续第2–4段统一跟随此模式，不支持任意逐段混合 |
| `firstSegmentFlowMode` / `seg1FlowMode` | byte5 bit7，仅在全局流量模式有效 | 流量模式首段可选压力或流量 |
| `pressureLogic` / `press` | byte1 bit7，独立控制位 | 官方流量界面显示 `Hs`（true）/`Ls`（false）；不是目标单位，不得丢失或固定 |
| `preinfusionSeconds` / `time` | byte5低7位，首段后的停泵闷蒸 | 与首段执行时限分开，0–127秒，不照搬官方UI大数回绕 |
| `firstDurationSeconds` / `time1` | byte12，首段执行时限 | 不是闷蒸时间；流量首段退出条件之一 |
| `target1..4` / `press1..4` | byte6–9，原始十分之一单位 | 按上述模式显示 bar 或 mL/s |
| 阶段水量四槽 / `flow1..4` | byte10–11、13–18，十分之一mL | 保留四槽，含未启用槽 |
| 独立总量 / `flow` | byte3–4，整数mL | 不改写成阶段水量合计 |
| 目标液重 / `weight` | 原App以0.1g保存 | 有秤编码总量为 `max(原总量, ceil(4×目标克数))` |
| 补偿 / `wdelta` | 原App以0.1g保存 | 不在20字节命令内，但影响App侧停泵，复制/分享不能丢失 |

官方 `saveTempChart`、`saveNewChart` 和两个启动函数始终读取/保存四槽，不能在减少段数时清零暂不启用的参数。保持字节一致不意味着四槽在任何段数下都参与执行。

## 固件边界

原始1.1.4固件 SHA-256：
`fa869de17b99836d5962d0566e93f543765194be2885265df514cd9a27dfc8aa`。

已有反汇编证据在本地分析材料 `deep/curve-report.md`、`deep/curve-disassembly.txt`：

- `F6B0`：拆分闷蒸低7位与首段模式高位；`F6BE`：首段目标范围。
- `A478`、`E49A`：首段退出条件。首段时限255无法满足u8计数严格大于255。
- `E6DE–E728`：流量多段首段跳过总量检查；首段时间/水量都为0会禁用两个首段出口。
- `A272`、`CC42`：读取独立 `pressureLogic` 并进入控制分支。
- `1024C` 等：阶段水量夹到9999个0.1mL。

新流量执行采用独立窄门禁：2–4段；首段压力目标0.5–10bar或流量1–10mL/s；后续目标1–10mL/s；整数阶段水量至多999mL；首段时限0–254秒且首段时间/水量至少一项大于0；后续有效阶段水量大于0；闷蒸0–127秒。温度沿用已有75–105℃验证边界，不是新增安全认证。

完整压力配方也不能因增加了参数块就绕过旧门禁。新的独立分支仅允许 `press=false`、首段flow=false、2–4段、首段目标0.5–12bar、后续1–12bar、有效阶段整数水量1–999mL、首段时间0–254秒、闷蒸0–127秒。这使原有压力出厂曲线的时间参数可无损保留，旧schema1/2仍走原有策略。`press=true/chart=false` 在原App显示“跟随”，其控制分支并不等价，本轮保留数据但不新增执行许可。

未启用槽仅验证原始wire范围（目标0–255，水量0–65535个0.1mL），不强加有效槽的整数mL限制。在本1.1.4正常状态机中，`E7CE–E7D2`/`E862` 将2段配方与目标3初始化分隔；`EADE–EAE2`/`EAF0` 将3段与目标4分隔。它们不是这些段数下的控制目标，但解码仍会读取所有槽，因此不声称全固件从不读取未启用槽。

总量不替代首段退出条件，不声称它对流量曲线每个阶段都是绝对硬上限。最后阶段水量也不能宣传为单独的硬停止条件。单段流量曲线的固件行为不能被抽象为理想单段配方，本轮不开放。原始1.1.4指令证据不构成1.1.3或实机验收。

### 按模式区分固件许可

复核已有6份1.1.3现场JSONL，仅有3个去重20字节启动帧，首段流量位均为0；两个流量帧均为 `press=true/chart=true`，与既有 `provenance.json` 一致。原件定位为 `1789738671426-49383746-0.jsonl` seq3497（压力），`1789783373182-0da5ffdc-0.jsonl` seq4382/12273（流量）。未发现1.1.3镜像或该版本首段流量/Ls现场证据。

因此新增自定义流量在1.1.3只允许已观察组合Hs且首段压力；Ls或首段流量需要当前固件精确1.1.4。在捕获启动上下文和实际派发时都重新验证。编辑、保存、分享不受此固件门禁影响；不满足时须向用户说明所需版本。既有factory/captured精确帧执行路径保持不变。

## 原版 APK 核对

直接从原始APK ZIP读取bundle，未使用修改版App。

| 版本 | APK SHA-256 | bundle SHA-256 |
| --- | --- | --- |
| 1.2.4 | `401afbd9d36494f5acc41b0c15f7dae5a59684573ecdc5d905c8fd8ed40ace21` | `70ccac9c2bb3705b446cbc9a338f68706506901be05d18d6c84cdae0b7a1cbe1` |
| 1.2.6 | `26133c2ac8ecf3d5ee7682bafa6555b1c78836749c937477b05214a88d8ea238` | `4ed90ea99240b7f37373cdb5c9df0bf315103ca7aea6aaab6f37b2c58dd564d7` |

bundle路径均为 `assets/apps/__UNI__7D80DAB/www/app-service.js`。

| 函数/模板定位 | 1.2.4字符偏移 | 1.2.6字符偏移 |
| --- | ---: | ---: |
| `saveTempChart` | 687075 | 1623747 |
| 闷蒸/首段模式编码helper | 852138 | 1544657 |
| `pSwChange` | 675740 | 1612303 |
| Hs/Ls开关模板 | 322878 | 886362 |
| `segTargetMax` | 671264 | 1607655 |

两版各100条出厂曲线中均有35条流量曲线。出厂曲线默认 `press=true/chart=true`、首段压力，但官方编辑器也支持Ls和首段流量，不能据出厂数据将它们写死。复制必须保留所复制版本的原参数，不能换成另一版本同名曲线的参数。

## 原编码器离线对照

脚本 [`generate_custom_flow_curve_oracle.py`](../../scripts/generate_custom_flow_curve_oracle.py) 同时校验原APK、bundle和提取编码器的SHA-256。提取官方原函数，仅把 `BleWrite` 替换为内存捕获；不调用BLE、Android设备或网络。

- 输入：[`custom-flow-curves.json`](../../mobile/src/test/resources/custom-flow-curves.json)，6个代表性完整配方。
- 输出：[`custom-flow-wire.tsv`](../../mobile/src/test/resources/custom-flow-wire.tsv)，6个槽位×有/无秤，共72帧。
- 覆盖混合首段压力、首段流量、Hs/Ls、完整压力计时、非零未启用槽、首段仅时间出口、独立总量与秤放大规则。补偿保留在输入中，不凭它不在帧中就将其丢弃。
- 分别执行1.2.4和1.2.6原编码器，72帧逐字节完全一致。仓库TSV记录1.2.6来源；运行时自定义曲线仍仅使用槽7，其他槽仅为编码对照，不因此放开执行权限。
- 另外将仓库100条出厂配方送入原1.2.6编码器，200个槽7有/无秤帧与已有工厂oracle逐字节相同。该检查复用了现有工厂数据与证据，未新增设备操作。

复现：

```sh
python3 scripts/generate_custom_flow_curve_oracle.py ORIGINAL_124_APK \
  mobile/src/test/resources/custom-flow-curves.json /tmp/flow-124.tsv
python3 scripts/generate_custom_flow_curve_oracle.py ORIGINAL_126_APK \
  mobile/src/test/resources/custom-flow-curves.json /tmp/flow-126.tsv
diff <(tail -n +2 /tmp/flow-124.tsv) <(tail -n +2 /tmp/flow-126.tsv)
```

这是独立的线协议参考，不代替当前存储文档匹配、每次派发重验、固件版本、空闲状态、端点或秤读数门禁，也不授予未经用户允许的真机测试。
