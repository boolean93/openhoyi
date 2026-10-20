# Window geometry比较的精确等价优化

当前task-first短分支→develop PR，root合批推送；只androidTest取证实现及纯函数测试，不改产品/设备/协议，不发布。回退仅恢复原一行字符串比较。来源为708 Settings实际timings：Machine light1.3比较2.582572358s，surface-after约2000ms→comparison_complete5342ms；原5000ms未通过。中间proof序列化已撤掉，不能再假称这是本次瓶颈。

## 最小owner范围

`NativeWindowFrameEvidence.kt`中`before.toString() == after.toString()`一处替换；新增同androidTest包内纯比较helper；独立纯函数测试/必要测试运行脚本。不动runner、capture/bitmap/geometry字段、timing名称、终态完整原件或预算。Sol_bcc顺序负责，不能同时修改其他owner测试。

## 必须保持的语义

已核本机官方SDK34 `org/json/JSONObject.java:699–763`、`JSONStringer.java:236–262,312–362`：对象按实际迭代顺序序列化；数组按索引；Number由`JSONObject.numberToString`产生文本；Double负零为`-0`；整数与可表示的1.0可能输出同文本；JSONStringer对String逐字符转义；JSON异常会令顶层toString返回null。

纯函数返回值必须等于原两次toString的Kotlin nullable String相等结果，不能改成JSONObject语义相等、无序map、Double近似、hash-only或忽略字段。建议低风险快路径：

1. 对两棵树完整预检：仅原生JSONObject/JSONArray（精确class）、null/JSONObject.NULL、String、Boolean及Byte/Short/Int/Long/Float/Double；所有Number必须能由当前运行时`JSONObject.numberToString`正常产生。不得发现前面不同就跳过后面预检，否则后部非法Number导致两边toString皆null的旧语义会丢失。
2. 完整预检通过才递归精确比较：对象数量、**迭代顺序及每个key**、每个value；数组长度/顺序；String按UTF-16内容相等（官方quote转义对字符串是单射，非trim/Unicode规范化）；Boolean及null按原JSON literal类别；Number逐个用当前运行时`JSONObject.numberToString`的结果相等，绝不转Double比较。String `"1"`不能等于Number1，Boolean不能等于String；Java null数组元素与JSONObject.NULL依原JSON literal同为null。
3. 任意非支持类型/容器子类/非有限数值或预检无法证明时，**整对回退原表达式**。不把未知值自动判false或抛新门禁。保原异常传播；不捕OOM/StackOverflow伪造相等。不能只对一个子树回退，因为顶层序列化失败语义不同。
4. 不生成任何整棵tree字符串、排序或深拷贝。两遍树访问可接受；先证明精确性，不混入SHA/diff融合优化。此处geometry为既有主线程捕获的独立JSON，不新增UI读取/并发访问。

## 验证合同

现mobile unit依赖`org.json:json:20240303`，**不是Android SDK34实现**；不得仅跑它便宣称number/order/null与设备等价。纯函数必须用官方Android34 org.json实现作旧算法oracle（保存源码来源/SHA的离线测试harness可行），另可在既有androidTest构建证明接线。不要为校验启动新设备scenario或改现验收门禁。

- 同/不同嵌套对象与数组；同键不同插入顺序；删除/增加/更改每一类字段；空对象/数组；null literal与NULL；数值1/1L/1.0、Float/Double、正负零、极值与科学计数；String包含引号/斜线/反斜线/control/CJK/代理对，顺序/原字符差异必须保留。
- fallback：自定义对象与其String输出、Number子类、非有限数值（沿当前JSON API可合法置入的方式）、JSONObject/JSONArray子类；保旧顶层null返回语义，尤其两边前部不同而后部均序列化失败。不可通过删掉此类输入声明通过。
- 使用已保全708 Settings/Scale的实际proof内geometryBefore/After逐对比较新旧结果；同时在复制的真实JSON上做单字段/顺序/数组变异。保存输入路径/SHA、原结果/新结果及计时。不要改原证据、不要只对重新排序的字典测。
- 成本对比在相同官方实现/同一输入下报告median及分配范围；离线速度不等于Android实际耗时。真实下一CI沿原timing字段验证5000ms内结果，未实测前只称静态/等价测试通过。

禁止增加5000ms、掩像素、缩geometry、绕hidden API、固定sleep或把相等性改成近似。若官方实现harness无法可靠建立，先保原比较，不以JSON-java结果代替Android证明。

## 最终静态审查：可交统一构建

Astra已全文核四文件：两棵树完整预检，精确容器class/原key迭代顺序/数组索引、String/Boolean/null literal、受支持Number调用当前JSONObject.numberToString；未知值/子类/非有限数值整对回退原两字符串相等。没有提前部分相等冒充顶层null语义，没有略字段或排序。caller仅替换原timed块内比较一行；5000ms、捕获顺序、identity、四零差、geometry观察及完整终态原件不变。当前无剩余静态阻断。

| 文件 | SHA256 |
|---|---|
| WindowGeometryJsonEquality.java | 641ecc3bd8e92fe82d70c5392cc3653957df635b9c6ce57c7a8d20aacb91e2da |
| NativeWindowFrameEvidence.kt | cdedad6c29c58f035108aec7cb19a523fa2fbc6011d4c0d008a57793e12655aa |
| WindowGeometryJsonEqualityOracle.java | a8f77bde21d1326d2f193c6534828e0f0179adbb46d1aa2d61cc5dd33a8aa077 |
| test_window_json_comparison_equivalence.py | f1d04f661df510d89b4f75adc5a8fce5ad11d8ea1c05399d182be6208cbe1b3c |

验证使用本机SDK34六份未改动org.json源码，仅补annotation stub；非JSON-java替身。owner与root独立运行最新oracle均57组通过。已核report输入为708 Settings PR实际merge847a2bef…的Machine light1.3和708 Scale push HEAD7082e04的两attempt；早期误选dc的报告已替换。源码/ZIP entry/输入SHA、逐案结果在`build/task-first-evidence/window-json-equivalence/report.json`。

最新host样本：Machine旧/新median约15.12/8.11ms、分配约38.53/8.56MB；Scale约3.82/2.24ms、10.56/2.93MB。只证明同官方库离线结果等价与减少分配，**不证明Android本轮5000ms已通过**。还需统一AndroidTest编译及下次真实CI原timing；旧Settings同步FAIL保留。
