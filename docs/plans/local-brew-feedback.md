# 原生本地萃取提示与音频

目标仍是原版完整本地提示体验，不以试听或播放器类替代结束提示。使用Kotlin/Android，无UniApp/JS运行时。控制与提示独立，0x21灯效不开放。

分层计划：
1. 本地音频目录及两段播放：先1.mp3/2.mp3，再四条相应英语语音之一；切换请求取消旧播放，旧回调不得推进新请求。自然结束/失败清理，显式停止或音频焦点丢失终止串播。Android MediaPlayer与AudioFocus只依赖Application资源，不接触Service/Hub/命令。
2. 依据同杯0x80秒数/累计流量及曲线preinfusionSeconds记录提示数据；显示启发式按旧>2.2/≤1.5等级，不等同秤重或质量。未知结束、缺同杯快照、断线及异常数据不误报成功。只读结果不能改变启动/停止/恢复资格。
3. 本地默认关闭偏好、设置Switch/试听、结束提示接入Home/Extraction与手动杯；已展示记录跨Activity重建不重播，离开页面或新杯时停止本地音频，原有立即停止入口不变。
4. 新UI资源完整同步八语言；Mock结束全链与模拟器音频回调/焦点失败验证。真实机器结束/后台体验留待用户恢复设备。

本轮推进第1项，不宣称完成提示功能或原版体验。复用用户提供旧目录的14个音频文件，记录独立文件SHA256与旧模块位置。构建产物核对资产未压缩，确保AssetManager.openFd可用。纯播放器测试先失败，覆盖顺序、取消、过期回调、同步回调、播放失败及焦点中断；独立审查、双变体编译/回归后提交。

第1项本地验证（2026-10-01）：纯播放器5项、资产SHA1项新增；Alpha/Mock各151项（150通过、1缺真实用户导出跳过），完整协议/会话/共享回归、三应用APK和Mock instrumentation APK编译、双Lint通过。19Python、正式资源生成检查及两APK各6969资源一致；两APK14音频均ZIP_STORED，字节/SHA与原资产证据一致。独立增量审查无实质问题。

新增只在Mock包执行的内置Android instrumentation，先确认MOCK_MODE和包名，再测试prepare立即取消、逐个14资产完成回调、实际两段串播；接入云端Mock生命周期工作流。取消观察仅300ms，焦点竞争/后台中断仍未验证；编译通过不等于Android运行时通过，云端结果另补。没有生产调用方、设置、提示判定或结束弹窗，用户完整提示功能仍未完成。

第2项接入约束（旧源码78345–78430、78108–78153及7740–7815已逐段核对）：
- 原版早期快照仅在isExtractingPhase且机器秒数≤10时更新；该判定排除brewWait，普通槽位压力>1bar或流速>0.8ml/s，6/7槽位阈值更低，也接受3秒内秒数/累计流量增长。不能把所有0x80或预热回报都当早期萃取。
- 原版结束提示由450ms无活动驱动；原生不新增这类设备结束判断，等待现有ENDED_OBSERVED或被动萃取明确结束后只读投影。未知结果/断线/IDLE清理不显示完成提示。
- 独立记录器在同杯begin时清空早期/最终快照，绑定shotId及选定参数preinfusionSeconds。机器秒数与累计流量使用0x80原字段，不能使用ShotPoint.elapsedMs或scaleWeight替代。乱序/回退/跨杯/缺早期快照保守拒绝提示，不能像旧JS一样让NaN落到level0。
- 当前service真实帧路径recordMachinePoint、Mock路径mockTick直接series.machine、手动路径被动Started两帧以及自发/Mock begin/finish都需逐项接入。不得遗漏Mock或用历史是否成功保存作为设备结束事实。记录器异常/提示失败不得阻断finishSeries、恢复记录清除、停止或样本保存。
- 本阶段先实现纯记录器和旧公式独立对照，后接入Service；偏好/弹窗/音频只订阅只读结果，不调用Hub或协议API。

第1项云端运行时结果（0942650）：Verify native app 36865762452与Verify Mock lifecycle 36865762552均success。读取后者mock-lifecycle日志artifact，audio-instrumentation.txt含LOCAL_AUDIO_CHECKS_PASSED clips=14 cancelledPrepare=true sequence=true，确认14资产逐个实际解码/完成、prepare取消无回调及两段实际串播。theme-recreation.json为1→2，生命周期检查通过；后台配置destroy仍false，不冒称通过后台配置重建路径。Android34无音频输出的模拟器不证明人耳听感、真机解码兼容性或真实焦点竞争。此结果只完成播放器阶段，第2–4项仍待开发。
