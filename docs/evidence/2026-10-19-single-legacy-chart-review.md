# SingleScreen LegacyDetail 精确结构适配

已核ci-49a1b46-single用户横屏中文浅色原PNG，真实两曲线存在；SingleScreenPageChecks通用visit在ScrollView停止，新的LegacyChartViewport因此遮断chart结构检查。不是图缺失证明。

最小测试范围SingleScreenPageChecks、SingleScreenListStateChecks、SingleScreenWindowRun。通用visit/非滚动文字完整规则不改。LegacyHistoryDetail分支从views唯一LegacyChartViewport取公开chart，核唯一child、parent、attached/shown/same root。保持viewport完整，核实际Canvas plot≥96dp及转换screen后与viewport真实可视rect交集首屏≥96dp，不能只核chart外框。高chart允许局部滚动，不加入通用visibleFully(chart)。轴/来源滚达与恢复、返回legacyId/Parcelable身份门禁保留。

verify可增加默认null expectedLegacyShot，但LegacyDetail分支requireNotNull；WindowRun从既有legacyBefore按intent legacyId取唯一shot传入；ListState从原rows[firstVisiblePosition]保留完整shot，detailReturn传入并核intent id与chart.points全等且≥2。不得主线程重读store，不得从chart反推期望。其他能到LegacyDetail的调用点须传已有fixture事实。三profile原失败均保留分别复跑；此静态适配不声称实际通过。

## 候选三文件静态复核

已读SingleScreenPageChecks/ListStateChecks/WindowRun最新diff：仅LegacyDetail显式访问唯一LegacyChartViewport.chart，不改变全局visit停ScrollView；原fixture LegacyShot由ListState当前选中原row或WindowRun导入后保留legacyBefore传入，id和points精确相等，不从chart反推预期。保同root/token/display/祖先identity与实测坐标映射，核实际Canvas plot及viewport可见交集96dp，而非长chart整框首屏完整。其余原返回身份/视口检查保留。ChineseForm现有调用未出现LegacyDetail路径。静态无新阻断，可重跑三个profile；不以编译成功关闭旧失败。

## 2026-10-20 bcc64c1 两个1600×1000 profile 限定实际验收

实际包：`build/task-first-evidence/ci-bcc64c1-single/single-screen-tablet-landscape/raw` 与 `single-screen-tablet-large-font/raw`。artifact元数据指向run39414724030/head bcc64c1a10c1bff6cd08d4e786b0807f27fe8a4d；根核ZIP SHA分别98df540d172ce7604256d7311bfcf788414278ef219387971e4ec3859d8ab507、9472b6375f902ade32a26d6cf822bbb2959aab014081f0f686bd6a6f76ab3c94，CRC分别646/638。该包未带tested-merge manifest，已向根索取同run checkout resolved SHA，不从另一CI run推定。

本次亲看两个profile×浅深×History/Detail共8张中文原PNG。density1.25、fontScale分别1.0/1.3；History列表/选中预览及Detail双系列真实可见，图例、坐标轴、时间轴在这8张初屏均完整。Detail来源/四项缺失数据/解释限制全文初屏可见，固定动作未被挤出；未发现新的已确认UI偏差。large-font History警示曾因浅图缩览怀疑尾部省略，配套实际Layout核31 UTF16、ellipsisCount=0、文字713px≤内容724px，深色PNG全文可见，故不立此FAIL。

`instrumentation.txt` 有两profile各16语言主题组合 LegacyDetail PAGE_OK，`result.txt` 均checked336/legacyDetailFixture=true。对应冻结检查实际走唯一viewport/chart身份与独立fixture points全等、Canvas plotBounds及screen可见交集≥96dp，不再被ScrollView通用遍历截断。PNG亦可直接看到真实plot远大于120px(96dp)，不是仅大外框。配套layout记录Detail viewport/chart同为894×603（1.0）或894×584（1.3），首屏全可见；注意这些是View外框，不代替Canvas数值。原件没有单独落盘plotBounds精确值，不能捏造新的dp测量结果。

列表恢复原记录：1.0 LegacyHistory recreate/detail-return均position3/offset−83；1.3均position3/offset−18，gesture=swipe/journal=false。因此旧Single LegacyDetail结构漏查导致的失败，在这两个profile已获得实际执行+PNG的限定闭合；列表返回恢复亦有原记录。

这不是M7完整协议2/真实5s/零差取证或右侧各目标滚达恢复的替代品：single包无该组逐目标proof，也未落独立Canvas完整参数。这里只对两profile已采初屏与Single既有门禁作LIMITED_PASS，不称G1–G4全通过，不外推2560×1600用户平板、短窗、特殊态或全24页。

