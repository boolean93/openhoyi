# 真实主页面多语言布局检查

**目标：** 延续已授权的原生功能对标，补齐共享组件测试没有覆盖的五个真实Activity的初始页面与设置分组布局。只操作隔离Mock，不安装或连接真实设备。

**方案：** 新增androidTest专属LanguagePageChecks，独立pageChecks运行模式。在云端模拟器真实系统配置下分别运行360dp宽/普通字体，以及1280dp宽/1.3字体；每种配置八语言×双主题×五页，合计160个主页面。系统配置值必须由Activity实际资源读回确认。先运行断言，只有真实产品布局失败才修生产UI。

## 执行步骤与文件

- [ ] 新建`mobile/src/androidTest/kotlin/io/openhoyi/mobile/LanguagePageChecks.kt`，通过现有独立语言/主题偏好重建Home，保留原Service与非语言偏好；其它四页每次新建并finish，避免NEW_TASK复用导致startActivitySync无限等待。失败仍尽力恢复偏好与首页。
- [ ] 核对每页真正挂窗的五项导航文字、RTL、48dp目标、完整屏幕可见、文字fit；直接root内容与固定启动/停止行不得侵入导航。全页已布局的非输入文字检查行高/行宽/行尾，保留曲线/历史摘要明确设计的省略，不用列表摘要代替操作文字完整性。
- [ ] 首页/萃取/设置纵向ScrollView逐步滚到末尾，每步保留导航和固定操作行；设置页只点击已核对的settingTabs展示五组本地面板，检查其文字；不点击配置写入、去皮、预热、启动或停止。初始萃取页启动入口必须完整可见；实际活动杯停止已有独立检查，此用例不启动新杯。
- [ ] `BrewAudioInstrumentation.kt`增加独立pageChecks分支和阶段/最终标记。`scripts/check_mock_lifecycle.sh`在原音频/语言运行后新增两个有600秒限时的页面调用，先force-stop同一Mock包再变更模拟器配置，最终恢复原1600×1000/200dpi/1.0字体；保留原全部通过标记。
- [ ] 运行Mock测试APK、Mock单测与Lint，bash -n、diff --check，独立只读审查后提交/push；云端构建和Mock必须完成，并读取两份页面产物。若失败，先定位产品/测试原因，再最小修复并重跑。

本轮不覆盖动态安全告警、弹窗、所有详情/列表数据、全设置交互、母语质量、图轴标注、真实前台Service或BLE/硬件。语言入口仍关闭；不得把160个初始页面配置当作全功能完成。

本地Mock测试APK、Mock单测和Lint成功（/private/tmp/hoyi-language-pages-reviewed.log，107 tasks）；bash -n和diff --check通过。独立审查指出切设置分组后的VSYNC竞态，已在每次checkPage前等待root与已显示文字实际完成layout；审查其余部分因代理配额中断，不记录完整复审通过。自查补强真实宽度溢出负例、失败后关闭子页/回到Home再恢复偏好、ERR恢复模拟器设置。脚本四种Mock模式的成功0/失败7/超时124、固定argv/600秒和partial stdout通过独立离线执行核对；未执行实际ADB。云端运行仍待验证，暂不修生产UI。
