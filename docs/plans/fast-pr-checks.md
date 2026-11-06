# 快速 PR 门禁与 AVD 分层

## 需求与范围

- 需求：把 AVD 从日常 PR 门禁完全移出，PR 只保留稳定、轻量的 `branch-policy`、`verify` 两项必需检查；AVD 改为合并后/手动证据，同时不削弱 Develop 自动测试 APK 的同 SHA 发布门禁。
- 范围：PR 触发器、`verify` 路径分类与快速检查、`lifecycle`/`upgrade` 及 visual workflow 触发方式、`integration.json` 必需 context、规则文档/模板、静态 workflow 回归测试。
- 不范围：管理员强合、关闭全部保护、修改签名/版本/账本/审批、设备操作、自动发版/安装、修改仓库其它任务文件、修改当前 OPEN 的 PR #21。

## 来源分支/基线提交

- 来源：`origin/develop`，基线 `78ff1f0`（`feat(extraction): show scale-derived cup flow in live chart (#20)`）。
- 独立工作树：`/Users/boolean93/workspace/hoyi_app/openhoyi-fast-pr-checks`。
- 工作分支：`chore/fast-pr-checks`（从 `origin/develop` 新建，不改原 `openhoyi-product-pages` 工作树）。
- PR 目标：`develop`。

## 设计

### 1. PR 必需检查

- 仅保留 `branch-policy` + `verify`，job context 不变。
- `branch-policy` 保持现有 PR 路由/规划检查；`verify` 保持 job id `verify`，不使用 workflow 级 `paths`/`paths-ignore`，避免必需 context 永久 pending。
- 远端 ruleset 只从 required checks 移除 `lifecycle`、`upgrade`；保留 pull_request、deletion、non_fast_forward、review 设置、`strict_required_status_checks_policy=true` 等其余约束。

### 2. `verify` 路径分类（fail-closed）

严格快速 allowlist：

- docs：`docs/**/*.md`（`docs/releases/**` 全部除外，即使 Markdown）、`README.md`、`AGENTS.md`；`docs/` 下的脚本、JSON、图片、HTML 等非 Markdown 文件不属于快速 allowlist
- site：`site/**`、`scripts/test_product_pages.py`

规则：

- PR 的完整 `base...head` diff 中所有路径都在 docs allowlist → 只跑文档快检、classifier/workflow 静态回归，不 setup Android/Gradle。
- 所有路径都在 site allowlist → 只跑 `test_product_pages.py`、classifier/workflow 静态回归，不 setup Android/Gradle。
- docs + site 混合 → 跑两者快检，仍不 setup Android/Gradle。
- 任一未知路径（含 app/code/CI/workflow/脚本/根文件）→ 完整非 AVD 验证：保留现有 Python/localization 测试、Gradle JVM/unit/compile、资源校验和 release signing guard。
- 空 diff、分类异常、base/head 缺失、浅克隆、git diff 解析失败 → 完整非 AVD 验证，不 fail-open。
- 处理重命名/删除：`git diff --name-status -z --find-renames` 同时纳入 rename 旧/新路径；删除路径仍参与分类。fork PR 使用事件中的 base/head SHA；checkout 使用 `fetch-depth: 0`，不可用则回退完整验证。
- 路径按 `git -z` 解码后的真实仓库相对路径原样分类：不 `strip()`、不把反斜杠改成 `/`、不裁剪 `./`；含控制字符、首尾空白、反斜杠、绝对路径、`..` 等非规范路径视为未知并回退完整验证，且不丢弃该未知路径。`GITHUB_OUTPUT` 的 `reason` 清除 CR/LF 等控制字符后再写出。
- `push`（含 develop/main/release/hotfix）与 `workflow_dispatch` 永远完整验证，保证 Develop 自动预发布看到的 `verify` 不是 PR 轻量成功。

### 3. AVD 分层

- `lifecycle`/`upgrade`：删除 `pull_request` 触发，保留 `push` 到 `main`、`develop`、`release/**`、`hotfix/**` 及 `workflow_dispatch`。合并后运行，不阻塞 PR；仍作为 Develop 自动预发布同 SHA 四项 push 证据。
- visual workflow：删除 PR 触发，改为 `workflow_dispatch`；不删除任何测试脚本、断言、截图/证据上传。
- 日常 PR 不运行任何 AVD。

### 4. 发布保障（不改变）

- `scripts/develop_release_policy.py` 的 `REQUIRED_WORKFLOWS` 保持四项：`branch-policy.yml`、`verify.yml`、`mock-lifecycle.yml`、`mock-upgrade.yml`。
- 控制器继续要求同 SHA、同仓库、develop 一阶 push、最新 attempt 成功、精确 job 名且不得 skipped；PR/fork/tag/手工 CI 成功不能替代。
- 本任务不修改自动发布签名、版本分配、持久账本、审批范围、启用变量，也不触发发版/安装。

