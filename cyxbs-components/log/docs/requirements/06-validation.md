# LOCAL-LOG-06：协议回归与真机性能验收

Part of: [LOCAL-LOG-MAP](./map.md)

- 类型/标签：task / wayfinder:task、needs-triage
- 状态：in_progress，协议自动化、跨平台源码编译与 Android 模拟器集成验收已执行；真机性能和 iOS 登录态交互未执行
- Blocked by：[03](./03-module-api.md)、[04](./04-storage.md)、[05](./05-integration.md)

## 需求

以对外行为与实机指标验收，而不是仅验证内部函数或引用开源宣传数据。

## 场景

- 常态、突发并发、静默、超大记录/异常、等级过滤 与请求上下文。
- 队列溢出统计、UTF-8 轮转、时间回拨、多进程文件名与总磁盘预算。
- 磁盘满、文件不可写、尾条中断、锁屏/后台、强杀恢复与降级。
- flush 超时/取消、snapshot/clear/close 竞争、撤回及账号切换。
- 用户导出可解析、敏感内容测试样本不泄漏、旧调用不重复、新旧行为回归。
- Android 低端真机及 iOS 真机，所声明 desktop/Web 能力分别验证。

## 指标与完成条件

记录 caller p50/p95/p99、分配、峰值内存、worker 吞吐、CPU/能耗、队列高水位、丢弃、flush/导出耗时和恢复结果。阈值沿用 02 的实测方案，未测平台不能标通过。全部必需协议与产品政策验收完成才关闭地图；保留已知日志缺口与不支持能力说明。

## 进度

已完成 Desktop 自动化边界测试：JSONL/manifest 可解析、Unicode 截断、轮转与总预算、静默尾批、崩溃尾条恢复、导出、clear、durable Unsupported、close 取消重试、启动文件数预留、等级复核、导出 seal 故障恢复、持续不可写指数退避、时间回拨、多生产者与管理命令并发、等级过滤/异常格式化隔离及常见敏感字段脱敏。Android、Desktop、JS、WasmJS、iOS Arm64 与 iOS Simulator Arm64 源码编译和 Android FileProvider manifest 合并纳入最终验证。2026-10-16 本机 20,000 次公开入口突发回归测得 p50 47,208 ns、p95 55,791 ns、p99 115,417 ns、约 19,502 calls/s；此值只用作 Desktop 非阻塞回归基线。

2026-10-17 在 Android 设备 `127.0.0.1:5555` 上覆盖安装当前 `HEAD` 的 debug APK，并使用用户手动建立的登录态完成黑盒验收：设置页可访问；“记录诊断日志”可关闭后重新开启；应用退到后台再恢复后可导出；系统 `ChooserActivity` 收到 `content://com.mredrock.cyxbs.debug.fileProvider/diagnostic_log_export/...jsonl` 及临时读取授权；清理提示“诊断日志已清除”；清理后再次导出得到不同的新快照 URI。第一次和清理后的文件名分别为 `cyxbs-log-export-v1-1792171265801-e8c13218b4ecbe05.jsonl`、`cyxbs-log-export-v1-1792171306256-84303b79ec21ec23.jsonl`。该设备镜像没有可接收分享的应用，且 `run-as com.mredrock.cyxbs.debug` 被系统拒绝，因此本轮验证的是应用到系统分享边界、清理反馈和重建快照，不声称已读取私有 JSONL 内容或完成接收端传输。

iOS Simulator 已完成当前日志实现的源码编译、私有目录生成和 `NSURLIsExcludedFromBackupKey=1` 检查；当前模拟器没有用户登录态，未把凭据注入自动化命令或测试文件，因此没有将登录后设置页、系统 Share Sheet 和清理链路标为通过。

仍未执行：Android/iOS 真机分享与锁屏后台，iOS 登录态交互，磁盘满/文件不可写实机故障，强杀、多进程压力，移动端 p50/p95/p99、分配、吞吐、CPU、能耗及长期队列高水位。上述项目及产品隐私文案/允许字段确认完成前，本项和地图不得关闭。
