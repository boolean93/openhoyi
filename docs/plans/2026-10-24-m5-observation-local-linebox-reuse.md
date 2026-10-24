# M5 单次 geometry 内 TextView line-box 复用计划

> 仅规划，root读后交Sol执行；使用 executing-plans。当前不实施，不与History间距变更混为同一修复因果。

**Goal:** 减少一次geometry主线程观测中同TextView被多个tag祖先/自身重复展开的临时对象，同时保持原完整JSON和严格验收。

**Architecture:** 仿已验证Settings模式，geometry局部 `IdentityHashMap<TextView,JSONObject>`，只缓存单TextView的line-box值，不缓存外层容器几何。每次调用新map；原无cache observation仍可用于单点与等价对照。技术为现Kotlin/Android JSONObject，不加依赖或通用框架。

## 来源及因果限定

来源 `94473bf662edc819d8e337a93dc1ef152d5bf4a2`，develop基线 `66ebda95dc7986eec66f9fa0b7e6cbd565c74506`；工作分支feature/task-first-ui，PR #4→develop。执行前重核HEAD，保留History及其他工作者改动。

Sol对run40056754099的只读首错：Extraction observation约127行构造TextView JSON，经geometry953的tag节点map、captureTarget1033的frame geometry-after发生OOM；writer仅suppressed，不是首因。现源码每个tag容器和TextView都调用observation，后者递归全后代TextView，`task-page-frame`覆盖全树；同TextView多次新建lineBoxes是已证分配放大。日志没有具体失败view/tag/index，不指名唯一对象，不保证此改动解决所有OOM。此次不扩strict writer或其它页面观察器。

## 改动范围

