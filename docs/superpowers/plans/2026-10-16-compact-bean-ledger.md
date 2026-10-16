# Compact bean ledger

Approved spec §7 BeanLedger, §8 source fidelity and F6. Continue feature/task-first-ui/develop through draft PR #4, existing develop66ebda9/ed01de0 dependency. Follow docs/git-workflow.md. No merge/release/device install or schema/control changes. Additive revert preserves user events.

Current ledger renders each read-only event as a large card with title spacing and four more lines. Replace repeated raised cards with compact flat rows and separators. Promote event kind/change and after-event balance; combine bean and batch identity into metadata. Full reason remains separate and wraps. Normal consumption is neutral. Keep reverse original event order, optional batch filter, empty/storage-error distinction and exact existing formatGrams/unit resources. No invented timestamp, source association, pagination or row action. Keep bounded inner ScrollView and restoration code unchanged.

Only BeanLedgerActivity layout, this plan and evidence. Reversible layout-only edit uses existing advanced-page language/geometry and actual scroll/recreate/return checks plus business storage invariants. No redundant mirror test. Local Mock+AndroidTest build, then exact CI screenshots and independent review remain mandatory before acceptance.

## Batch detail companion layout

BeanBatch currently shows balance and hides all three dates in Full details, leaving the rest of the work area empty. Migrate only render(): TaskWorkspace primary balance/name and secondary initial quantity/batch/three actual dates, with existing fixed Full details/Ledger/Adjust actions. No changes below adjustment(), preview/recheck/confirm/UUID/commit/recovery. Full long name remains accessible. Dates are batch metadata, not ledger event timestamps. Existing business adjustment/concurrency and native layout tests remain authoritative.
