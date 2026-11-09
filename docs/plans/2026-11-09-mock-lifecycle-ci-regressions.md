# Mock lifecycle CI 修复（2026-11-09）

## 背景

`develop` 的 post-merge `Verify Mock lifecycle` 自 11-07 `e4c16a0`（#24 自动连接）起持续失败，导致 Develop APK prerelease 停在 dev-61。两个独立回归被前一个失败掩盖，逐一暴露：

1. **自动连接会话记账写入 `devices`**：`DeviceAutoConnectOwner.refresh()` 在 visibility 回调把 `useSessionId/useElapsed/coffeeAutoSuppressed…` 持久化到 `devices`；Mock 生命周期/语言检查 recreate Activity 后比较 preserved prefs，触发 `IllegalStateException: Display change mutated devices`（`LanguageContextChecks.kt:101`）。
   修复（#35）：`refresh()` 在 `BuildConfig.MOCK_MODE` 直接返回；真实机逻辑不变，测试断言不放松。

2. **反射读取 `MainActivity.bound` 字段**：visible service binding 重构后 `bound` 变成 computed property（只有 getter、没有 backing field），`LanguagePageChecks.kt:265` 的 `getDeclaredField("bound")` 抛 `NoSuchFieldException`，`pageChecks` instrumentation 在第一个语言/主题矩阵前崩溃。
   修复：测试改为先尝试字段、失败再调用私有 `getBound()`，断言含义不变（Home 恢复后 service 仍绑定）。

## 验收

- `Verify Mock lifecycle` 在 develop 合并后复跑通过（AVD instrumentation 全流程，含 landscape/wideFont 页矩阵与 footer 截图校验）；
- Develop APK prerelease 自动恢复出包；
- 不改真实设备行为与业务断言。
