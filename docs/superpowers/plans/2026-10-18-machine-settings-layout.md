# 机器设置温度与回读布局

来源develop66ebda9+ed01de0，feature/task-first-ui→develop PR#4；遵守docs/git-workflow.md。依据machine-settings-v1候选及已认可通用规格，整理温度页；不改五分类、校验范围、confirm/prefill/render、回读和未知结果处理。

回读由四块强调底色改为中性两列摘要，辅助14sp/值24sp、8/16dp；应用设置与完整回读动作本批保留，视觉层级差异待后续收口。温度字段保留原EditText及按钮实例/回调，分为三组固定标签（含范围）+输入/操作行。宽屏标准字横排行，放大字或窄窗口上下排，不缩字体。原hint保留便于无障碍和既有定位；标签labelFor对应输入。待机输入也获得固定标签和16sp，但原单列动作不变。

验证Mock/AndroidTest构建、diff检查；现有设置Mock确认/真实写入门禁以及全页截图CI回归。构建不证明运行对齐。无设备/安装/合并/发布/签名版本schema变更；只本地提交，下一完整批次推送；回退布局提交。
