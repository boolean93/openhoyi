# M7 host轴坐标：按真实Float来源重建

## 仓库与交付边界

遵守 `docs/git-workflow.md` 与 `docs/plans/task-template.md`。当前source `2f784ec502b8d0651e02e1de8cc86db8714d4ac2`，沿用从develop派生的 `feature/task-first-ui`，经现有PR #4合入develop；本批是普通增量合批，不直接推main/develop，不强推、不改共享历史。根统一构建/提交/推送，必需CI及未解决失败仍需通过，不将本地host通过等同PR全过。

本次仅host判定恢复真实数值类型，无协议、库存或硬件写入；不授权发版、标签、安装、设备操作或用Mock代替实机。若需回退，以新提交撤销本脚本增量或明确revert PR，保留原件和失败记录，不重置共享历史、不卸载清数据。

## 1. 决策与范围

只改 `scripts/test_legacy_history_axis_contract.py` 的屏幕坐标重建及对应有限测试。不是产品布局修改，也不是允许微小误差。沿用冻结设计、原可见性/恢复门槛，无需新Android构建或设备动作。Sol负责该脚本；共享树其他改动保留，root统一合批。

实际证据 `build/task-first-evidence/ci-aac5c91/m7-axis-first-failure-brief.md`：dark1.0/history initial tick0 的glyph y=535.928、origin y=692，而screen y=1227.927978515625。原`LegacyShotChartView.kt:164`将Float算出的glyph放入JSONArray；`LegacyVisualMigrationChecks.kt:617–622`在同一内存对象上getDouble取出Float真实值，再加整数原点。JSON短十进制重读为Python double后直接求和，错误地换了数字来源。四配置240坐标中120个此类十进制不等，按Float32重建全等；无真实像素偏移证据。

## 2. 唯一修正

复用已有struct导入，必要时加小型局部/helper表达式：`struct.unpack('>f', struct.pack('>f', value))[0]`。仅把来自原glyph Float的四个box坐标恢复为float32、再在Python double中与整数origin相加，最后仍用原 `screen == expected` 精确比较。

不能把最终screen也转Float32，不能把求和结果再舍入Float32：生产端是在getDouble之后求和。不能round、epsilon/isclose、扩大clip、删wholeGlyph或跳过任何tick。原glyph与canvas逐项相等、矩形正宽高、requestRect floor/ceil、固定owner/plot/detail/action、reached严格clip和restored完全相等全部不改。没有证据要求修改其他脚本或JSON生产端。

## 3. 有限验证与交付

1. 原合同测试全过；补一份来自上述真实数值的合法序列化Float样本，经JSON round-trip后仍通过。保留原整数样本。
2. 同一样本只改screen一个分量为相邻double（`math.nextafter`），必须仍失败；说明不是以Float量化screen吞掉真实差异。原outside-clip等反例仍失败，不新增场景矩阵。
3. 仅定向读既有ZIP四份history axis及对应initial/reached/restored目标，执行同一个validate函数，不另写只比较坐标的替代验收。若其它既存严格门槛随后失败，保留其首错，不顺带扩大修复。
4. 记录脚本SHA、实际四配置结果、原件SHA/member；`git diff --check`。Astra窄审后root合批，不重建未变APK，不伪称整M7视觉通过。

旧reader-drains 12份/68条、Toast lifecycle12份/56操作已越过旧OOM且原10秒不变，仅作为运行边界；不把这些记录签为12页G1–G4。原5秒四零、宿主设置恢复及其它模块失败保持独立。

## 4. Astra增量静态与原件验收（2026-10-24）

脚本冻结SHA256 `25ecaeb9829ba3d391dba742f7add2dd45928e29357c5550dfd84676ce137cc8`，原SHA `a2cbeaaacfd268b32a641c3a1311aed7b31d0cff5eca0e75aa0eeb97fd451fca`。已核完整diff：仅glyph来源四值恢复Float32，随后原整数origin在double中相加，screen仍精确相等；screen/结果未量化、无epsilon，原clip/wholeGlyph/requestRect/恢复断言未变。新增真实535.928与origin692的JSON往返样本、相邻double screen必须拒绝；未改Kotlin或截图生产/断言。静态无阻断。

Astra独立运行 `python3 scripts/test_legacy_history_axis_contract.py`：6 tests OK；包含原反例与nextafter拒绝。再按 `ci-aac5c91/m7-host-axis-float32-offline.json` 所列member定向读原ZIP，逐个核SHA，以manifest.window.mainHistoryAxis、reached/restored geometry.mainHistoryAxis传入同一 `validate`：

| 原profile | 独立validate结果 |
|---|---|
| tablet-user-landscape-dark-font-1.0 | PASS |
| tablet-user-landscape-dark-font-1.3 | PASS |
| tablet-user-landscape-light-font-1.0 | PASS |
| tablet-user-landscape-light-font-1.3 | PASS |

原件索引记录SHA `561e70c48bf63395402437283d2808c0e8f11963ca42b50bc288227391ad05d1`，源ZIP SHA `60a6f4564522fe707f85103dac3dc174a94725802841c9d68c15ecc0f0b7d512`。这是四份既有原件的host轴合同接受，不是新Android运行、整M7或12页视觉验收；后续严格失败仍独立。`git diff --check` exit0。仅Python变化无需重建未变APK，可随本批普通合批；本轮不追加范围。
