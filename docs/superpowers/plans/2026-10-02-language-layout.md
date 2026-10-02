# 多语言布局运行时检查

延续用户授权的原生功能对标和UI迭代，不连接测试设备。先检查现有组件，取得Android运行失败后才修UI。不会开启语言入口或修改控制门禁。

## 范围与方案

使用LanguageContextChecks已经重建并绑定的真实Home Activity，在八语言×深浅主题下创建生产HoyiUi导航/标题/按钮的离屏视图，按320/360/600dp真实measure/layout。覆盖五种当前导航选中态，要求五个文字完整、子文字边界在父组件中、点击区域至少48dp；标题、副标题、危险按钮使用真实资源。不能点击这些离屏按钮。直接验证活动杯现存固定停止按钮的文字layout完整，不重新创建停止按钮。

组件检查使用从已重建Home派生的显式320/360/600dp配置和未启动的仅视图LayoutHost（MobileTheme），要求配置宽度与紧凑分支匹配，因此只证明紧凑组件测量，不能冒称覆盖宽屏横向导航、整页滚动/重叠、字体缩放、视觉对比度或母语质量。后续仍需宽屏与五页真实内容/详情/错误状态矩阵，真实Service通知刷新条件，以及独立BLE硬件验收。

## 执行步骤

1. 添加androidTest专属LanguageUiLayoutChecks，独立检查Android Layout行尾、ellipsis、行高度、水平范围与父组件范围；不能调用业务/协议/音频。
2. Context现有16组合调用组件检查；ActiveCup用例对真实停止按钮补文字边界；保留既有Service/偏好/同杯断言。
3. instrumentation新增独立LANGUAGE_UI_COMPONENT_LAYOUT_CHECKS_PASSED，CI必须实际命中，保留原所有检查。
4. 构建测试APK/Mock单测/Lint、只读审查后推送Mock CI；任何失败定向诊断并最小修UI；尚未运行不标通过。

本地证据：最新测试APK、Mock单测和Lint离线构建成功（/private/tmp/hoyi-language-layout-reviewed.log，107 tasks）。审查发现离屏body默认LTR遗漏，已显式设置并断言RTL/LTR；二次只读复审无剩余问题。检查器带实际Android省略/高度裁切负例，只有运行确认负例被拒绝及正常组件均通过后才记录布局验证成功。脚本bash -n和diff --check通过；云端运行待补，未修生产UI。

首版云端36950093899真实失败：language阶段报Compact layout fixture requires a compact Activity configuration。Cloud Home实际为宽屏，证明测试不能假设运行设备属于紧凑分支，未发现/未证明产品文字裁切。修正仅测试：每个width创建Home派生ConfigurationContext，给HoyiUi提供不注册/不启动/不挂窗口的LayoutHost，显式MobileTheme并委托getTheme，不改Home/App配置，保留所有原断言和两负例。宽屏分支仍另行安排。修正版本地测试APK/Mock单测/Lint成功，运行结果待第二次云端。

修正版本只读复审：SDK35资源/普通系统服务经已附加base Context可解析，getTheme委托避开Window初始化；当前生产组件构造/测量没有已识别未初始化Window调用，配置独立且不改变Home/Application，原断言保留。未声称已运行；等待新云端结果。

第二次云端e8dde69 /36950911811实际失败于产品导航：ja dark=false width=320 selected=HomeActivity，プロファイル text Layout 38px、available 36px，vertical text overflow。记录于/private/tmp/hoyi-language-layout-36950911811/errors.txt。原固定bar62dp+item MATCH_PARENT在换行时约束文字高度。生产HoyiUi最小修正bar最低62dp/WRAP_CONTENT、item最低50dp/WRAP_CONTENT，不改文字/字号/颜色/点击/位置；常规中文单行保留最低62dp，长文字可增高，主体权重区域随导航自然减少。双APK/Mock testAPK、双单测/Lint本地成功（/private/tmp/hoyi-navigation-wrap-fix.log，170 tasks），只读审查无问题，最终云端验证待补。

最终运行证据dfc4c55：Verify native app36952198505、Verify Mock lifecycle36952198311及Capture Mock UI36952198275均success。已核对Mock产物的LANGUAGE_UI_COMPONENT_LAYOUT_CHECKS_PASSED：8语言×2主题×3宽度×5选中态共240个紧凑导航fixture、标题/按钮与八语言现存固定停止按钮全部通过，两裁切负例实际被拒绝。此前日语38px/36px裁切的断言保留，导航wrap修正后通过。原Context/活动杯/图表/通知与五项音频回归标记均在产物中存在。下载地址本地/private/tmp/hoyi-ui-service-notifications-36952198311。截图任务只确认完成并下载产物，未在本轮逐图视觉核验，不能宣称整页视觉/宽屏或母语验收完成；仍无真实设备操作。

下一步宽屏/字体扩展（用户持续授权的既有计划）：保留320/360/600dp及普通字号全部断言，增加700dp边界与1000dp宽屏、fontScale=1.3；每fixture确认实际配置和导航方向，放大字号时要求每个原生TextView.textSize确实大于同宽/同选中态的fontScale=1实际基线，而不是仅检查配置字段。原文字/父边界/48dp/RTL/两个裁切负例不删；总8×2×5宽×2字号×5选中态=800导航fixture。新增独立CI必需标记。只新增测试，先运行后按真实失败修生产UI；这仍不证明五页全部内容/滚动/图轴和所有系统字号，语言入口保留未完成状态。

扩展矩阵本地证据：Mock AndroidTest APK、Mock单测及Lint成功（/private/tmp/hoyi-language-wide-font.log，107 tasks）；bash -n和git diff --check通过。只读复审确认800组合、字号基线键、700dp横向分支及原负例断言保留，无生产代码改动。云端Android实际运行待验证，不能以编译结果标记宽屏/字号布局通过。
