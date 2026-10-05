# 产品恢复确认的回读证据

目标：五kind在原机合成READY下仍须新的完整回读；仅设备已就绪不能清记录。扩展现有产品测试，不改生产。

每kind三项负例：idle时间缺失；当前序号等于恢复基线（非更新）；该kind的关键回读不完整或不一致。最后恢复有效snapshot/基线，继续原busy与Storage失败/显式retry正例，避免ACK永久关闭造成假通过。

kind负例：杯数两路不一致、设置帧缺失、周计划缺第二片段、立即睡眠状态2、预热恢复非awake。15次真实ACK要求attempt0/pending/raw prefs/warning保持与精确waiting事件，fake执行0。强制Mock包、detached生命周期、UUID prefs及合成READY沿用。

范围：产品snapshot/serial输入的门禁接线，非实际通知顺序、GATT身份或硬件验收。

- [x] 15负例与marker实现。负例刷新非空时间字段，保留待测缺失字段；复位后继续原有效回读与存储重试正例。
- [x] testAPK/Lint/shell、独立复审。最终离线构建101任务成功，diff与shell语法检查通过；只读复审无阻塞。
- [x] 新源码Mock37373939613最终success并读产物，新增15项marker和原完整矩阵均在；native37373939684成功并读日志。

源码提交：`4cec0b32704c74c331fb9a708835c2d153fb18ac`。
云端任务：native `37373939684`、Mock生命周期 `37373939613`、Mock升级 `37373939603`。记录时均排队；待实际运行产物验证，不重复触发。

升级37373939603终态失败：实际annotations为hosted runner未获取，steps空，无测试结果；新版升级另有既有排队任务，不重跑旧任务。运行证据见 `docs/evidence/service-recovery-readback-2026-10-06.json`。
