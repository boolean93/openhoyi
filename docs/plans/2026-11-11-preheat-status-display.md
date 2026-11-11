# 正常预热状态展示修正

- 需求与范围：trace85证实0x11预热期间连接READY、持续idle报告，但导航误写“暂不可用”，锅炉因预热互斥禁用却无原因。本次仅修正常预热/就绪文案与禁用说明，不放宽控制。
- 来源分支/基线提交：develop基线e7c7ac8a上的本任务HEAD `87bf960d`。
- 工作分支：`fix/home-switch-readback`；PR目标：`develop`。
- 改动文件与步骤：纯展示状态映射、TaskShell传入真实预热及温度证据、首页锅炉短原因；中英文资源/catalog/count同步，代表性pure状态测试。
- 推送与合并：用户已授权PR与合并，由协调方统一create-pr/CI/merge；本工作者不commit/push。
- 验证证据：定向mobile单测与Kotlin编译、localization/diff校验；不重复无关矩阵，后续PR门禁由协调方运行。
- 协议与设备风险：不改0x11/0C/0D、不改isEnabled、Service写门禁、互斥、取消/UNKNOWN、持久记录或固件许可。FAILED/UNKNOWN/断线/报警不被正常标签覆盖。READY锁存但当前温度未知或离目标时用中性“预热占用”，不谎称温度就绪。
- 发布：不发版、不改版本/签名、不安装/启动/操作设备；1.1.5许可另有文件归属，不触碰。
- 回退：revert本展示改动，数据/未知记录不变。
- 未完成与阻塞：真机展示与操作验收留给后续授权；trace85是既有用户操作证据，不代其控制机器。

规则：[Git工作流](../git-workflow.md)。Astra独立审查，Sol实现/测试。

## 验证结果

- `python3 localization/android_resources.py`、`python3 localization/validate_catalog.py`、`python3 localization/android_resources.py --check`：通过，中英catalog各993项、英文XML992项（排除变体app_name）。三个旧数量快照同步，原逐键/格式断言保留。
- `./gradlew --offline --no-daemon --console=plain -PhoyiVersionCode=80 -PhoyiVersionName=0.2.0-preview.80 :mobile:testDebugUnitTest --tests io.openhoyi.mobile.PreheatStatusPresentationTest --tests io.openhoyi.mobile.TaskShellDeviceStatusTest --tests io.openhoyi.mobile.HomeBoilerSwitchPresentationTest --tests io.openhoyi.mobile.LanguageCatalogTest --tests io.openhoyi.mobile.LanguageDraftTest --tests io.openhoyi.mobile.ResourceMessageTest :mobile:compileDebugKotlin`：52秒成功。新增3项pure状态测试覆盖正常预热、锁存READY当前温度、断线/异常/unknown/安全问题优先、锅炉原因适用范围与原锅炉恢复优先级。
- `git diff --check`通过。Astra已独立审查代码、构建日志和新增测试XML，无已知阻断。未改物理enabled、Service或dispatch门禁，未安装/启动/操作设备，未commit/push。
- 正常预热的互斥仍保留；“预热就绪”只在当前新鲜校正温度处于原目标±1°C时显示，否则中性“预热占用”，不是自动清记录或允许锅炉写入。
