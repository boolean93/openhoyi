# Mock storage probe fixture coverage

- Scope: extend existing fixed synthetic probe only; dual model slots, compact checkpoint, complete tool transaction, OCR interrupted status and bounded Provider faults.
- Baseline/work branch: existing authorized feature/ai-chat at 599670aab75d94070bf836cb813239e30e3e46e7 (develop-derived); preserve dirty tree. Future PR target develop; no push/PR/merge in this task.
- Files: AiStorageProbeHost.kt, AiStorageProbeProvider.kt, AiStorageProbeScope.kt and new testMock AiStorageProbeFixtureTest.kt.
- Verification: source checks only during implementation; Gradle belongs to coordinator. New fake tests pending execution; actual Android Keystore/Resolver/process restart remains unverified.
- Safety: random dedicated alias/private noBackup run only; fixed nonexported Provider paths, no network/real data/default owners. No deletion or automatic retry. Keep unknown/pending objects.
- Release/signing: no release, APK, install or signing change; no upgrade claim.
- Rollback: remove this incremental source change only, retaining all prior edits and probe objects. No data cleanup.
