# CI page owner migration recovery

- Scope: repair two stale MachineSettingsActivity reflection references in LanguagePageChecks. Controller now owns service and settingTabs; keep exact service identity and all five section assertions.
- Source: develop-derived feature/task-first-ui, b8bfff8a94f620f2298565a042eff89d98390f16; isolated clone, fix/ci-page-service. Existing PR #4 targets develop; parent decides normal append/cherry-pick and push. No push or merge here.
- Evidence: PR lifecycle run41176081044 artifact13033129232 reaches LANGUAGE_PAGE_START landscape zh-Hans dark=false page=MachineSettings, then NoSuchFieldException service. Idle/running geometry, navigation, shell lifecycle, audio and language business pass before this point.
- Verification: ANDROID_HOME=/Users/boolean93/Library/Android/sdk ./gradlew :mobile:compileMockAndroidTestKotlin --offline --no-daemon: BUILD SUCCESSFUL, 47 tasks; git_policy.py branch and git diff --check pass. Android runtime matrices remain required CI evidence. No physical ADB operations while separate install runs.
- Audit: full androidTest reflection and retired scroll-tag search; SettingsVisualMigrationChecks already routes Machine fields through controls and AppSettings through active Fragment. TaskFrame machine-settings-scroll.txt is an evidence filename, not a retired selector. Other direct reflection fields belong to forms, not migrated settings owners.
- Protocol/device/signing/upgrade: test-only access migration; no production change, wire writes, threshold/timeouts or matrices changed. No release/install/clear-data.
- Rollback: revert this commit; preserve device records and shared history. See ../git-workflow.md.
