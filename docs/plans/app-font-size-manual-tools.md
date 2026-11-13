# 字号测试工具迁移清单

应用字号独立于系统后，旧工具的“设置 OS font_scale 就能控制应用文字”假设不再成立。不得删除布局/可见性/状态断言来掩盖此变化。

## 本次已迁移

- `scripts/check_mock_lifecycle.sh` 调用 `fontSizeChecks`，验证三档、系统独立、预览、萃取/预热重建和草稿。
- `LanguagePageChecks` 的 wideFont：系统仍设 1.3，应用明确选 LARGE=1.1，分别严格验证两者。finally 恢复原应用偏好；保留全部布局和业务断言。
- `LanguageUiLayoutChecks`、`FourMetricChartChecks`、`LiveNarrowChartChecks` 直接创建 Configuration 的合成 1.3 压力测试没有依赖全局系统字号，不需降档或删掉。
- CI 系统字号快照/恢复工具保持原行为，仍须恢复设备设置。

## 尚未迁移，不能作为本次字号验收通过证据

| 手动入口 | 下游/假设 | 影响及所需迁移 |
|---|---|---|
| `check_advanced_mock_pages.sh` | `AdvancedPageChecks` wideFont 期待 Activity=1.3 | 应显式选择并恢复应用大档，同时验证系统值独立 |
| `check_task_frame_ui.sh` | Home/双杯/曲线库 expectedFontScale=系统值 | 1.3 单元会报不匹配；改为应用档位驱动并分别记录系统值 |
| `check_product_visual_migration.sh` | 传 expectedFontScale/profile | 旧系统1.3不能再代表应用大字 |
| `check_form_visual_migration.sh` / `form_visual_case_aggregation.py` | 参数与 Float32 证据绑定系统1.3 | 需成对更新运行入口和证据校验，不放宽比较 |
| `check_settings_visual_migration.sh` | 矩阵参数/expectedFontScale | 需将应用档位与系统scale分开，保留完整矩阵证据键 |
| `check_scale_visual_migration.sh` | 同上 | 同上 |
| `check_curve_visual_migration.sh` | 同上 | 同上 |
| `check_legacy_visual_migration.sh` | profile后缀与window.fontScale严格相等 | 需证据字段分离，不能简单删除断言 |
| `check_bean_workflow_visual_migration.sh` | expectedFontScale 与系统矩阵 | 需显式设置应用字号并恢复 |
| `check_extraction_history_visual_migration.sh` | expectedFontScale 与系统矩阵 | 同上；曲线坐标和数值断言保持 |
| `check_strict_native_samples.sh` | 系统1.3与参照样本字号/profile绑定 | 原生参照和真实应用档位应明确区分，不冒充同条件 |
| `check_single_screen_ui.sh` | tablet-large-font仅设置系统字号 | 即使不报错也失去应用大字覆盖，应补显式档位选择 |

上述入口多为手动可视验收工具，本次未运行，不声称其适配完成。后续统一通过 Mock instrumentation 设置/恢复应用档位，记录 `systemFontScale`、`appFontSize` 与实际 `Configuration.fontScale`，而非给生产代码加测试后门。
