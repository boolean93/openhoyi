# Import 512cf48 重建失败：限定因果核查

## 结论

**parent-root 新方案仍未实际成功；严格恢复失败仅 raw.scrollY 变化。** 当前证据不支持改产品或放松相等。先保留已证事实；下一最窄动作是只读解码现有原生trace的两次 `ViewRootImpl.W#hideInsets` 关联服务端请求，不新跑CI、不增通用遥测。

run40057873441，source `512cf48bf1d95fffd9ff3874268632933654b80b`，artifact12546701995；原 ZIP `build/task-first-evidence/ci-512cf48-curve/artifact.zip` 39809309B，SHA256 `a788fc5b3d0b32af6820d9d7ead5a5a27715d56e21fd90d0b8bd4b52fabf1d61`，root核远端/484CRC。本次直接从ZIP读取state/probe/causal/stability/原生envelope及IME日志，未全解包。派生摘要 `import-causal-summary.json` SHA256 `fecef8f606613f3ab9b27065ce374e3f909767bbd9ff6b5369448d9dcdeccff1`。

## 1. root解析边界

旧owner raw/middle/host均count1。replacement-post-created于209.092650s只执行一次resolver，止于page-root-read：raw与formScroll父链共享顶层LinearLayout identity123708874，但均 `parent_not_view`，尚未到content FrameLayout/decor，异常 `Import content ancestor: not owner decor`；三count均null/not_evaluated。fallback_partial保原异常，没有重试。

新raw引用202297027、formScroll196489849已真实存在。不能将postCreated阶段的未挂窗父链判成产品丢根；后续product host_attached在209.139252s，新field真正attached的show_request在209.542211s。该生命周期阶段过早要求decor祖先，是探针假设问题，不是产品建树证据。部分探针未持有新field/host，因此后续raw范围/scroll的逐帧变化仍为unknown，不得补写猜测。

## 2. 唯一字段变化与实际先后

`raw-restoration.json`逐键对比：text（含空格换行）完全相同；selection1076..1084相同；focused=true、scrollX=0相同；**scrollY4885→4281**。严格比较发生在212.329396s，截图副作用尚未执行。

| 单调时刻（s） | 原件事实 |
|---|---|
| 209.006679 | clients原生trace：ViewRootImpl.W#hideInsets |
| 209.542211 | 产品唯一show_request；active/focus/attached=true |
| 209.588764 | clients原生trace：InsetsController#hide |
| 209.710479 | replacement探针实际IME=false |
| 209.979408 | clients原生trace：第二次ViewRootImpl.W#hideInsets |
| 210.109901 | 产品接RESULT_SHOWN(2)，当时实际IME=false、host1040px |
| 210.155766 | clients原生trace：第二次InsetsController#hide |
| 210.636627 | replacement探针实际IME=true |
| 210.639096 | 产品apply_scroll_after_native_result：host329px、raw layout4966、content81、range4885，实际applied4885 |
| 210.647573 | 产品自报RESTORED_AFTER_NATIVE_IME_RESULT_AND_LAYOUT |
| 210.705969 | replacement探针实际IME再次false |
| 212.329396 | 严格比较：IMEfalse、app高1391、raw高721、scroll4281 |

产品自报恢复时与之后真实窗口状态分开。IME后续收起伴随raw视口增高，与scroll被收敛到较小范围吻合；raw高从117到721差604，scroll4885−4281也为604。不能因此证明具体隐藏调用者或说保存的scroll本来错误。

## 3. 已排除与尚缺

- fixture的显式 `unfocus(activity,nativeTrace)` 位于原严格比较成功及finally之后，本次比较抛错后不会到达；causal events没有fixture_unfocus事件。失败诊断发生在catch，晚于比较，不能解释比较前scroll变化。
- ImeRecreationTrace本身只读；本段Import产品只show/恢复，没有显式hide调用。日志pid2714确有HIDE_SOFT_INPUT及HIDE_SOFT_INPUT_BY_INSETS_API，但request tag不可仅凭临近时间与app window token混同。
- 现有envelope说明两次系统hideInsets先后存在；不能仅凭该调用名断言是谁触发服务端hide，也不能认定是fixture主动hide、产品onCreate或旧window继承请求。需现有clients/managerservice/service protobuf里实际token/请求状态关联，若字段不存在就明确不可辨，不制造映射。

