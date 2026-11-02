
## [LRN-20261015-UI01] correction

**Logged**: 2026-10-15
**Priority**: high
**Status**: pending
**Area**: frontend

### Summary
单屏几何测试通过，不代表UI好用；不能把每个功能铺成同权重的大按钮。

### Details
用户直接指出当前HOYI UI不合理并要求依据Google/Apple原则重设计。旧实现反复追求控件在屏内，忽略任务流、信息层级、平板导航和曲线语义；大量CI截图检查没有替代设计审阅。

### Suggested Action
先用官方原则审查任务与主次，提交高保真核心场景供用户判断；数据/状态/主操作分别设计。通过真实状态识别和路径检查验收，再用几何/回归保证底线，不把它们当设计质量结论。

### Metadata
- Source: user_feedback
- Related Files: docs/research/2026-10-15-task-first-ui-redesign-draft.md
- Tags: ui, hierarchy, tablet, usability, acceptance
