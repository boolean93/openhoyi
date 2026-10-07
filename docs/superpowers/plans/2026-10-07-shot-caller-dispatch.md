# Shot caller dispatch authorization

Goal: revalidate original product request at queued preflight tare and coffee start dispatch. Preserve all wire bytes, observed stop semantics and flow-only timing.

- Capture original hub/address/profile and permitted machine recovery record before arming the shot record.
- Caller predicate must retain original hub, READY product snapshot, exact selected validated profile, no manual shot or ordinary tracker activity, original pending shot address, and unchanged machine intent (empty or previously eligible BREW_WAIT only).
- Do not reject the request's own pending shot recovery; do not require READY preparation after startShot consumes it.
- Add fail-closed callback to ExtractionController with compatible existing entry. Carry it through pending scale zero; evaluate before initial prepare and before both transport writes. Exceptions block without retries.
- Write failing tests for pure policy and real queued transport revocation, then implement, run local full build and independent review. Android actual Service fixture remains a separate explicitly tracked validation step; no device actions.