## 改动文件与步骤

1. 新增本规划。
2. 重写 `.github/workflows/verify.yml`：路径分类 + docs/site 快速分支 + 完整分支保留原命令。
3. 新增 `scripts/pr_verify_scope.py` 与回归 `scripts/test_pr_verify_scope.py`。
4. 新增 `scripts/ci_workflow_policy.py` 与静态回归 `scripts/test_ci_workflow_policy.py`。
5. 修改 `.github/rulesets/integration.json`：required contexts 改为 `branch-policy`、`verify`，其余不动。
6. 修改 AVD workflows 触发：`mock-lifecycle.yml`、`mock-upgrade.yml`、`advanced-mock-pages.yml`、`bean-workflow-visual-ui.yml`、`curve-visual-ui.yml`、`extraction-history-visual-ui.yml`、`legacy-visual-ui.yml`、`scale-visual-ui.yml`、`settings-visual-ui.yml`、`single-screen-ui.yml`、`task-frame-ui.yml`。
7. 同步 `docs/git-workflow.md`、`docs/plans/task-template.md`、`docs/develop-apk.md`、`docs/plans/develop-apk-publishing.md` 的门禁描述。
8. 本地非 AVD 测试、PR、等待 CI；不合并、不改远端 ruleset。

## 推送与合并

1. 检查工作树 → 本地非 AVD 验证 → 普通推送 `chore/fast-pr-checks`。
2. PR 到 `develop`，正文写明迁移次序、AVD 审计、发布保障和未验证范围。
3. Lead Astra 独立复核后执行迁移。

### 迁移次序（关键）

当前远端 ruleset 仍要求四项 context。若先合入本 PR，PR 本身不会再产生 `lifecycle`/`upgrade`，会被旧 ruleset 永久阻塞。因此执行顺序必须是：

1. Lead 审核本 PR。
2. 管理员按下方计划更新远端 `integration` ruleset，required contexts 改为 `branch-policy`、`verify`，并读回核对。
3. 确认本 PR 的 `branch-policy`、`verify` 是最新 attempt 成功且 strict up-to-date 满足。
4. squash 合入 `develop`。
5. 观察 develop push 的四项 workflow：`branch-policy`、`verify` 完整执行，`lifecycle`、`upgrade` 合并后运行；这是同 SHA 发布证据，不用 PR 轻量结果替代。
6. 后续 PR 只等待两项必需检查。已存在的其它 PR（含 #21）不由本任务改写。

## 验证证据

- `python3 -m unittest discover -s scripts -p test_pr_verify_scope.py`
- `python3 -m unittest discover -s scripts -p test_ci_workflow_policy.py`
- `python3 -m unittest discover -s scripts -p test_product_pages.py`
- `python3 -m unittest discover -s scripts -p test_git_policy.py`
- `python3 -m unittest discover -s scripts -p 'test_develop_release*.py'`
- `python3 -m unittest discover -s scripts -p test_apk_distribution.py`
- `python3 -m unittest discover -s scripts -p test_mock_upgrade.py`
- `python3 -m unittest discover -s scripts -p test_mock_ui_tap.py`
- `python3 -m unittest discover -s localization/tests`
- `python3 localization/validate_catalog.py`
- `python3 localization/android_resources.py --check`
- 静态 workflow/规则校验：`test_ci_workflow_policy.py` 断言所有 AVD workflow 无 PR 触发、lifecycle/upgrade 保留 push、必需 context 精确、verify 无 paths、发布控制器四项/最新 attempt 约束未变。
- 不运行 AVD、不构建 APK、不安装、不发版。

## 协议与设备风险

- 无 BLE/协议/固件/咖啡机/秤控制改动，无设备操作、无安装、无数据迁移。
- CI 门禁变更只影响 PR 检查选择；AVD 证据延后到合并后/手动，不能把 PR 的轻量成功当作视觉/生命周期/升级验收。
- 发布风险由“同 SHA 四项 push workflow + 最新 attempt + 精确 job”保持不变；`verify` 在受保护分支 push 仍完整执行并保留 signing guard。

## 发布

- 不发版、不打标签、不发布 APK、不安装。Develop 自动测试预发布沿用既有授权与配置；本任务不触发、不修改其签名/版本/账本/审批/启用状态。治理 PR 合入后若既有控制器按授权运行，属于既有发布流程，不由本任务宣称发版成功。

## 回退

- 代码回退：revert 本 PR 的 workflow/脚本/文档/规则 JSON 改动，恢复 PR 触发与四项必需 context。
- 远端回退：按更新前读回的 ruleset JSON 原样 PUT 恢复四项 context。
- 不删除测试脚本、不清除已有 CI/设备证据、不卸载、不清数据。

## 未完成与阻塞

