# LOCAL-LOG-06：协议回归与真机性能验收

Part of: [LOCAL-LOG-MAP](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/map.md>)

- 类型/标签：task / wayfinder:task、needs-triage
- 状态：in_progress，协议自动化与跨平台源码编译已执行，真机性能未执行
- Blocked by：[03](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/03-module-api.md>)、[04](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/04-storage.md>)、[05](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/05-integration.md>)

## 需求

以对外行为与实机指标验收，而不是仅验证内部函数或引用开源宣传数据。

## 场景

- 常态、突发并发、静默、超大记录/异常、过滤 lambda 与请求上下文。
- 队列溢出统计、UTF-8 轮转、时间回拨、多进程文件名与总磁盘预算。
- 磁盘满、文件不可写、尾条中断、锁屏/后台、强杀恢复与降级。
- flush 超时/取消、snapshot/clear/close 竞争、撤回及账号切换。
- 用户导出可解析、敏感内容测试样本不泄漏、旧调用不重复、新旧行为回归。
- Android 低端真机及 iOS 真机，所声明 desktop/Web 能力分别验证。

## 指标与完成条件

记录 caller p50/p95/p99、分配、峰值内存、worker 吞吐、CPU/能耗、队列高水位、丢弃、flush/导出耗时和恢复结果。阈值沿用 02 的实测方案，未测平台不能标通过。全部必需协议与产品政策验收完成才关闭地图；保留已知日志缺口与不支持能力说明。

## 进度

已完成 Desktop 自动化边界测试：JSONL/manifest 可解析、Unicode 截断、轮转与总预算、静默尾批、崩溃尾条恢复、导出、clear、durable Unsupported、close 取消重试、启动文件数预留、等级复核、导出 seal 故障恢复、持续不可写指数退避、时间回拨、多生产者与管理命令并发、消息惰性/异常隔离及常见敏感字段脱敏。Android、Desktop、JS、WasmJS、iOS Arm64 与 iOS Simulator Arm64 源码编译和 Android FileProvider manifest 合并纳入最终验证。2026-10-16 本机 20,000 次公开入口突发回归测得 p50 47,208 ns、p95 55,791 ns、p99 115,417 ns、约 19,502 calls/s；此值只用作 Desktop 非阻塞回归基线。

未执行：Android/iOS 真机分享与锁屏后台，磁盘满/文件不可写实机故障，强杀、多进程压力，移动端 p50/p95/p99、分配、吞吐、CPU、能耗及长期队列高水位。当前没有连接 Android 设备或已启动的 iOS 模拟器；上述项目及产品隐私文案/允许字段确认完成前，本项和地图不得关闭。
