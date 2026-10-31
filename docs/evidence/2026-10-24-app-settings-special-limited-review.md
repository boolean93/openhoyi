# M8 AppSettings 特殊态限定实图验收

## 原件与范围

- source `8fee86b4e3f004ed3be5811d22a5bb49c2f87b98`；run `40056754002`。该 run 的 Machine 截图失败不等于此前 AppSettings 图失效，也不签整个 run。
- ZIP `build/task-first-evidence/ci-8fee86b-settings/artifact.zip`，470669800 B，SHA256 `4a449a231047c75d05f6c921aa90c3dd6f5fa429ce1a5bbbdc119e86187c0c11`；root 已核 remote digest/5999 CRC。
- 排除此前已审普通四主态/每配置26滚达及 restored。本轮四配置 light/dark × 1.0/1.3，各30目标，共120目标、90唯一 PNG，已逐唯一图实际看图。重复像素复用视觉结论，资格逐目标核验。
- 索引 `ci-8fee86b-settings/app-special-selected/index.json`（含原 member、PNG SHA、路径），SHA256 `c05c1cbd226773e402b61eadab568b7b250c89962abf4c5e6fe456a210ffce10`。同目录 `audit.json` SHA256 `074899a9418a7074ff63b496484b3a95648f0439fb8ca808e8580512656f0b76`：120图 SHA 与 manifest/proof相等，accepted、5000ms、四差异0，elapsed 1330–2799ms。这只证明图资格，不替代下面视觉判定。
- 原尺寸补核：`native/` 为原图 `(600,180,2150,1465)` 无重采样裁剪。实际看20张：dark1.3八语言、四配置语言失败 dialog、四配置 feedback 拒绝及离页停止。裁剪外布局按完整PNG核，不把 crop 截边误判产品裁切。目录其余预制 crop 不冒称全看。

## G1–G4 判定

| 原目标（每配置） | G1 布局 | G2 字体/字形 | G3 字号/行盒 | G4 颜色 | 业务/限定结论 |
|---|---|---|---|---|---|
| 八语言各 window/current-reachable/target-0/restored，共24 | 当前语言全显；阿语 RTL；俄/西长文自然换行，日语1.3当前值独立一行，无重叠 | 中文固定资产；其余平台语言字形未见豆腐字/断笔，原尺寸核八语言 | 未缩字；当前值/说明自然高，长文不省略 | 浅深文字/卡片/强调一致 | 接受当前语言及图内可见内容；dark1.3英文 window 的反馈说明被旧语言错误 Toast 覆盖，不签该处；其滚达/restored无遮挡 |
| language-write-rejected，1 | 实际是完整八语言选择框：8选项、原选择、取消均完整；底部Toast未盖选项/取消 | 八 nativeName 原尺寸可读 | 原自然行高及触点保持 | 两主题 dialog/选中强调清晰 | 接受选择框可见范围及错误全文；不把底层被覆盖页面签过 |
| theme-write-rejected，1 | 旧开关状态保留，组内完整红色错误，不盖动作 | 中文一致 | 错误自然换行 | danger错误色明确 | 接受此可见拒绝态 |
| theme-success-recreated，1 | 新主题/开关可辨；旧导出Toast遮挡试听区域，见下表 | 可见文字一致 | 可见角色未缩字 | 新浅/深配色接受 | **部分FAIL：不签被遮挡试听内容** |
| feedback-write-rejected，1 | 旧开关状态保留，错误Toast全文可读，但遮挡试听区域 | 错误文字可读 | 不以错误全文替代其下说明 | 可见色态正常 | **部分FAIL：反馈呈现需要修正** |
| feedback-preview-start-stop/leave-stopped，2 | 停止后按钮恢复“试听提示音”；离页停止图下半页说明、信息四项、诊断动作完整 | 原尺寸中文小字清楚 | 14/16角色和自然高保持 | 原浅深层级一致 | 接受图内停止后页面；播放/停止记录不等于物理扬声器验收 |

实际字体测量来自每状态 `AppSettingsActivity-typography.json` 的当前 TextView/Paint/Layout，以及 accepted proof 的 geometry；不是只读声明。中文 manifest asset SHA `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`。density2.25：1.0 body16sp=36px、aux14sp=31.5px，实测中文行高54/49px；1.3按系统scaledDensity增长，不通过压字号塞入。非中文沿冻结平台fallback策略，公共TextView API不暴露逐glyph私有字体，未虚称其等于中文asset。实际深色正文 `#FFEDF3F2`、次级 `#FFA9B9B8`、强调 `#FF83D6D0` 与原像素一致；浅色白卡/灰白底/深正文/青绿动作保持原规格。图内分组留白、16dp内距、related8及自然流与已接受主态一致。

## 明确未通过的 Toast 遮挡

| 目标 | 配置 | 原图直接可见问题 |
|---|---|---|
| feedback-write-rejected/window | light/dark 1.0 | “设置未保存，请重试”遮盖“仅播放本地声音，不操作咖啡机。” |
| feedback-write-rejected/window | light/dark 1.3 | 同Toast覆盖“试听提示音”按钮区域 |
| theme-success-recreated/window | light/dark 1.0 | 前一操作“操作记录已导出”仍覆盖试听说明，不是主题成功文案 |
| theme-success-recreated/window | light/dark 1.3 | 同旧导出Toast覆盖试听按钮区域 |
| language-en/window | dark1.3 | 前一“语言保存失败，请重试。”覆盖英文feedback说明第二行；current-reachable/restored没有该遮挡 |

这是产品反馈遮挡，不是四零采集失败；不能靠隐藏Toast、等消失、改变截图时序或只签无遮挡帧关闭。2026-10-24核 `8fee86b..929de4e` main差异只有LegacyHistory两文件及MachineSettings，AppSettings/Language/Feedback/MobileApplication实现未改，故此source边界仍适用当前实现。

## 未覆盖边界与下一步

SAF cancel/fail/own-success 仅有 `PASS_BUSINESS_ONLY_NOT_VISUAL_ACCEPTANCE`，没有对应专门PNG，不签系统选择器/各结果视觉；主题图残留导出成功Toast不替代SAF结果态验收。物理audio仍待真实硬件证据。八语言只覆盖原采集当前语言目标及图内内容，未把初态屏外所有翻译说明冒充全文已审。

最小修正建议已交root：语言失败在dialog内联，反馈失败在原反馈组内联，AppSettings导出结果在原诊断组内联；辅助14sp/related8/自然高度，保完整原错误与旧值，当前区域只保最新反馈、不抢焦点、离页清理；共享导出保旧调用者行为，不全局改Toast。本报告不实施、不改门槛。
