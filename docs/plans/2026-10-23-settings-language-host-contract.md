# Settings language Dialog host 合同对齐

> 实施者：Sol，按 executing-plans 串行执行；仅本计划范围，不覆盖其他 owner。root 负责统一构建、提交与推送。

**Goal:** 修正 App language-write-rejected 原生非输入 Dialog 的两处 host 假设，保留真实原件与全部协议门禁。

**Architecture:** 只改现 runner 的指定路径分支；不改产品、采证源码、窗口协议或 schema。使用原 ZIP 离线重放证明合同一致，不把条件实验称为视觉通过。

**Tech Stack:** Bash 内嵌 Python verifier、既有 JSON/PNG 原件。

## 范围与依据

沿已授权 `feature/task-first-ui` → develop 批次，遵守 [git-workflow](../git-workflow.md)、[task-template](task-template.md)。读取 `ci-3a78c4c/settings-app-verifier-diagnosis.md`，并实际读取原 ZIP 中该 Dialog proof：四 artifact、acceptedAttempt=1、IME=false；实际文本类型为 Button / CheckedTextView / DialogTitle。生产 `AppLanguagePreferencesCard.choose` 是单选列表+negative 取消按钮，没有 EditText；SAVE_FAILED 保留 Dialog、恢复原勾选并 Toast，不要求控件获得键盘焦点。采证 `dialogSafeControls` 在同帧读取 decor.findFocus，null 原值不是丢字段。

允许：`scripts/check_settings_visual_migration.sh` 中 require_protocol 与 validate_language_dialog_target 的窄调整，及新 `scripts/test_settings_language_host_contract.py` 离线测试。禁止修改生产、SettingsVisualMigrationChecks、NativeWindowFrameEvidence、其他 runner。manifest/Curve 已冻结，不混改。

## 执行合同

- [ ] 先为原两处断言添加失败用例：从原 ZIP 选择读取指定 proof、geometry、safe-controls（不全展开），抽取 runner 实际函数执行，不能复制一个脱离实现的 verifier。先确认旧代码因四角色/null 焦点失败。
- [ ] require_protocol 只对现完整路径 `app/special-states/language-write-rejected` 且 `captureWindowKind=EXPLICIT_WINDOW`、`mappingMode=MEASURED_WINDOW_BUFFER_TRANSLATION` 启用四角色。路径匹配但 kind/mode 不符直接失败；别仅凭 mode 允许所有状态四角色。

```python
expected_roles = {'layer-before','display','display-after','layer-after'} if measured else {'display','display-after'}
assert len(artifacts)==len(expected_roles)
assert {item['role'] for item in artifacts}==expected_roles
```

保留默认两角色合同。measured 每条 attempt 必须等于 acceptedAttempt，role 唯一、文件唯一且为该目录的固定原名：display→window.png，其余→window-<role>.png；实际文件存在且 SHA 与 fileSha256 一致。bitmap 元数据与 accepted attempt 对应同 role 的 bitmap 记录一致（不得拿 layer-before 充 layer-after）。保留 manifest 文件 SHA、原 proof source/window/decor/token/display、S1/S2 sourceRect SDK 平移、ROI/尺寸/target 同源检查；不得只验四角色集合。四角色原图各有意义，不删 layer 文件或改 producer。

- [ ] validate_language_dialog_target 保留同帧 safe==accepted.safeControls、每个 control 对应唯一 accepted node 且去附加字段后精确相等、所有可见/全文/48dp/安全区条件。只在该调用路径允许 null 焦点，实施分支如下：

```python
focused=[control for control in safe['controls'] if 'focused' in control['roles']]
if safe['focusedControlIdentity'] is None:
    assert not focused
else:
    assert len(focused)==1
    assert safe['focusedControlIdentity']==focused[0]['viewIdentity']
```

此函数增加非输入断言 `accepted['imeVisible'] is False`，safe controls 无 isEditText；accepted nodes 的实际 typography.class 符合此原生语言列表上下文（Button、CheckedTextView、DialogTitle），不靠资源常量伪造节点。所有 control roles 非空、无重复、仅 positive/negative/focused；positive/negative 角色必须与 buttonRoles 的实际 identity 双向对应，不能多造一个同角色 control。buttonRoles 精确两项且 role 唯一，只有 negative shown；显示按钮必须 present、身份非null、恰对应一个 shown control。null ID 与 focused role 同时出现、非null ID 无对应实际 node、隐藏焦点、重复身份或重复角色均失败。允许未来实际控件获得焦点时走非null严格分支，不强迫永远null。

