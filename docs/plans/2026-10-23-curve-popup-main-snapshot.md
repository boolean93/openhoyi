# M3 原Popup菜单证据主线程快照

- 范围：修正popupEvidence跨线程读取View的已证线程问题，保留真实原菜单/完整证据与严格身份门禁；不声称定位了CI唯一null对象。
- 基线：PR39906809355，head4076c2d862f94608fa6ec3d405c91d23a6b6867c，merge/source f0cee480d07fda61f20ad904e4b2818aa8bb7acd。当前授权feature/task-first-ui→develop批次，遵守[git-workflow](../git-workflow.md)、[task-template](task-template.md)，root统一构建/推送。依据 `build/task-first-evidence/ci-4076c2d/popup-npe-source-diagnosis.md` 与当前实际调用。

## 已证边界

原main只取到menuList，popupEvidence随后在Instrumentation线程读取adapter/childCount/getChildAt/TextView/observation；后续slotPopup身份门禁还没执行便NPE。跨线程读View已证，child消失只是竞争候选；CI1262包含多处可空操作，不能认定一定child或text为空。本地class不是PR dex，不能反推唯一运行指令。

## Sol允许范围

仅 `CurveVisualMigrationChecks.kt` 的popupEvidence、两个librarySlots调用点、openLibraryMenu末尾choices读取以及必要私有只读辅助；保留已冻结source-return诊断增量。生产、共享取证/runner及后续slot/Dialog等待逻辑不改。

1. 将原菜单对象获取、完整证据读取放入**一次main块**。positive slot使用这次取得的同一menuList；不允许nullable参数在失效时重新popupList寻找替代。negative两例也在main内一次确定原List再传入，不在popupEvidence内部另选。openLibraryMenu的末尾popupChoices也经main读取，返回冻结labels值，不留adapter引用跨线程。其它点击行为/等待次数不变。
2. popupEvidence要求调用线程为主线程，接收非空原List及原expected menu labels。读取前核同一List确实attached、唯一Inspector祖先root、非Activity decor、PopupWindow$PopupDecorView、同root/token/display；不凭class近似或哈希相同接受对象替换。可复用slotPopup的只读校验，但不得删除原后续slotPopup及transition/Dialog门禁。
3. 冻结adapter对象、adapterCount、firstVisiblePosition、childCount和每个实际child强引用。逐项require非空，position必须在原adapter范围内；完整labels精确等于原expected，不只比数量。visible child数量只要求合法且与实际child快照一致，**不要求所有adapter条目同时可见**。每个child的parent仍为原List；文字TextView.text非空，否则明确失败，不用空串/跳过节点。仍保留全部actualNodeTexts、actualNode observation、adapterPosition/text与noIndependentWindowProtocolClaim。
4. 读取结束在同一main块再核adapter对象/数量、firstVisiblePosition/childCount、逐index child对象、原root/token/display未变；不一致立即失败，不重试。额外读取root/list布局/attachment、adapter与child身份、数量/position合法性写诊断，强判断用对象引用，不只System.identityHashCode。
5. 在main读取前把可变诊断对象挂到原report独立字段（按slot或negative case索引），逐阶段填入已读值。null/不一致抛带slot/index/phase的明确错误；原catch/finally仍保存business.json并保原异常。诊断失败不能把尚未完成的菜单记成PASS或伪造完整证据；无需新截图/窗口协议。原后续slotPopup/slot chooser身份、10秒await、5秒frame与四零差全部保持。

## 验证与交接

静态对照原字段未删、无Instrumentation线程View读取残留于上述链、无fallback或重复取菜单；核nullable平台值与Kotlin类型。小离线控制流反例可覆盖null child/text、adapter换对象/变count、firstVisiblePosition变化、child重排、token/root替换与合法部分可见列表；说明模型不是Android执行。diff检查后冻结源码SHA交Astra，root统一编译和下一正常批次CI，不微推、不绕必需检查。

真实新运行才证明此NPE不再出现；若失败变为明确null/身份错误，应保原件继续定位，不能删证据换通过。本计划不授权修产品、加等待/重试、忽略null或关闭窗口门禁。无设备协议/写入/未知重发变化，不发版。回退仅revert本窄增量，保留原日志与ZIP；当前仅计划，未改实现。

## Astra 静态终审（2026-10-23）

本窄增量静态通过。独立读取实际diff并核 `CurveVisualMigrationChecks.kt` SHA256为 `2a7f22494c66baa19d86b190823c8f264e16b55232cd11cf0d79abe0c0cd777e`；source-return诊断无回退，新增范围符合计划。

positive/negative的原List获取和popupEvidence都在一次main块内；helper强制主线程、非空原List，不再有nullable fallback。openLibraryMenu的choices唯一调用已包main。adapter/items/child/TextView读取均在该快照内；requireNotNull保留真实失败，不跳节点。before/after复用原slotPopup强校验，额外核adapter同对象/count、first位置、child数量及逐child同对象/parent、root/token/display原对象；合法部分可见菜单不被误要求全量child。

原localizedAdapterChoices、visibleNodes、actualNodeTexts、observation与noIndependentWindowProtocolClaim均保留；menuDiagnostics在main前挂入report，失败phase/已获数量保留，原catch/finally写盘及保因路径未删。positive后续slotPopup仍使用同一个menuList，在同main内继续执行；原Popup→Dialog过渡门禁、10秒await/5秒frame/四零差无改变。未见本增量的Kotlin类型静态阻断；真实编译仍由root完成。

独立diff whitespace检查通过，未运行设备或Gradle。此结论只关闭已证popupEvidence跨线程读数缺口，不认定旧NPE唯一对象、不宣称新运行通过，也不将此窄审外推为整个Curve无其他线程问题。未改源码、提交或推送。
