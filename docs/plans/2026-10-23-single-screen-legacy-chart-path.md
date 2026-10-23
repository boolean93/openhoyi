# SingleScreen Legacy Detail 局部语义路径修正

> Sol执行，Astra静态验收；root完整构建/合批推送。不改产品、不覆盖他人增量。

**Goal:** 通用遍历仍不穿滚动容器，仅为Legacy Detail从明确产品层级取得原chart viewport。

**Architecture:** 用通用views里唯一LegacyDetailContentScroll作为入口，逐层验证直接child类型/tag/parent及唯一性，到原workspace.primaryBody的LegacyChartViewport。取得对象后沿用全部原检查。

**Tech Stack:** Kotlin Android instrumentation。

## 来源/规则

依[Git工作流](../git-workflow.md)/[模板](task-template.md)，工作分支feature/task-first-ui，基线588855c96fdceec79bf87f0fd5950f33fa6aff1c，PR目标develop。root原件`ci-588855c/single-user-artifact.zip` SHA`0c31f5392cca9e612cb4d7ec29486499bf63d78169da750d345bcf132ae0bcda`，114803096B/642CRC已由root核；本审实际读`single-user-result.txt`与源码。

SingleScreenPageChecks:78空集合single失败发生在任何viewport/plot断言之前。`visit()`明确不穿ScrollView，产品当前LegacyDetailContentScroll是永久外层局部容器；其child为tag legacy/detail/content的column，里面仍有TaskWorkspaceLayout，其primaryBody直接拥有LegacyChartViewport，chart tag legacy/detail/chart、points仍原shot。不是证明产品plot消失或不可见。SingleScreenListStateChecks:254复用同verify，因此同点修正可覆盖调用链，不另改listRestore断言。

## 仅允许一个实现文件

`mobile/src/androidTest/kotlin/io/openhoyi/mobile/SingleScreenPageChecks.kt` 的 LegacyHistoryDetailActivity分支。原generic visit/visibleFully/列表/其他页面一字不动。

- [ ] 将旧 `views.filterIsInstance<LegacyChartViewport>().single()` 替换为显式路径，具体次序：
  1. `views.filterIsInstance<LegacyDetailContentScroll>().single()`，校验tag `legacy/detail/content-scroll`、attached/isShown/与content同root。
  2. 必须childCount==1，唯一直接child为ViewGroup且tag `legacy/detail/content`、parent===outer；不是findViewWithTag递归找任意同名后代。
  3. 仅枚举该column**直接children**，取唯一TaskWorkspaceLayout，parent===column；不能全树descend或getChildAt猜索引。
  4. 仅枚举`workspace.primaryBody`的直接children，取唯一LegacyChartViewport；校验其parent===primaryBody、tag `legacy/chart/viewport`，原chart.tag==`legacy/detail/chart`。primaryBody须仍是workspace自身公开属性对应的子树，校验parent===workspace。
  5. 继续原`val chart=viewport.chart`及其之后所有断言；不把chart添加generic views让原设计中的长Canvas误套全View可见，也不从后代中选择第一个替代single。
- [ ] 缺/重复outer、错content tag、缺/重复workspace/viewport、错parent/chart tag必须失败并带明确路径消息；不fallback旧全树搜索、不catch后继续、不按文字/坐标找图。
- [ ] 保持原expectedLegacyShot非空/legacyId等式、chart单child/原points完整相等、attached/shown/root/token/display一致、完整变换链、`visibleFully(viewport)`、实际Canvas plot及屏幕交集宽高>=96dp等全部原样。不能新增scroll/reveal使初态检查通过；若寻址修正后出现真实几何失败，保存新首因另审，不能在本任务放宽。

## 验证/交接

Sol先记录旧源static路径不能穿outer的反例及新局部路径diff，检查只动此分支；编译交root统一执行。现有SingleScreen主336配置与listRestore真实仪表是运行验收，不新增镜像源码的无效“测试通过”。可在本地现有Android测试环境验证缺/重复/错tag路径拒绝，但不得为缺环境假称已执行；构建成功不代表320/336变336/336。新CI必须核原16配置与listRestore原fixture，确认越过寻址后原严格几何/采样断言也通过。

提交前Astra核实际diff/SHA及原断言不变；root普通合批推送、PR必需CI/评审后合并develop，不微推/force/no-verify。无产品、设备写入、未知重发或数据变化；不发版；回退只测试寻址增量并保留原件。当前可按单文件范围实施。font snapshot load ACK后ADB timeout outcome unknown/restorationVerifiedfalse属于另一任务，不能作为本项或字体恢复成功依据。

## Astra 静态复核（2026-10-23）

本项静态通过，文件SHA `0fbe9a1dd7291b3db85463d410ae48bb50ee2598c96a779100467ac9a5fda8c8`。实际diff仅将旧全局visitor中的viewport.single替换为显式content-scroll→唯一direct content→唯一direct workspace→其primaryBody→唯一direct viewport；检查tag/parent/root/attachment，未增加全树扫描或把长chart加入全局visibleFully。局部val在check后的ViewGroup类型收窄成立；原chart/points/visibleFully/实际与可见plot96dp等后续断言原样保留。未运行Android，本项不宣称320/336已补齐，待新真实原件。M7其它文件的待修结论独立。
