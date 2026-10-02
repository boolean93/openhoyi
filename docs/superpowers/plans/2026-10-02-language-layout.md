# 多语言布局运行时检查

延续用户授权的原生功能对标和UI迭代，不连接测试设备。先检查现有组件，取得Android运行失败后才修UI。不会开启语言入口或修改控制门禁。

## 范围与方案

使用LanguageContextChecks已经重建并绑定的真实Home Activity，在八语言×深浅主题下创建生产HoyiUi导航/标题/按钮的离屏视图，按320/360/600dp真实measure/layout。覆盖五种当前导航选中态，要求五个文字完整、子文字边界在父组件中、点击区域至少48dp；标题、副标题、危险按钮使用真实资源。不能点击这些离屏按钮。直接验证活动杯现存固定停止按钮的文字layout完整，不重新创建停止按钮。

组件检查保留当前Activity配置的紧凑导航分支，因此只证明紧凑组件测量，不能冒称覆盖宽屏横向导航、整页滚动/重叠、字体缩放、视觉对比度或母语质量。后续仍需宽屏与五页真实内容/详情/错误状态矩阵，真实Service通知刷新条件，以及独立BLE硬件验收。

## 执行步骤

1. 添加androidTest专属LanguageUiLayoutChecks，独立检查Android Layout行尾、ellipsis、行高度、水平范围与父组件范围；不能调用业务/协议/音频。
2. Context现有16组合调用组件检查；ActiveCup用例对真实停止按钮补文字边界；保留既有Service/偏好/同杯断言。
3. instrumentation新增独立LANGUAGE_UI_COMPONENT_LAYOUT_CHECKS_PASSED，CI必须实际命中，保留原所有检查。
4. 构建测试APK/Mock单测/Lint、只读审查后推送Mock CI；任何失败定向诊断并最小修UI；尚未运行不标通过。

本地证据：最新测试APK、Mock单测和Lint离线构建成功（/private/tmp/hoyi-language-layout-reviewed.log，107 tasks）。审查发现离屏body默认LTR遗漏，已显式设置并断言RTL/LTR；二次只读复审无剩余问题。检查器带实际Android省略/高度裁切负例，只有运行确认负例被拒绝及正常组件均通过后才记录布局验证成功。脚本bash -n和diff --check通过；云端运行待补，未修生产UI。
