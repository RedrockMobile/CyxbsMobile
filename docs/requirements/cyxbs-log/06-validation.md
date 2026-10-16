# LOCAL-LOG-06：协议回归与真机性能验收

Part of: [LOCAL-LOG-MAP](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/map.md>)

- 类型/标签：task / wayfinder:task、needs-triage
- 状态：planned，未执行
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

未开始；本轮没有测试或性能通过结论。
