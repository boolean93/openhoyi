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


## 56d0684：四指标原生图首轮

PR工作流38733280170三个窗口均实际成功：每窗24页Frame/导航检查＋机器三项/四项/断点参考/100dp字号1.3紧凑及恢复检查。下载路径`build/task-first-evidence/ci-56d0684-{landscape,portrait,phone}/`。原生组件PNG由Detached View直接draw，背景原先透明，在黑底查看器显黑；实际App截图是浅色白卡，不将透明PNG当深色UI证据。

实际查看：横屏`chart-components/four-metrics.png`，手机`gaps-and-reference.png`、`short-height-font-1.3.png`及`screenshots/ExtractionActivity.png`。四量纲坐标、真实分段和焦点参考已出现；**尚不接受为最终视觉通过**：

- 原生默认Button灰底偏重；手机4项图例呈3＋1不平衡，点选后应有更明确轻量选中态。
- 常驻3行提示挤压图，手机仅约100dp图线区，说明局部滚动出现半行，虽然可达且测试通过，观感与聚焦不佳。
- 萃取主读数仍是旧时间/压力/重量/流速，未落实时间＋有效出液或注水的层级，副工具仍占两行。F3必须重排，不能以组件green交付全页。

决定：widget明确语义背景、轻量描边图例；窄窗/竖屏最多两列完整自适应行；把hint放读屏和控件语义，常态可见说明压成“各项独立量程 · 本杯采样至…”，必要reference/gap/reset信息保留。随后主页面F3释放图高。短高说明是异常退化，不代替正常窗口完整图。
