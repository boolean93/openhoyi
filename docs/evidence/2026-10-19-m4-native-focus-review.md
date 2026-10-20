# M4 长理由原生详情焦点竞争诊断

证据：ci-49a1b46-bean，source 46e2dbc79ca0732290db92b5c78ad097c82c86cb，light/font1.0/batch/failure-context。已实看原 PNG：库存流水 dialog 实际呈现完整长理由、末尾“该次后库存：71 g”及确定按钮。geometry 只录 Activity，且 sameCompositorFrameProven=false；不能据此声称异常瞬间已获取焦点。

源码 BeanWorkflowVisualMigrationChecks.kt:809 在 row.performClick 后的下一 main 立即 single 查唯一 focused 非 Activity decor；没有原生 focus 异步就绪等待，而 dismissDetails 已有等待。因此证据支持焦点竞争候选，不是完整因果证明。

最小修复只改测试：点击后用既有有界 await，等待唯一非 owner、attached、laidOut、hasWindowFocus decor；不得把全文内容通过放进等待条件，也不吞 single 异常或重试点击。就绪后 main 重核唯一身份，再原样执行 contains(longReason) 全文断言。dismiss 后仍等待 Activity focus 恢复。超时保原件，禁止固定 sleep/放宽全文/生产猜改。实际 CI 复现验证前仍未关闭。
