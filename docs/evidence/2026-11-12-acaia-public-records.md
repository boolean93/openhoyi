# Acaia 组合记录独立公开证据

参考 MIT 项目 [aioacaia 固定提交](https://github.com/zweckj/aioacaia/tree/0d542170b3c5e8db8dd3983d46318d1066bd1f6b)：
- [parser.py](https://github.com/zweckj/aioacaia/blob/0d542170b3c5e8db8dd3983d46318d1066bd1f6b/src/aioacaia/parser.py) 的公开说明和记录宽度：重量5/6字节、电池6/1字节、计时7/3字节、按键8/1字节、ACK11/2字节；记录链可组合，最后重量有效。
- [test_parser.py](https://github.com/zweckj/aioacaia/blob/0d542170b3c5e8db8dd3983d46318d1066bd1f6b/tests/test_parser.py) 包含组合通知案例。
- [LICENSE](https://github.com/zweckj/aioacaia/blob/0d542170b3c5e8db8dd3983d46318d1066bd1f6b/LICENSE)：MIT。这里仅记录协议事实并独立实现，没有复制源文件、引入库或 SDK。

用户真实通知另见 Lunar 诊断回放计划。新增复杂组合测试明确为按公开布局构造的 fixture，不冒充用户抓包。

本实现比参考更保守：完整校验、整条记录链成功才输出；已知截断或任何无效重量不发布前缀读数；未知边界不猜。仅跳过电池/计时/按键/ACK，不从其推断控制成功。

该公开参考仍读取重量低16位、整字节单位；所以它不足以证明另一调研提出的 u32 和 unit bit7 假说。本轮不靠受限制的 AAR 反编译材料补齐这些假说。高两字节非零改为显式不支持，避免可信读数静默截断；单位保持原始白名单，不将0x82当克。

未修改连接时序/端点/重试：当前用户实际 GATT、订阅和初始化均成功，缺乏现场证据表明必须一起改。SDK 的 tare/timer API 也不等于本项目物理控制已验证，Lunar/AP 仍不开放控制。
