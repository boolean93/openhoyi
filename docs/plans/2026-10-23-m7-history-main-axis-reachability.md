# M7 History 普通主态时间轴滚达补证计划

> Sol按executing-plans串行实施；Astra静态/实图验收；root统一构建与推送。与Toast计划会改同一测试/runner，必须串行，不覆盖他人增量。

**Goal:** 为原四配置History主fixture补实际完整时间轴及原scroll恢复，不改变产品布局。

**Architecture:** 使用原LegacyChartViewport和同一个LegacyShotChartView，由实际Canvas time-tick glyph bounds计算一次滚达请求；保存返回同帧target及恢复。原初态图先保留，轴滚达图不冒充全图同屏。

**Tech Stack:** Kotlin instrumentation、既有NativeWindowFrameEvidence、Python host verifier。

## 基线与设计判断

依[Git工作流](../git-workflow.md)/[模板](task-template.md)；当前feature/task-first-ui，基线588855c96fdceec79bf87f0fd5950f33fa6aff1c，目标develop。旧8b PR merge44d34c52a3f3b18537f59cb92607bcc7c6252d09主图History chart层级少50/171px，list-restored同样裁切，Toast与内部裁切必须分开。

冻结`2026-10-19-m7-m8-remaining-visual-migration.md`§History允许预算不足时中部局部滚动、固定原详情入口；标准1.0真实plot目标160dp、短高96dp另有轴/图例带。实际LegacyChartViewport仍用minimumVisualHeight(width,160)，中部ScrollView已存在。本次不将初态时间轴裁切直接定为产品违约；先补原合同要求的完整轴滚达/恢复，禁止缩字体、改160/96或隐藏说明。不能以新轴帧判初屏全图同屏。

## 只允许两实现文件

- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/LegacyVisualMigrationChecks.kt`
- `scripts/check_legacy_visual_migration.sh`；可为其纯合同验证新增 `scripts/test_legacy_history_axis_contract.py`，fixture自含、不依赖大ZIP。

不改LegacyHistoryContent/Activity/ChartView、共用frame helper，不复用short-window或长名fixture冒充main。

## 具体步骤/合同

- [ ] 原main成功保存后、任何listBusiness/选中/手势/重建前，对仅page=history调用新`mainHistoryAxisReachability`。同main冻结原Activity/Window/root/chart/viewport引用、所有ScrollView实例及x/y、ListView位置/offset、selectedId=原mainId、原points/业务不变量。先写未完成报告，保原初态refs；不能改变原main命名或删除裁切证据。
- [ ] 从原真实Canvas operations筛选**全部role=time-tick**，按实际原index/text/glyphBounds顺序冻结；不是固定“某第N字”或资源字符串。验证集合非空、所有有效边界、映射可信。按所有time-tick的glyph union在chart内容坐标计算一次`requestRectangleOnScreen`；若调用viewport须加入它的实际scrollX/Y转换，禁止硬编码50/171、422或fixture高度。目标union须能放入实际viewport，不能反复scroll/capture找通过。
- [ ] 用原Activity标准frame协议采唯一`states/history-main-axis-reached/window.png`。geometry扩展的同main目标须含原对象身份、chart实际origin/scroll、全部Canvas操作/字形screen bounds、clip映射与scroll列表、selection/list offset/固定详情动作。**把返回frame.target直接落geometry**，不要截图后重新geometry代替它；typography实际同帧节点或明确其独立边界。
- [ ] 返回target上验证原time-tick集合逐项index/role/text/glyphBounds不变且全部wholeGlyph、viewport/Window身份未变、业务/选中/list offset不变、固定详情入口全文及实际48dp完整可见。保存原Canvas真实plot>=160dp，不能把轴滚达后上部plot被滚出当整个plot始终可见；分别记录initial/reached可见plot，标准初态plot合同不得降格为96。图例/纵轴不冒称与底轴同屏，若新图另揭示裁切仍保留。
- [ ] finally按原对象逐个恢复全部ScrollView x/y，再采唯一`states/history-main-axis-restored`，验证全部scroll、ListView位置/offset、selectedId、Window/chart身份及业务不变量精确等于原基线。主错保Throwable，恢复/写盘错suppressed；只有reach和restore都认证才completed=true，不因finally成功覆盖主错。
- [ ] host四配置各精确要求reach+restore两组PNG/geometry/proof与SHA/manifest refs，重算全部time-tick glyph在实际可见clip内、矩阵/原窗口映射、动态目标集合与原Canvas一致、恢复精确、固定动作48dp；原5秒四零未改。离线反例含漏一tick、边界外1px、错index/role/text、glyph不变但screen clip不容纳、错scroll/selection、换Window、96冒充标准160、未completed或缺恢复。不能只验证布尔PASS。

## 验收与边界

四配置将新增8实际原件路径；若一帧某目标不可达，则保存失败原件后停，不改产品来绕过、不循环重拍。Toast生命周期取证先接入，轴补证本身不证明全局Toast不存在。Astra实际看新轴/restore及清晰主图后，才能关闭对应G3缺口；旧12图/80短窗轴字结论保留，不被新配置替代。

Sol离线/语法/diff检查并冻结SHA→Astra静态审核→root完整构建、普通合批推送、PR必需CI及评审后合并develop；禁止微推/force/no-verify。不发版、不动设备命令/未知记录；回退仅本测试增量并保全原件。当前无需产品布局决定，可在Toast完成后串行执行。

## Astra 静态复核：两项阻断（2026-10-23）

实际diff冻结 Legacy test `74374d3eac11cf9c758ffbac4fa19af7d38442a3fa400671f5d0e5f2ca3e938d`、runner `f91a831a7fe848fe379f8ff026e76dd932b73ad4256fb2864f99ba0ff8e38f22`、axis oracle `edb2c831c7f34c1c5802bd6601f98a02034f15268b5b20bb6f1c08ee5fdb8ef8`。原main先采、一次glyph union请求、返回target落盘、reach/restore引用与SHA/原5秒四零、160dp标准plot及48dp动作未降格。恢复分支保存原Throwable并suppressed后错。

1. `mainHistoryAxisReachability` 开头直接在instrumentation线程读root层级/chart.parent/所有scrollX/Y/points/viewport.width,height。须在同一main块冻结原实例与原scroll、尺寸/points，并核与已接受initial中的身份/scroll一致后再请求，不能用后读值替代初态或在恢复时恢复到另一基线。
2. host `validate` 未从完整`canvasOperations`重建全部time-tick；当前仅比较三个state自带timeTicks，三帧一致漏项可漏检。现sample仅一条Canvas而tick index=4仍通过。须每帧按原Canvas实际index筛选全部operation=text/role=time-tick，精确核index/role/text/glyphBounds的全序列，不接受遗漏/多出/重复/错index；更新自含样本与反例，增加三帧一致漏项、三帧一致错index/text/glyph等拒绝。

独立6 tests通过不替代上述完整合同。仅修测试/host，不动产品；当前不通过，修后窄复核。

### Astra 第一次增量复核（2026-10-23）

已核 test `88d3802462032e456083c567176779723eb87a44bcdf894f460b7062df4aa216`、axis oracle `a2cbeaaacfd268b32a641c3a1311aed7b31d0cff5eca0e75aa0eeb97fd451fca`，独立6 tests通过。Toast begin/invoke/drain统一try，began守卫finish，callback/cleanup原Throwable及suppressed后错、observer所有权失败不覆盖他人、同startedNs截止已闭合。axis原引用/scroll/尺寸与initial对照已移至同main；host从完整Canvas派生全部index/role/text/glyphBounds，三态固定Canvas与目标集合一致，原漏项漏洞已闭合。

仅剩axis同main块之后重复check仍有 `&& chart.points==scope.mainShot.points` 非主线程读取；该断言已在上方main保留，请仅删除块外重复部分。余项不需重做；新hash窄核后再签整组通过。

### Astra 最终窄复核：静态通过（2026-10-23）

新test SHA `0b474122e54f10d332968a92f3e554c23049a87a42a5335238e0ab93340a87fc`；独立逆补唯一块外重复points条件，精确还原前版 `88d3802462032e456083c567176779723eb87a44bcdf894f460b7062df4aa216`。main内points严格检查仍在，未降门禁。此前三阻断现均闭合，结合前次8文件实审与6tests，本M7/单屏/badge增量静态通过。root可继续统一构建与合批流程；不等于新Toast/轴字/badge/单屏真实运行或视觉验收，仍待新原件。无源码修改。