- 远端 ruleset 未修改（按用户要求留给 Lead 审核后执行）。
- PR 未合并；本任务只普通推送并创建 PR。
- 未运行任何 AVD/Android 构建；本机或云端 AVD 结果不在本任务证据范围。
- 需要 Lead Astra 独立复核，不以本实现者自查替代。

## PR 触发器 AVD 审计

| workflow | 当前 PR 触发 | 是否 AVD | 处置 |
|---|---|---|---|
| `branch-policy.yml` | 是 | 否 | 保留，必需 context |
| `verify.yml` | 是 | 否 | 保留，路径分类；push 永远完整 |
| `product-pages.yml` | 是（paths） | 否 | 保留，非必需站点检查 |
| `mock-lifecycle.yml` | 是 | 是 | 删除 PR；保留 push/manual |
| `mock-upgrade.yml` | 是 | 是 | 删除 PR；保留 push/manual |
| `advanced-mock-pages.yml` | 是 | 是 | 改 manual |
| `bean-workflow-visual-ui.yml` | 是 | 是 | 改 manual |
| `curve-visual-ui.yml` | 是 | 是 | 改 manual |
| `extraction-history-visual-ui.yml` | 是 | 是 | 改 manual |
| `legacy-visual-ui.yml` | 是 | 是 | 改 manual |
| `scale-visual-ui.yml` | 是 | 是 | 改 manual |
| `settings-visual-ui.yml` | 是 | 是 | 改 manual |
| `single-screen-ui.yml` | 是 | 是 | 改 manual |
| `task-frame-ui.yml` | 是 | 是 | 改 manual |
| `mock-ui-screenshots.yml` | 否 | 是 | 已 manual，保留 |
| `font-scale-snapshot-probe.yml` | 否 | 是 | 已 manual，保留 |
| `develop-apk.yml` | 否 | 否 | 保留，发布控制器 |

## 远端 ruleset 更新精确计划（审核后执行）

只读现状（2026-11-07 读取）：

- ruleset id `24759469`，name `OpenHOYI integration branches`，target `branch`，enforcement `active`，bypass 为空。
- include：`refs/heads/main`、`refs/heads/develop`。
- rules：`deletion`、`non_fast_forward`、`pull_request`、`required_status_checks`。
- required contexts：`branch-policy`、`verify`、`lifecycle`、`upgrade`；`strict_required_status_checks_policy=true`；`do_not_enforce_on_create=false`。
- pull_request 现有参数含 `allowed_merge_methods=[merge,squash,rebase]`、`require_extra_approval_for_unattributed_changes=true`，更新时必须保留。

执行计划（不在此任务执行）：

1. 读回并保存当前 ruleset JSON：
   `gh api repos/boolean93/openhoyi/rulesets/24759469 > /tmp/integration-ruleset.before.json`
2. 从 GET 结果只取 `name`、`target`、`enforcement`、`bypass_actors`、`conditions`、`rules` 构造 PUT body（不发送 `_links`、`id`、`node_id`、`source`、`created_at`、`updated_at` 等只读字段），只把 `required_status_checks` 的 contexts 改为 `[{"context":"branch-policy"},{"context":"verify"}]`；保留 `pull_request` 的 `allowed_merge_methods`、`require_extra_approval_for_unattributed_changes` 等现有参数；生成 `/tmp/integration-ruleset.target.json`。

   ```bash
   python3 - <<'PY'
   import json
   from pathlib import Path
   before = json.loads(Path('/tmp/integration-ruleset.before.json').read_text())
   body = {key: before[key] for key in
           ('name', 'target', 'enforcement', 'bypass_actors', 'conditions', 'rules')}
   for rule in body['rules']:
       if rule['type'] == 'required_status_checks':
           rule['parameters']['required_status_checks'] = [
               {'context': 'branch-policy'}, {'context': 'verify'}]
   Path('/tmp/integration-ruleset.target.json').write_text(
       json.dumps(body, indent=2) + "\n")
   PY
   ```
3. `gh api --method PUT repos/boolean93/openhoyi/rulesets/24759469 --input /tmp/integration-ruleset.target.json`
4. 读回核对：
   `gh api repos/boolean93/openhoyi/rulesets/24759469 --jq '{name,enforcement,bypass_actors,conditions,rules}'`
   预期：仅 contexts 从四项变两项；deletion/non_fast_forward/pull_request/strict 及其参数不变；bypass 仍为空。
5. 若读回不一致，立即用 `/tmp/integration-ruleset.before.json` 的 `name/target/enforcement/bypass_actors/conditions/rules` 重建 PUT body 回滚，停止合并并报告。
6. 远端更新成功且 PR 两项检查最新通过后再合并；不要先合并再改 ruleset。

仓库内 `.github/rulesets/integration.json` 随本 PR 同步为两项 context，作为可审查配置；JSON 存在不等于远端已启用，必须读回。
