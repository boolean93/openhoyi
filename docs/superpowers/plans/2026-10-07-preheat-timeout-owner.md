# Preheat timeout ownership

A queued cancellation captures current record at explicit request, which is correct for human recovery but wrong for an old automatic timeout. Bind timeout to original registration Ownership, Hub and address before invoking cancellation. Failed/throwing authorization blocks without invoking requestCancel and transitions only the original active preparation token to UNKNOWN; stale/newer preparation token does nothing. Preserve600000ms delay, callback UNKNOWN behavior, record retention, explicit cancel recovery and original bytes.

TDD guarded watchdog overload with token/owner/rearm/exception cases across WAITING and READY. Add host scheduler seam (same Handler delay in production) for detached actual Service fixture: capture exact timeout and invoke real scheduled closure; allow, same-address rearm, missing original Hub in both states (6cases,8fakeWrites,6reloads). No clock/physical timeout claims. Build/review and cloud mock runtime verification required.
