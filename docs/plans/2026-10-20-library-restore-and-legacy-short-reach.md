# 643 原件：Library 恢复基线与 Legacy Detail 短窗入口窄修

## 范围与结论

来源为当前 `feature/task-first-ui`（643d828），后续合批 PR → `develop`；无发布、签名、升级、设备操作。只由既有 owner 改 Curve/Legacy 两组测试与 runner，不改产品。回退为本窄补独立 revert，不撤销其他改动。

证据：`build/task-first-evidence/ci-643d828-curve-push/diagnosis.md`、`ci-643d828-legacy-push/diagnosis.md`。保留原失败及独立 font cleanup FAIL，不追认原图为完整通过。

## 1. Library：合法交互后的精确恢复基线

已证 `long-factory` 初始 detailRequested=false；恢复真实 ListView 点击必调用 show，使其 true。宽屏 detailBack 是 GONE，真实 Back 会退出，不能隐藏点击、反射 showBrowser、直接赋字段来造恢复。

最小修法：每个独立特殊 case 保存 before 之前，保留启动 false 的原图及同次状态（不能用准备后的图替代，也不减少主态初始验收），再用现有 librarySelect 真实点击当前原选中项，待原布局稳定，保存 prepared baseline。明确两者不同；恢复目标是该真实交互后的基线，不声称恢复未经交互的启动态。已有主 scope 不变，不增场景、不改105模型集合。

基线仍包含原选中 ID、query、position/offset、detailRequested、两 scroll、Window/Activity identity、prefs/store/service。恢复时保原真实选择与精确还原、完整相等断言；逐项 actual/expected 或 mismatch names 写入 case，使首因可识别，不弱化复合条件。compact、短窗先恢复活实例再按真实 Back 退出的既有合同不变。

runner 验证 preparation 是真实选中原 ID，prepared detailRequested=true，restored 全字段等于 prepared；保留启动 false 原值作为来源说明，不能只改 expected 常量。

## 2. Legacy Detail：短窗全文入口真实滚达

原 accepted 初图 action rect `[252,1971,2488,2079]`，root `[0,485,2560,1115]`，字一行无省略；这是窗外未滚达，不是文字宽度不足。产品 TaskWorkspaceLayout 在短 stacked 窗口已有 supportingScroll，原固定断言在滚动前检查 whole 不成立。本修正仅适用已批准的极短 stacked 整个 supporting 区域可滚回退；正常 split pane 的固定动作首屏完整要求不变。若实测该窗仍处于 split pane，则此规则不适用，保留产品布局 FAIL，不用 scroll 回避固定动作要求；报告与 runner 必须核实际布局模式。

保留短窗初图与真实 chart >=96dp 检查。对同一 full-notice 原按钮调用 requestRectangleOnScreen，经原 hardware commit/同5秒协议采独立 reachable 图；从同次 accepted target 验证真实屏幕 rect、文字 Layout/零省略/完整卡、原最小触控尺寸与对象 identity。不得用采后重新读 UI 替换同次 geometry。不能实际滚达则 FAIL，不以调用返回值充成功。

保存所有实际受影响 scroll 的 identity/x/y；finally 先恢复 scroll 和原 Window，稳定后采 restored 图，核精确坐标、原永久图表/列表身份及业务不变量。保首错，恢复/写盘异常 suppressed。仅原短窗 Detail 分支增加 reachable/restored 原件及相对路径+SHA引用；不把完整 geometry/proof 再嵌 summary。runner 必须逐份读取并核原协议/模式、同次 geometry 绑定、目标 whole、恢复相等，不只验文件存在。

## 验证

两组静态审 → 唯一 owner 统一 AndroidTest 编译 → 原固定 CI scope。真实原图另做限定验收；本方案不承诺下一 CI 必过，不新增状态，不放松 whole/字体/96dp/48dp/5000ms/四零差。

## 实施静态终审

Astra 首审发现 Legacy 将 originalScrolls 保存于 resize 之后，会把短窗状态冒作原窗恢复基线；已要求并核实修正为 resize 前同次默认窗 original capture，保存真实全部 scroll identity/x/y，并校现场状态等于 accepted target。finally 顺序为恢复原Window→稳定→恢复原scroll→稳定→独立restored capture。runner 回读 original/initial/reachable/restored，各自 SHA、同次 geometry、协议模式与身份绑定，恢复比较 original 而非短窗 initial。

Curve 保留 startup false 同次原图与状态，真实 ListView 选择原item后 prepared true 基线；原启动到准备的变化明确，不假称恢复false。prepared/restored逐字段相等且host从原件重核；原主scope/业务不变。Legacy split 仍要求初态固定按钮 whole，stacked 才真实滚达，whole/48dp/96dp/5秒门禁不减。此四文件静态无剩余阻断，可合批构建；不代表真实UI恢复已经通过。

最终 SHA256：

- Curve Kotlin `eddf4b0d6e5365fd281d22b4788e3faeccd05022231b9d32d1b3085f0c4465e9`
- Curve runner `4de82e34fd61fffd7ef69f5800b6ba4a19f0b5693638eb8db58c15c0af44825c`
- Legacy Kotlin `203c74f8aaebe336ca08321d8b259807f09a10b2e9e6c4e94e44b49b29e9559b`
- Legacy runner `8014868bf6942df4b4ce16015795d5a1b8d9ad6579ef4a8c90dce1e442a10538`

独立 `bash -n` 两runner与 `git diff --check` 通过；Gradle由唯一owner统一执行，实际原scope CI另验。

Curve runner 最后两行差异另核：真实选择返回的中间 list 不再与稳定后的 prepared 全对象相等，仅核真实 adapterItemId/selectedId 与启动选中ID一致；稳定后的 prepared/restored 全字段相等仍严格保留。此为中间态与稳定基线区别，不放松恢复，最终hash以上述4de82e34为准。
