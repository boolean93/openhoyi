# 预热READY与CANCELLING的产品恢复门禁

目标：预热正常警告隐藏时仍有事务互斥，取消传输未决时不能清记录或启动萃取。扩展真实Service ACK/入口验证，不改生产。

沿用五kind合成READY/原机/新回读/UUID prefs/双fake传输的ServiceRecoveryPersistenceChecks。五kind共通WRITING/WAITING两阶段仍保留；BREW_WAIT由真实BrewPreparation.observe(2,9300)推进READY，再beginCancel推进CANCELLING。每阶段ACK保持Storage attempts0/pending/raw prefs/tracker状态，入口检查保留精确文案。

READY沿用隐藏advisory但阻止五种无关控制，不把正常消费预热的启动算作应阻止。CANCELLING要求可见brew_wait警告、五种无关控制及startShot均被阻止；不调用实际取消发送。共12次busy ACK、69次控制入口。刷新非空回读时间以减少时效过期假通过，再disconnect UNKNOWN进入原Storage失败/显式重试正例。

边界：状态由真实trackerAPI驱动，温度/身份/通知合成，非真实预热、取消命令或设备确认。全部已有身份/回读负例、busy互斥、清除失败重试和原矩阵保留。

- [x] 加两状态/精确文案/计数marker，刷新有效时间但不补缺失证据。原58+5+6=69入口、原10+2=12确认。
- [x] 测试APK/Lint101任务成功，shell/diff通过；独立只读复审无阻塞。日志 `/private/tmp/hoyi-preheat-recovery-phases-build.log`，未宣称实际运行通过。
- [ ] 提交推送并核对新源码实际产物；排队/运行不当通过。