同run源码身份补齐：亲核 `ci-bcc64c1-single/job-118600839146.log` 与 `job-118600839517.log` 各131–134行，checkout后git log实际均8e8f8877aa5dc07b6e0e1c9ee6603cbde07ef249；均记Merge bcc64c1 into 8e12fe159db6bbd5aa168ea938645b091f4d1300。上述tested-merge待证项据此关闭，不再依赖跨run推定。

## 用户2560×1600配置增量

追加原包 `ci-bcc64c1-single/single-screen-tablet-user-landscape/raw`，artifact12262052466；根已核SHA629cec9abea6ca994a84a96e644965582be1666b3912f289f662b68a60b58928与646CRC。亲核同run job118600839661 checkout日志133–134行，testedmerge仍8e8f8877aa5dc07b6e0e1c9ee6603cbde07ef249。

本次亲看中文浅深History/Detail四张原2560×1600 PNG，density2.25/fontScale1.0。两系列真实plot、完整轴/时间标、图例、历史只读警示、Detail数据来源/缺失数据/解释限制及固定动作均可见；未发现本四张已确认新偏差。Detail实际viewport/chart同为1398×874，screen[252,519,1650,1393]全可见（仍是外框，不伪称Canvas精确数值）。原Single运行checked336/legacyDetailFixture=true，实际执行独立points身份+Canvas与可视交集96dp断言；列表recreate/detail-return均position3/offset−17、gesture=swipe。

因此三个profile的旧Single结构漏查修复与既有列表恢复门禁均获得限定实际闭合；用户配置不再是未跑。但该包同样不替代M7 protocol2逐目标滚达/短窗/特殊态，不构成G1–G4或全24页验收。其他M8设置图未在本轮专项审查，不能据single成功补成通过。

## 2026-10-20 dc5875c 正式LegacyHistory真plot不足

原件 `build/task-first-evidence/ci-dc5875c-legacy/raw/raw-device/84aaa3efd951e4f6229fc9d235e257e61f0f257c/tablet-user-landscape-light-font-1.3/history/`。Astra已实看原window.png，图真实扁矮；geometry chart完整rect `[1090,732,2488,1204]` 高472px，同源Canvas轴/图例占282.7512px，plot `[108,176.5008,1303.5,365.7496]` 高189.2488px=84.11058dp。不是visible坐标转换误差，连既准短高96也不足，更不满足当前正式160断言；G1 FAIL保留。原PNG还有前一导入失败Toast盖住底部部分说明，不据此签全文可见，不猜产品说明本身坏。

只读根因：LegacyChartViewport.onMeasure虽然将child layoutParams.height设置为max(同Paint最低轴带+160dp,available)，LegacyShotChartView却未override onMeasure。SDK34 ScrollView.java measureChildWithMargins给child高度UNSPECIFIED；普通View默认测量不把layoutParams数值当自然高度，fillViewport再填到viewport高，因此最终472而不是应有ceil(282.7512+360)=643。此为测量契约缺口，不应降低测试160或字体14。

最小修正规格交生产owner：仅LegacyChartViewport/LegacyShotChartView的opt-in测量契约，使同一永久chart实际自然高至少同源minimumVisualHeight(actual width,160)，ScrollView的UNSPECIFIED测量必须实际返回该自然高，EXACT/AT_MOST约束行为明确；默认visualStyle=null旧renderer路径保持，不在onDraw改尺寸，不通过硬编码643/84修单夹具。优先在visual opt-in chart onMeasure落实自然高，viewport保局部滚动；不更改数据/轴/图例/字体/列表/选中/固定动作。记录实际measure mode、width、同Paint最小高、resolved chart高及viewport高，验实图Canvas≥160；短窗仍按既准可见96与滚达完整图/轴、恢复判定，不能用chart自然高替代可见交集。不要求全chart同时进小viewport，也不能放宽原可达门禁。此修改共用独立Detail，需要同批验证其默认及图/说明滚达无回归。尚未实施或新运行，本节不关闭原FAIL。

### Legacy自然测量候选静态复核

两文件diff已核：LegacyShotChartView SHA `5215603577d40d29af193b7a958a608b031be0ec7ffd1e0cd415dfbd8a282fc5`；LegacyHistoryContent SHA `cb1172fdd7706d703537ec17f6f170237a98a44c131b69e536556afa6284e4c0`。visualRenderer非空时按resolved width调用同renderer minimumHeight(width,160)，UNSPECIFIED返回真实自然高；AT_MOST遵守上限、EXACTLY遵守指定尺寸，不伪报约束下始终足160。默认null直接super，旧绘制路径不变。viewport测量/滚动未改，仅追加实际measurement到原Canvas evidence；原160/短窗96/可见交集与滚达门禁未松。

静态无剩余阻断，待编译和实际图。Detail共用opt-in测量，需其原图/轴滚达与恢复回归；不能从这份代码推定短窗可见96或Detail已通过。原dc 84.11058dp FAIL须由新Canvas及原PNG关闭。
