# Develop 更新索引发布（阶段 2）

- 需求与范围：沿用唯一 Product Pages 整站部署入口，自动生成 `updates/dev.json`。仅提供可核验的最新 Develop 测试 APK 元数据；不做 Android 关于页（阶段 3）、下载器、安装或设备控制。
- 来源/基线：`develop` / `78c5391fdf334ba1f7bbc0f36f30335bbaca3a7c`。
- 工作分支：`feature/release-update-index`；PR 目标 `develop`。
- 分工：Sol 实现与测试；Astra 独立审查边界与失败路径；用户决定最终体验。
- 工作流遵守 [Git 工作流](../git-workflow.md) 和 [任务模板](task-template.md)。不直推受保护分支，不修改版本/发布门禁。

## 已完成的阶段 1 证据

2026-11-12 手动 Product Pages [run 43212980392](https://github.com/boolean93/openhoyi/actions/runs/43212980392)，head `78c5391f`，validate/deploy 均成功；Pages `build_type=workflow`。公开首页加 cache-busting 参数的 HTTP 200 响应与该提交的 `site/index.html` SHA-256 均为 `a65e491ee2dd1ab5a056e4c48f7221ee38bf590770b10d7149014c6746fb8cdd`。这是协调者提供的实际验证证据，不冒充本阶段索引已上线。

## 实现与验收

1. `scripts/release_update_index.py` 分页读取固定仓库全部 Releases，按 `dev-N` 数字选最高**公开**版本，要求 prerelease。不使用 stable `releases/latest`，不因为最高版本缺件/校验失败回退低号。
2. 校验 annotated tag 中既有 Develop reservation 与实际 tag commit、固定包名、版本、批准证书。匿名 HTTPS 读取 `distribution.json`、真实 APK、`SHA256SUMS.txt`，复用现有 manifest/schema 与 aapt/apksigner 检查，核对大小、资产 digest 与 SHA-256。`sourceCommitVerified=false` 保留，不能把声明源码当二进制溯源证明。
3. 线上已有 `dev.json` 必须符合本 schema；新索引不能降号，同号不能改变身份/元数据，不能更换签名。初次 HTTP 404 可初始化，网络/权限/畸形 JSON 等其他情况停部署。
4. Product Pages 的非 PR 入口共用串行锁，锁内 checkout **当前 protected develop**，重新读取最新公开 Release，并在验证结束时再次检查最高版本。旧 run 重放不使用旧源码、旧 APK 或触发 run 的 artifact。workflow_run completed 可能包含部分发布成功，不能以整体 conclusion=success 作为刷新条件。
5. Python 与 job 条件双重检查 workflow_run 仓库、head_repository、develop 分支和 publisher workflow 路径；接受发布器 push/manual/workflow_run 入口。PR 仅运行离线 fixture 与静态站点检查，无 Pages/OIDC 写权限。
6. 先完整验证，再原子写入生成目录，上传整个 `site` artifact 并 deploy-pages。源码不存生成 JSON，不引入 PAT，不给下载暴露认证 token。线上回读字节完全一致后才报告索引部署已确认。

索引 schema v1：`schemaVersion`、`channel=develop-test`、`repository`、`packageName`、`versionCode`、`versionName`、`apkFile`、`apkSize`、`apkSha256`、`signerCertificateSha256`、`releaseTag`、`publishedAt`、`releaseUrl`、`downloadUrl`、`distributionUrl`、`checksumsUrl`、`declaredSourceCommit`、`sourceCommitVerified=false`。所有下载/发布 URL 固定 HTTPS GitHub 仓库；客户端仍需校验版本、包名、签名、URL，不能自动安装。

## 验证与发布

- 离线 fixture：数值排序、最高版本不 fallback、缺件/重复/外链、manifest/真实 APK/SHA256SUMS 不一致、发布过程中出现新版本、公开高水位、事件信任、原子文件替换和 CDN 回读重试。
- 本地执行 `python3 -m unittest discover -s scripts -p test_release_update_index.py -v`、`python3 -m unittest discover -s scripts -p test_product_pages.py`，并运行仓库完整非 AVD `scripts/verify-pr-checks full`（CI/脚本改动必须完整 verify）。实际结果随交付记录，不把待执行命令写成通过。
- 本任务不人工发布 APK、不安装、不操作硬件，无蓝牙协议/数据库/未知记录改动。
- 提交前核对完整差异，仅提交本任务文件；普通推送工作分支后用 `./scripts/create-pr` 创建 PR，等待精确头提交 `branch-policy`、`verify` 必需 CI，再由授权流程合并。完整本地检查后也不能免除 create-pr 的最终 clean/head/base 检查。
- 合并后 Product Pages 首次整站+JSON 部署、真实 publisher workflow_run 触发与公开 HTTP 回读属于远端待验证；阶段 3 必须等索引上线证据，不冒称 App 内检查已完成。

## 失败与回退

- API、完整性、签名、schema、公开高水位或生成失败时，上传/部署步骤不会执行，保留当前线上整站。无公开 dev Release 也停部署，不生成伪版本或空索引。
- Pages 自身部署失败由 Pages 保留原部署；部署成功但公开回读暂未确认时，明确标为未确认并可手动重跑，不宣称已自动恢复旧站。CDN 最多等待约两分钟。
- 代码问题走修复/revert PR；网站修复部署仍从当前公开高水位重建索引，不靠恢复旧 APK 或旧 JSON 回退。APK 原发布通道与持久签名不变。
- 不承诺 GitHub Releases 与 Pages 跨服务事务原子性：校验后才出现新 APK 时，本轮可能短暂落后，publisher completed 的下一次刷新会补齐；索引不能早于完整公开资产。

## 线上高水位的适用边界

现站 JSON 仅用于正常部署的高水位比较，不是独立持久防回滚账本。HTTP 404 代表首次初始化，也可能是外部人为删除；本范围不另建状态分支。不要删除最高公开 dev Release，不要重跑本改造前的 Pages workflow 定义，否则可能删除索引并使 404 初始化无法保留历史高水位。合并/启用前需确认旧定义 Pages 任务均已结束；日常修复只重跑新定义任务。

## 本地已取得证据（2026-11-12）

- `python3 -m unittest discover -s scripts -p test_release_update_index.py -v`：9 个离线测试通过（含合法低版高水位、manifest/sums 合法但实际 APK 身份不符）。
- `python3 -m unittest discover -s scripts -p test_product_pages.py`：19 个站点回归通过。
- `git diff --check`、`sh -n scripts/verify-pr-checks`、Ruby YAML 解析、`python3 scripts/git_policy.py branch`：通过。
- 只读真实集成：用已登录 GitHub CLI 的 read token 列出本仓库 Releases；用匿名 HTTPS 读取 dev-94 全套公开附件，以本机 build-tools 35.0.0 执行真实 aapt/apksigner 校验，复用 generator 生成 `/tmp/openhoyi-dev-update-verified.json`。版本 `0.2.0-dev.94` / 94，APK 17,241,551 bytes，SHA-256 `74dafd3096cf9d0afdb0fd2527de6a5fba24fa11e78f0120fb327965de0fa013`，来源声明 `78c5391fdf334ba1f7bbc0f36f30335bbaca3a7c`，证书 `c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54`。annotated tag、reservation、manifest、附件大小/digest、SHA256SUMS 与实际 APK 身份均匹配。现站 index HTTP 404 按首次初始化处理；尚未向 Pages 发布本地生成 JSON。
- 完整非 AVD 本地检查：`JAVA_HOME=$(/usr/libexec/java_home -v 17) ANDROID_HOME=$HOME/Library/Android/sdk scripts/verify-pr-checks full` 已成功（exit 0）。JVM/unit、Debug/Mock 编译/assemble、Android lint 全部通过；主 Gradle `BUILD SUCCESSFUL`（191 tasks）；APK 本地化 6 roundtrips / alpha+mock 各 3169 strings；隔离临时签名 release APK fixture 与 12 项 signing guard 通过，无安装。
- Astra 最终独立只读审查：无阻塞问题；依建议补齐合法低版本高水位、公开 manifest/checksums 合法但实际 APK cert/code/package 错误的 fixture，再次运行上述 9 tests 通过。
