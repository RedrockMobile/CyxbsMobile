# LOCAL-LOG-02：全目标依赖与移动端存储 PoC

Part of: [LOCAL-LOG-MAP](./map.md)

- 类型/标签：prototype / wayfinder:prototype、needs-triage
- 状态：in_progress，依赖/全目标源码/Desktop 协议与突发延迟已验证；移动真机待执行
- Blocked by：[01 已完成](./01-research.md)

## 目的

用最小验证确认依赖、目标和存储行为，先消除会改变设计的未知项。模块与原型已经按后续授权落地；本项继续记录可复现的验证结果与尚未具备设备条件的部分。

## 范围

1. 固定 Kermit 2.2.0 或届时明确选定的版本，记录实际 Maven 变体、解析后的 Kotlin/协程/datetime 组合及许可证。
2. 验证 manager.lib 自动模块发现、命名空间；独立开启 Android、iOS arm64/simulator、desktop、JS/Wasm 目标，分别记录结果。
3. 验证无 Compose/业务依赖环，Kermit 门面与 noWebMain 文件依赖边界；不把 IO 组件引入浏览器。
4. Android/iOS 真机验证私有目录、备份排除、锁屏文件保护、静默尾批 flush、强杀后半条 JSONL 恢复。
5. 验证 snapshot 可解析、分享临时副本及清理竞争；如果宿主 Swift 直接调用，验证公开类型与 export/cinterop 方式。
6. 测正常/突发日志量、调用 p50/p95/p99、分配和内存，提出可审查的首版配额与性能指标。

## 验收

- 输出固定版本、每个目标的编译与能力矩阵；未测目标明确标注。
- 真机保存/恢复/分享的步骤和结果可复现；失败给出原因与方案。
- 基线证明普通调用不做同步磁盘 I/O、不因满队列阻塞；数值阈值在这里确定，不能借用厂商 benchmark。
- 把结论回写地图 Decisions/Fog；失败不得将后续任务标为 ready-for-agent。

## 进度

已完成：固定 Kermit 2.2.0；确认独立模块自动发现且依赖图无环；Android、Desktop、JS、WasmJS、iOS Arm64 与 iOS Simulator Arm64 源码编译通过；Web 不解析 Okio 文件层；Desktop 已验证有界非阻塞队列、轮转/恢复/导出、持续不可写退避、时间回拨与并发管理。2026-10-16 本机 20,000 次公开 `CyxbsLog` 入口突发回归结果为 p50 47,208 ns、p95 55,791 ns、p99 115,417 ns、约 19,502 calls/s；该测试包含消息构造、脱敏和队列判断，队列饱和后仍不得出现阻塞级 p99。

未完成：Android/iOS 真机目录、锁屏、强杀、系统分享与移动端 p50/p95/p99、分配、内存、CPU/能耗。Desktop 数字仅作为代码回归基线，不作为移动端产品阈值。
