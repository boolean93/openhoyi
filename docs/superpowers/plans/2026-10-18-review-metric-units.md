# 复盘页结果单位层级

沿用已认可brew-review-v3及统一24sp数值/14sp辅助字体规范，feature/task-first-ui→develop PR#4，遵守docs/git-workflow.md。4bd173c业务实图30s/36g仍数字单位同大粗体；复用curveMetricText处理本地化journal_seconds/journal_grams单位（格式字符串空数值trim得到后缀），数字24sp/700，单位14sp/400，未知—不加span。完整文本和观察来源不变，不改笔记/库存/控制行为。仅改BrewReviewActivity结果TextView，八语言不硬编码s/g；无需额外镜像测试。验证Mock及AndroidTest构建、diff，再由已有用户平板截图测量及长文/IME检查验收。不安装、合并、发版或改变签名；合批普通推送，回退追加revert。
