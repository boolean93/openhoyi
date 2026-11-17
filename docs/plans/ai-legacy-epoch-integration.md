# Mock132 旧凭据世代兼容回合

- 需求与范围：将已安装Mock132的只读legacy epoch补丁合回共享源码，防止后续版本随机初始化旧凭据epoch导致已保存会话身份不匹配。仅修改AiKeyStore.kt与AiCredentialEpochTest.kt，保留其余未提交开发。
- 来源分支/基线提交：develop来源的现有feature/ai-chat，HEAD599670aab75d94070bf836cb813239e30e3e46e7；按协调者指令继续此工作区，不另切分支或回滚。目标PR分支develop。
- 文件归属/步骤：本轮仅持有上述两文件；已核对共享树和补丁可干净应用，改前备份/tmp/openhoyi-epoch-integration-before。不碰并发Activity/Engine/ProtocolHistory、曲线和附件模块。补丁来自openhoyi-local-materials/ai-context-mock132/legacy-epoch-readonly.patch，合入后和132冻结源码逐字比对。
- 推送与合并：仅本地整合验证；不提交、不推送、不创建或重试PR。未来如获授权，按Git工作流及scripts/create-pr执行。
- 分工与复核：Sol整合并运行focused回归；复用Astra独立只读核对实际共享源码、132一致性和兼容边界。
- 行为：缺epoch只返回固定legacy UUID，不读取sealed、不写prefs；已有合法epoch原样保留，非法值失败关闭。显式保存/删除Key继续生成随机epoch并隔离旧会话；无Key不会因此获得发送资格。
- 验证：AiCredentialEpochTest、AiKeyStoreTest、AiKeyCodecTest、AiSessionStoreTest、AiContextPolicyTest的Mock JVM targeted回归，以及凭据静态检查；复用132同代码全量Mock946项零失败/1跳过及lint证据。不新增设备或完整组合矩阵门槛。
- 协议与设备风险：不改模型/BLE/机器协议，不读真实Key、用户数据，不使用设备、Computer Use或付费API。测试均使用假凭据/内存存储，不能称为真实Android凭据解密或迁移验收。
- 发布/签名/升级：不发版、不改包名/版本/签名、不安装。保持132既有legacy身份和schema2兼容；旧客户端降级后更换Key的路径不在支持范围。
- 回退：不能简单恢复随机epoch初始化，否则可能隔离132已保存会话。后续修复保留legacy映射；不卸载清数据，不删除未知会话。
- 未完成与阻塞：真实Android存储/解密与设备体验本轮明确不验证；没有源码整合阻塞。工作区及父目录未发现.agents/skills，用户级autocli与本次代码整合不相关。

## 实际结果

- `./gradlew --offline --no-daemon --console=plain :mobile:testMockUnitTest --tests io.openhoyi.mobile.AiCredentialEpochTest --tests io.openhoyi.mobile.AiKeyStoreTest --tests io.openhoyi.mobile.AiKeyCodecTest --tests io.openhoyi.mobile.AiSessionStoreTest --tests io.openhoyi.mobile.AiContextPolicyTest`：BUILD SUCCESSFUL in 58s。五类共38项，0失败/0错误。日志 `/tmp/openhoyi-epoch-integration-tests.log`。
- 凭据静态检查6项通过，`git diff --check`与补丁反向只读校验通过。
- 两文件与已安装132冻结源码逐字一致；AiKeyStore SHA256=`1057f580dbe0dcfa6e7b3015fff7ae0e1597250a29448acf042d1f9116a2ae32`，AiCredentialEpochTest SHA256=`e1d5d756109d9abee3d2da759ec36d0f46255c61a14a6f8857b474bd231045b5`。这仅证明两文件一致，不宣称整个共享工作区与132相同。
- Astra独立只读检查共享源码通过，确认132行为一致且其余逻辑保留，无新增阻塞。
- 本轮精确代码差异 `/tmp/openhoyi-epoch-integration.diff`，结构化证据 `/tmp/openhoyi-epoch-integration-evidence.json`。本轮无任何设备/真实Key/付费API/推送/PR操作。