窗口焦点与控件焦点分离：`decorHasWindowFocus`、`uniqueFocusedWindow`、accepted.hasWindowFocus、协议两端 hasWindowFocus、callback/owner/token 等保持严格 true/一致；不能为了 null 控件焦点放开任何窗口焦点。其他输入 Dialog、IME/键盘、Machine 焦点合同完全不改。

## 验证及交接

- [ ] 离线反例明确拒绝：缺/重复/额外 artifact role、错 attempt、错固定文件名/复用同文件、SHA 或 bitmap 与对应来源不符；其它 Activity/普通 explicit Dialog 带四角色；指定路径伪装非measured。
- [ ] 焦点用例：原 null+无focused通过；存在真实唯一focused且ID/node一致通过；null+focused、非null无focused、错ID、重复control/node/role、伪negative身份、IME=true、EditText、窗口失焦/归属不符均失败。不得扩展 schedule/legal-input 的规则。
- [ ] 执行 `python3 -m unittest discover -s scripts -p test_settings_language_host_contract.py`、`bash -n scripts/check_settings_visual_migration.sh`、内嵌 Python 语法检查、`git diff --check`。用原 ZIP 重放完整 App 首格原 VERIFY_PY，保存明确“离线复验”结果与输入 SHA；不覆盖原 FAIL_ORIGINAL_FULL_VERIFY。
- [ ] 冻结所属 diff/SHA 交 Astra 静态复核；root 合批构建与普通 PR 推送，不微推、不 force/no-verify、不直推主干，必需 CI 通过才可合并。

## 边界

两处合同冲突并非产品焦点错误的证据；该非输入原生列表允许无控件焦点，但仍须真实窗口焦点唯一。修正后离线 App 通过不等于新 CI/视觉通过；Machine 原异常及其它配置仍独立保留。本计划不改 5 秒、四零差、采样、业务编码/单位/队列/设备写入，不自动重发未知。无硬件验收、无发版安装分发。回退仅 revert 本 runner/test 增量，原失败 ZIP、记录、未知状态不清除。

## Astra 静态终审（2026-10-23）

结论：本两文件窄增量静态通过。已实际读取 runner diff、新离线测试、原输入字段与离线重放报告，核对冻结 SHA；不以此关闭其它 Settings 失败或视觉缺口。

- 四 artifact 仅精确 `app/special-states/language-write-rejected` 路径且 EXPLICIT_WINDOW / MEASURED_WINDOW_BUFFER_TRANSLATION 接受。数量+集合保证角色唯一完整，文件名固定且互异，attempt绑定acceptedAttempt，bitmap绑定相同role的accepted记录，各文件实际SHA仍逐一核。原sourceRect/ROI/窗口归属/target两端/5秒四零差门禁不变；其他分支仍两角色，仅补数量防重复，未普遍允许四角色。
- null控件焦点仅在该专用validator中接受，要求无focused角色；非null仍恰一focused且ID一致。所有control保持唯一实际accepted node及raw精确相等；角色去重、按钮身份双向对应、shown/present、IME=false、非EditText与原生语言列表实际类型均校验。窗口唯一焦点、callback/owner/token及安全区/48dp/全文仍严格；输入Dialog和Machine路径未放宽。
- 离线测试从实际runner AST提取函数及原App验证循环try body，不复制另一个判定器；ZIP输入SHA锁定 `3bbb97e13964d7b116551f8dedf8d5df1c085d94cb05abb4fdb2a8dfbc55d315`。完整App单格body重放覆盖其原manifest、文件引用、协议、特殊态、reachability等；不是整12格矩阵末端成功断言。8测试含多组篡改反例，覆盖角色/attempt/file/SHA/bitmap、路径与mode、焦点/节点/按钮身份/IME/owner。非null正例是合成一致性分支测试，不是另一个真实有焦点帧。
- 已读 `settings-app-host-contract-offline-replay.json`：8测试及原App单格离线重放通过；root亦报告独立8测试与完整构建通过。本审查未重复跑测试或构建，独立 bash -n 和 git diff --check 通过。
- runner SHA256 `befbc7d643231fd6f98332588de5c5d2ffc45f4b25c93da9c03fc12163277990`；test SHA256 `7c5c19225431aa4b4b378720b841572f12be612f0ab4de92e0b21e7304ddb60c`。只追加本计划，未改源码、提交推送。
- 原 FAIL_ORIGINAL_FULL_VERIFY 原件保留；本结论为修正合同后的离线单格与静态验收，不是原CI通过。Machine未知原异常、其他未跑格、实际新CI及视觉验收均保留待办。
