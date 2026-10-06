# Preserve latest active extraction evidence

Root cause: ExtractionController filters age but assigns lastActiveFrame for any fresh valve-open frame. A receivedAt older than the current active evidence can move the 2800 ms idle completion threshold backwards. ExtractionPolicy already preserves latest machine elapsed receipt, so this is inconsistent within the same controller.

1. Add failing tests using actual controller and fake coffee/scale controls. Cover RUNNING, STOP_REQUESTED and OUTCOME_UNKNOWN, fresh older frames (including exact 1500 ms window), strict 2800 ms boundary, blocked restart before completion and valid new shot afterwards. Verify duplicate time and newer active evidence preserve intended behavior.
2. Keep lastActiveFrame at the greatest qualified receivedAt; preserve existing age/start checks, idle confirmation timeout, stop/start frames and callback handling. No general rejection of same-millisecond messages or new serial/firmware claims.
3. Run session regressions, full offline app build/lint because production shared source changed, independent review, and existing automatic cloud workflows. Record actual results and pending handles.

Limits: supplied monotonic receive timestamps only. Cannot distinguish an old BLE payload assigned a new receive time. Not proof of physical valve state, real stopping, firmware or registered Android lifecycle.
