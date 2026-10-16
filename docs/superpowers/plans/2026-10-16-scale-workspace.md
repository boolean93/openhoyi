# Scale workspace

Approved spec §7 Scale/§9, F7. Continue feature/task-first-ui/develop via draft PR #4 (develop66ebda9 + recorded ed01de0 dependency); docs/git-workflow.md applies. No merge/release/physical install/device action. UI-only additive revert.

Existing ScaleActivity already preserves one service owner and actual telemetry provenance, but large windows still stack a full-width weight strip above timer/tools. Move readout/tare into TaskWorkspace primary; App timer/connect/settings remain secondary local scroll. Keep freshness/battery/activity guard fixed above workspace; capture stays fixed below. For capture entry, capture is sole primary-colored action and tare is secondary. Normal scale page keeps tare primary. No change to stableDose, timer, saved state, renderScale, BLE ownership or callbacks/gates. Preserve visible raw weight/freshness/tare statuses, no inferred beverage yield. Raise status/help text from13 to14sp without shrinking values.

Files ScaleActivity.kt, plan/evidence only. Existing actual scale/capture geometry, localized native pages, standalone tare/session tests and timer state cover functional contracts; local build then exact CI/screenshot review. Independent review pending; green geometry alone is not acceptance.

## Native redraw race evidence

Exact33ab403 run38839838096 passes Chinese/other earlier wideFont cases (including cup dates), then Arabic ScaleActivity fails missing battery TextView.layout. AdvancedPageChecks previously waited for layout in one UI callback and checked it in another while Scale refresh reassigns text every250ms. Check settled state and geometry atomically on the main thread at initial/scroll/restored positions, with existing bounded wait and all geometry assertions. ScaleActivity renders identical strings only when changed, reducing unnecessary layout invalidation; live data changes still update. No exception swallowing or missing-label bypass.
