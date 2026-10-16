# LOCAL-LOG-MAP：掌邮 CyxbsLog 与用户本地诊断日志

- 类型：map / 新需求
- 本地标签：wayfinder:map、needs-triage
- 状态：调研、最小模块、平台输出与 noWeb 文件引擎初版已完成；应用集成和真机验证待办
- 创建日期：2026-10-13
- 跟踪方式：本地 Markdown；不是 deck 后端票，没有 GitHub issue 编号
- 来源：用户要求进一步调研独立 log 模块、自有封装与用户侧本地保存，只在本地添加需求

## 目的地

在 `cyxbs-components/log` 提供稳定 CyxbsLog 入口；支持受控控制台输出、Android/iOS 用户侧私有滚动日志、可等待刷出、安全清理、主动导出；有界内存与磁盘，文件故障不影响业务。desktop/Web 明确能力矩阵，不能把浏览器等同 Node 文件系统。

用户后续授权先引入第三方库、创建最小 log 模块并搭建门面，当前已开始此基础实现。自动上传、服务器、远程拉日志、原生崩溃捕获、默认采集完整网络 BODY 不在首版需求。

## Notes

- [第一轮源码选型](</Users/generals/AndroidProject/CyxbsMobile/docs/research/kmp-logging-research.md>)：Kermit 适合复用平台输出；kermit-io 的满队列阻塞与生命周期缺口影响长期文件层。
- [模块与封装调研](</Users/generals/AndroidProject/CyxbsMobile/docs/research/cyxbs-log-module-design.md>)：模块依赖、协议、旧入口迁移、初始化风险及验收建议。
- [平台本地保存调研](</Users/generals/AndroidProject/CyxbsMobile/docs/research/cyxbs-log-local-storage-platforms.md>)：存储目录、备份、文件保护和用户导出依据。
- 按[仓库地图流程](</Users/generals/AndroidProject/CyxbsMobile/docs/agents/issue-tracker.md#L53>)组织本地地图与子项。当前插件 backend 为 GitHub，用户明确只本地，因此未调用其创建/更新功能，未改变后端；标签/依赖/进度是文档元数据。

## Decisions-so-far

1. 推荐独立 log，不继续扩展 utils 的存储职责；utils 保留向 log 的兼容桥。
2. log 核心不依赖 utils/config/account/页面或 Compose；应用注入配置、目录和匿名上下文。
3. 自有 API 隐藏 Kermit 与存储类型；平台输出复用 Kermit，文件引擎负责自有协议。
4. Android/iOS 私有保存、用户主动导出；浏览器首版有界内存。支持矩阵由 PoC 分目标验证。
5. 文件单 worker、有界条数与负载、批写/周期 flush、字节/数量/年龄配额、快照租约与清理屏障。
6. 本地保存和反馈附日志分别受产品政策控制；不承诺强杀、断电或后台挂起零丢失。
7. 用户已授权先行实施最小模块与门面；基础 API 与平台输出已完成，其余完整需求仍由下列子项跟踪。

## Fog

- Release 默认关闭诊断采集的建议是否符合产品要求？若默认最小采集，具体允许名单/告知是什么？
- 用户开启后持续时间、保留天数、是否提供日志容量和清理入口；反馈是否仅导出还是后续上传？
- Android 是否存在需纳入的子进程，iOS 扩展是否需要日志？首版建议主 App，不使用共享 App Group 目录。
- iOS 锁屏写入所需保护等级、首解锁前行为、后台时间和真机能耗。
- Kermit 的 Android、Desktop、JS、WasmJS、iOS Arm64/Simulator Arm64 编译已通过；Native 框架链接、Swift 导出方式和宿主框架 export 配置仍待 PoC。
- 性能阈值由真实日志量与 PoC 基线确定；当前参数仅为候选。
- 是否要加密、跨重启浏览器持久化？建议首版不纳入，后续独立评估。

## 计划

| 本地子项 | 类型 | 当前状态 | Blocked by |
|---|---|---|---|
| [LOCAL-LOG-01 调研](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/01-research.md>) | research | completed | 无 |
| [LOCAL-LOG-02 最小验证](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/02-prototype.md>) | prototype | planned / needs-triage | 01 已完成 |
| [LOCAL-LOG-03 模块与门面](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/03-module-api.md>) | task | in_progress / 基础门面已完成 | 02（完整能力） |
| [LOCAL-LOG-04 文件协议](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/04-storage.md>) | task | in_progress / noWeb 初版与 Desktop 边界测试完成 | 02、03 |
| [LOCAL-LOG-05 应用集成与用户导出](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/05-integration.md>) | task | planned / needs-triage | 03、04 |
| [LOCAL-LOG-06 验收](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/06-validation.md>) | task | planned / needs-triage | 03、04、05 |

图中的依赖是文档显式关系，没有写入插件原生边。用户已授权先搭建 03 的最小基础框架；完整能力继续按 02 的验证结果推进。

## 进度

- 已完成：两轮源码/项目边界调研、本地需求落盘；Kermit 2.2.0 引入、最简 Gradle 模块、等级/配置/惰性消息门面；Android/iOS/Desktop 有界 JSONL 单 worker、轮转配额、崩溃与可恢复文件故障恢复、清理和本地导出；Web 明确 Unsupported；10 项 Desktop 边界测试和六目标源码编译通过。
- 未完成：产品政策定稿、结构化上下文与控制台故障隔离、应用生命周期与平台私有目录接入、业务迁移、系统分享 UI、真机锁屏/磁盘满/能耗/更完整并发取消压力测试和性能参数定稿。
- 统计：1/6 子项完成，03 与 04 已有可编译初版；整个需求未关闭。
