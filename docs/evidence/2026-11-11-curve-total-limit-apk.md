# 原 APK「总流量 / 总量上限」证据

日期：2026-11-11。范围：只读分析现有原 APK、解包 JS 与改造旧工程；只新增本证据文档。未安装、未连接设备、未发送 BLE、未恢复功能实现。固件反汇编由另一份独立证据覆盖，本文件不把 App 编码当成设备停水证明。

## 结论

1. 原 APK 1.2.4、1.2.6 都有真实的「总流量」输入框。它绑定 `totalFlow`，保存为曲线的独立 `flow` 字段，不是仅显示阶段合计。
2. 新建、编辑分别保存 `flow` 和 `flow1..4`，修改总量不会自动重分配阶段。保存路径没有 `flow >= 阶段合计` 校验；总量输入仅四舍五入并限制在 `0..65535`。
3. 两版各 100 条出厂曲线的 `flow` 恰好都等于有效阶段水量之和。这个数据事实不能反推字段不可独立配置。
4. 出厂归一化会调整总量和末段，但只在生成出厂列表、特定旧工厂迁移时使用，不是每次编辑保存必经的重算。1.2.6 还用 `paramsCustom` 保护自定义过的工厂参数。
5. `flow` 经接秤规则处理后进入启动报文 bytes 3–4；各阶段水量进入另外的字节。接秤且有目标重量时仍是 `max(flow, ceil(4 × 目标克重))`，所以保存值不是所有运行条件下的绝对硬上限。

## 原件、版本及哈希

以下路径都位于本机 `/Users/boolean93/workspace/hoyi_app/`。版本来自 APK 内 `assets/apps/__UNI__7D80DAB/www/manifest.json` 的 `version`。

### APK 1.2.4

- 原 APK：`1.2.4.apk`；内嵌版本 `name=1.2.4, code=124`。
- APK SHA-256：`401afbd9d36494f5acc41b0c15f7dae5a59684573ecdc5d905c8fd8ed40ace21`。
- 解包 bundle：`analysis/hooyi-app-1.2.4/www/assets/apps/__UNI__7D80DAB/www/app-service.js`。
- Bundle SHA-256：`70ccac9c2bb3705b446cbc9a338f68706506901be05d18d6c84cdae0b7a1cbe1`；1,741,902 字节。

### APK 1.2.6

- 原 APK：`analysis/hooyi-app-1.2.6/hoyi-1.2.6.apk`；内嵌版本 `name=1.2.6, code=126`。
- APK SHA-256：`26133c2ac8ecf3d5ee7682bafa6555b1c78836749c937477b05214a88d8ea238`。
- 解包 bundle：`analysis/hooyi-app-1.2.6/www/assets/apps/__UNI__7D80DAB/www/app-service.js`。
- Bundle SHA-256：`4ed90ea99240b7f37373cdb5c9df0bf315103ca7aea6aaab6f37b2c58dd564d7`；1,996,518 字节。

已使用 Python `zipfile` 直接读取两份 APK 的 `assets/apps/__UNI__7D80DAB/www/app-service.js`，分别与上述解包文件逐字节比较，均一致。不是仅凭目录名认定来源。

### 与改造旧工程区分

`hoyi-project/app/assets/apps/__UNI__7D80DAB/www/app-service.js` 是另一份改造源码，SHA-256 为 `1451bddc95735fdc86b071aef98d9d5b88add12b0b3612c66f666faca7572ddc`，4,011,946 字节。现有旧编码器 oracle 使用过这份文件，不能把它的哈希、模块名或完整行为直接称作原 APK 证据。本次以下结论均重新检查原 APK 内的 bundle。

## 可复核的定位

偏移为各自原始 bundle 的 **0 起始字节偏移**。两份文件都是 ASCII，故与 Python 字符偏移一致；不要用格式化后的文件套这些偏移。

| 定位字符串 / 内容 | 1.2.4 偏移 | 1.2.6 偏移 |
|---|---:|---:|
| `value:t.totalFlow,expression:"totalFlow"` 输入绑定 | 319052 | 882536 |
| `getFlow:function` | 706161 | 1643338 |
| `getFlow1:function`，后接另外三段回调 | 706030 | 1643207 |
| `curveFlowMl:function` | 678014 | 1614603 |
| `savedTotalFlowMl:function` | 678125 | 1614714 |
| `saveTempChart:function` 编辑保存 | 687075 | 1623747 |
| `saveNewChart:function` 新建保存 | 688569 | 1625487 |
| `updateSaveChartLib:function` 持久化 | 701259 | 1638332 |
| `changeChart:function` 编辑回显 | 691408 | 1628326 |
| `addChart:function` 新建初始化 | 692754 | 1629756 |
| `getFactoryChartList:function` | 197013 | 305671 |
| `normalizeFactoryChartFlow:` 导出 | 197109 | 305767 |
| `migrateSavedChartList:function` | 197137 | 305864 |
| `startChart:function` 固定槽启动编码 | 869871 | 1562390 |
| `startTempChart:function` 临时曲线启动编码 | 872052 | 1564571 |

