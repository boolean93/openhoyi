# Automatic extraction review

The API key settings page has a separate, default-off switch. It discloses that confirmed cups' pressure, machine flow, water, temperature and available scale measurements go to DeepSeek and can incur charges. It authorizes only the current saved credential identity. The app uses both a durable credential-epoch preference and a dedicated Android Keystore grant. Replacing or deleting the credential or disabling the switch invalidates pending requests. Revoking either persistent gate suffices. If neither storage confirms revocation, the app pauses locally and tells the user to retry or remove the saved key, rather than display a durable OFF state. Notes, names, IDs, historical cups, chat history and attachments are not sent.

## Trigger and isolation

- The application owns the review, not a screen/session. The existing terminal cup handler captures the authorization only for a confirmed, uninterrupted completion, after the history record commits. The existing asynchronous sample writer reports when that cup's sample data commits. Failed writes, absent/unusable evidence, no authorization and no saved key do not send a request.
- A bounded representation of observed brewing samples goes to the fixed DeepSeek Flash HTTPS chat/completions endpoint through the existing protected transport. It includes explicit units and provenance, the optional machine duration and a distinct observed sample span. It does not infer how the coffee tastes or pretend to know the intended recipe. No agent tools, search, further history access or recipe/machine writes are available.
- A local per-cup attempt marker commits **before** sending. The same eligible retained cup is never replayed, including on restart, even if a timeout or process death leaves the outcome unknown. The bounded ledger prunes with the existing recent sample retention. Corrupt ledger state fails closed. Endpoint, transport operation, credential epoch and consent revision are fenced. Disabling or changing the credential closes stale in-flight transport and suppresses stale results. If a request already reached the provider, cancellation does not guarantee it was not processed or charged.
- The live finish page shows a compact progress line, then a short details action for its current cup. Only an explicit tap opens all three lines in a scrollable view, so a narrow display or large font cannot silently omit the recommendation. The full historical detail shows the persistent frozen result while that cup's samples are retained. Later cups, navigation, and process restarts cannot reassign results. Mock never sends to a real provider.

## Review content

The request requires three bounded JSON text slots, displayed as three concise lines: an evidenced good/stable time interval, an evidenced anomaly (or explicitly no identifiable anomaly), and exactly one next-cup change or one additional measurement if evidence is insufficient. It treats grind and puck distribution as separate hypotheses, not causes. No immutable same-shot stage targets are available in the durable history: the current or edited preset is never queried, and sample time intervals are not advertised as intended recipe stages. Missing beverage or scale data never authorizes a weight/scale-flow estimate, and the dose is not shared, so the prompt forbids a brew ratio. The parser rejects missing, extra, blank, multiline, or oversized slots rather than truncating away the advice. Unit tests verify this structure and the prompt/data contract, not the quality of an untested provider response.

## Verification

Offline unit tests exercise bounded sample payloads, same protected transport with a fake HTTPS connection, durable-attempt contract, isolation, duplicate prevention, timeout, local persistence, consent and key-rotation fences. Real hardware or paid provider acceptance is separate.

## 本次工作计划

- 基线：`develop` 的 `8bf760bfd0bd3656d8eab4a6e749c9eeb1f14e7b`；工作分支 `feature/automatic-shot-review`。本阶段只开发与验证，不推送或创建 PR；后续 PR 目标为 `develop`，按 [Git 工作流](git-workflow.md) 运行门禁和核对精确提交。
- 验证：`:mobile:testMockUnitTest` 筛选 `AutomaticShotReviewTest`、`ShotSamplesStoreTest`、`AiSafeTransportTest`，并编译非 Mock 代码。UI 实机验收及真实 DeepSeek 端到端调用未进行。
- 范围：只读取完成萃取后的历史采样及合法的机器计时，不修改蓝牙协议、机器门禁、指令队列、停止判定或任何固件。未知网络结果不重试。
- 本阶段不打包分发、不签名、不安装、不升级、不发版。回退用普通代码回退；新设置与评价记录独立于既有历史样本，旧版可忽略它们，无需通过卸载或清空数据恢复。
