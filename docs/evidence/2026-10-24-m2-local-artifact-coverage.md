# M2 本地 form 原件覆盖盘点（2026-10-24）

## 结论

本地已保存的正式 `form-visual-migration/raw-device` 主图只覆盖 `CurveEditorActivity` 的 `tablet-user-landscape-light-font-1.0`。该配置此前已有 Astra 实看记录，本盘点不重复签图。`BeanEntryActivity`、`BrewReviewActivity` 各四配置（共 8 项）及 `CurveEditorActivity` 其余三配置均未见正式主图；当前 form 流程在首个 Editor light/1.0 的 `editor-stage-hidden-values` 重建 IME 严格恢复断言失败后退出，后续配置和页面未进入采集。未产出不等于页面视觉失败。

## 范围与完整性

- 只读扫描 `build/task-first-evidence` 下**现有本地 126 份可读 ZIP**的 central directory，筛选正式 `task-first-evidence/form-visual-migration/raw-device/.../window.png` 与对应 `manifest.json`。共找到 13 份主图副本，配置集合只有 Editor light/1.0。没有下载、重跑或整包解压，也**没有声称覆盖远端全部 artifact**；重复历史 ZIP 没有补出另 11 项。
- a47 原 ZIP：`build/task-first-evidence/ci-a47c870-frame/task-frame-tablet-user-landscape.zip`，98,265,594 B；[原离线诊断](../../build/task-first-evidence/ci-a47c870-frame/m2-offline-diagnosis.md)记 GitHub Actions run `39417690223`、artifact `12263587444`、ZIP SHA-256 `30a9099fa511cd3647720deb0e0272806948afbd65da479712d73ed774157b68`、930 entries 与全量 CRC 通过。PR head `a47c87090240d5b682501d84759d48f0bcf36736` 不等于实际 tested `sourceSha`。
- 58ae 原 ZIP：`build/task-first-evidence/ci-58ae69e/frame-artifact.zip`；[provenance](../../build/task-first-evidence/ci-58ae69e/frame-provenance.json)记 run `39915609523`、artifact `12484913335`、98,188,411 B、ZIP SHA-256 `48a7eac9fcb5d1d39ca51ba8a39393502e77fb001af83ad89ffe3352844b6fc1`、1086 entries 与全量 CRC 通过。

| 原 ZIP / tested `sourceSha` | 正式主图 ZIP member（同目录 `manifest.json`） | 主图完整性与状态 |
|---|---|---|
| a47 / `12de379c8bc98ed1d309d04c8e73edf09b3cae87` | `task-first-evidence/form-visual-migration/raw-device/12de379c8bc98ed1d309d04c8e73edf09b3cae87/tablet-user-landscape-light-font-1.0/curve-editor/window.png` | 237,108 B；ZIP CRC `ea78c7f1`；SHA-256 `04fd099c71affeb9f0cf3aacee678092be3b365f39fcbd81443b804ab5ae3632` 与 manifest `fileSha256` 相符；`initialFrameSync.accepted=true`，但 `layoutStatus=FAIL`、`visualAcceptance=ASTRA_PENDING`。 |
| 58ae / `58ae69ed99c1bbde98311fed24c8bf9c056b9134` | `task-first-evidence/form-visual-migration/raw-device/58ae69ed99c1bbde98311fed24c8bf9c056b9134/tablet-user-landscape-light-font-1.0/curve-editor/window.png` | 236,006 B；ZIP CRC `10a0f4cc`；SHA-256 `2fdcab3a95caf3e26a069724ff20e1c7d5f97485d03b5ae119981a424fd1abc3` 与 manifest `fileSha256` 相符；`initialFrameSync.accepted=true`，但 `layoutStatus=FAIL`、`visualAcceptance=ASTRA_PENDING`。 |

两份 manifest 的 `failure` 都是 `M2 form recreation changed draft/focus/selection/identity/scroll: editor-stage-hidden-values`。`accepted=true` 仅说明同帧采集合格，不抵消业务恢复失败，也不是视觉签收。a47 [离线诊断](../../build/task-first-evidence/ci-a47c870-frame/m2-offline-diagnosis.md)明确恢复前后 IME `true→false`、内容区高 `680→1391`、content scroll `662→0`；字段值和选区一致。

## 图审边界

[M2 计划](../plans/2026-10-18-m2-form-visual-migration.md)的 064、555 记录与[页面矩阵](task-first-page-matrix.md)已明确记载 Astra 实看 Editor light/1.0 正式主图并限定关闭初态裁切；a47、58ae 是同配置后续 source 的失败复现，不列为新的四配置图审候选。ZIP 中 `task-frame-ui/tablet-user-landscape/screenshots/BeanEntryActivity.png`、`BrewReviewActivity.png`、`CurveEditorActivity.png` 属于通用单配置截图，没有相应四配置 form manifest，不能替代正式 M2 矩阵。当前可交 Astra 的新增四配置正式主图清单为空；剩余 11 项仍是未产证据缺口。
