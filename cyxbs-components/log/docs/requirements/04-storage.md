# LOCAL-LOG-04：有界文件引擎与管理协议

Part of: [LOCAL-LOG-MAP](./map.md)

- 类型/标签：task / wayfinder:task、needs-triage
- 状态：in_progress，noWeb 文件引擎、导出协议与平台目录已实现
- Blocked by：[02](./02-prototype.md)、[03](./03-module-api.md)

## 需求

noWebMain 统一单 worker 文件管理；普通记录非阻塞、有界接收；提供有结果的 flush/snapshot/clear/close，用户设备上滚动保留可诊断日志。

## 范围

- 条数与负载双预算、单条/字段/堆栈限额；丢弃与写失败统计。
- JSONL schema、编码、字节/时间批写；静默期间定时刷出。
- UTF-8 字节轮转，单文件/目录总字节/文件数量/年龄配额；按进程/session 独立命名。
- flush 接收序号屏障与回执；区分 buffered/durable/unsupported；控制命令不能在溢出时丢弃。
- close 幂等；snapshot 封存、切新文件、有限租约；打包取消和失败清理。
- clear 与策略 epoch 串行协调，不让旧队列在清理/撤回后重现；导出与清理协调。
- 磁盘满、锁屏不可写、目录失败的限频健康与退避；重启尾条修复，不误删其他文件。
- 租约、active 与导出临时包计入明确磁盘预算；导出并发和寿命受限。

## 验收

满队列普通调用仍不阻塞；统计与实际丢弃一致；静默尾批在配置时间内刷出；成功屏障前已接收记录可解析。轮转/配额始终遵循明确上限，失败不破坏既有可读文件。close/clear/export 并发与取消测试通过，旧 epoch 不重现。durable 不支持时明确报告；不承诺强杀/断电零丢失。

## 进度

已完成：Android/iOS/Desktop 共用 noWeb 单 worker；普通记录按 1024 条与 2 MiB 估算负载非阻塞接收；16 KiB JSONL 单条上限；64 KiB/约 1 秒 buffered flush；UTF-8 字节轮转、总字节/8 文件/年龄清理；崩溃尾条修复；flush/clear/export/close 可靠控制队列；导出 `.partial` 原子发布、manifest、最新 3 个源文件与 32 MiB 上限；可恢复文件故障后重建 active；持续写失败从 250 ms 指数退避到最高 30 s，并暴露 `droppedWriteBackoff`；健康统计；Web 返回 Unsupported。Desktop 自动化覆盖 JSONL、Unicode 截断、轮转配额、静默尾批、尾条恢复、导出 manifest/上限、clear、durable unsupported、取消后幂等 close、启动文件数预留、等级复核、同步采集门、导出封存故障恢复、持续不可写降级、时间回拨及多生产者与管理命令并发。

应用已接入 Android `noBackupFilesDir`、iOS Application Support 排除备份、Desktop 用户目录及各平台受控导出目录。仍需完成：Android/iOS 真机上的磁盘满、权限变化与锁屏写失败验证；更长时间的管理调用并发/取消压力；真机文件保护和后台行为验证；移动端真实吞吐、调用延迟、分配、CPU/能耗与参数定稿。当前不承诺断电持久化或强杀零丢失。
