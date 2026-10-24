# Import重建探针：从已知控件共同祖先定位content根

## 仓库、范围与依据

遵守 `docs/git-workflow.md` / `docs/plans/task-template.md`。当前source `8fee86b4e3f004ed3be5811d22a5bb49c2f87b98`，沿用develop派生的 `feature/task-first-ui`，现有PR #4→develop；root普通合批提交/推送及必需CI，不直接推main/develop或改共享历史。Sol负责限定实施、Astra静态验收、root构建。保留其他工作者文件。

原件 `ci-aac5c91/import-resolution-first-failure.json`：replacement-post-created同main turn，具名raw有引用及parent，formScroll也有引用及parent，但`findViewById(android.R.id.content)`取到的pageRoot下rawCount=0；middle/host未评估，不能据此断言缺失。sameDecor不等于sameWindow；产品现有onCreate在page安装后建立raw/middle并加到host。原记录不足以证明重复根，但足以拒绝“全decor按ID首个结果必为本次具名控件content根”的假设。

批准的是**只读探针root定位**，不是产品IME修复，也不预判严格rawState恢复会通过。只有实际父链证明成立才继续，不候选搜索、不找任意EditText、不延后/重试。失败仍原PARTIAL/异常链。

## 唯一实施路径

仅修改 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/CurveVisualMigrationChecks.kt` 中Import的 `ImeViewportTransitionProbe` resolver；保留原raw/formScroll具名读取与类型要求。必要的离线模型仅在 `scripts/test_ime_viewport_probe_contract.py`。不改共享probe API、Editor resolver、产品源码或其它root(owner)调用。

1. 原计数先初始化null/not_evaluated。读取具名raw、formScroll；在原resolver同一次主线程调用中，分别从这两个实际View沿`parent as? View`向上走，包含自身到owner.window.decorView。保持对象身份链，检测重复对象以拒绝循环；未到该decor、链中断或指向别的decor均失败。不能把相同class/ID当对象相等。
2. 每条链中`id==android.R.id.content && view is FrameLayout`必须各恰一个，且两个候选必须`===`同一个实例；该实例就是pageRoot。不是取最近一个、交集任意一个或fallback到旧findViewById。不得下行搜索其它根/控件。
3. 对此pageRoot执行原rawCount==1、middleCount==1、host非null且hostCount==1；保留原短路状态区分、原scrolls获取和Targets对象。选择根的依据增强，计数门槛不变。
4. 失败必要证据：记录本次已经走过的两条链的顺序、identity/class/id以及中断/循环/未到decor状态。利用现有attempt.value/reference按索引写标量，或序列化简短链字符串，不引入通用遥测框架/共享API。记录动作必须不覆盖原错误；链失败时原异常继续交既有partial处理。成功只记录既有pageRoot/阶段等必要字段，不重复采全树。不额外读取输入内容或重跑resolver。

原Lifecycle回调、5000ms、稳定三帧、四差异零、产品恢复证据、rawState严格相等、错误优先级及宿主设置恢复全部原样。通过根定位后遇到下一失败，应准确保留，不能以本计划授权顺手改产品/恢复合同。

## 有限验证与交付

- 原14反例/1生命周期及原resolver模型仍运行通过。只补本根定位的有限模型：两具名控件共享唯一content且链顶同decor成功（即使decor另有不在两链的旧content）；零content、任一链两个content、两链不同content、不同/未达decor必须失败。保留0/2计数与原异常身份模型，不展开新场景矩阵。
- 静态核同main/单resolver、按身份匹配、父链只读、失败原异常及not_evaluated状态；失败链证据不能制造成功目标。记录两文件精确SHA与diff-check。
- root统一 `:mobile:assembleMock :mobile:assembleMockAndroidTest`；原Import CI观察replacement-post-created准确根、原计数和后续rawState/原恢复断言。离线通过不冒称Android真实祖先/IME恢复通过，不需新设备操作。
- 根按普通PR增量合批，无发版/标签/安装/设备写入。无协议/单位/库存/队列/重试/停止影响，Mock不等于硬件验收。回退只追加新提交撤销探针增量/明确revert PR，保留旧失败原件、未知记录及数据。

本轮仅此探针修正。History四主态图预算真实裁切仍为独立产品布局问题，下一轮规划，不在此实施。

## Astra冻结增量审查（2026-10-24，待单点修正）

首轮冻结：CurveVisual SHA `6cc8bcbe7ab6bb25f13e2cf7e3a9138f7d663d4ef985cf8e774c131090702bad`，Python SHA `e49da03594464989a04a6354f0b4023a4e3edb0b462c99a62c3b3601d1fbf6f7`。父链按对象身份上行、防环；两链必须到同owner decor、各唯一content FrameLayout且同实例。原raw/middle/host短路计数、not_evaluated与共享probe partial处理未变，Editor/产品未改。独立运行原脚本exit0（14反例/1生命周期；新增祖先成功及6反例在main执行，输出仍保留原计数），diff-check0。

首轮唯一阻断：failAncestor的joinToString参数在attempt.value安全边界外构造，而且目标IllegalStateException在记录后才创建。按本计划“证据不能覆盖原失败”，需先创建failure，将链证据构造/记录包于try/catch，最终抛同一failure。已报root，构建期间未动源码。修后精确SHA和结论待追加；本段不是最终通过。

### 单点修正后最终静态结论

二次冻结CurveVisual SHA `4edacdf4fda5b25685d5a1548ee42e8e4626a0bc5042337cc643d1e2e784f865`，Python SHA `352527343a605cbce811e3c702f6609c2e0a57b547ff906083fa3bf929c8b5c3`。failAncestor现先构造failure，链序列化/每字段记录与外围列表构造均置于保护内，最后throw同一failure；不会以诊断构造错误取代祖先定位失败。仅此增量，父链/原短路/partial逻辑维持首轮已核结论。

Astra独立再跑 `python3 scripts/test_ime_viewport_probe_contract.py` exit0；原14反例/1生命周期及祖先模型执行，新增诊断序列化失败模型验证原异常身份；`git diff --check` exit0。静态阻断关闭，可交root统一构建及普通合批。尚不宣称真实replacement父链成功、rawState恢复通过或产品IME修复；必须由精确新源码CI保留实际下一结果。

## Root最终构建证据

二次冻结后统一构建 `:mobile:assembleMock :mobile:assembleMockAndroidTest` 成功，36s、91 tasks（4 executed / 87 up-to-date）。最终日志 `build/task-first-evidence/ui-2026-10-24-import-root/build-final.log`；首轮 `build.log` 保留但不作为最终源码证据。构建前后 303 个Kotlin源文件SHA一致，见 `source-retry-before.json` / `source-final-after.json`。Root重新运行离线脚本通过，diff-check通过。

APK SHA256：

- `mobile/build/outputs/apk/androidTest/mock/mobile-mock-androidTest.apk`：`f000d044dc58c0c0a5680730f48b588cf91a9d814d55e2be08b6b7f45a58e857`
- `mobile/build/outputs/apk/mock/mobile-mock.apk`：`950d177683b99704784cd18d813bbfe3e1a56853ad40d9250989a00e9f5eef5c`

仅编译，不安装、不发布。真实Import父链与严格恢复仍待CI，History首屏裁切未因此关闭。
