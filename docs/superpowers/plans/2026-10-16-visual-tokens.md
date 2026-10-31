# 视觉参数首批收敛

用户新增字体/间距与认可图一致要求，沿用设计规格§3及视觉一致性补充。develop66ebda9/ed01de0依赖，feature/task-first-ui → develop草稿PR#4；遵守docs/git-workflow.md，仅普通推送、不合并/发版/设备安装，追加revert保留数据。

首批文件TaskVisualTokens.kt/HoyiUi.kt/ExtractionActivity.kt。统一共用卡片16dp内边距/组间距/圆角、8dp相关控件间距；按钮正文16sp、目标48dp或主按钮56dp、圆角12dp。实时主值40sp/粗体/等宽数字，去掉生成稿没有的两块浅绿读数底板，保持实际内容测量和图表/停止检查。标签显式采用sans-serif，强调sans-serif-medium；这些是当前系统字体策略，不能冒称已经与ImageGen字体完全相同。

不批量替换所有数字或控制宽高，逐页尺寸差异继续审计；字体文件/字重/中文fallback实证尚待补齐。保留语言、字号、主操作、安全、原生表单及图表量程全部门槛。验证完整JVM+两MockAPK，后续精确CI及同状态截图；在视觉配对前保持未验收。

首批已实现并本地验证：/tmp/task-visual-tokens-build.log完整JVM/两MockAPK构建46s成功，297tests/0fail/0error/1skip。测试数量包含前一批采样探索纯函数。当前新tokens尚未原生CI/读图，不将代码值一致当整页视觉一致。待上一轮849e1c1终止后再推送，避免取消其执行证据。