1.2.6 的编辑模板在模块 `71bf`，组件逻辑在 `b19b`，工厂数据在 `0be7`，显示/存储准备在 `8343`，BLE 编码在 `addd`。1.2.4 的工厂模块为 `0ffc`。下面摘录适当加空格以便阅读；定位以原始字符串及偏移为准。

## 输入、初始化与保存

模板不是只读文本：

```js
directives: [{ name: "model", rawName: "v-model",
  value: t.totalFlow, expression: "totalFlow" }],
domProps: { value: t._$s(57, "v-model", t.totalFlow) },
on: { input: [function(e) {
  e.target.composing || (t.totalFlow = e.target.value)
}, t.getFlow] }
```

1.2.6 中文资源 `hoyi.chart.totalflow` 为「总流量」（偏移 433512），英语为 `Max. Volume`。标签同时被用于实时累计量页面，**不能仅凭同名文案判断每处数据语义**；本结论依赖编辑绑定及保存链路。

- 两版 `getFlow`、`getFlow1..4` 均为空函数。值由 `v-model` 更新，不在回调里按合计重算。
- 1.2.6 组件 `watch`（1603188）处理遥测、布局、分类，不监听这些水量字段并重分配。
- `created` / `changeChart`：`this.totalFlow = this.list[this.chartIndex].flow`，各阶段分别读取 `flow1..4`。
- `addChart`：将 `totalFlow` 和各阶段水量初始化为 0。`saveNewChart` 内虽构造带 `flow:50` 的临时对象，随后会被用户的 `totalFlow` 覆盖，不能把 50 当成有效的新建默认值。

两版保存的关键逻辑相同：

```js
this.list[this.chartIndex].flow1 = this.flow1;
// flow2、flow3、flow4 分别保存。
this.list[this.chartIndex].flow = this.curveFlowMl(this.totalFlow);
// 新建对应：t.flow = this.curveFlowMl(this.totalFlow)。
```

`curveFlowMl` 使用 `parseCurveDisplayNum` 解析输入后：

```js
return Math.max(0, Math.min(65535, Math.round(e)));
```

这证明原 App 允许独立总量，**不证明 0、任意 16 位值或任意模式都应在原生 App 开放**。无效输入回退、静默舍入是旧 App 行为，也不建议直接照搬。

## 出厂归一化与持久化

两版 `normalizeFactoryChartFlow` 的核心逻辑相同：先按目标重量调整出厂 `flow`，再比较它与有效阶段水量合计，必要时调整末段，最后 `t.flow = s`。例如目标 32–40g 且总量不在 65–78mL 时，出厂默认改为 70mL。

调用边界：

- `getFactoryChartList()` 对出厂条目复制后归一化。
- 1.2.4 工厂版本为 3；存储版本已达 3 时，`migrateSavedChartList` 原样返回。较旧版本迁移会刷新匹配的出厂条目，因此不能保证旧版跨版本迁移保留所有工厂自改值。
- 1.2.6 工厂版本为 5；保存时 `stampEditedFactoryCharts` 比较参数并标记 `paramsCustom`，迁移遇到这个标记保留用户参数。正常保存不调用归一化来覆盖总量。
- 1.2.6 `prepareChartForStorage`（模块 `8343`）复制条目，仅移除非自定义的出厂名称/说明；不改 `flow` 或阶段水量。`updateSaveChartLib` 最终写 `hy_chartLib`。

离线执行原 APK 提取的工厂模块：1.2.4 的 100 条、1.2.6 的 100 条，原始及归一化后列表的 `flow != 有效阶段合计` 数量均为 0。内置配方相等是默认数据安排，不是编辑限制。

## 保存后启动与报文

两版 `savedTotalFlowMl`：

```js
return this.list && this.chartIndex >= 0 &&
  this.chartIndex < this.list.length && !this.addChartFlag
  ? parseInt(this.list[this.chartIndex].flow, 10) || 0
  : parseInt(this.totalFlow, 10) || 0;
```

`commitCurveFieldsToHoyi` 将此值写入 `this.hoyi.flow`，随后 `startRelase` 调用 `startTempChart(7, this.hoyi, 1)`。所以已有曲线的总量编辑须保存才进入该启动路径，不能只改屏幕输入就宣称已生效。

原 APK 1.2.6 的 `g(t)`（1544082），以及 1.2.4 对应 `m(t)`，读取 `parseInt(t.flow,10)`；接秤且目标重量大于 0 时，以 `Math.max(flow, Math.ceil(4 * 目标克重))` 替换。临时及固定槽启动都调用该函数。

```js
o = g(i);                 // 1.2.4 对应 m(i)
s[3] = (65280 & o) >> 8;
s[4] = 255 & o;
// 各阶段另按 10 * parseInt(i.flowN) 编码，未从总量重新分配。
```

由此可证：独立值保存后会改变传输的总量字段。**仅凭 App 不能证明设备何时比较、提前结束后的动作、最后阶段行为及所有模式的边界**，这些必须结合对应固件字节分析或实机证据。

## 本次实际离线验证

