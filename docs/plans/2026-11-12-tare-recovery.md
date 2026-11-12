# Tare recovery and preflight ordering

## Goal
User reported a temporary zero-confirmation block, including an unavailable tare action, followed by recovery. The scale and app version are unknown; this change does not claim the field cause is established.

## Scope
- Preserve the five-second extraction-start intent deadline. A late zero may settle tare but never revive an expired start.
- Use the hub's monotonic sample sequence to recognize a post-success zero delivered in the same clock millisecond. Timestamp-only callers retain their existing conservative behavior.
- Clarify pending, cancelled-start and explicit retry UI; retain no-automatic-retry and actual-zero requirements.
- Add bounded diagnostic transitions and targeted regression coverage. Follow `docs/git-workflow.md`.

## Non-goals / safety
No protocol or tare command changes, no unknown-unit or stale-data acceptance, no changes to normal running-weight timestamp de-duplication, no real-device control or publication. UNKNOWN remains latched until a new successful explicit tare and subsequent valid zero. Mock and host tests do not prove physical scale behavior.

## Verification
Run protocol/session, Bluetooth and mobile focused tests and compile changed modules. Independent Astra review of ordering and UI; preserve cancelled and failed flows as well as success. Hardware acceptance remains unperformed.

## Diagnostic-confirmed DFDF framing issue
A later user diagnostic supplied two notifications: two complete command responses followed by a six-byte abandoned sensor prefix, then a new complete zero-weight frame at the next notification boundary. The old stream joined the prefix with the new frame, emitted a synthetic checksum error, temporarily revoked READY and latched tare UNKNOWN before decoding the valid zero. This is distinct from the initial timing hypothesis.

The repair opts only DiFluid into boundary resynchronization. A replacement must start at notification offset zero, be complete and independently checksum-valid, while the retained incomplete candidate plus incoming bytes must be long enough and fail checksum. A checksum-valid original continuation takes priority, including semantic errors; the replacement goes through all normal unit/weight validation. No arbitrary interior header scanning or invalid-frame suppression is added. A separate diagnostic records resynchronization, not successful tare. This narrow recovery policy cannot mathematically distinguish every possible corrupted continuation from an abandoned prefix; tests cover the known boundaries, not an absolute corruption-proof claim.

## Integration and release boundary
Source: `fix/tare-recovery`, based on develop; target: `develop`. The owner subsequently approved PR creation and merge. Integrate the latest develop without rewriting shared history; create the PR through `scripts/create-pr`, wait for exact-head required checks and resolve reviews before merging. Run the full local verify gate, not only focused tests. Independent Astra review passed for the implementation and safety boundaries. No physical scale verification has been performed.

Only minimal protocol-byte fixtures needed to reproduce framing are included; no original diagnostic archive, user identity, device address, or private Library reference is published. Existing Develop automatic prerelease may run after merge; no manual release/tag or installation is part of this task. Preserve package/signature/version policies. Revert via a new PR if needed; never delete unknown-operation records or retry unknown writes.
