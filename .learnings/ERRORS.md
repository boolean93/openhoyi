# 开发错误记录

## [ERR-20261006-001] MobileService registration replacement

**Logged**: 2026-10-06T06:26:22.592290+08:00
**Priority**: high
**Status**: resolved
**Area**: backend

### Summary
按begin文本全局替换误命中同函数的Mock分支，删除真实前置门禁；复审和编译在提交前发现。

### Error
首次完整构建mobile:compileDebugKotlin/compileMockKotlin失败；错误分支引用未定义coffeeAddress/current。

### Context
prepareBrew在Mock和真实分支都有brewPreparation.begin。替换搜索首个begin至真实recoveryAfter，错误跨越分支。

### Suggested Fix
恢复整个函数原文，以函数内真实coffeeAddress作唯一锚点，只换begin/arm。自动比较替换前所有前置检查，再读完整diff并编译；不要用首个重复文本定位安全逻辑。

### Metadata
- Reproducible: yes
- Related Files: mobile/src/main/kotlin/io/openhoyi/mobile/MobileService.kt

### Resolution
- **Resolved**: 2026-10-06T06:26:22.592290+08:00
- **Notes**: Mock和真实前置门禁恢复并逐段对照，独立复审无阻塞，最终219任务完整构建/回归/Lint成功。错误改动未提交或安装。

---

2026-10-06 NotificationOwnerTest: added StartContext readback assertion initially used 94.07C idle for validated 91C profile. Existing ±1C studio gate correctly returned null (two fixture failures). Fixed synthetic idle to existing 91.00C sample; did not loosen production gate. Match independent test prerequisites before interpreting null context as ownership regression.

2026-10-06 git push: process stayed live without output; remote API remained previous SHA. Read-only targeted process/socket diagnosis showed git-remote-https TCP CLOSE_WAIT (peer closed), not merely elapsed timeout. Terminated only known task push PID, confirmed session exit143, preserved commit. Next push includes both unpublished changes; never duplicate a push only because observation yields.

2026-10-16 task-first PR metadata: rewriting PR#4 body renamed required headings to synonyms, so edited-event Branch policy38835974921 rejected missing nonempty「验证证据」. Preserved all evidence and restored exact repository-required four headings (scripts/git_policy.py:27) via --body-file. Do not paraphrase machine-validated PR section headings; inspect policy before metadata rewrites. No branch/gate suppression or source rollback.

2026-11-12 shortcut-direct-start local verification: edited production helpers while an initial Gradle compile was running, so later test compilation saw old classes and reported missing new APIs. Waited for that run to end and reran from the final source; focused JVM tests and Mock/AndroidTest builds passed. Keep compilation inputs stable until a verification run completes.

2026-11-12 shortcut toggle verification: local Oracle JDK17.0.7 ARM64 Gradle daemon crashed in C1 OSR compilation of lint UAST getArgumentForParameter (assembler_aarch64.hpp:267, Field too big for insn). Source compilation and selected JVM tests were not the cause. Kept source fixed; reran with one worker/no parallel and transient Gradle JVM -XX:-UseOnStackReplacement; three APK builds and lint succeeded. No global JDK or repository runtime configuration changed. Moved hs_err/replay generated diagnostics outside the worktree; do not commit them.

## [ERR-20261112-DEX] Mock incremental APK missing class definitions

**Logged**: 2026-11-12
**Priority**: high
**Status**: resolved
**Area**: infra

### Summary
An incremental Mock APK built successfully but omitted application class definitions and failed before tests could start.

### Error
`NoClassDefFoundError: io.openhoyi.mobile.AppLanguagePreference` at `Application.attachBaseContext` (reported by coordinator; no test cup started).

### Context
The compiled Kotlin class existed, but the project DEX archive and final APK lacked AppLanguagePreference and Storage definitions. Actual DEX class_defs contained only Selection. A strings search would falsely report class references as presence. Earlier build sessions had overlapped source edits; exact D8 invalidation cause is not established.

### Suggested Fix
Rebuild only this worktree's outputs with clean, --no-build-cache and --rerun-tasks. Keep sources stable throughout. Before reinstall, compare compiled io/openhoyi class files to actual final DEX class_defs (not string references), verify AppLanguagePreference/Storage and compare old APK classes. No user-data clearing or business-code workaround.

### Metadata
- Reproducible: unknown
- Related Files: mobile/build/intermediates/project_dex_archive/mock/dexBuilderMock/out

### Resolution
- **Resolved**: 2026-11-12
- **Notes**: Clean/no-build-cache/rerun-tasks rebuilt Mock successfully. Actual DEX definitions include all 754 compiled application classes and all original APK io.openhoyi classes; 341 instrumentation compiled classes also present. Coordinator retried successfully through a real Mock start/stop cup. Separate portrait capture was invalid due MIUI Activity letterboxing, not a remaining class-loading failure.

## [ERR-20261112-001] repository-entry-and-network

**Logged**: 2026-11-12
**Priority**: low
**Status**: resolved
**Area**: infra

主仓库 main 是旧入口，没有 docs/git-workflow.md/.agents；从 origin/develop 建独立工作树后读取现行规则。sandbox 内 fetch DNS 不可用，经已授权的网络升级后 fetch 成功。未改其它工作树。
