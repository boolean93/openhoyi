# 用户范围变更：仅横屏验收

用户2026-10-16明确：“可以不用兼容竖屏了。ROI太低”。本变更覆盖旧规格/概念稿/路线中的竖屏适配要求；21页功能和横屏任务目标不缩减。优先用户平板2560×1600/density360，保留一般横屏和1.3字号、横屏IME/短高、主题/语言/原业务和安全检查。既有窄屏回退可保留，不再单独优化或作为交付门槛；本次不修改manifest强制锁屏方向。

沿用develop66ebda9+ed01de0依赖，feature/task-first-ui→develop draft PR#4，遵守docs/git-workflow.md。修改规格/路线和默认CI配置：single-screen只保留三个横屏profile；task-frame优先用户平板横屏（内部仍测两字号/短高组件）；advanced和lifecycle页面矩阵从竖屏compact改横屏landscape，业务流程也横屏运行；生命周期后台resize保留但改为横屏之间变化；旧Capture脚本不再执行竖屏尾段。协议/状态/库存/升级/文件对话框/幂等断言不删除。历史竖屏profile可手动诊断，不是持续验收项。

本地shell语法检查、Mock APK/instrumentation构建，精确新CI尚待执行。旧源仍在运行的任务不取消；完成后推送下一批，避免打断已有证据。不发版/安装/合并，不改签名/版本/schema。普通push工作分支；追加revert回退配置，保留用户记录。所有减少的默认竖屏任务基于本次用户指令，不因已有测试失败临时绕过横屏门禁。
