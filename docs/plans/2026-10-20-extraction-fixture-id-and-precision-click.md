# 708 夹具ID与精度开关窄修

来源：当前task-first短分支（708原件；三组补证已本地提交b8dcc1f），目标仍develop PR。仅既有测试/runner，不改产品协议、仓库格式、真实设备或发布；由root合批验证/推送。回退仅本批测试改动，原失败原件保留。

## 1. Extraction sampleFailures

实际push原件 `ci-7082e04-m5-push/raw` 的STOP_REQUESTED/case.json：shotId及actualTraceIdentity为`m5-extraction-STOP_REQUESTED`，首真实offer后sampleCount=1；cleanup在writer终止后报告sampleFailures非空。测试直接将case拼入nextId，`ShotSamplesStore.file`第一步严格要求`[A-Za-z0-9-]{1,64}`，下划线必非法。STARTING无点不触样本保存，不能代表下一状态ID合法。原回调只记异常类型且未保全原message，故区分“输入必被现代码拒绝”与“已从CI读到Invalid shot ID原异常”；后者本轮没有。

owner仅 `ExtractionHistoryVisualMigrationChecks.kt` + runner。使用固定字典，case/目录/状态名不变：

| case | 独立scope shot ID |
|---|---|
| STARTING | m5-extraction-starting |
| STOP_REQUESTED | m5-extraction-stop-requested |
| UNKNOWN_live | m5-extraction-unknown-live |
| no_scale | m5-extraction-no-scale |
| stale_scale | m5-extraction-stale-scale |
| no_service_ready | m5-extraction-no-service-ready |
| manual | m5-extraction-manual |
| historical_reference_toggle | m5-extraction-historical-reference-toggle |
| missing_samples | m5-extraction-missing-samples |
| water_reset | m5-extraction-water-reset |
| sample_gap | m5-extraction-sample-gap |
| journal_unavailable | m5-extraction-journal-unavailable |

进入前核键集合/顺序与原12完全一致、值唯一且符合原ID约束；同步start/trace及runner精确值。no_service仍无shot、manual仍不伪造app start返回ID，沿原passive模型保留各自语义。不得改ShotSamplesStore正则、吞writer错误或失败后继续下一scope。

现onError保完整Throwable，在cleanup仍fail closed之前把class/message/stack（含cause/suppressed）、case/owned scope写入原隔离报告；可记录已有traceIdentity/offer序号，不重建新日志框架。原失败码/原错误优先级不变。用真实ShotSamplesStore保存合法映射ID的小样本验证；无需新设备场景。新CI须实际跨过STOP_REQUESTED严格清理后才可执行余态，不追认旧FAIL。

## 2. M8 precision单次点击

708 Settings四ScaleSettings格在`click`的`check(view.performClick())`失败。原geometry确认tag=`product/settings/scale/precision`、class=`android.widget.Switch`；生产仅setOnCheckedChangeListener写one_decimal。SDK34 `CompoundButton.java:143`先toggle再返回super.performClick的handled；无OnClickListener时false不等于toggle失败。当前测试已只为App theme/feedback处理此语义，遗漏precision。

owner仅 `SettingsVisualMigrationChecks.kt`。仅将上述精确tag加进现CompoundButton单次click分支；保isShown/enabled/clickable、whole、双边48dp、真实reveal/commit，其他View仍check返回值。scaleSpecials点前同main核实际Switch unchecked且pref=false，点后核checked且pref=true；一次performClick，不再点、不直接setChecked/改pref代替操作。保原recreate、实际Scale页返回及scope typed preference恢复检查，不加业务状态/runner豁免。

原件没有点后状态，不能声称此次业务通过；修后构建与新CI验证。App/Machine同步超时、M2/M3恢复未知均不被此修复覆盖。

## 最终静态核对

三文件无剩余静态阻断：M5测试 `d51e1cda370d008b837d81383b08912249269208cdbab8feb891d0b987aa4aab`，runner `c3fef4a2ee9ca4868bd608a7013ccd0226414e8e536e05884b55e635ef137d56`，Settings测试 `420e22467339cf2f7b65e9d239d1ef04312d14cefa3df00cda5aac73cd9b584c`。12项真实Store save/load+SHA预检、固定ID对应及manual/null边界均符合。cleanup失败后诊断构造/写盘异常附加原Throwable，未更改停止/恢复。待与Curve合批构建及真实CI；旧writer/precision失败不追认通过。
