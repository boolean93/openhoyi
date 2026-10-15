# Task-first UI 实屏读图记录

## c3a5675：共享框架首轮

证据来自PR#4工作流`38727735636`的`task-frame-tablet-user-landscape`artifact。配置2560×1600、density360、字号1.0、简体中文、浅色；隔离Mock，未操作真实平板。每张均为实际Activity窗口，不是ImageGen图。

本地artifact：`build/task-first-evidence/ci-c3a5675-landscape/tablet-user-landscape/`（ignored）。`result.txt`有24页启动与`TASK_FRAME_CHECKS_PASSED pages=24 noBle=true`；当前主导航/工具路由、目标尺寸、焦点模拟检查已在该窗口实际运行，不证明其他窗口、键盘实机或全部业务。

| 实际截图 | 本次观察 | 处置/结论 |
|---|---|---|
| `screenshots/HomeActivity.png` | 左轨四目的地与底部秤/设置可见；尚未选配方时任务入口明确。主体仍是旧版横铺槽位和按钮，下部大空白；没有新准备工作台阶段预览 | 框架可继续；F3必须重排主体，不接受为全页改版完成 |
| `screenshots/ExtractionActivity.png` | 图占主要区域、左轨可见。实际图仍为压力＋g/s；主读数仍重量/流速，尚未落实四指标；右侧工具仍较拥挤。当前未选曲线，开始禁用 | F2/F3必须替换；此空态不代表RUNNING/STOP_REQUESTED/UNKNOWN已视觉验收 |
| `screenshots/CurveActivity.png` | 左轨选中曲线；连续列表、搜索、分类与旧版入口仍在。右侧尚未选择曲线为空白详情，按钮与分类占高，曲线预览尚弱 | F4继续任务化改版与真实曲线缩略图；本批不删功能 |

这些观察只支持框架阶段进展。其余21页内容、满配/无秤/断采样、深色/大字号、真实IME和控制状态仍需后续实屏验证。