## 4. 下一最窄动作及修复边界

先只读定向解析现有 `ime-native-traces/light-1.0-import/` 三份winscope，只关心上述两个hideInsets及对应新旧window/input connection/请求状态；保原member SHA与单调时刻，不整包解压，不新场景或再次采集。该原件若不能给调用链归属，提交“现有trace不足锁定hide来源”，不把结果码/时间接近写为唯一因果。

探针触发点另有明确窄方向供root裁定：postCreated只登记实际具名raw的首次真实attach回调，随后在该生命周期事件执行**唯一一次**resolver，保原共同content祖先/同decor/三个count1；不是延时、轮询、post重试或重复resolver。不得为满足早期探针改产品。此方向尚未实施，先完成现有trace归因；不能声称修探针就修了真实IME/scroll恢复。

不改Editor、产品恢复合同、single resolver、5000ms/四零或原严格rawState相等；不补show/hide、不重复申请IME、不写回scroll盖失败、不缩字。当前无产品修正方案获证。

来源工作分支feature/task-first-ui（本轮Machine已推94473bf）；后续若获准仅探针修正则另列确切实施文件/验证，通过root普通PR #4→develop合批。无设备/发布/签名/升级操作；任何后续回退用新revert提交，保原件和未知数据，不清记录。本报告只读、未改源码/构建/提交。

## 5. 原生三份 trace 进一步解码（2026-10-24）

已只读解析现有三个原件，不新增采集。派生 `ci-512cf48-curve/ime-selected-native-fields.json` SHA256 `99f4d354d37857526735ac5f7fe080c211da44c519352d65c2066eed8746797f`，保每原member及entry SHA。依据AOSP android14-release `core/proto/android/view/inputmethod/inputmethodeditortrace.proto`、`inputmethodmanager.proto`、`viewrootimpl.proto`、`server/inputmethod/inputmethodmanagerservice.proto`、`inputmethodservice/inputmethodservice.proto`解命名字段；service原件实际另有field3包装，与InputMethodService.dumpToProto显式INPUT_METHOD_SERVICE层对应，已按原wire结构拆开。未出现的optional字段不伪造为采样值。

新增确定事实：

- 208.998849s manager `hideSoftInput` 对同pid2714/uid10141的ClientState290109c，seq70，focus/target均 `fc70eca ...CurveImportActivity`；208.999927s IMS收到hide，209.006679s客户端W#hideInsets时IMM served/next均字符串null。
- 209.541647s manager startInput/windowFocus；209.542280/500s IMS finish/start input；209.545184s manager show转seq71。209.544783s客户端show时served/next均新EditText `c0ecec3`（即202297027），同DecorView15ca0a4，非旧raw。
- 209.802247s manager再hide，仍同client/window、seq71；209.975825s IMS再收hide，209.979408s W#hideInsets时served/next仍同新EditText；210.155766s客户端InsetsController#hide也仍该field。
- 210.666410s manager第三次hide调用记录仍同seq71/client/window，紧接产品210.647573s恢复标记、早于210.705969s实际IMEfalse。三份trace没有按同一请求ID连接这些入口；IMS token是自己的binder，与app窗口token不等价。

**仍不足以形成产品最小修复。** 解码确认隐藏已进入系统服务→IME服务→同新窗口，且新input connection已建立；不能解释为“只有旧输入未绑定”。但这些proto schema没有该hide的调用栈/明确调用者归属，也不携带与ImeTracker request tag的一一关联字段。不能凭同seq/client或紧邻时间把第三次hide指定为某一旧请求继承，更不能把日志中HIDE_SOFT_INPUT_BY_INSETS_API当作应用显式API调用证据。

因此本轮停在已有证据边界：产品修复不获证、不改合同、不新增遥测框架或重跑。另一个独立、可证的探针问题仍是postCreated过早；若root决定先补此缺口，应另按首次真实attach触发唯一resolver的窄方案评审，不声称它会消除IME隐藏。后续History独立规划不等待本项。
