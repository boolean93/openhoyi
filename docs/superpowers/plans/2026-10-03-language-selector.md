# 设置页应用语言入口

**目标：** 落实native-language-parity已有的独立语言偏好设置步骤，在Alpha和Mock使用相同原生入口，不新增任何设备指令。已有资源完整性、统一Context、主要页面/同杯/图表/通知及详情/取消检查提供开发基线；母语与其它特殊状态/真实BLE仍保留未验收，不再把底层能力永久留在仅测试可调用状态。当前不安装真机。

**方案：** 新建AppLanguagePreferencesCard，放在机器设置页的本地偏好区域，与音频偏好分开。八个选项使用稳定的语言自称与原规范tag；只存app_language。保存失败保留原界面/选中项并提示；选择当前语言不重复保存/重建。保存成功后由Activity仅刷新通知显示并recreate；其他Activity沿用既有onResume语言检查。卡片不拥有Service、BLE或机器设置。四条新文案同时进入八语言目录。

- [x] 先补AppLanguage.nativeName与原生card API测试，编译确认缺API失败。
- [x] 增加八语言自称、AppLanguagePreferencesCard和设置页绑定，原控制回调/门禁不变；通过原偏好Selection三态处理结果。
- [x] 四资源（title/current/hint/save_failed）同步默认XML、source hash和七catalog，生成XML；修正机械目录基数而不放宽检查。
- [x] Mock页面instrumentation通过真实card打开八项选择，核对顺序/当前选中，选择当前项不重建；保存成功切换/还原与独立失败storage用例另补，只有完整实际入口回归后才标语言功能验收。
- [x] 单测、资源校验、双APK/双Lint与Mock测试APK、独立审查、云端Mock运行；所有原检查保留。不用编译/Mock替代真机同杯验收。


本地验证：2026-10-03：新增设置页原生应用语言入口，八语言自称、独立偏好提交、当前项免重建、失败保持旧语言/选中项及重试提示。成功后刷新通知显示并重建设置页；异步Service绑定后补刷新，不重启设备会话。Mock入口测试覆盖切换/还原、当前项、保存失败和同Service。最终双APK/双单测/双Lint及测试APK构建成功（170 tasks），19项Python、890键目录、889键生成一致性、两APK各7113模板和6转义回环通过，独立复审无阻塞。实际入口运行仍待新云端CI；本轮不安装/操作真机。计划docs/superpowers/plans/2026-10-03-language-selector.md。
最后一项云端运行现已完成，证据如下。

2026-10-03：语言入口0b94f39已完成云端实际运行。Verify native app37125094623、Verify Mock lifecycle37125094578及Capture Mock UI37125094627均success；已读取Mock产物compact/wideFont各80 PAGE_START和三项PAGE_LAYOUT/DETAIL_DIALOG/SELECTOR_UI标记，原八语言及五音频标记完整。实际选择/当前项/保存失败/切换还原/同Service检查通过。完整构建日志确认39,823协议检查、32,856通知回放、92会话场景，以及两APK各7113模板与6转义回环。截图任务成功未逐图新视觉核对；不宣称母语、真实通知生命周期、BLE或硬件完整验收。产物/private/tmp/hoyi-language-selector-37125094578/mock-lifecycle。