仅 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/ExtractionHistoryVisualMigrationChecks.kt`：observation中的TextView值构造抽成私有textLineBox，observation可选接局部identity map，geometry建立/传递map，以及Scope准备前的有限Android合同比较。产品/NativeWindowFrameEvidence/strict writer/Settings/业务状态/host字段合同不改。必要新增离线验证脚本仅用于构造/别名计数，不把Python模型冒充Android getter等价。

## 实施步骤

- [ ] 将原 `nodes(view).filterIsInstance<TextView>().map` 内JSONObject表达式原样抽为 `textLineBox(text:TextView)`：字段/put顺序、NULL与缺字段、文本快照、行数组顺序、Float类型及可见性计算保持。不能把Settings更复杂字段复制进M5。
- [ ] observation签名增加可选 `IdentityHashMap<TextView,JSONObject>?=null`。cache非空时每identity首次构造，后续引用同完成对象；不同TextView即便同tag/text也不共用。外层semanticId/rect/visibleRect/complete及每层textLineBoxes数组仍逐观察构造，原完整重复展开输出不删。
- [ ] geometry入口断言/确认main线程，在该函数内new map，传给nodes map及本次charts rect的observation；其它独立调用默认不共享。不能cache字段、全局、ThreadLocal、跨geometry/before-after/frame、按tag或索引缓存；不改变nodes递归、过滤、排序或业务层生命周期。
- [ ] 审核所有消费方只读共享line-box；外层 `.put`装饰可继续，但不得向共享textLineBoxes元素追加role/替换字段。若现调用者会改其子对象，精确指出并停止候选，不以深拷贝每份抵消目的或删字段过关。

## 有限等价与内存验证

- [ ] TDD小树在原Scope prepare/install/业务Activity启动前执行：两个标记祖先包含同一个真实TextView、另一个同tag/text不同identity、一个未layout TextView；覆盖实际多行Layout/零省略与原NULL。同main turn执行无cache与cache完整展开 `toString(2)`，逐字节相等；cache.size等唯一对象数，重复引用对象确相同。不能将真实业务View换成synthetic。
- [ ] 第二次观测前改变小树文本/尺寸/scroll，再新map，证明新值重读且旧snapshot不随Editable变。保同一observed object无跨turn缓存，错误即失败不删检查。原型只有限构造，不另造业务场景或私有字形门槛。
- [ ] 实际SDK小树扩为固定有界重复祖先样本，在同一测试进程测构造次数与identity图：旧模式为重复展开次数，新模式为唯一TextView数；保完整序列化字节相等。这是确定的对象分配减少证据，不以波动heap差值设新CI门槛，也不声称已证明全部192MiB峰值可承受。可复用既有真实大geometry JSON做SDK序列化别名/独立对象等价及受限堆离线验证，但必须标明它不包含真实View getter和运行峰值。
- [ ] root新CI保持原所有Extraction状态/数据与完整geometry、原5s四零、strict restore。实际UNKNOWN_live是否越过和内存是否足够只看真实运行；若新的OOM仍在，保原首错/已完整产物，不删除节点、缩状态/证据、改变阈值或自动推广缓存。

该验证只证明一次观测getter/JSON等价及对象复用，不要求新矩阵，不无限追逐不同OOM。共享对象节省内存但序列化仍全展开，磁盘JSON结构/字节不应改变；不能把序列化输出变小列作成功指标。

## 验证交付/流程

Sol交精确diff/SHA及Android合同比较接线，`git diff --check`；静态核原完整表达式与所有cache生命期。root统一 `:mobile:assembleMock :mobile:assembleMockAndroidTest` 后原M5 CI，Android真实比较未跑前只签静态。无需另建泛化benchmark或重复修改已验strict writer。普通推送PR #4，必需CI和评审后合develop，不直推/强推。

无产品、BLE/机器/秤指令、库存/队列/未知记录影响，无发布/签名/升级/设备授权。回退新增revert撤销本fixture局部复用，保留原件及数据，不清记录、不取消既有失败门槛。当前只计划，不承诺OOM已修。

## 2026-10-24 冻结实现静态验收

Astra限定静态通过：唯一源码 `mobile/src/androidTest/kotlin/io/openhoyi/mobile/ExtractionHistoryVisualMigrationChecks.kt` SHA256 `2d4e5c4f6c4d49e1aa46295627b27cb25c17b8070befd6611d2424ecbc3b6172` 独立重算吻合。真实Android合同尚由root构建/CI执行，不把小树源码或编译冒充运行/完整峰值证明。

独立脚本抽取HEAD原map内表达式与新textLineBox，除缩进/抽函数return外token完全相等；原字段、顺序、NULL、Float行界与文本快照无改。geometry检查主线程，每次局部新IdentityHashMap，只传本次nodes及charts observation；outer观测/数组不缓存，不按tag共用、不跨before/after。节点筛选/排列与完整JSON展开保留。消费者检查：captureTarget只装饰geometry顶层，whole只读；会修改fullViewVisible的screenObservation明确调用默认无cache的observation，因此不污染共享line-box。

真实Android小树合同在Scope.prepare/install/业务Activity启动前同main执行；两个祖先重复展开+两个同tag/text不同identity+未layout对象，旧7构造位/cache3唯一，检查alias及不同对象隔离、NULL/零省略、两种完整JSON UTF8字节相同。随后修改文本/scroll并重新layout，新map重读且旧文本snapshot保持。此为有限对象图/重读验证，不是完整业务峰值或所有OOM闭合；没有删除业务场景或改5s四零。

`git diff --check`通过，产品/共享writer/Settings无本次修改；root统一构建。运行若仍OOM应保首错，不以缓存存在就宣布通过。

## Root 本地构建证据

在HEAD `7490d16aa3b9b7ad30bb6624bcb26009718d85d0` 加上述冻结修改构建 `:mobile:assembleMock :mobile:assembleMockAndroidTest`，36秒成功，91 tasks（4 executed）。构建前后303个Kotlin源文件SHA完全一致，日志及清单在 `build/task-first-evidence/ui-2026-10-24-m5-local-observation/`。

Mock APK SHA256 `94deec8c8d681fc4f146da2202591b10e6182989233229dcd13e825d15297b17`；androidTest APK SHA256 `7b2f5044df966569aa6a52770bdd48d04c30f45c9e54fcaa658196187d70ec25`。仅编译通过，未执行Android小树合同，不宣称OOM已解决；等待原CI运行。无安装、设备操作、合并或发版。