在 Node `vm` 隔离上下文中提取并执行 1.2.6 原 bundle 的工厂模块、存储准备模块及上述保存方法。`uni` 提示、分类辅助、温度辅助和存储接口用内存替身；无 Bluetooth/网络/设备访问。这是函数路径验证，不是 Android UI 或真实磁盘验收。

- 编辑工厂条目：两段 15+40mL、总量输入 160，保存结果 `flow=160, flow1=15, flow2=40, paramsCustom=true`。模拟 JSON 持久化并执行版本 5 重载迁移，三值保持。
- 不保存把输入改为 99，`savedTotalFlowMl()` 仍返回 160。
- 新建相同阶段、总量 170，保存结果 `flow=170, flow1=15, flow2=40`。
- 原件及旧工程均未修改。新增文件仅本报告。

用于复核提取一致性的 1.2.6 函数 SHA-256（从 `function` 起到其匹配右花括号，原始无格式化文本）：

- `saveTempChart`：`965997e232c768efdabad20924402771d151197c9dfff6bc562fccb5fcd071d8`
- `saveNewChart`：`0cfaa66cb2342b56f1511e4706f3c2b0feae9896d6a108105c774189d8fb3d6c`
- `curveFlowMl`：`2310e441fbd7ba7475e475f98e682c154f6a84855745921ff2d68e896ada28fc`

## 原 APK 与现有编码 oracle 的关系

重新按 `generate_legacy_curve_oracle.py` 的同一函数顺序和 harness 包装，分别提取原 APK 1.2.4 与改造旧工程：

- 改造旧工程 harness：11,575 字节，SHA-256 `635ddbe7bbc63d8d74b5053cd5c2ca1d5e8a0dfab0f24447171e5043fd01855f`。
- 原 APK 1.2.4 harness：5,636 字节，SHA-256 `2fa002d7307e0b6192afdaf6c0d1b50f2d9d1c9f9f477eeb87c8aa0e879d515f`。

现行提取器要求 `8625: function` 等带空格标记。此次仅在内存将原 APK 的模块/方法冒号后补空格，适配定位；这些标记位于提取函数之外，原始函数体没有格式化或改写。该调整未写回 bundle。原 APK 压缩格式与改造源码不同，因此哈希不一致；不能据此宣称两个编码器逐字节相同，也不能仅凭输出样例宣称所有输入语义等价。

随后在无 BLE 的同一个 `NODE` harness 中对两个版本输入同样样例，实际结果：

- 既有 `custom-pressure-curves.json`：72 配方、6 槽位、2 秤模式共 864 帧相同；输入 SHA-256 `9a81255697af6a22abcbbd2a27daf2d71a4dfdba475bcaf3e257a3859fd9305c`。
- 本轮当时的 `custom-pressure-water-limits.json`：3 配方、6 槽位、2 秤模式共 36 帧相同；该次输入 SHA-256 `2317c10f28480541bab33a081c53ebbdc96996c1e2f26c73f5d85437c23ec7e2`。后续修改该文件不能沿用此结果。
- 仅在内存另构造阶段 15+40mL、总量 40mL、目标 36g 的压力配方，12 帧也相同。slot 7 无秤帧为 `02175D0028001E5A000000960001900000000023`，有秤帧为 `02175D0090001E5A00000096000190000000009B`，总量字段分别为 40、144mL。

以上核对补上了原 APK 的直接输出证据，不能只把改造旧工程的 TSV 来源文字改称原 APK。随后本轮新增 `scripts/generate_custom_water_limit_oracle.py`，直接从原 APK 读取 bundle，并同时校验 APK、bundle、提取 harness 三个固定哈希；旧的 72 配方 oracle 和 fixture 保持不变。

独立复核再次运行这个新入口，仅输出到自动清理的临时目录，与本轮 `custom-pressure-water-limit-wire.tsv` 逐字节比较通过。3 配方共 36 帧，TSV SHA-256 为 `20efd4d61f50e0c208a02d8bf21d03ec18aa75385b002125fd63573ff067b5bc`，输入仍为上列 `2317c10f…`。因此本轮新增 fixture 现在具有原 APK 直接来源绑定，而非仅修改来源声明。

## 对本轮设计的修正建议

独立可编辑已经有原 APK 证据。先前建议的「不得小于阶段合计」只是原生当前 `CustomPressureStartPolicy` 的受限许可，并非原 APK 的字段定义；继续沿用会阻止用户将 15+40mL 的配方总量设为 40mL。

结合独立固件复核若确认当前受支持压力子集可在累计量到阈值时结束，可考虑将该子集的下界改为 1mL，上界仍保留 `max(阶段合计, 800)`，不开放 0，不扩大 FLOW_RAW 或计时/首段流量模式。小于阶段合计表示可能提前结束，大于阶段合计表示末段行为须按固件理解；不能自动改阶段来凑总量。接秤仍须如实说明上述 4 倍重量规则。

这项调整涉及实际执行许可，不能把它包装为纯输入框改动。需要记录固件证据、补充相称的低于/高于阶段合计编码和门禁回归，再决定实现范围；本报告本身不授予设备操作或发布权限。
