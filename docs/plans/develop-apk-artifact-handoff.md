# Develop APK 单产物传递修复

- 需求与范围：首次真实云端运行完成签名构建，但 publish 将实际存在的单产物误判缺失，workflow 绿色却没有公开 APK。仅修复控制器对下载目录的识别及无可发布产物时的失败状态，保留全部签名、版本、CI、lease、标签和三附件核验。
- 来源分支/基线提交：`develop` / `3503082ec1dad8ff87017a4b029b5df9eea0b777`。
- 工作分支：`fix/develop-apk-artifact-handoff`。
- PR 目标：`develop`，遵守 [Git 工作流](../git-workflow.md) 与 [任务模板](task-template.md)。
- 改动文件与步骤：`scripts/develop_release.py` 兼容单产物根目录与多产物命名子目录；匹配精确计划版本/源码后仍调用完整实际 APK 发布核验；增加最小离线回归；更新本计划记录证据。
- 推送与合并：本地回归及真实 Astra 独立复核 → 普通推送 → PR → 四项必需 CI 通过 → 正常 PR 合并；不修改 workflow/保护，不使用 admin/force 或直接推送集成分支。
- 真实失败证据：[运行 42219402124](https://github.com/boolean93/openhoyi/actions/runs/42219402124) 的 build 成功；artifact `develop-apk-1-54` 包含根目录 APK、`distribution.json`、`SHA256SUMS.txt`；download-artifact v7 日志实际解压到 `packages` 根目录（即使 `merge-multiple=false`），publish 却查 `packages/develop-apk-1-54` 并输出 `Build 54 unavailable`。不能把此 workflow success 算作发包成功。
- 验证证据：`python3 -m unittest discover -s scripts -p 'test_develop_release*.py'` 本地19项通过（包含新增3项及其代表性子场景），`python3 scripts/git_policy.py branch` 与 `git diff --check` 通过。覆盖根目录单产物、命名子目录、多计划仅一个成功产物、错误身份/重复布局拒绝、完全无产物不再绿色退出；复用既有发布安全回归。远端完成后再核对实际公开 APK、三附件哈希、tag/CI/账本/cleanup，不追加无关测试矩阵。
- 协议与设备风险：不改 App、协议、数据或硬件控制；不连接或安装设备；CI 不代表安装、数据保留或硬件验收。
- 发布与签名升级边界：沿用用户已明确批准的既有签名及 Develop 测试预发布持续授权，不发布稳定版/RC。发现异常后已暂停 `DEVELOP_APK_ENABLED`。54 号已保留，永久占用，不重用、不删除账本或标签；修复合入后恢复通道，由固定 SHA 历史队列分配新号并处理原合入提交及修复提交。已知历史下限仍为53，实际下个版本还须覆盖账本54及所有云端清单。不换密钥、不输出密钥、不宣称全设备版本现状已验证。
- 回退：保持入口暂停并排空/确认 lease 释放；代码通过新 revert/fix PR 回退。不移动标签、不删除草稿/保留号、不卸载或清数据。真实权限错误则停止，不偷偷添加 PAT 或更换 APK 源码。
- 独立复核：真实 `openai/gpt-6-astra`（high）已只读核对实际解压日志、修复 diff、测试及未变的发布安全路径，未发现阻断项；未重复跑测试，未访问密钥/设备或修改云端。不把独立代码复核当成真实公开发包成功。
- 未完成：PR 门禁和真实公开首包验收仍待完成；当前不宣称自动发包已跑通。
