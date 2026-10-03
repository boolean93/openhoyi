当前状态（2026-10-03）：八语言中文源/目录各890键，七份Android译文各889键（共享app_name排除）。设置页语言入口已开发，本地构建与机械资源验证通过；真实入口切换/还原/当前项/保存失败及同Service已在云端Mock通过（37125094578），母语与真实Service/BLE仍未验收。下文早期计数和入口状态是历史阶段记录。

# 原生语言资源准备

当前完整草稿目录为 `catalog/<tag>.json`，七语言各872键、共6,104条，中文源快照见 `catalog/source.json`。七语言目录已转换为Android资源并打包，各871键，共6,097条（共享app_name排除）。语言选择入口尚未开放，母语/布局/运行时Context仍需验证。

完整范围见 `docs/plans/native-language-parity.md`。中文基准在 `mobile/src/main/res/values/strings.xml`；原版还支持英语、俄语、泰语、阿拉伯语、日语、韩语和西班牙语。

`drafts/safety/<tag>.json` 是首批 10 个安全/初始提示的翻译草稿，七语言共70条。保留三个不同条件：结果未知且断线、结果未知但可连接、萃取中断线；停止请求处理中不能说成已停止。文字不参与按钮资格或协议判断。

分组draft文件仍只保留来源证据；完整catalog已经生成res/values-{tag}/strings.xml。应用默认语言是进程偏好的简中，当前没有语言选择入口；正式启用仍需语义、长文字/RTL与服务显示更新核验。

`LanguageDraftTest`保留分组来源/格式检查；原临时禁止语言资源断言已先复现失败，再改为七份安装资源各871键且无app_name覆盖。这些检查不证明母语准确性或屏幕排版。

## 通用导航与基本操作草稿

`drafts/basic-controls/<tag>.json` 新增七语言各47条、共329条：通用导航、机器/秤连接状态、去皮状态、加热/照明标签、密码输入提示和应用信息。去皮的写入、等待、收到零值与结果未知分开，连接READY只表示当前会话就绪，不增加硬件验收声明。应用版本与包名、导航无障碍描述保留原位置/类型参数。

两组分组草稿共57个不同资源键、399条，已原样纳入下述完整catalog。不能只用分组文件提前启用语言。当前872键资源池的完整草稿已补齐，正式资源转换已完成；原始曲线/用户元数据的展示策略仍待完成。

LanguageDraftTest新增组范围与格式检查，允许调整位置参数的出现顺序，但参数索引、类型与次数必须一致。JVM测试工具新增template读取原始模板，避免在未传参数时误调用String.format；不修改生产资源加载。机械检查不等于母语审校或RTL/长文字布局验收。

## 完整目录与校验

`catalog/source.json` 保存当前默认XML的SHA256及解开Android外层引号/换行后的872键中文模板。`catalog/en.json`、`ru.json`、`th.json`、`ar.json`、`ja.json`、`ko.json`、`es.json` 各完整覆盖872键，已有两组57条草稿原样复用。完整资源目录不等于所有元数据或界面已经支持该语言；用户输入、曲线名称/tips、规范历史原因仍单独处理。

运行：

```sh
python3 -m unittest discover -s localization/tests
python3 localization/validate_catalog.py
```

第二条只有七语言全部通过才退出0；源快照过期/内容不符退出2；翻译缺失或格式错误退出1。重复JSON键不允许静默覆盖。检查参数索引、类型、补零宽度、次数、换行、空值及隐藏方向控制字符。其中10项校验器Python测试覆盖合法/非法格式、源哈希/内容、缺语言与错误文档类型。校验器只读，不生成或启用Android资源。

`LanguageCatalogTest` 对七语言全部6,104个模板按对应Java Locale实际格式化，核对中文源与默认资源、位置参数与渲染参数；用7检查补零与本地数字输出。机械格式通过不等于母语或布局验收。Android两变体测试已登记catalog/drafts JSON和默认XML为任务输入，改变数据不能复用旧的通过结果。CI先执行Python检查，再执行现有Gradle测试。

日语非既有57键的180条用语已润色为読み戻し/書き込み/読み込み/スリープ/スリープ解除，拨杆告警明确レバー位置；其它语言保留写入、等待、确认、未知的区别。尾水许可用语只描述现有预警中的允许使用剩余水，不扩大为水位安全或无告警。

转换已排除共享app_name译文，并按编译资源验证Alpha/Mock名称。统一Context/偏好和部分持续事件刷新已接入；剩余嵌套提示、入口、Android运行时及RTL/长文字视觉核验未完成，当前不开放切换。运行时审计见docs/plans/native-language-runtime-audit.md。

## 生成资源与编译核对

```sh
python3 localization/android_resources.py          # 全目录通过校验后生成，已有相同文件不重写
python3 localization/android_resources.py --check  # 只读检查生成漂移
python3 localization/verify_apk_resources.py \
  --aapt2 "$ANDROID_HOME/build-tools/35.0.0/aapt2" \
  --android-jar "$ANDROID_HOME/platforms/android-35/android.jar" \
  --alpha mobile/build/outputs/apk/debug/mobile-debug.apk \
  --mock mobile/build/outputs/apk/mock/mobile-mock.apk
```

修改catalog后重新生成XML，勿直接编辑生成文件。转换对七语言先完整验证，再写文件；写盘IO失败可能留下部分输出，下次--check/CI会报漂移，不承诺跨文件原子写入。

19项Python测试包含5转换器测试、4APK dump/比较测试及原10校验器测试。AAPT读取器曾漏掉真实无缩进空行，已用失败测试复现并修复；不通过改变译文掩盖问题。APK检查每次实际编译/链接六组复杂转义fixture（双换行/空格、制表/CR、反斜杠、单双引号、@?引用前缀、XML符号及参数），再核对两个APK的默认/七语言文字，各6,969配置值。CI只读检查生成一致性，Gradle构建后检查APK；Test登记catalog和翻译XML为输入。

编译核对不是Android选择资源/Context/布局验收，也不证明硬件行为。工具输出解析依赖当前AAPT2格式，工具输出变化应先排查校验器，不削弱逐字比较。见docs/plans/android-language-resources.md。
