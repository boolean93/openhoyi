# Scale workspace

Approved spec §7 Scale/§9, F7. Continue feature/task-first-ui/develop via draft PR #4 (develop66ebda9 + recorded ed01de0 dependency); docs/git-workflow.md applies. No merge/release/physical install/device action. UI-only additive revert.

Existing ScaleActivity already preserves one service owner and actual telemetry provenance, but large windows still stack a full-width weight strip above timer/tools. Move readout/tare into TaskWorkspace primary; App timer/connect/settings remain secondary local scroll. Keep freshness/battery/activity guard fixed above workspace; capture stays fixed below. For capture entry, capture is sole primary-colored action and tare is secondary. Normal scale page keeps tare primary. No change to stableDose, timer, saved state, renderScale, BLE ownership or callbacks/gates. Preserve visible raw weight/freshness/tare statuses, no inferred beverage yield. Raise status/help text from13 to14sp without shrinking values.

Files ScaleActivity.kt, plan/evidence only. Existing actual scale/capture geometry, localized native pages, standalone tare/session tests and timer state cover functional contracts; local build then exact CI/screenshot review. Independent review pending; green geometry alone is not acceptance.

## Native redraw race evidence

Exact33ab403 run38839838096 passes Chinese/other earlier wideFont cases (including cup dates), then Arabic ScaleActivity fails missing battery TextView.layout. AdvancedPageChecks previously waited for layout in one UI callback and checked it in another while Scale refresh reassigns text every250ms. Check settled state and geometry atomically on the main thread at initial/scroll/restored positions, with existing bounded wait and all geometry assertions. ScaleActivity renders identical strings only when changed, reducing unnecessary layout invalidation; live data changes still update. No exception swallowing or missing-label bypass.

## 竖屏实图修正

9d457ef 的用户平板竖屏实图中，读数卡占据大部分屏高，而计时/连接工具被限制在160dp局部滚动区。新增 TaskWorkspace 的可选紧凑主区高度（默认行为不变），仅 Scale 指定320dp；紧凑模式主区不再无限拉伸，支持区使用剩余空间并按实际内容高布局。横屏双栏、读数64/48sp、固定去皮和捕获、原状态/回调不变。短窗仍至少留48dp支持访问区，主区按预算收缩。保留原文字/按钮几何检查，实际竖屏截图确认；编译不能证明实图已解决。
