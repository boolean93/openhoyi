# M7 原徽标同帧字体与颜色补证

> 由当前M7 Sol在Toast/主轴/SingleScreen增量之后串行实施，Astra审核；root统一构建推送。只补已证缺口，不覆盖其他改动。

**Goal:** 将原short-window徽标实际字体/颜色属性绑定到既有badge-reachable/restored返回geometry，不再靠另帧字体旁证。

**Architecture:** `shortDetailState()`已同main唯一取得原readonly TextView；在该对象上调用公开getter与已有无副作用Layout/color取证函数，把结果嵌入同一次geometry。原frame before/after将自然覆盖新字段，不增加全局采样或新PNG。

**Tech Stack:** Kotlin Android instrumentation、TaskTypographyEvidence.actualTextLayout、TaskVisualColorEvidence公开取证函数、现runner。

## 来源与规则

遵守[Git工作流](../git-workflow.md)/[模板](task-template.md)，当前feature/task-first-ui→develop，基线588855c96fdceec79bf87f0fd5950f33fa6aff1c。新push39911032377原图四配置8帧已审，见[限定review](../evidence/2026-10-23-m7-badge-current-visual-review.md)：1.3真实21px滚达全文/精确恢复已接受；原geometry仅行框，无本帧实际textSize/weight/颜色。另帧long-name-source的31.5/42.3px/400不能替代本帧。

## 仅两个实现文件

- `mobile/src/androidTest/kotlin/io/openhoyi/mobile/LegacyVisualMigrationChecks.kt`。
- `scripts/check_legacy_visual_migration.sh`。可在现M7离线合同测试内加反例；不改产品、TaskTypographyEvidence/TaskVisualColorEvidence或共享frame helper。

## 最小实施

- [ ] 在`shortDetailState`当前唯一readonly对象处，新增`headingReadonlyTypography`对象。禁止再全树找同tag/调用全Activity snapshot。断言当前main线程；记录与`headingReadonlyIdentity`相同对象identity、semanticId、完整`text.toString()`、text来源类、attached/layoutRequested及density/fontScale（均实际getter）。
- [ ] 同一次快照保存实际`textSize`、`typeface`存在状态/对象identity、API28+`typeface.weight`与`isItalic`、`paint.isFakeBoldText`、`letterSpacing`、`textScaleX`/`paint.textSkewX`、`includeFontPadding`、`lineHeight`、`lineSpacingExtra`/`lineSpacingMultiplier`、`baseline`、padding。getter不可用/Typeface null必须显式null+reason，不用资源常量填值；不把Typeface.toString/identity当字体家族或SHA认证。
- [ ] `actualLayout`复用 `TaskTypographyEvidence.actualTextLayout(readonly)`，无需创建Layout/测量/请求布局。`textColors`复用 `TaskVisualColorEvidence.text(readonly)`，另保实际`currentTextColor`；`drawableState`复用state，background/foreground用已有`TaskVisualColorEvidence.background(drawable,resources)`。该helper若用constantState副本只能操作副本，禁止原drawable.setState/mutate、反射GradientDrawable私有字段或自行假设fill。记录未知/动态层的原unknown原因，不为机械通过伪造色值。
- [ ] `glyphFontIdentity`明确`UNKNOWN_PUBLIC_TEXTVIEW_API_DOES_NOT_EXPOSE_FALLBACK_GLYPH_FONT`，字体资产SHA仍只是来源关联。公开TextView/Typeface实际属性可证明当前字号、weight、spacing和状态色；不能证明每个中文glyph来自哪一字体，也不能把drawable配置色当合成后屏幕像素。
- [ ] 此字段仅随原shortDetailGeometry initial/reach/restore的现screenshot返回target落盘；不在截图后另取View拼数据、不新增独立“同帧”文件，不改截图数/初态先采/一次滚达/restore/原5秒四零或原异常保因。禁止在字段塞时间戳等自变信息破坏稳定比较。

## runner精确验收

- [ ] 原4配置同一8帧链中，实际文件SHA/proof两端target==geometry仍必需。新对象id等headingReadonlyIdentity，tag/text等该帧headingReadonly，actualLayout来源/附着状态/行数/ellipsis/尺寸与同帧行框相容；reach没有pending layout。原初态/restore裁切仍合法，不能要求restore全文可见来破坏精确恢复合同。
- [ ] 验证字段类型完整，所有字号/spacing/基线数为有限值；已知本fixture实际textSize按原缩放合同（1.0辅助14sp的31.5px；1.3实际字体缩放42.3px），weight400、letterSpacing0、无fakeBold，均核实际字段而不是写期望常量到产物。若运行与既有实际值不同，应保留原实值失败，不静默容差或伪造。
- [ ] 比较initial/reach/restore实际字体属性与色状态不因scroll变化；actualLayout的screen位置/可见区允许随原scroll改变，不能拿整个Layout JSON强行相等。恢复时仍按原精确scroll/原对象/行框合同，不降级。浅深各自currentTextColor与现warning角色合同核对；背景helper的unknown必须留原数据并标记该项不可签，不能替换成“expected palette”。不得将unknown判全G2/G4完成。
- [ ] 离线反例：错对象/文字、缺actualLayout、frame后新取字体不等target、字号/weight/spacing被改、NaN、把资源色当actual、错主题、typeface未知却伪造家族/逐glyph声明、restore未恢复而只有颜色相同。正例保留same-source同对象和未知glyph声明；现全部旧门槛回归不变。

## 交接与边界

Sol实际diff/两SHA与离线结果→Astra静态审核→root统一构建及普通合批推送，必需CI/评审后合并develop，不微推/force/no-verify。新实际8帧及同帧属性再交Astra关闭该元数据缺口；不重审其他M7图，不凭CI绿称全模块通过。

不改变Toast、产品字体/颜色、业务、设备写入/未知重发；不发版。回退仅新增采证字段/校验，保留旧实图和已接受的可达/恢复结论。当前是批准的补证计划，未执行；原另帧旁证不升级为同帧证明。

## Astra 静态复核（2026-10-23）

本项增量静态通过；承载Legacy test SHA `74374d3eac11cf9c758ffbac4fa19af7d38442a3fa400671f5d0e5f2ca3e938d`，runner `f91a831a7fe848fe379f8ff026e76dd932b73ad4256fb2864f99ba0ff8e38f22`，axis/badge oracle `edb2c831c7f34c1c5802bd6601f98a02034f15268b5b20bb6f1c08ee5fdb8ef8`。shortDetailState原readonly实际TextView中读取同main公开getter、实际Layout及既有颜色helper，无另帧采样；object/semantic/text/尺寸/原layout与geometry互核，实际31.5/42.3px按float32比较，weight400/letterSpacing0/当前主题色及恢复固定metadata检查。未将Typeface identity或资产SHA冒称逐字形字体，fallback glyph继续UNKNOWN；无法解析的背景保留pending，不伪称G4完整。独立6 tests含badge正反例通过。

此结论不等于新同帧运行或G2/G4视觉闭环；等四档新badge PNG/geometry/proof实际核验。承载文件中的Toast/axis阻断仍须修复，不批准整组推送。
