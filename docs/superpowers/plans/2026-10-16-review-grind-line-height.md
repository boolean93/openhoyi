# 复盘研磨字段完整行高

依据e98d7c7业务截图：研磨字段minLines=2却被TaskReviewSections.fitEditors固定52dp，长内容第二行只露一部分。仅调整参数区研磨字段预算：两行需求=2×实际lineHeight+compoundPaddingTop+compoundPaddingBottom，至少52dp；预算不足沿用原48–52dp回退，不扩大其他笔记、不改保存和滚动恢复。

feature/task-first-ui沿用develop66ebda9+ed01de0依赖，PR #4目标develop，遵守docs/git-workflow.md。双Mock APK/diff检查后普通push；既有中文长字段IME/重建、17路径业务检查及实际截图验证，编译不证明完整显示。不安装实机、不合并发版、不改签名/schema/协议；追加revert回退，保留记录。

候选双Mock APK40秒构建成功，git diff --check通过。实际行高显示和短高回归待精确提交CI。
