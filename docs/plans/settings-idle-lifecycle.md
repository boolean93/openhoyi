# 设置页空闲更新与 Mock 生命周期检查

- 来源 `develop@0ef73df9e21f3619c1093426a255d8a7e3bccbda`，工作分支 `fix/settings-idle-lifecycle`，PR 目标 develop。
- 两次 [推出后生命周期](https://github.com/boolean93/openhoyi/actions/runs/44998510915) 均在设置页的静止 UI 树抓取失败。设置观察者保持 500ms，避免未变化的扫描标题、咖啡机重试标签反复 setText。外观入口使用现行「外观 → 深色」，保留可见／隐藏边数、配置回调和持久化验证。
- 用原生 Settings TextWatcher 跨两个刷新周期验证相同值未重写；继续执行原 Mock lifecycle 成套行为和页面检查。运行 `scripts/create-pr`，等待同精确头的 verify 与 branch-policy，合入后由正常 Develop 推出门禁控制。见[工作流](../git-workflow.md)。
- 没有 BLE、配对、咖啡机命令、热控、持久数据、发布或签名变更。没有真机测试或安装。回退用 revert PR，不清除数据。

- Settings 专属点击从当前同 PID 窗口重新读取有 nonce 的原生树，保留真实点击、活动页、重建和可见性检查；其他页面保持原选择方式。单独记录空闲 Settings 布局和无障碍事件。
