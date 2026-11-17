# AI public web search
- 范围：DeepSeek 独立 search API 封装为 Koog function；公开豆子参数检索后结合本地曲线建议；升级首发告知，不新增开关。
- 来源/工作分支：既有 develop 来源 feature/ai-chat 未提交实现，保留其他工作。目标 develop。
- 文件：AiWebSearch、AiSafeTransport、AiChatEngine、AiChatPolicy、Activity consent 和中英文案、相关假 HTTP 单测。
- 推送/PR：本轮仅本地修改与假 Key fixture 验证，未授权公开推送/PR，不执行。后续遵循 git-workflow。
- 风险：最多4次chat、2次search、6次tool；同源固定HTTPS、禁重定向、共享Key修订/取消保护；网页不可信，不fetch URL。仅query发送search，不发送本地历史；模型关键词选择不是绝对隐私隔离。
- 验证：相关 JVM 假HTTP测试和编译；真实API与设备验收待独立授权。
- 发布/签名：不发版、不安装，不改签名/版本或设备协议。
- 回退：撤销本轮局部改动，不清数据；v2 consent不继承旧批准。
