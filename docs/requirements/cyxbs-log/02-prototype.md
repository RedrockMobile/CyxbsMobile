# LOCAL-LOG-02：全目标依赖与移动端存储 PoC

Part of: [LOCAL-LOG-MAP](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/map.md>)

- 类型/标签：prototype / wayfinder:prototype、needs-triage
- 状态：planned，未执行
- Blocked by：[01 已完成](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/01-research.md>)

## 目的

用最小验证确认依赖、目标和存储行为，先消除会改变设计的未知项。此票是未来任务，本次未创建模块或原型代码。

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

未开始；当前无验证结果。
