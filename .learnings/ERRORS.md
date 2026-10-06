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
