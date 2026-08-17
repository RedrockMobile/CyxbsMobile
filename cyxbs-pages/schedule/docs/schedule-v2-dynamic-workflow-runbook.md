# Schedule v2 跨端后续动态工作流执行要求

> [!CAUTION]
> **状态：已归档的 Workflow 历史执行手册与恢复资料。** 本文不再是当前 Schedule v2 master DAG、启动指令、跨端合同、完成门槛或停止条件；下文的“当前”“后续”“必须”“直接启动”等措辞只用于还原当时的动态 Workflow、泳道、验证与恢复上下文，不能据此重新启动 `/loop`、创建任务、恢复旧 lane 或判定今天的完成状态。
>
> 当前跨端合同只认三篇 canonical 文档：[AtomicField 与原子批次](schedule-v2-field-group-sync-design.md)、[重复日程单次覆盖能力矩阵](schedule-v2-recurrence-override-capability-matrix.md)、[资源版本同步流程](schedule-v2-resource-version-sync-flow.md)。当前后端事实只认独立仓库 `/Users/guoxiangrui/GolandProjects/magipoke-todo` 的 `guoxiangrui/schedule` 分支及其 `SCHEDULE_BACKEND_DESIGN.md`；旧 Android 客户端代码只可作为历史领域能力与已完成切片证据，不得反向覆盖 canonical 合同。
>
> 本文第 2.2、3、4.2.1、7.3、8、9、12、13 节中的旧 migration、七表、cursor/change-feed、旧 endpoint、semantic W06 路线、任务顺序和完成条件均按历史执行时点保留；其中已取消或被替代的后端要求**不得重新解释为现行 TODO**。后续跨仓实施、验收和恢复统一从已经建立的 [Schedule v2 Codex 交接](schedule-v2-codex-handoff.md) 进入；不得从本文派生新的 master DAG。
>
> 下文保留原始阶段编号、验证记录、worktree/lane 说明与提交线索以便追溯；恢复任何历史切片前必须重新核对当前 checkout、实际代码/测试、canonical 合同与交接文档。

> 文档定位：Android 有限双向同步、iOS EventKit 单向导出与 Schedule v2 后端改造的主 Agent 执行说明。
>
> 本文规定 Schedule v2 的任务范围、项目位置、测试限制、完成条件和停止条件；通用的复杂需求编排、持久 worktree、IDE 索引、串行集成与清理协议统一以全局 skill `worktree-workflow-orchestration` 为准，本文不再维护一份平行实现。
>
> 本文不是当前实现事实或协议 schema 的事实源。架构状态以 [总路线图](schedule-v2-calendar-roadmap.md) 及对应专题文档为准。
>
> 当前执行提示：S26a 已完成 planner-issued `OpenConflict` 的 Room-only durable terminal opening；S26e 已把 S26d logical choice intent 以 Room schema v2 独立 append-only table durable-record；S26f/S26g 仅生成并 fresh 等值重验证无 I/O、无 mutation、无 capability 的 ScheduleWins/CalendarWins 普通数据。S26h 只提供两个 typed pure terminal entry，生成可伪造且会过期的 `TerminalProposal`；它不是 writer input、token、receipt、command、capability、authorization、provenance 或执行结果。S206-02 已为 ScheduleWins 提供 production-uncalled 的最小 Room-only terminalizer foundation：独立 transaction fresh-read exact pair/choice、重跑 transition、exact-CAS `LINKED + conflictId == null`、物理 exact-delete evidence并保留 choice；S206-03 已补 production-uncalled、仅显式用户调用的 separate exact terminal inspector 与 ScheduleWins recovery foundation：同一 Room read transaction strict-read 当前账号/Android 平台完整 link/evidence 集合并复用 `validateCalendarConflictEvidencePairs`，历史 projection/conflictId 仅在不属于已验证 unresolved 集合且其余 exact proof 完整时才 `AlreadyTerminal`；orphan、配对/账号/平台/identity mismatch、同 projection 不同 conflictId 或 replacement evidence 均 reason-only fail-closed。already-terminal 零写，只有 exact active + `Converged` 才最多复用一次既有 S206-02。#239/S206-07 与 #240/S206-08 已分别提供 CalendarWins/ScheduleWins executor，#241/S206-09 提供严格 choice-record/dispatch，#242 已接入设置页 exact-session one-shot 手动 caller；仍无后台 callback、可靠投递、自动 retry/replay/compensation、publication 或新 link state，`CONFLICT` 不自动传播，S26c/f/g 也不因此放宽。S26i 仅提供 `ScheduleCalendarConflictNoWriteConvergencePreflight`：以 fresh durable choice、fresh exact active pair、Ready snapshot 与 complete discovery 做当前完整五字段的纯 no-write 分类，返回 reason-only `Blocked` 或 typed ordinary current facts；不接线 runtime/terminalization，不能结算 durable state，也不产生 S26h proposal、命令、token、receipt、callback 或 writer input。历史纯 S26h/S26i 合同自身不选择 evidence/choice 的物理策略；当前 S206-02 已固定 由 `#242 → #241 → #240` 一次性手动链调用的 ScheduleWins Room-only terminalizer 为 exact-delete active evidence + retained append-only choice。OBS-RO-1 仅新增未接线的 S26i observation-input capability：构造期冻结账号的 direct Room view 仅零写读取普通当前 snapshot、Android exact active pair、pair-bound durable choice 与窄 `CalendarLink`，不经过 publication 或 generation。它不是 runtime/live observation，不执行 classifier，不注册或读取 Provider，也不引入 callback、retry/replay、terminalization、executor、evidence retirement 或 choice retention。unknown choice writer commit 只能对仍逐值匹配当前 link/evidence 的 active pair fresh inspect；旧 pair 缺失、终态或替换时 fail-closed。#242 已接入设置页的 exact-session 显式观察、选择与 one-shot 终结入口；可靠后台、进程死亡重投递、Merge 与自动/完整 conflict state machine 仍是后续工作。

> S206-01 已交付 pure common、无 production caller 的 outcome/inspection/ScheduleWins recovery 合同。S206-02 已实现最小 Room-only ScheduleWins terminalizer foundation：typed exact DAO CAS/delete，独立 `withWriteTransaction` fresh-read exact Android pair/choice、逐值匹配 typed winner expected facts、重跑 S26h、写 `LINKED + null conflictId`、物理 exact-delete active evidence、保留 append-only choice并 strict read-back。S206-03 已实现 production-uncalled、**只允许显式用户调用**的一次性 ScheduleWins recovery foundation 与 separate read-only exact terminal inspector：inspector 在同一 Room read transaction 内 strict-read 当前账号/Android 平台完整 link/evidence 集合、retained choice 及所需 Schedule facts，复用 `validateCalendarConflictEvidencePairs` 验证完整 unresolved 集合，再按 conflictId 从 choice 恢复历史 exact pair；只有 winner-specific current convergence、exact terminal link、evidence absence、unchanged choice，且历史 projection/conflictId 不在已验证 unresolved 集合时才给 `AlreadyTerminal`；既有 active-pair choice inspector 不放宽。orphan、配对/账号/平台/identity mismatch、同 projection 不同 conflictId、replacement evidence、partial/third/opposite/stale 均 reason-only fail-closed。recovery 先 inspect，already-terminal 零写；exact active 后重新 fresh-read，并且只在专用 recovery preflight `Converged` 时最多调用一次既有 S206-02，old Provider state 不写。没有 schema migration、generation、Schedule/outbox/publication、Provider gateway/runtime/UI 或 CalendarWins executor；取消、异常与 lost-return commit 保持 exception/`RoomCommitUnknown`，禁止自动 retry/replay/compensation。该基础仍 production-uncalled，不得宣称 #206 完成；S26h `TerminalProposal` 只允许作为事务内普通校验输出，不能成为 caller authority。

> S206-04 已完成 pure materializer 与由 `#242 → #241 → #239` 一次性手动链调用的 #237 noWeb Room 原子提交基础：writer 在唯一 `ScheduleRoomStore.transaction` 内 fresh strict-read graph、exact Android link/evidence 与 retained `CALENDAR` choice，重跑 materializer，以单一 `commitAt`、`revision + 1` 和正常 `ScheduleCommand.Update` reducer 写完整 graph/PATCH outbox；strict read-back 后重跑 S26h，exact-CAS terminal link、物理 exact-delete active evidence、保留 append-only choice并确认 unresolved pair 消失。receipt/evidence 只来自同一 writer scope；受控 mismatch 整体回滚，取消、异常与 lost-return commit 原样传播，不自动 retry/replay/compensate。#238 已将其与既有 ScheduleWins path 置于 construction-bound exact-session 窄 capability 下；#239/S206-07 已在其上实现由 #242 设置页一次性调用的手动 CalendarWins executor：每次两轮都严格读取 exact pair → current Schedule snapshot → 完整 Android CalendarLinks → W40 Provider read-only snapshot → 完整 discovery → pair-bound choice，首轮仅 CalendarWins candidate、第二轮必须 `RevalidatedCalendarWins`，随后至多一次 #237 Room command。它不写 Provider/choice，不执行 ScheduleWins、terminal inspection/recovery 或 post-command 自动检查；command 开始后的普通异常/lost-return 为 `RoomCommitUnknown`，取消原样传播，禁止 retry/replay/compensation。#242 设置页现以 exact-session、一次观察、明确选择和二次确认后的 one-shot request 调用 #241→#239/#240；它不是后台 runtime 或可靠投递，Room/Provider 不共享 transaction/CAS。#243 隔离真实 Provider 验收、#244 与 #206 completion 仍 pending。

> **S206-08 / #240 与 S206-09 / #241 当前执行基线**：#240 只在两轮完整 exact facts 重验证后执行一次 W48 update，写后重新读取 Schedule、完整 links、W40 snapshot 与 discovery；只有 recovery preflight `Converged` 才调用一次 Room terminalizer。Provider/Room unknown 分别保持 `ProviderEffectUnknown`/`RoomCommitUnknown`，任何 unknown 都不自动 inspect、recover、retry 或 replay。#241 只接受 conflictId + side，从 fresh pair 派生 intent，恰好一次 record；writer 返回 record 必须以 choice、intent、expected link 与 expected evidence 全量回绑本次 pair 后，才 dispatch #239/#240 中一个 executor。choice lost-return 是 `ChoiceCommitUnknown`，禁止自动 inspect/recover/retry/replay。两步非原子；#242 只将其接到设置页的一次性显式用户操作，不得将其描述为后台 runtime、可靠投递或真实 Provider acceptance。

> **S206-05 / #238 当前执行基线**：production issuer 只能由已经完成正常 Room factory `create` 的同一 factory，为 exact `AccountSession` 原引用签发 access；未构造、外来账号、同账号旧 generation、结构相等副本均在 Store/DAO/Room I/O 前 `CancellationException` fail-closed。cold issuer 不构造 factory，不读 Android ID/Settings Provider，不初始化数据库、不注册 export/initialized hook，也不启动 dispatcher/background work。access 账号分区只从冻结 session 派生，限于 ordinary snapshot、Android exact active pair、**无参数** `readAndroidCalendarLinks()`（只固定读取该账号 `CalendarPlatform.ANDROID` 的完整 `CalendarLink`，调用方不能选择 platform）、choice read/write、S206-03 terminal inspection、显式 ScheduleWins recovery、既有 ScheduleWins Room command 及 #237 CalendarWins Room command；完整 links 仅供 #239 构造完整 `CalendarLinkDiscoveryResult`，禁止用单 pair 替代，也不泄漏 Store/DAO/repository。CalendarWins receipt/内部 evidence 被抹除，broad repository/Store/DB/DAO、Provider、remote gateway、Flow/publication、controller、token/raw authority 不外泄。Provider/Room 不共享 transaction/CAS；unknown/cancellation/lost-return 不自动 retry/replay/compensate。capability 自身不直接执行 Provider effect、UI/runtime 或可靠投递；#239/#240/#241 由 #242 设置页按用户 one-shot 操作调用，但 #206 仍未闭环。当前由 Desktop 临时 SQLite contract suite 与 Android host cold contract 覆盖；integration 的 Desktop contract suite 与 `compileKotlinDesktop` 均通过，未访问设备、真实 Provider 或用户数据。
>
> **Android Room 初始化 handoff 当前基线**：`RoomScheduleRepository.initialize()` 在 `operationMutex` 内只执行 strict read、Ready/Initialized 发布与 typed handoff reserve，正常解锁后同步 release。registration 使用 exact-session identity、跨 registry 实例全局单调顺序和固定 `registryLock → controller stateLock`；same-owner replacement 推进 start generation，Settings 使用 successor revision 与 exact true receipt。release 及其测试 seam 均在 Room 锁外，任何 reject/throw/unknown 不自动 retry/replay，也不改变 Room、Settings、Provider 的非原子边界。

> W40 是 Android-only 的最小 session-bound Provider snapshot reader，而不是 OBS-RO-2、facade 或独立 S26i runtime。除当前 W45b 固定只读链外，它仍无 caller；W45a acquisition 本身不调用它。issuer 冻结 exact session、账号 scope 和 export scope，仅保留 application context 并内部构造 W39 reader，签发零 I/O；`read(expectedSession)` 以 `===` 和生命周期 gate fail-closed，且把同一 gate 传给 W39 每个 Provider/Cursor 读取边界，只有 copied ordinary snapshot 完成 outer post-copy 复核才输出。不得据此宣称已组合 Room 与 Provider reader、已取得联合 provenance 或跨 store atomicity；W40 没有写入、terminalization、retry/replay、publication、callback/Flow 或 action artifacts。W39 当前 snapshot reader 同时要求 `READ_CALENDAR` 与 `WRITE_CALENDAR`；#242 只在两项权限均已授予时显式调用 W45b，不降低该权限合同。
>
> W41 是 OBS-RO-1 的 Room-only exact-session issuer：`RoomScheduleRepository.issueCalendarConflictNoWriteObservationReadAccess(expectedSession)` 仅在 `expectedSession === boundSession` 时发放私有无写 view；foreign、同账号新 generation 和结构相等副本均在任何 Store/DAO/Room I/O、publication 或 generation 操作前取消。view 只从 exact boundSession 在内部派生唯一 Room account partition key。W41 不建立 Room/Provider 共享 provenance、binding、atomicity、currentness 或 authorization，也不创建 runtime、Flow/callback、terminalization、execution、retry/replay 或 publication。
>
> W42 是 Android internal、同步、stateless 顶层 pure mapper：`AndroidManagedCalendarSnapshot.toCalendarLinkDiscoverySnapshot()` 只映射调用方提供的普通 `AndroidManagedCalendarSnapshot`（其形状可以与 W39 输出一致）为普通、可陈旧 discovery data，保留 calendar id/原事件顺序，并新建 observation、canonical fields 和 reminder list。W39 仍是 production Provider acquisition 的唯一实现；W42 不自行 issue/read W39/W40/W45a、不访问 W41/Room，也不是独立 runtime/caller。W45b 只在取得一次 W40 snapshot 后按固定顺序消费 mapper 输出，再调用 discovery/S26i；这不产生 source provenance、currentness、shared scope、跨 store atomicity 或 authorization，W42 仍不是生产 entry point。controller/coordinator/initializer、callback/Flow、retry/replay、holder/facade/wrapper、生产 wiring 与 lane-02 均禁止由 mapper 本身纳入。
>
> **历史 W45a acquisition 基线**：Android production factory 的 `issueProductionScheduleCalendarConflictNoWriteObservationReadAccess(expectedSession)` 仅以 dedicated lazy `RoomScheduleRepositoryFactory` 临时创建 broad Room repository，再立刻经 W41 issuer 降格为窄 view；broad repository 不逃逸、不缓存。factory 只共享进程 Room database resource 与稳定 Android device ID，remote gateway unavailable、initialized hook no-op，不主张共同 transaction、provenance 或 currentness。首次签发会按 lazy 语义初始化 dedicated factory/database owner，并构建 Room database resource；但不调用 `RoomScheduleRepository.initialize()`、initialized hook 或 export initialization，不注册 export hook，不读写 Store/DAO、不发布 snapshot/event、不推进 generation、不读 Provider、不执行 W42/discovery/S26i、也不启动工作。签发既不暴露、签发或消费 authorization，返回的窄 view 不具有 authorization authority。这里“IDE references 无 caller，故没有 coordinator/runtime/consumer/UI/controller/initializer/background wiring 或 observation flow”仅是 W45a 还没有 W45b 时的历史/自身 acquisition 边界；Room/Provider 观察仍非原子，也不建立共享 provenance、currentness、scope 或 authorization。

**当前 W45b/#242 约束与验证限制**：`AndroidManualScheduleCalendarConflictObservationCoordinator` 已由设置页在 exact session、读写权限齐全且用户显式点击时调用；它仍不由 initializer、worker、SyncAdapter、background runtime 或 callback/Flow registration 自动触发。它只能在 exact AccountSession/scope/owner lifecycle gate 内，依次读取 active conflict pairs、空集即时返回、Room snapshot、Android links、恰好一次 W40/W39 Provider snapshot、W42 snapshot-to-discovery mapper + `CalendarLinkDiscovery`、每 pair final durable-choice inspect，最后作 S26i no-write classification。返回仅为 immutable ordinary per-conflict outcomes：choice absent、observed Schedule target、observed Calendar target 或 reason-only blocked。它绝不授权写入、terminalization/resolution、snapshot/event publication、retry/replay、后台行为、shared Room/Provider transaction、provenance/currentness proof、authorization 或 cross-store atomicity；因此 coordinator 或结果均不能作为 runtime wiring 或更广 sync implementation 的许可。host-only fake contracts 仅覆盖 mandated order、empty fast return、exact-session/lifecycle cancellation、无 retry、immutable/narrow result surface 与 outcome mapping；未运行 device、真实 Calendar Provider、真实 Room database 或其他真实外部行为。

> iOS S205-04 / #281 已在 #279 gateway/真实 bridge 与 #280 explicit settings 之上接入 production 的 process-resident、单向 Schedule → EventKit runtime。initializer 在 `LocalFirstScheduleRepository.initializeMutex` 内仅注册 direct repository、exact `AccountSession`/account scope/owner Job，返回 opaque one-shot handoff，并在同一次 `initialize()` 正常退出 mutex 后同步 release；runtime 以单 serialized Full actor/conflated generation 处理 `Initialized` 与同账号 `SchedulesCommitted`，`RemoteCommitted` 不触发。settings source/disable 在首次 durable write 前 invalidate exact-session generation 并打开 `explicitIntentPending`；只有 complete intent durable 且 exact-session signal 启动新 generation 后才恢复。durable enabled、selected source、FULL_ACCESS、Ready/Recovered snapshot 与 exact session/scope/owner/generation 是每个 effect boundary 的硬门禁。source/calendar/foreign identity/locator/cache/ack/retirement 失败或 EventKit terminal uncertainty 均 fail-closed；不授权同 generation retry/replay。atomic-create recovery eligibility 只在明确 commit-entered unknown 或 confirmed commit 后建立，绑定当前进程/store/scope/source/projection/fingerprint 与 gateway/proof 对象身份，进程死亡丢失；cache/EventKit 非原子。后续工作不得把该 runtime 扩张为 notification/background/manual-sync/inbound/bidirectional/occurrence exception。当前证据仅为 710 个 in-memory/fake iOS tests、pure foundation state-machine tests 与 iOS/metadata/common/Android/noMobile/Desktop 编译；不构造 `EKEventStore`、不访问用户日历。#282 的专用测试日历、真实 full-access、source/iCloud、真实 store、原子创建/恢复和真机验收仍待完成。具体平台事实以 [iOS EventKit 导出设计](schedule-v2-calendar-ios-eventkit.md) 为准。4.6 的 D-041 历史快照继续只表示当时状态，不得作为当前 #281 缺口。

## W48 当前执行基线：Android Calendar row 世代身份

W48 supersede 历史 D-036/W47 的 `android-calendar-row:v1:<id>` row-only identity：Xiaomi 可复用被删除的 `CalendarContract.Calendars._ID`，故该格式不是物理 row incarnation identity。当前唯一严格格式为 `android-calendar-row:v2:<positive-row-id>:<canonical-lowercase-uuid>`；创建 managed LOCAL Calendar 时生成每次创建唯一 UUID，并写入仅 sync-adapter 拥有的 `CAL_SYNC1`。v1 durable identifier、tokenless/malformed row、缺 marker、uppercase/noncanonical UUID 全部 fail-closed；禁止 read-time backfill、silent durable-link rewrite 与 automatic migration，只有另行授权的 relink/reset 才可恢复。

执行时，snapshot 在 Events 和 Reminders 读取后复验 full identity；finalized Create 在 `beforeInsert` 后再验，finalized Update 比较 full identity；cleanup 每个枚举行都复验，以单个 token-conditioned Provider batch 和 `expectedCount` 确保 token 漂移整批 rollback。它们是 best-effort preflight/read-back gates，绝非 Provider CAS。W48a 仅将 Xiaomi item-URI+selection incompatibility 和 Deadline projection-kind precedence 修正在测试 fixtures，production Update shape 与 timing inference 不变。可用证据仅为 task/integration branch 的 full Android host、desktop suites 与 `compileAndroidDeviceTest` 已通过；当前 loop 没有新的 device run，12-case Xiaomi Provider suite deferred，不能说 runtime Provider 已验收。Room schema/DAO 与 iOS/EventKit 语义保持不变。

## 1. 启动方式与总目标

使用 `/loop` 动态模式持续完成以下三个相互关联、但必须隔离管理的工作流：

1. Android 受管系统日历有限双向同步；
2. iOS EventKit 单向日历导出；
3. Schedule v2 后端改造。

不要预设固定循环间隔。主 Agent 应根据后台 Agent、编译、测试及外部状态自行安排动态唤醒；Claude Code 能自动通知的后台任务不得通过短间隔轮询等待，只设置不短于 20 分钟的兜底唤醒。

主 Agent 负责统筹全程。简单、明确、局部的单个需求直接在当前主工作区串行完成；复杂或大型需求必须先调用并完整阅读 `worktree-workflow-orchestration`，再按其协议设计 DAG、创建并复用持久 worktree 泳道、串行集成和最终清理，同时遵守本文的 Schedule 项目、文档、验证和停止要求。

## 2. 项目位置与代码索引

### 2.1 Android / iOS 客户端项目

项目路径：

```text
/Users/guoxiangrui/AndroidStudioProjects/Cyxbs/CyxbsMobile_2
```

这是同一个 Kotlin Multiplatform / Compose Multiplatform 项目：

- Android 代码主要位于 `androidMain`；
- iOS 代码主要位于 `iosMain`，以及必要的 iOS 宿主或 Swift facade；
- Android、iOS 可能共同修改 `commonMain`、构建配置、领域模型和文档。

代码查找、定义跳转、引用分析、文件结构和诊断必须优先使用 `android-studio-index` 的 `ide_*` 工具。不得先用 Bash `grep`、`find` 或 `rg` 代替 IDE 索引；只有 IDE MCP 查询失败、索引未完成，或任务确实需要构建、测试和文本批处理时才使用 shell。

### 2.2 后端项目

项目路径：

```text
/Users/guoxiangrui/GolandProjects/magipoke-todo
```

该项目已在 GoLand 中打开，应直接使用 `goland-index` 的 `ide_*` 工具，包括：

- `ide_find_file`；
- `ide_find_symbol`；
- `ide_find_definition`；
- `ide_find_references`；
- `ide_call_hierarchy`；
- `ide_search_text`；
- `ide_diagnostics`。

后端旧 mutation/bootstrap/sync、七表 GORM `AutoMigrate`、retention compactor 与 Android/iOS/Desktop `RequestSync` 的 source contracts 仍可作为迁移资料；但 W06 Wave 0 已从 production router 移除旧 `POST /v2/schedule-mutations`、`POST /v2/schedules:bootstrap`、`POST /v2/schedules:sync`。精确旧 POST 在 TokenVerify、service 与 store 前 route-level `404`，认证与持久化零副作用。W5 已完成三条 semantic POST route、authoritative store/service、cursor/reset 与 disabled capability 的 backend source/runtime closure；整体 production semantic v2 仍 **BLOCKED/DISABLED**、未部署/未启用。仍待客户端 factory/repository/capability integration、native migration、Web 真实 wiring、真实 gateway、MySQL/network interoperability、deployment/enablement 与 cutover；Web production 仅为无持久化、无 I/O unavailable façade。此源码状态不表示部署、实际 enable、服务启动、MySQL/canary、网络互操作或真实 MySQL 验收。必须继续以当前 GoLand 项目的实际代码和 Git 状态为准，不得恢复旧 route 或同时维护手写 SQL 与 ORM 两套 schema 事实源。只有 GoLand MCP 不可用、索引未完成或查询失败时才使用 shell 文本搜索。

## 3. 开始前必须阅读的文档

主 Agent 开始设计工作流前，必须完整阅读以下文档：

1. [Schedule v2 总路线图](schedule-v2-calendar-roadmap.md)：总状态、阶段依赖和启动门禁；
2. [当前创建、更新与同步数据流](schedule-v2-current-data-flow.md)：AccountSwitchingScheduleRepository、平台 delegate、Settings fallback、outbox、tombstone、UI 和远端同步骨架；
3. [分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)：Android/iOS/Desktop Room3 production path、Web 历史 Settings repository fallback 与当前无持久化 unavailable façade、Room3 KMP 3.0.0 P0 工具链与 durable store 设计、Desktop `FileKit.filesDir/schedule-room3-production.db`、iOS Home 下 `schedule-room3-production.db`、各自独立 stable `deviceId` namespace、`CalendarLink`、baseline、conflict、patch 三态和 Web remote-required 目标；
4. [Android 单向日历导出架构](schedule-v2-calendar-export.md)：当前 Android 出站与有限双向运行时；
5. [Android 单向导出收尾](schedule-v2-calendar-export-hardening.md)：阶段二单向出站的历史规范、Provider canonicalization、Unsupported 与验收基线，不是当前剩余工作或方向门禁；
6. [双向日历同步设计](schedule-v2-calendar-bidirectional.md)：common 三方基线、`CalendarLink`、冲突、防回环和 Android Account/SyncAdapter；
7. [iOS EventKit 导出设计](schedule-v2-calendar-ios-eventkit.md)：iOS 权限、source、受管日历、adapter、后台边界和验收；
8. [后端 Schedule v2 设计](schedule-v2-backend.md)：wire grammar、表结构、API、CAS、幂等、bootstrap、change feed 和事务；

若文档与当前代码冲突：

- 当前实现事实以代码为准；
- 未来未实现能力以专题设计文档为目标；
- 先记录并修正文档矛盾，再实施依赖该结论的代码；
- 不得静默选择其中一个版本继续实现；
- 如果冲突涉及不可由代码和既定决策推导的产品或架构选择，应按第 10 节停止并请求用户决策。

## 4. 已确认且不得自行推翻的技术决策

### 4.1 开发期兼容边界

Schedule v2 尚未正式上线：

- 不兼容旧 Schedule/Todo 数据；
- 不兼容旧 Provider 身份；
- 不建立 legacy reader；
- 不建立迁移 marker；
- 不进行长期 Settings/SQL 双写；
- 开发期允许清理测试数据或卸载重装。

### 4.2 Room3 KMP durable store 与 Web 边界

- 非 Web durable SQL 使用 **Room3 KMP 3.0.0 + SQLite**；Schedule 当前通过独立 `useRoom3()` 使用 `androidx.room3`，旧 `useRoom()` 与 Room 2.8.4 继续保持其他既有模块的原语义。SQLDelight 不在计划中，不添加其 plugin、driver、`.sq` 或 `.sqm`；
- Android 生产 Provider 已接入稳定的 AccountSwitchingScheduleRepository：AccountSession/generation 与 accountCoroutineScopeFor 已落地；Android、iOS 与 Desktop production factory 均使用各自进程级 Room3；W06 Wave 0 后 Web production 为无持久化、无 I/O unavailable façade，不使用 Settings-backed factory。iOS 的 `IosScheduleRoomDatabaseOwner` 固定使用 Home 下 `schedule-room3-production.db` 与独立 stable-device-id namespace；账号/generation 变化只重建 immutable facade，首次数据库或 identity 持久化失败直接传播。EventKit intent/calendar hint/event-ref ledger 仍是独立 Settings cache，不是 repository fallback，且 Room、Settings cache、EventKit 不共享 transaction/CAS。Room3 已完成 P0 toolchain/probe、业务 schema、strict mapper、noWeb internal transaction Store、durable generation Flow、bounded candidate query 与 D-019 Slice A-C。internal local-command adapter 在一次 writer transaction 内执行 strict read、冻结 deviceId provider、common reducer、atomic graph replay、outbox/tombstone 和单次 generation 推进；receipt 精确承载账号、提交 generation、冻结 deviceId 与 block value，不承载发布 snapshot。parent 删除已先归约稳定排序的 exception outbox，再归约 parent；同账号新 generation 重建 delegate，旧 snapshot/calendar event 隔离，初始化 pending/failure fail-closed，Room strict-read 初始化失败继续抛出，代理转发 calendar event 前先发布最新 snapshot。`a318dfa4b` 已在初始 Room schema v1 以 `calendar_link` 单表持久化完整 canonical `CalendarLinkRecord` payload；`3586e1b1a` 已新增纯 common `CalendarConflictRecord`/`open` transition，`f2f9eecb9` 已冻结 schema v1 strict `CalendarConflictCodec`，S26e 已用唯一 1→2 migration 新增独立 `calendar_conflict_choice` append-only choice payload 表，`de31f7e20` 已新增 Android strict read-only managed-calendar snapshot；`5ea4d19e4` 已补 planner-issued guards、写后 confirmed-state 的纯 confirm、opaque advancement 与 Room whole-record exact-CAS baseline advancement primitive。上述基础能力现已在 Android 的有限范围接线：W15/D-045 通过 Room repository、`ScheduleCalendarExportController` 与 `ScheduleCalendarToScheduleRuntime` 完成启动时有界 `TO_SCHEDULE`/resolved NoOp；W16/W17 在 finalized worker 处理受限持续 `TO_CALENDAR`、exact orphan adoption 或单次 fixed-row Create；W22 从 `replay=0` 本地删除候选独立提交一次 Room `DETACHED`，**普通删除不删 Provider**。完整/连续入站、`BIDIRECTIONAL`/Merge、自动/完整冲突 state machine、SyncAdapter、远端闭环仍未接线；#242 仅接入设置页 one-shot 手动入口；详见[第 12.1 节](#121-w16w17-finalized-长期-worker-的-calendarlink-生产事实)和[第 12.2 节](#122-w18w22-普通删除的本地-detached-生产路径)。Settings 未删除且不迁移、不建 legacy reader、不双写；W06 Wave 0 已将 Web production 切为无持久化、无 I/O unavailable façade。W5 的 semantic route、authoritative store/service、cursor/reset 与 disabled capability 已 source/runtime 闭合但未部署/未启用；仍待客户端 factory/repository/capability integration、native migration 与 Web 真实 remote gateway wiring。
- 后续业务实现首选在同一 Schedule 模块的 `noWebMain` 放置 Room3 API、Entity、DAO、Database 与 Store；commonMain 不得含 Room3 contract/repository；
- 当前 `useRoom3()` 仅向 `noWebMain` 注入 `room3-runtime` 与 bundled SQLite，compiler 仅配置 `kspAndroid`、`kspDesktop`、`kspIosArm64`、`kspIosSimulatorArm64`；JS/Wasm dependency graph 不得含 `room3-runtime`、`room3-compiler`、`sqlite-bundled`；
- 使用 `BundledSQLiteDriver`、`@ConstructedBy`、Room3 KSP 生成的 `RoomDatabaseConstructor`、各平台 `androidx.room3.Room.databaseBuilder(...)` 与 `schemaDirectory(...)`；
- Room3/SQLite transaction 必须保留账号隔离、outbox/tombstone/cursor/deviceId、提交后发布、网络不入 transaction 和 fail-closed；不得重建或重写全库 JSON envelope；
- Provider 当前已改为平台中立 factory：Android/iOS/Desktop actual 均组装进程级 Room3 local-first，Web actual 为无持久化、无 I/O RemoteRequired unavailable façade。iOS EventKit intent/ledger 保持独立 Settings cache，不能据此把 iOS repository 归类为 Settings fallback，也不得声称该 cache 与 Room/EventKit 原子。Room3 具备 Web runtime/API 不等于默认提供本地 durable persistence；common remote-required contract、Web production factory 与 Web actual 的 unavailable 组装已完成，但真实 remote transport 尚未接线；
- 业务 schema 接入后的 constructor 生成、Web 依赖隔离与 configuration cache 已复验，维持 `KEEP_SINGLE_MODULE`；仅在后续 mapper/Store 接入真实出现 constructor 生成失败、Web 依赖图看到 Room3/SQLite，或 configuration cache 不再收敛时，才拆独立 no-Web Room3 module。
- `d0a321a7` 仅覆盖 Schedule Provider 与 AccountSession 生命周期门禁；全局 Token/account lifecycle 后续已由 `c7771c5a` 与 `97c24e69` 独立完成。refresh、UserInfo 与普通 Ktor/Retrofit typed authenticated response 均冻结 exact `AccountSession`/源 `TokenBean` identity，迟到 `20002`/`20003`/`20004` fail-closed，`ApiWrapper.data` 延迟访问不再执行账号副作用。
- 后续认证网络任务不得把 typed authenticated response 重新列为未完成，也不得回退到按学号、token 字符串、响应时全局账号状态或 ThreadLocal 判断请求归属。HTTP error body、raw response、stream/download 与 `RedrockApiWrapper` 仍是显式边界；若需求触及这些路径，必须建立 request lease 传播与确定性测试后独立提交。
- Android `enable`/`disable`/`clearAndDelete` 的独立 hardening 已由 `914c5ba8` 完成：初始化 hook、设置页权限/删除异步上下文与 Controller 都冻结完整 `AccountSession`；Controller 同时以 command generation 和 owner Job/session 淘汰陈旧意图；D-038/`b615468b1` 已将 Coordinator 条件移除收紧为完整 session/scope/owner，并在 Provider 只读结果、逐项写入和状态投影边界复核同一生命周期。后续任务不得回退为仅按学号读取 scope、无 owner 的全 scope stop，或把 Provider preflight 描述为跨系统 CAS。

### 4.2.1 W06 remote semantic protocol scoped sub-roadmap

本小路线图不替代 Android/iOS/EventKit/CalendarLink/conflict/backend/acceptance 的 master DAG；该旧路线已归档。当前实施只以 [Codex 交接总文档的 master DAG](schedule-v2-codex-handoff.md#9-剩余-master-dag)、三篇 canonical 文档和后端 `SCHEDULE_BACKEND_DESIGN.md` 为准。下文 W06 route-level `404`、semantic command/cursor 与旧 source 状态只作历史恢复资料，不定义当前 dirty typed 后端的 route、wire 或部署状态。

整体 production semantic v2 仍 **BLOCKED/DISABLED**。W1 backend #166 与 client #167 已分别完成 pure-wire codec、canonical bytes/hash，以及基于两份不可变 fixture corpus 的跨语言逐字节验证；W2 storage isolation 亦已完成：legacy `SettingsScheduleLocalStore` 与 `createSettingsScheduleRepositoryFactory` 仅在 noWebMain，Web 不含 Schedule-owned durable persistence 或 Web-visible durable adapter，既有 account/profile/tourist Settings 不在 W2 范围。future 页面/文档/repository 内存至多为 active `candidateId` 加一个原子 confirmed 单元（opaque cursor + authoritative graph/cache），重建即丢弃，W2 不接线该状态。**W3 authoritative reads/genesis/grouped sync、W4 recurrence proof/all 11 planners、W5 backend semantic command/read source/runtime closure 与 W6 K0/K0b/K1/K2/K3 shared-client common/Desktop source/tests 现已完成并集成**：W5 的 route/store/service、cursor/reset 与 disabled capability 已闭合但未部署/未启用；W3/W4 的 backend dev/test HEAD 为 `27469b4e0139c435673a16388972dd2dda66320a`，当前 Android baseline 为 `c094df6c957d6def9c8668db2ed7f701de030eb0`（`b7b418efbf1171b791c4f15f3cd96c9592dc223f` 是历史 W4/K2 baseline）；W6 依次为 strict pure-wire/opaque cursor `2e3f876e96bb07ae99381456a4bf58153a4aea8d`、authoritative graph/reducer `bdc7a9c24e705cc2dc469f775d3380b90083772a`、confirmation coordinator `73836cb5f5ba1f8e96084380e9da5c3b502b5546`、Ktor confirmation port `82ea4631135734e8691fcd8c62ddd4488dac84da` 与 composition contract `c094df6c957d6def9c8668db2ed7f701de030eb0`。W6 确保 opaque cursor 不被客户端解析，accepted 仅在 candidate-bound settled authoritative snapshot 后发布；已知 changed acceptance 后，ordinary cancellation 以 `NonCancellable` 精确一次尝试/发布 `AcceptedButUnconfirmed` cleanup，随后关闭并重抛原 cancellation；exact-session account replacement/result-fence rejection 则关闭至 `ClosedNeedsReset` 并重抛 `ResultFenceRejectedCancellationException`，不发布 `AcceptedButUnconfirmed`；两路径均不 retry confirmation 或 replay command；它仍未接线 repository/factory/capability 或 native runtime。17-vector semantic-plan corpus 的唯一 `RequestSync` 始终是 local-only/decoder-only，绝非 remote dispatcher branch。后续须先完成客户端 factory/repository/capability integration 与 W7 native migration，并满足所需 deployment/enablement gate；W8 Web production wiring 在这些前置满足前不得启动，精确依赖排序唯一以[总路线图](schedule-v2-calendar-roadmap.md)为准。其后再按真实 MySQL/network interoperability、W9 all-platform release gate → W10 explicit `AUTHORITATIVE_READ_ONLY` → `SEMANTIC_FULL` operational cutover 推进。source/runtime complete 不等于 production cutover；冻结新路由为 `/v2/schedule-commands`、`/v2/schedules:authoritative-bootstrap`、`/v2/schedules:authoritative-sync`，旧路由不得复活。

### 4.3 时间模型

- `Date` 表示纯日期；
- `MinuteTimeDate` 表示分钟级本地墙上时间；
- `LocalDate` / `LocalDateTime` 只允许在时区、RFC 5545、网络或平台 API 边界临时使用；
- 秒或纳秒不得静默进入业务领域；
- RFC 5545 `UNTIL` 的最后一秒只是 adapter 边界值。

### 4.4 Android 日历身份

- `CalendarExportScope` 使用规范化学号；
- `ACCOUNT_NAME` / `OWNER_ACCOUNT` 使用当前学号；
- `NAME` 与 `CALENDAR_DISPLAY_NAME` 均为“邮子清单”；
- 不重新改回匿名 scope；
- 学号不是 secret，但属于可识别信息，不得作为鉴权凭据，也不得无必要输出到日志。

### 4.5 受管范围

系统日历只处理满足全部条件的受管范围：

- 当前账号；
- 当前“邮子清单”日历；
- canonical v2 URI；
- scope、Schedule ID、kind、recurrence identity 和所有权均通过校验。

不得扫描、导入、修改或删除第三方日历内容。

### 4.6 双向同步

- 必须使用 `base / Schedule / Calendar` 三方比较；
- 不使用设备时钟 LWW；
- fingerprint 不能代替字段级共同基线；
- `CalendarLink`、baseline、conflict 和 outbound origin 必须持久化；
- 所有系统日历入站修改最终都要生成 `ScheduleCommand`，并走同一 transaction、outbox 和远端同步链；
- 删除整个受管日历或移除平台账号不能批量删除 Schedule；
- patch 的 `INHERIT / CLEAR / REPLACE` 三态已在领域与 wire 协议冻结；后续持久化 schema 必须无损保留该语义，不得退回 nullable 猜测。

#### 历史快照（截至 D-041，W15/D-045 与 W16 之前）

以下 D-031–D-041 记录的是当时的前置能力与缺口，保留它们以追溯决策，**不**描述当前 production wiring。当前基线是：W15/D-045 已完成显式 enable 或 durable resume 触发的启动时有界 inbound-first `TO_SCHEDULE`；W16 仅在其 finalization 后的长期 worker 确认已有唯一 `LINKED` 的 `TO_CALENDAR` Update/`RESOLVED` NoOp，W17 仅在 whole-batch eligibility 通过时认领 exact orphan 或一次 fixed-row missing-link Create 后完整重规划；W22 已接线普通 Schedule 删除的 `replay=0` 本地候选 → exact controller runtime → 一次 Room `DETACHED`；S26a 已接线 planner-issued `OpenConflict` 的 Room-only durable terminal opening。当前是 Android **有限双向同步**：普通删除不删 Provider，且没有连续 Provider inbound、`BIDIRECTIONAL`/Merge、自动/完整冲突 state machine、SyncAdapter 或远端派发；#242 只提供有限手动 UI。完整当前行为、精确 W16/W17 文件清单和回滚边界以[第 12.1 节](#121-w16w17-finalized-长期-worker-的-calendarlink-生产事实)为准；W18–W22 本地删除边界以[第 12.2 节](#122-w18w22-普通删除的本地-detached-生产路径)为准。

- common 已提交 `CalendarLinkRecord`、Schedule/Calendar 独立字段快照及严格 codec 的 typed canonical baseline contract。`a318dfa4b` 已将其以单表完整 canonical payload 纳入初始 Room schema v1；`faebec222` 已提供纯 `CalendarThreeWayPlanner`，逐字段处理 title、description、timing、RRULE 与 reminders 的 NoOp、单边传播、非重叠自动合并、同值收敛和同字段异值冲突；`3586e1b1a` 已提供纯 `CalendarConflictRecord` 与 fail-closed `open` transition，`f2f9eecb9` 已冻结 strict `CalendarConflictCodec`，`96b63b9ed` 已将最小 `calendar_conflict` entity/DAO persistence 纳入初始 Room schema v1，`51c6ad39c` 已新增 `ScheduleRoomDatabase.openCalendarConflictAtomically()`：它在 Room3 `withWriteTransaction` 外交叉验证 Opened identity、双基线、时间与重算 planner conflict，事务内冻结 LINKED/null-conflictId durable link、拒绝已有 evidence，并固定 link 后 evidence 写入且任一失败回滚；`de31f7e20` 已提供 Android strict read-only `CalendarAbsent`/`Present` canonical snapshot。该句是 `51c6ad39c` 当时的历史边界：它不实现重放成功、coordinator/recovery/idempotency，也未接入运行时。S26a 现已通过独立 planner-issued preflight、账号绑定 façade 与 Store batch transaction 接入窄 terminal open；取消或未收到 receipt 时仍只能由后续 fresh bounded reconciliation 精确重读 link/evidence，禁止自动重放。当前不添加 `calendar_link` FK；CalendarLink 的精确 scope delete 已存在，D-039/`a9406a307` 已提供严格 account/platform list，D-041/`187b0a9d7` 已提供 account-bound、固定 Room 后 Provider 的只读 discovery facade；仍没有 bootstrap/recovery scan。D-031 纯 command planning 与 `c4ad4e3d1` 的纯 common `CalendarScheduleMaterializer` 已完成；materializer 只把真实 planner guards 授权的 `TO_SCHEDULE`/`BIDIRECTIONAL` canonical target 反向生成完整 Schedule candidate，既不创建命令/outbox，也不分配 revision/`updatedAt`。这是截至 D-034 的历史下一步：当时尚需确定 coordinator 的最小可验证 action 边界、metadata 分配责任与 writer-scope runtime 接线。D-035 已完成 `TO_SCHEDULE` 的 metadata、normal Update/PATCH/outbox 与 writer-scope execution；现在只需为该方向补 production Provider preflight 和 runtime 调用接线。`TO_CALENDAR` 不物化或更新 Schedule，只在 SQLite 外处理 诚实命名的 best-effort Provider ownership/ref preflight + canonical read-after-write（不是 Provider CAS）；`BIDIRECTIONAL` 仍须先具备 phase/recovery/compensation。`OpenConflict` 的既有一次性 atomic open 已被 S26a 窄运行时调用，但只形成 terminal durable pair，不扩展为 resolution。可靠 Provider inbound、SyncAdapter、远端命令执行、完整 outbound write-back、平台同步基线推进、conflict resolution/UI 和真实双向闭环仍未实现；禁止一次性接入全平台闭环或用 Web remote-required 冒充已经完成。

`25b5245f9` 已新增纯 common `CalendarReconciliationCommandPlanner`：它在完整 account/platform/projection、durable link/双基线、Schedule revision 与 Provider ref/fingerprint guards 下，只返回 `NoOp`、`TO_CALENDAR`、`TO_SCHEDULE`、`BIDIRECTIONAL` 或 `OpenConflict` typed intent。`5ea4d19e4` 已进一步将 guards 收紧为 planner 签发的私有实现：公开接口或 delegate 即使字段自洽也不能伪造 provenance。动作后的 `confirm()` 从冻结 base/双方观察重跑 planner，精确校验 classification、propagation、target 与 confirmed final facts，只接受 `RESOLVED` NoOp、单边传播与 Merge；`ALREADY_CONFLICTING`、`NOT_ELIGIBLE`、`OpenConflict` 均不得推进。确认只签发 opaque advancement，Room 再以 whole-record exact CAS 推进 event ref、baseline revision/双方快照、fingerprint、updatedAt 六项；取消或未收到 `Unit` 时必须按 expected/updated/第三状态精确重读。planner/confirm/CAS 不创建 `ScheduleCommand.Update`、mutation/outbox/`requestSync`，不读写 Provider、repository、network 或 UI。`c4ad4e3d1` 的 materializer 同样无副作用，但已完成 Schedule leg candidate materialization：它重跑授权规划、重投影当前 source/exceptions 和 candidate，且只保留受管字段更新。`d0f85e451` 已完成 Store writer-scope durable composition primitive；D-035/`c668d69e6` 已进一步实际提交 `TO_SCHEDULE` 的本地 Schedule leg，而非仍待实现的 executor：未来 runtime 在 transaction 外冻结 Provider observation，内部 adapter 在单一 writer scope 内固定 metadata、normal Update/PATCH/outbox、read-your-writes、confirm/link advancement 与 generation，且零 Provider write。`TO_CALENDAR` 不物化或更新 Schedule，只在 SQLite transaction 外执行 honestly named best-effort Provider ownership/ref preflight + canonical read-after-write（不是 Provider CAS），再以最终 Provider facts 推进 confirm/link。`TO_SCHEDULE` 当前缺 production Provider preflight 与 factory/repository/runtime 调用接线；`OpenConflict` 继续交既有一次性 atomic open；`BIDIRECTIONAL` 的 Provider 外部 effect 与本地 bundled commit 仍需要 durable phase/recovery/compensation，尚不可声称可执行；之后才可继续 resolution/UI 与更广平台同步。

D-035/`c668d69e6` 已把 TO_SCHEDULE 的本地执行边界实现为 `ScheduleRoomLocalCommandAdapter.executeCalendarPropagationToSchedule()`，但它不是 production coordinator：只接受 planner-issued plan，并在 transaction 外 provenance/account fail-closed；同一 SQLite writer transaction 内 strict durable graph read、exact revision/overflow/exceptions guard、materialize、单次 Clock、`revision = expected + 1`/`updatedAt = commitAt`、真实 normal Update reducer 的 PATCH/baseRevision/outbox replay、read-your-writes/reprojection、纯 confirm、link whole-record exact-CAS、单次 generation 与 receipt。默认 Room Update 不支持 PUSH，只有 DEVICE reminders 可经成功路径持久化；pure materializer 的 PUSH preservation 不扩大此能力。stale revision/link、账号不符、overflow、exceptions、两类时钟倒退和 commit 前取消均 SQLite 整体 rollback；commit 竞态取消未知时重读 generation/link，必要时核对 graph/outbox。该入口零 Provider I/O，事务只原子覆盖 SQLite；未来 runtime 必须在入事务前冻结 TO_SCHEDULE Provider observation，提交后新 observation 只能进入下一 reconciliation。没有 runtime/coordinator/repository/factory 接线，现有 create/update 不自动触发；诚实命名的 best-effort Provider ownership/ref preflight + canonical read-after-write（不是 Provider CAS）、account-scoped runtime preflight facade、BIDIRECTIONAL durable phase/recovery/compensation、Provider inbound 与 recovery scan 均未实现。后续必须从新 HEAD Discover，不预先指定 coordinator 或 Provider effect 的实现形式为下一切片。

D-036/`a1e65656c` 已冻结纯 common Android CalendarLink discovery：版本化 codec 只接受 `android-calendar-row:v1:<positive canonical decimal>`，不可信 decode 安全返回 `null`；classifier 将冻结的 trusted account/platform links 和 `CalendarAbsent` 或 `Present(identifier, observations)` 以完整 `CalendarProjectionId` 分类为 canonical URI 稳定排序的完整 `Ready` 或 typed `Blocked`。trusted account/platform/link 失配抛 `IllegalArgumentException`；不可信 observation 的 platform mismatch、blank event ref/fingerprint，以及 identifier、duplicate full projection、absent-with-links 矛盾一律 fail-closed、无部分结果。它不读写 Room/Provider/network，不创建或更新 link，不执行 planner、`TO_SCHEDULE`、`TO_CALENDAR` 或 `BIDIRECTIONAL`，不授权写操作，也不声明跨存储原子快照；只定义 Android identity，不定义 iOS EventKit。D-037/`e63cf0c65` 已独立完成 snapshot-only 前置：Android snapshot `Present` 现在携带同一次 `registry.findCurrentManagedCalendar(accountId)` 得到的真实 Calendar row ID，经 codec 编码一次的 `calendarIdentifier`；空 `scheduleIds` 早返回和正常事件查询复用同一 identity。D-038/`b615468b1` 已完成由 production 单向导出协调器消费、以完整 session/scope/owner 生命周期门禁保护的 `AccountSessionScopedReadOnlyReader`：Provider 查询结果只在该生命周期仍权威时交付，逐项同步写前后继续复核，worker stale cancellation 会结束完整 lifecycle 并清理手动 completion，状态也按当前账号 binding fail-closed 投影。D-039/`a9406a307` 已完成 account/platform 严格范围、canonical projection URI 稳定排序且逐行 strict mapper 的 `CalendarLinkRoomDao.list()`；D-041/`187b0a9d7` 已完成 account-bound `AndroidCalendarLinkDiscoveryFacade`，固定 Room 后 Provider 的只读、非原子读取并交给 D-036 classifier。**历史 D-041** 没有 production caller、retry、repair、bootstrap、planner/coordinator execution 或设备运行验证；不实现 Provider inbound/write-back、`BIDIRECTIONAL` recovery、SyncAdapter、pull/apply、retention、Web remote-required、iOS EventKit runtime 或 conflict UI。不得把 D-036 `Blocked` 直接塞入既有单向 export `coordinator.start`；CalendarProvider 的 Events ownership pre-read 与 `applyBatch` 之间不是 CAS，且 fingerprint 横跨 Events/Reminders，未来最多称为 best-effort Provider ownership/ref preflight + canonical read-after-write，仍需生产 runtime 接线与恢复语义。后续必须从最新 integration HEAD 重新 Discover，不得预设下一切片就是 coordinator。

当前 Android `ACCOUNT_TYPE_LOCAL` 不等价于正式 Android Account/SyncAdapter。`IAccountService.accountCoroutineScope` 可以管理当前应用进程内导出 worker，但不能承担系统 SyncAdapter 生命周期或持久化同步事实；正式双向必须依赖 durable SQL、outbox 和 baseline，而不是进程内 `Channel`、map 或 flag。

## 5. 动态工作流入口

任务开始时先按规模选择执行方式：

1. **简单、明确、局部的单个需求**：由主 Agent 或普通 subagent 在当前主工作区串行完成，不创建 worktree，不启动 Workflow；
2. **复杂或大型需求**：只要需要多任务并发、多阶段独立审查或跨多个子系统协作，必须先调用并完整阅读全局 skill `worktree-workflow-orchestration`，按其中的 DAG、持久 worktree、IDE 索引、串行集成、恢复清单和清理协议执行；
3. 每轮复杂任务仍应先批量收集候选需求并建立依赖 DAG 与文件/API/测试/文档冲突矩阵；是否并发由依赖和冲突决定，不因同仓库而自动串行，也不在共享主工作区放置多个 writer；
4. 正式 Workflow 只负责任务泳道编排。物理 worktree、任务分支、IDE 项目、提交、集成与清理由主 Agent 管理，禁止把单次 `agent(..., { isolation: "worktree" })` 当作可跨阶段复用的正式泳道；
5. 主工程当前分支是唯一 integration branch 和单写者入口。各实现 Agent 只写自己的持久 worktree；主 Agent 在泳道内按精确 pathspec 提交，再串行集成到主工程；
6. 连续实现、返修和补测试优先恢复原实现 Agent；独立 reviewer 只读且不得与实现者并发修改同一泳道；
7. 每个实施切片完成后立即执行适用的编译或单元测试、IDE diagnostics 和独立审查，修复确认问题后独立提交，不停在“已发现问题”状态等待用户催促；
8. 后台 Agent、构建和测试能自动通知时不做短间隔轮询；除非触发第 10 节停止条件，否则依据专题文档、当前代码和项目惯例继续推进；
9. 不自动 amend、push、创建 PR、部署服务、连接生产数据库或操作真实用户日历；
10. 动态循环每轮结束前必须明确选择继续执行、等待已启动任务、正常完成或因阻塞停止，不能无说明地结束。

## 6. Schedule worktree 与 Git 附加边界

通用规则不在本文重复展开；以下仅补充本项目必须传给 `worktree-workflow-orchestration` 的约束：

- 客户端 integration checkout 为 `/Users/guoxiangrui/AndroidStudioProjects/Cyxbs/CyxbsMobile_2`，持久泳道默认位于其 `.claude/worktrees/lane-XX`；后端 `/Users/guoxiangrui/GolandProjects/magipoke-todo` 使用独立的 worktree 池和 Git 状态；
- 主 Agent 创建 worktree 前必须冻结 integration commit，并验证目标目录已被外层仓库忽略；创建后使用 `git worktree list` 核对登记状态。linked worktree 中的 `.git` 是指向主仓库元数据的文件，不是嵌套的完整 Git 仓库；
- 任务分支使用 `<integration-branch>_claude/<wave>-<task-id>-<slug>`。物理泳道复用时，从最新 integration commit 新建下一任务分支，不在 linked worktree 中 checkout 已被主工程占用的 integration branch；
- Android/Kotlin 泳道通过 `android-studio-index` 的 `ide_open_project`/`ide_close_project` 管理；后端泳道使用 GoLand MCP。物理泳道连续复用时可保持对应 IDE 项目打开，外部文件变化后按需 `ide_sync_files`；
- 每条泳道必须冻结任务 ID、task branch、worktree 路径、base commit、精确文件 allowlist、API 依赖、验证入口和预期提交。writer 需要扩大路径时先停止修改，由主 Agent 重新计算 DAG 与冲突矩阵；
- subagent 不得暂存、提交、切换分支或使用 shell 绕过文件工具限制。主 Agent 核对实际 diff、按精确 pathspec 提交，并按依赖顺序串行集成；源码与文档继续使用独立 commit；
- 搜索、格式化、代码生成和清理命令必须排除 `.claude/worktrees/`。存在仓库内 linked worktree 时禁止外层 `git clean -fdx`，删除物理泳道只能使用 `git worktree remove`，不得使用 `rm -rf`；
- 所有实施与 integration 验证完成后可以关闭 IDE 并删除物理 worktree，但任务分支必须标记为 `AWAITING_USER_ACCEPTANCE` 并保留。只有用户明确验收全部需求后才进入 `USER_ACCEPTED` 并清理任务分支；
- 两个仓库都要保留各自的恢复清单。后端受保护文件 `/Users/guoxiangrui/GolandProjects/magipoke-todo/schedulev2wire/decode.go` 不得修改、格式化、暂存或提交。

## 7. 测试与验证限制

### 7.1 Android

已具备受限的 Android 真机验证入口：

- 只使用 `:cyxbs-pages:schedule:persistentAndroidDeviceTest`；该任务只能从 test application module 打包并安装测试 APK，依赖 `installAndroidDeviceTest` 覆盖安装后再以 ADB 运行 instrumentation，结束时保留测试 APK；不得借此打包或安装 production app；
- **不得**使用 `connectedAndroidDeviceTest`，避免其常规清理路径卸载测试 APK，并在部分 Xiaomi 设备上重复触发人工安装确认；
- 通过 `-PandroidDeviceSerial=<serial>` 指定唯一设备；优先使用 `-PandroidDeviceTestClass=<class>` 限制为单个测试类；
- 任务在启动 instrumentation 前校验本次 AGP 生成 manifest 的 package 与 runner，禁止保留旧 APK 时误跑过期 component；同时捕获 AndroidJUnitRunner 的终态失败摘要，避免 `adb am instrument` 在断言失败时仍返回 shell exit 0 而被 Gradle 误报成功；
- 已在实体设备执行 `AndroidManagedCalendarSnapshotDeviceTest`。该 smoke test 只验证 instrumentation runner 与 W42 纯映射：不创建 Context、不读 Calendar Provider、不打开 Room、不访问网络，也不请求或写入真实用户日历；
- Calendar Provider、OEM、权限、AccountManager、Authenticator、SyncAdapter、系统账号、真实日历 CRUD 与后台行为已获得定向真机验证授权，但每项仍须以指定 serial/class 执行、界定并清理测试创建的数据；单一 smoke test 或通过结果不能扩大为未运行场景的验收，也不允许外部后端 mutation 或生产/其他用户数据访问。

应完成：

- common 单元测试；
- Android 可运行的 host/unit test；W47 曾因 `Date` 顶层初始化会在 host stub 中调用未 mock 的主 Looper，临时从 `androidHostTest` 排除 `CalendarProviderTimingCanonicalizerTest`。该临时措施已由 W49 supersede：canonicalizer 直接构造领域 `Date`，避免加载 `DateKt.<clinit>`，此纯时间合同现重新纳入 Android host suite；同一合同仍须由 `desktopTest` 保持执行，不能误报为 Provider 行为已覆盖或删除该测试；
- non-Web Room3/SQLite contract tests：transaction 任一步失败全 rollback 且不发布、账号隔离及迟到 I/O、outbox/tombstone/cursor/deviceId、并发、候选范围与 moved exception、DB 打不开/约束/mapper 失败 fail-closed；
- Room3 toolchain 验证：生成 P0/后续业务 constructor、Android/iOS/Desktop builder、Web JS/Wasm 无 Room3 dependency 与 configuration cache；P0 编译或 builder 存在不能替代业务 Store contract tests；
- Settings primary/backup JSON 专属测试只覆盖已退役的 Web repository fallback；Android、iOS 与 Desktop production factory 已接入 Room3，Web production 已切为无持久化 unavailable façade。旧 fallback 实现与测试仅作为历史迁移资料保留，不得再描述为 production；后续删除须另设受审查任务，不能混入 semantic v2 实现。iOS EventKit intent/ledger 的独立 Settings cache 另有自身合同，不能与 Web repository fallback 混写；
- planner、canonicalizer、baseline、三方 diff、冲突、防回环和 mapper 等纯逻辑单测；
- Android 主源码编译；
- `androidDeviceTest` 源码可编译时运行对应 compile 任务；对于无 I/O 且有明确设备范围的 smoke test，可使用上述 persistent task 执行。仅“编译成功”不得表述为“真机测试通过”；
- Android Studio IDE diagnostics。

### 7.2 iOS

本轮无法进行 iOS 真机、模拟器 EventKit 集成或实际权限测试，因此：

- 不执行真实 EventKit calendar CRUD；
- 不声称 write-only/full-access、source、identifier、notification 或后台行为已经通过设备验证。

应完成：

- common 单元测试；
- iOS 可编译部分与纯 adapter/mapper 单测；
- 使用 fake 或 in-memory EventKit gateway 测试 projection、link 恢复、canonicalization 和错误映射；
- 可用时执行不依赖真实 EventKit 数据库的 iOS target 编译；
- IDE diagnostics。

实际权限、Info.plist、source、iCloud、`EKEventStoreChangedNotification`、BackgroundTasks、`calendarIdentifier` 和 `eventIdentifier` 行为必须列入后续真机验证清单。

### 7.3 后端

后端需要独立集成测试设计，但本地不启动完整服务或外部依赖。`go`/`gofmt` 未加入 shell `PATH` 时，必须使用已安装 SDK 的绝对路径或显式 `PATH`；本轮已通过 `/Users/guoxiangrui/sdk/go1.26.4/bin/go` 完成 `./schedulev2wire` 与 `./service -run 'TestScheduleV2Bootstrap'` 两组聚焦测试。不得把这两组结果扩大为 unrestricted service package、真实 MySQL 或端到端验证。

本地应执行：

- Go 单元测试；
- 不依赖真实数据库或外部服务的 service、repository、validator、codec、cursor 和 idempotency 测试；
- `gofmt`、项目既有静态检查和 GoLand diagnostics。

本地不执行：

- 真实数据库 migration；
- 完整服务启动；
- 依赖认证、数据库、中间件或部署环境的端到端测试；
- Android/iOS/后端联合集成测试。

真实 MySQL integration suite 的准备按 D-012 暂不纳入当前代码切片；部署准备阶段必须另开任务：

- 使用明确的 integration build tag、独立 suite 或项目既有集成测试约定；
- 覆盖 migration、CAS、mutation idempotency、change sequence、冻结高水位分页、retention floor 与 `410/reset_required`、tombstone、bootstrap、split transaction 和 owner 隔离；
- 不伪造当前已编写或已通过；
- 届时列出集成测试入口、所需环境、清理流程和部署环境中的验证步骤。

若后端已有测试、migration 或容器约定，部署准备时应复用既有结构，不另造平行框架。

## 8. 文档同步要求

实施过程中持续维护：

- Android 双向：[双向日历同步设计](schedule-v2-calendar-bidirectional.md)；
- iOS：[iOS EventKit 导出设计](schedule-v2-calendar-ios-eventkit.md)；
- 后端：[后端 Schedule v2 设计](schedule-v2-backend.md)；
- SQL、基线与冲突：[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)；
- 当前 Android：[Android 单向日历导出架构](schedule-v2-calendar-export.md)；
- 当前数据流：[当前创建、更新与同步数据流](schedule-v2-current-data-flow.md)；
- 总状态与门禁：[Schedule v2 总路线图](schedule-v2-calendar-roadmap.md)。

磁盘恢复事实源：`3586e1b1a`（common conflict evidence/open）、`f2f9eecb9`（strict conflict codec）、`96b63b9ed`（Room `calendar_conflict` persistence）、`51c6ad39c`（一次性 open conflict pair）、`de31f7e20`（Android strict read-only snapshot）、`25b5245f9`（纯 common reconciliation command planning）、`5ea4d19e4`（planner-issued guards、confirmed-state/opaque advancement、Room whole-record exact CAS）与 `c4ad4e3d1`（planner-guarded 的纯 common Schedule candidate materialization）、`d0f85e451`（Store writer-scope durable composition primitive：同 SQLite commit 内 graph/outbox/link/sync-state generation）、`c668d69e6`（planner-authorized TO_SCHEDULE adapter 内部单事务执行入口）、`a1e65656c`（纯 common Android row identity codec 与 CalendarLink discovery classifier）、`e63cf0c65`（snapshot-only 真实 Android Calendar row identity：`Present` 从同一次 registry 查询取得 row ID，并经既有 codec 编码一次为 `calendarIdentifier`，空过滤早返回与正常查询复用该 identity）、`b615468b1`（production-consumed session/scope/owner scoped reader、Provider 逐项生命周期 preflight、完整 worker 取消与 owned status 投影）、`a9406a307`（按 account/platform 严格范围、canonical projection URI 稳定排序且逐行 strict mapper 的 CalendarLink Room list）、`187b0a9d7`（account-bound Room 后 Provider 的只读、非原子 CalendarLink discovery facade）、client `eb2322571`（严格 `schemaVersion=1` bootstrap DTO/wire codec）、`57085e1fd`（Settings-envelope 权威 bootstrap 合并与原子 apply planner）、`0f341b58d`（Settings fallback 每轮最多冻结 2 条 eligible mutation、同账号互斥且逐条串行的 durable dispatcher），以及 backend `05b7224`（mutation immutable change：`NOT NULL LONGBLOB` 的 exact stored bytes、其 SHA-256 与私有 DELETE snapshot）和 `499fec3`（默认关闭、单响应、资源数/最终 canonical body 字节双硬上限、HMAC 认证 owner-bound fixed-high-water opaque cursor 的内部 bootstrap v1 service）及其定向验证记录。**紧随其后的 D-035–D-041 “future runtime”缺口是截至 D-041 的历史恢复说明，不是当前 production 状态。**当前基线为 W15/D-045 的 bounded inbound-first `TO_SCHEDULE` 启动链，以及仅在 finalization 后启用的 W16、既有唯一 `LINKED` `TO_CALENDAR` Update/`RESOLVED` NoOp 确认；W17 只在 whole-batch preflight 通过时认领 exact orphan 或进行一次 fixed-row missing-link Create，随后完整重规划。S26a 的 Room-only durable conflict open 已接线；S26h 保持两个 typed pure terminal entry 与可伪造/会过期的 `TerminalProposal`；其历史纯合同不增加 writer，也不指定 evidence delete/archive/FK/GC 或 choice retention。S206-02 已另行实现 production-uncalled 的 ScheduleWins Room-only terminalizer：fresh-read/re-run transition 后 exact-CAS `LINKED + conflictId == null`、物理 exact-delete active evidence并保留 append-only choice；仍不增加 Provider/Schedule executor、runtime/recovery/UI/callback/retry/replay/compensation/publication/新 link state，`CONFLICT` 不自动传播，S26c/f/g 不识别 evidence-after-change convergence。连续 inbound 可靠投递、`BIDIRECTIONAL`/Merge、自动/完整 conflict state machine、SyncAdapter 与远端派发仍未实现；#242 已集成的设置页手动入口只按用户单次操作调用 #241，不能替代这些能力；普通删除已只做本地 `DETACHED`、不删 Provider，完整范围与回滚以[第 12.1 节](#121-w16w17-finalized-长期-worker-的-calendarlink-生产事实)及[第 12.2 节](#122-w18w22-普通删除的本地-detached-生产路径)为准。恢复后先确认 planner guards、materializer、confirmation transition、Room primitive、D-038 生命周期门禁及 D-039/D-041 的只读边界一致；不得重复 snapshot、reader、严格 list 或 facade 前置。**历史 D-041** 没有 production caller、retry、repair、bootstrap、planner/coordinator execution 或设备运行验证，不得把它或 D-036 `Blocked` 直接塞入既有单向 export `coordinator.start`。执行边界必须以完整 identity 重读 link/evidence、Schedule revision 与 Provider ref/fingerprint；`OpenConflict` 走既有 atomic open。D-035 已完成 `TO_SCHEDULE` 的 internal SQLite Schedule leg：future runtime 先在 transaction 外冻结 live Provider observation，再调用 adapter；adapter 在同一 writer scope 内完成 strict read、candidate materialization、固定 metadata、normal Update/PATCH/outbox、read-your-writes、confirm、link advancement 与 generation，零 Provider write。`TO_CALENDAR` 不物化或更新 Schedule，只在 SQLite transaction 外执行诚实命名的 best-effort Provider ownership/ref preflight + canonical read-after-write（不是 Provider CAS），再以最终 Provider facts confirm/link advancement。取消或未收到 `Unit` 不能当作未提交。`BIDIRECTIONAL` 的 Provider 外部 effect 与本地 bundled commit 仍缺 durable phase/recovery/compensation；连续/完整协调器的 recovery/idempotency、Provider inbound application、`BIDIRECTIONAL` runtime、SyncAdapter、pull/apply、retention 与 conflict UI 也尚未实现；不得将它们与已接线的 W15–W22 有限运行时混为一谈。client `eb2322571` bootstrap codec、`848c48e7b` strict delta codec/exact-session `KtorScheduleReadTransport`、`57085e1fd` Settings planner、`ef1726adb` bootstrap applier 与 `ScheduleRoomDeltaApplier`/`ScheduleRoomRemoteSyncRunner` 只形成 Android/iOS/Desktop 的 legacy/source consumer；W06 Wave 0 后旧 endpoint 已不可达。只有显式 `RequestSync` 先 bounded push 并得到 `Success` 后，runner 才从 strict durable cursor bootstrap 或拉取 page；每个 page 在单 SQLite transaction 原子提交 graph+opaque cursor，严格 reread 后才发布。same cursor 在 commit 前拒绝，cycle 在已提交 page 后、重发前停止；410 不清库且仅 bootstrap 一次后停止，400 terminal。历史 Web Settings dispatcher 仅作迁移资料；当前 Web production 是无持久化、无 I/O unavailable façade，不创建 dispatcher、durable state 或请求。上述客户端源码不能证明后端已部署/启用或真实服务互操作。backend 旧 source 曾包含 bootstrap 公开路由、默认关闭的 runtime config、fixed-window delta 与严格认证 `POST /v2/schedules:sync`；W06 Wave 0 已从 production router 移除这些旧 route，精确旧 POST 在认证、service 与 store 前 `404`。这些 high-water/cursor/reconstruction/reset artifact 只作迁移资料，不是当前公开 endpoint。D-040 私有 DELETE snapshot/hash 仍不进入 public DELETE wire。backend integration `e367445` 已提供内部未接线的单 owner retention compactor source capability：owner 行锁、半开 change 删除与单次最多 10,000 个 sequence 的 floor 推进已由 transaction-local database/sql fake 与 service interleaving fake 聚焦测试覆盖，但不证明真实 InnoDB 并发。当前仍无 retention policy、owner enumeration、invoker/调度、metrics/backoff、部署或实际 enable 验收、网络互操作、真实 MySQL、Web remote-required consumer、remote occurrence-exception full stack、可靠后台/进程死亡恢复或最终跨平台 acceptance；不得因后端 source route 或客户端 gateway construction 已存在而提前声称远端同步已部署、自动运行或完成端到端验收。Android/iOS/Desktop 的 production source runner 仍保留显式 `RequestSync` push-success 后的 paged bootstrap/delta、单页原子 graph+cursor commit、cursor safety、`410/400` 分型及 post-run strict publication；这些只是旧协议客户端源码事实，旧 endpoint 已不可达，且不得被描述为当前 production remote path。

规则：

- 已实现行为才更新到“当前实现”；
- 未实现或无法验证的内容不得标为完成；
- 真机未测试时保留明确验证缺口；
- 后端真实 MySQL 集成验证若已编写，注明“已编写、未在本地执行”；若尚未编写，必须明确记录并列出部署准备阶段的入口、所需环境与范围；
- 不在多个文档复制同一份 schema 或算法，使用相对链接指向唯一事实源。

## 9. 正常完成条件

只有以下条件全部满足，主 Agent 才能将本次动态工作流标记为“正常完成”，并调用动态循环的停止能力（`ScheduleWakeup(stop = true)`）：

### 9.1 实施范围完成

- Android 有限双向同步、iOS EventKit 单向导出和后端 v2 改造均已达到对应专题文档为本轮定义的代码范围；
- common Room3 业务 durable store、持久化 link/baseline/conflict/origin、三方 diff、远端同步闭环等硬前置已经落地，或专题文档明确将不属于本轮的部分排除且不被已完成能力依赖；Room3 P0 或 builder 编译通过不等价于这些业务门禁完成；
- 不存在用进程内临时状态伪装 durable 同步事实的实现；
- 已实现内容与路线图、专题文档和当前数据流说明一致。

### 9.2 本地可执行验证通过

- 本文要求的客户端 common、Android host/unit、iOS 纯逻辑及后端 Go 单元测试全部通过；
- 适用的 Android/iOS 编译任务通过；
- `gofmt`、项目既有静态检查、Git diff 格式检查和 IDE diagnostics 无本次改动引入的错误；
- 不能本地运行的 Android/iOS 真机测试已明确排除并保留入口；后端真实 MySQL integration suite 按 D-012 延后到部署准备阶段，当前不得伪装为已编写或已通过；
- 所有未执行验证均有运行入口、环境要求和后续步骤，不能只写“待测试”。

### 9.3 审查收敛

- 每个主要工作流至少经过一次独立审查；
- 审查确认的 correctness、数据丢失、越权、隐私、并发、幂等和跨端协议问题均已修复并重新验证；
- 不存在未处理的高严重度问题；
- 低严重度遗留项若不影响本轮承诺，必须记录原因和后续处理入口。

### 9.4 协作与工作区收敛

- 复杂 Workflow 中每条实际启动的 Android、iOS、common、文档、同平台独立需求和后端泳道都有任务 ID、task branch、worktree 路径、base commit、精确文件列表、验证结果和对应 commit；简单主工作区任务只需记录任务 ID、精确路径、验证和 commit，worktree/task branch 字段记为 `N/A`；
- integration branch 已按依赖顺序串行集成全部完成切片，并通过实际 `git status --short --untracked-files=all`、任务元数据和提交 pathspec 核对，不存在未登记、错归属或混入其他任务的修改；
- 所有物理 worktree 均已结束写入。完成最终 integration 验证后可关闭 IDE 并使用 `git worktree remove` 清理物理目录；若仍保留，恢复清单必须明确其分支、HEAD、工作树和 IDE 状态；
- 所有 task branch 在用户最终验收前保持 `AWAITING_USER_ACCEPTANCE`，不得提前删除；
- 两个仓库的 Git 与 `git worktree list` 状态已检查，没有意外覆盖用户改动，也没有遗留未归属修改；
- 已完成、验证且独立审查收敛的切片均已独立 commit；未 amend、push、建 PR、部署或连接生产环境。

### 9.5 最终报告完成

最终报告必须完整给出第 11 节要求的信息。仅完成代码但没有测试结果、未验证清单、worktree/task branch 状态和后端集成测试入口，不视为正常完成。

### 9.6 不阻塞正常完成的外部验证

在满足上述条件的前提下，以下事项因本轮环境限制不阻塞“本地实施完成”，但必须明确标为“未验证”，不得标为“功能已在真实环境验收”：

- Android 真机上的 Provider、AccountManager、Authenticator、SyncAdapter、权限和 OEM 行为；
- iOS 真机上的 EventKit 权限、source、iCloud、identifier、notification 和 BackgroundTasks 行为；
- 后端真实数据库 migration、认证、多实例并发和部署环境集成测试；
- Android、iOS 与后端端到端收敛。

## 10. 停止条件与禁止误停

### 10.1 必须停止并请求用户处理

出现以下任一情况，主 Agent 必须停止继续写入，保存当前可恢复状态，给出阻塞报告，并调用 `ScheduleWakeup(stop = true)`；不得绕过或擅自扩大权限：

1. **需要用户决策**：文档和代码无法推导出唯一方案，且选择会改变产品语义、协议兼容、安全边界、数据迁移方式或用户可见行为；
2. **需要外部或不可逆操作**：下一步必须 push、建 PR、部署、运行真实数据库 migration、连接生产服务、操作真实用户日历或执行破坏性设备测试；
3. **发现意外工作区冲突**：目标文件含无法归因的用户或其他 Agent 新改动，继续写入会覆盖或错误合并；
4. **隔离无法保证**：复杂任务无法创建或核验所需 worktree/任务分支，或冲突文件无法安全安排为同一泳道串行处理；
5. **关键工具或权限被拒绝**：IDE 索引、目标文件、构建或测试所需权限持续不可用，且没有符合本文约束的安全替代路径；
6. **既定决策不可实现**：平台或当前依赖版本证明专题设计中的硬要求不可实现，需要修改已冻结架构或缩小产品承诺；
7. **安全或数据完整性风险**：发现实现可能越权处理第三方日历、泄露可识别信息、破坏 Schedule 事实、造成无法恢复的数据丢失或错误 migration，且无法在当前范围内可靠修复；
8. **持续无进展**：同一根因经过三轮有实质差异的诊断与修复尝试仍无新证据、相同验证仍失败；此时必须报告尝试、证据和最小后续动作，而不是无限循环；
9. **测试无法收敛**：本地应执行的测试或编译仍失败，且失败不能归因于已明确排除的外部环境；不得把失败状态标记为完成；
10. **任务范围发生外部变化**：用户、其他会话或仓库状态改变了本轮目标，使现有计划不再安全或有效。

阻塞停止不等于正常完成。报告中必须明确写“因阻塞停止”，列出：已完成项、未完成项、阻塞证据、工作区状态、已启动但未结束的任务，以及用户需要做出的最小决定或操作。

### 10.2 可以主动停止的其他情况

- 用户明确要求暂停或停止；
- 用户拒绝继续所必需的权限或操作；
- 动态工作流被系统预算、会话生命周期或不可恢复工具错误终止。此时应尽可能留下恢复点和未完成任务清单，不得声称正常完成。

### 10.3 不能作为停止理由

以下情况不得让主 Agent提前结束：

- 后台 Agent、构建或测试仍在运行；应等待自动通知或设置长兜底唤醒；
- 只完成了探索、计划、其中一个平台或后端；
- code review 刚发现可在当前范围内修复的问题；
- 真机或部署环境不可用；这些限制已在本文中明确，应改为完成本地可验证范围并记录后续验收；
- 某个实现 Agent失败但主 Agent或其他合适 Agent仍可继续；
- 只剩文档、worktree/任务分支清单、diagnostics 或最终报告；
- 当前轮次没有立即可执行动作，但已有后台任务会通知；此时应设置长兜底唤醒，而不是终止循环。

## 11. 最终交付报告

正常完成或阻塞停止前，主 Agent必须给出以下报告。

### 11.1 需求泳道、worktree 与 task branch 总表

复杂 Workflow 中每条实际启动的 Android、iOS、common、文档、同平台独立需求和后端泳道都必须单独列出；简单主工作区任务也需单列，但 worktree/task branch 字段记为 `N/A`：

- 任务 ID、owner、仓库、task branch、worktree 路径与 base commit；
- 精确文件列表、API 依赖、与其他泳道的冲突处理和串行集成顺序；
- 已完成能力、独立审查结论、验证结果、任务 commit 与 integration commit；
- 未执行验证、已知风险、工作树状态，以及 task branch 是否仍为 `AWAITING_USER_ACCEPTANCE`。

### 11.2 Android 平台补充

- Android 编译和测试结果；
- 未执行的真机/Provider 测试清单；
- Android 平台特有风险。

### 11.3 iOS 平台补充

- iOS 编译和测试结果；
- 未执行的 EventKit/真机测试清单；
- iOS 平台特有风险。

### 11.4 Worktree 与共享文件

- 物理泳道、IDE 项目和任务分支的创建、复用与清理状态；
- 共享文件被安排到哪条单写者泳道，以及对应的串行集成顺序；
- 是否仍有未合并、未提交或未归属修改。

### 11.5 后端仓库

- 文件列表、task branch、worktree、任务 commit 与 integration commit；
- 单元测试结果；
- 按 D-012 延后的真实 MySQL integration suite 范围与部署准备入口；
- 集成测试入口和依赖环境；
- migration、部署、认证等未验证项。

### 11.6 文档与门禁

- 已更新的阶段状态；
- 已满足和未满足的 Gate；
- 后续真机、部署和端到端验证步骤。

### 11.7 Git 与停止结论

- 两个仓库各自的 Git status 与 `git worktree list`；
- 所有 task branch、物理 worktree、任务提交、集成提交和未提交状态；
- 确认每个完成、验证且审查收敛的切片已独立 commit，且未 amend 或 push；
- 确认物理 worktree 仅在完成 integration 验证后清理，task branch 在用户验收前仍保留为 `AWAITING_USER_ACCEPTANCE`；
- 明确结论为“正常完成”或“因阻塞停止”，不得使用含糊表述。

## 12. 当前 D-045 生产范围与后续工作门禁

D-045 的 W15 生命周期、撤销、`bootstrapMissingLinks`、inbound-first `TO_SCHEDULE`、Delete-free legacy 首轮 Create/Update/NoOp 导出、post-apply bootstrap 与 final `NoOp` fence 都是当前生产启动链。legacy exporter 会在任何 Provider callback 前由 `CalendarExportPlanner.assertLegacyProviderPlanDeleteFree()` 拒绝含 `CalendarExportAction.Delete` 的整批计划，因此不存在 legacy 单事件 Provider 删除；`legacyDeleteFact` 只可为 W22 本地 `DETACHED` 提供证据，普通删除不删 Provider。W16+W17 只增强 finalization 后的长期 worker，绝不替代或跳过这些启动步骤。

### 12.1 W16+W17 finalized 长期 worker 的 CalendarLink 生产事实

W16 dispatcher 只在已有且唯一的 `LINKED` link 上推进 CalendarLink：planner-issued `TO_CALENDAR` outbound `Update`，或 recovery 中 planner-resolved `RESOLVED` `NoOp`。W17 不改写 dispatcher 的 eligibility：它在 dispatcher 之外，仅恢复经过 whole-batch preflight 的 missing link，优先 adoption 已存在的 exact canonical orphan（零 Provider 写），否则只允许一个 planner-issued fixed-row Create。S26a 在 W16/W17 callback 前另行拦截 planner-issued `OpenConflict`，只做 Room-only durable terminal opening；Delete、连续 Provider inbound、`BIDIRECTIONAL`/Merge、自动/完整冲突 state machine、SyncAdapter、backend/remote dispatch 和设备 acceptance 仍未实现或未验证；#242 one-shot 手动 UI 不改变这些缺口。

W16 Update 执行边界必须固定为：

1. **Room read → Provider read**：按 exact session/account/platform 先做 Room strict link read，再读取受管 Provider snapshot 并规划；禁止把较早 Provider facts 与较新 link 配对。读取非原子，不能称跨-store snapshot。
2. **fresh pre-write → write → read-back**：Provider write 前必须新做严格 canonical read，要求 stable calendarIdentifier、唯一 projection/event ref、fields 和 fingerprint 全部逐值等于 planner 冻结 observation；随后才执行受授权 Provider Update，并以携带 calendarIdentifier 的严格 read-back 得到 target receipt。
3. **Room exact-CAS → final reread**：只有 read-back 精确匹配 target，才在 Room writer transaction 内重验 provenance/link/local projection 并 whole-record exact-CAS 推进 CalendarLink/generation。CAS 后再次严格 reread；若 calendarIdentifier 或唯一 observation 不再精确等于确认 receipt，保留 truthful last-common baseline，返回 typed fresh-replan failure，不 rollback Provider/CalendarLink 且不发布 Completed。

W17 missing-link recovery 的完整边界必须固定为：

1. **whole-scope preflight**：以完整 Room links、完整 Provider scope、local snapshot、calendar identity 和 export actions 做 single-winner 选择；任何另一 action/link 为 retrying/conflict、`TO_SCHEDULE`、Delete、Unsupported、missing-event 或 identity-invalid，整批零 Create，且不向 W16 dispatcher 派发局部 callback。
2. **adopt 或 fixed-row Create**：exact orphan 仅走 zero-write adoption；完全缺失 target 必须由 planner 签发 Create，并在 strict preflight 确认的同一 Calendar row 上调用单次、非创建 `Provider Create`。禁止 get-or-create、替换或认领同名 replacement row。
3. **严格 receipt 与 durable winner**：Create 后 strict canonical read-back 必须包含唯一 target、完整 canonical fields、同一 calendarIdentifier 和 returned event ID；随后 bootstrap planner 才可签发 candidate，Room `create-if-absent` 返回值必须精确等于 durable winner。
4. **final whole-scope reread → complete replan**：adoption/Create 后均必须重新读取完整 Provider scope；只有最终事实仍精确时才视为 recovery verified，旧 Room/Provider/plan 一律废弃并 complete replan，不能计入 Completed 或继续复用 W16 input。
5. **post-Create 边界**：Room 与 Provider 没有共同事务。Create 后 blocked、unknown 或 Room failure 保留 orphan，不做 compensating delete 或 retry；同一逻辑 worker batch 的 shared ledger 禁止第二次 Create，后续 fresh discovery/bootstrap 才可 adoption exact matching orphan。

**W16+W17 完整实现与回滚清单（唯一当前事实源）**如下；其他当前实现、production wiring 或 rollback 描述必须完整复述本清单，或直接链接到本节，不能只列上层 coordinator。相对 W16，W17 代码/测试增量必须**恰好**为：`ScheduleCalendarMissingLinkCreateRuntime.kt`、`ScheduleCalendarMissingLinkCreateRuntimeTest.kt`、`ScheduleCalendarExportCoordinator.kt`、`AndroidScheduleCalendarGateway.kt`、`AndroidCalendarProviderInstrumentedTest.kt`；不能漏回退或扩展为未批准的文件：

- commonMain（W16）：`cyxbs-pages/schedule/src/commonMain/kotlin/com/cyxbs/pages/schedule/domain/calendar/ScheduleCalendarReconciliationAccess.kt`、`cyxbs-pages/schedule/src/commonMain/kotlin/com/cyxbs/pages/schedule/domain/calendar/ScheduleCalendarOutboundLinkRuntime.kt`（含 production batch dispatcher）；
- commonMain（W17）：`cyxbs-pages/schedule/src/commonMain/kotlin/com/cyxbs/pages/schedule/domain/calendar/ScheduleCalendarMissingLinkCreateRuntime.kt`；
- androidMain（W16+W17）：`cyxbs-pages/schedule/src/androidMain/kotlin/com/cyxbs/pages/schedule/calendar/ScheduleCalendarExportInitializer.android.kt`、`cyxbs-pages/schedule/src/androidMain/kotlin/com/cyxbs/pages/schedule/calendar/AndroidManagedCalendarRegistry.kt`、`cyxbs-pages/schedule/src/androidMain/kotlin/com/cyxbs/pages/schedule/calendar/AndroidScheduleCalendarGateway.kt`、`cyxbs-pages/schedule/src/androidMain/kotlin/com/cyxbs/pages/schedule/calendar/ScheduleCalendarExportController.kt`、`cyxbs-pages/schedule/src/androidMain/kotlin/com/cyxbs/pages/schedule/calendar/ScheduleCalendarExportCoordinator.kt`；
- noWebMain（W16）：`cyxbs-pages/schedule/src/noWebMain/kotlin/com/cyxbs/pages/schedule/data/local/room3/RoomScheduleRepository.kt`、`cyxbs-pages/schedule/src/noWebMain/kotlin/com/cyxbs/pages/schedule/data/local/room3/ScheduleRoomLocalCommandAdapter.kt`；
- contracts（W16）：`cyxbs-pages/schedule/src/commonTest/kotlin/com/cyxbs/pages/schedule/domain/calendar/ScheduleCalendarOutboundLinkRuntimeTest.kt`、`cyxbs-pages/schedule/src/desktopTest/kotlin/com/cyxbs/pages/schedule/data/local/room3/RoomScheduleRepositoryDesktopTest.kt`；
- contracts（W17）：`cyxbs-pages/schedule/src/commonTest/kotlin/com/cyxbs/pages/schedule/domain/calendar/ScheduleCalendarMissingLinkCreateRuntimeTest.kt`、`cyxbs-pages/schedule/src/androidDeviceTest/kotlin/com/cyxbs/pages/schedule/calendar/AndroidCalendarProviderInstrumentedTest.kt`（受控随机 LOCAL 日历上的 fixed-row Create/membership 漂移 compile-only source contract）。

不改变 W15 启动链、Room schema、backend 协议或远端 caller。Android registry lookup 固定使用已确认的 calendar row，非创建且不替换日历；gateway 的 ownership/membership Provider Update 与 fixed-row Create、coordinator/runtime 属于同一生产修改边界。W16 batch dispatcher 在任何 callback 前用 `CalendarLinkDiscovery` 验证全批 stable row/link/observation，只派发 finalized worker 的唯一 LINKED Update/待 runtime 证明的 `RESOLVED` NoOp；W17 不能借此放宽 W16，而是先完成上述 whole-batch recovery。

本次验证证据包括已批准的 production review、`ScheduleCalendarMissingLinkCreateRuntimeTest` focused contracts（callback 顺序、whole-batch 阻断、strict receipt、post-Create no-second-Create ledger 和 final reread）、既有 `ScheduleCalendarOutboundLinkRuntimeTest`/`RoomScheduleRepositoryDesktopTest` focused contracts、Android main/device-test source 编译与 `git diff --check`。`AndroidCalendarProviderInstrumentedTest` 的 fixed-row Create test 是 compile-only device-test source，**未执行任何 connected/device test**，也未操作真实 Provider、backend/network、MySQL 或真实用户日历。若需回滚生产行为，必须同步回退上述**全部** W16+W17 文件，再重新执行上述 focused tests、Android source compilation 与 `git diff --check`；固定行的非创建 lookup、ownership/membership Provider Update 与 fixed-row Create 必须和 coordinator/runtime 一起回退，不能只回退上层接线或文档，否则会留下已改变的 Provider 行为边界。

### 12.2 W18–W22 普通删除的本地 `DETACHED` 生产路径

W18 仍是 pure common 的**显式删除生命周期 gate**：只有已删除 local Schedule、无 conflict 的 `LINKED` CalendarLink、当前 managed Provider event 与 explicit destructive authorization 形成完整同批证据时，才签发 proof；缺失、替换、重复、陈旧、非 `LINKED`、无授权或任一不一致都 fail-closed。W19 从同一 proof 生成 opaque、不可执行的 whole-batch transition，expected `LINKED` 与 terminal `DETACHED` 精确绑定 revision、原始 `deletedAt` 和 `deleteMutationId`；terminal 只改变 state/event ref/不回退的更新时间。

W20b 是 Store-owned Room exact-CAS：严格重读 target links、Schedule graph、tombstone lifecycle、sync-state 和 unrelated links 后，只有 `ALL_EXPECTED` + exact durable generation 才在一个 SQLite commit 写完整 `DETACHED` 批次并使 generation 加一；`ALL_TERMINAL` 仅是更高 generation 下的零写 `AlreadyTerminal`。mixed/third/stale/recreated/lifecycle mismatch 返回 reason-only `Blocked`，取消与 SQLite commit 竞态是 unknown commit，均不得自动 retry。`DETACHED` 保留旧 Provider event，且不得 Update、recreate、adopt 或成为 missing-link Create 候选。

W21/W22 已完成 production caller：Room 从 exact retained local-deletion proof 发布 `replay=0` candidate；Controller 仅在同一 direct Room façade 注册 issuer、独立 opaque authorization、fresh evidence source 与 detachment access 后，于 finalized Ready 安装 router。router 将 exact `AccountSession`/generation、scope、owner、export scope、issuer 和 controller generation 闭合；runtime 独立一次性消费 proof/authorization，读取完整 fresh evidence、要求全批 W18/W19 eligibility，并最多调用一次 Room detachment commit。它不拥有 Provider、coordinator 或 callback authority，legacy Delete 仅是 evidence；disable、clear、替代 enable、注册替换、账号切换或 owner 结束会先 revoke binding。`Blocked`、`AlreadyTerminal`、异常、取消与 unknown commit 都是本次候选的终态，禁止重放/retry。因此**普通删除不删 Provider**；只有用户明确确认的 `clearAndDelete` 才走独立的 whole-managed-calendar Provider 删除路径。

W22 source → integration 映射：T1 `3f329df36` → `0b30e13ae`，T2 `614f09425` → `a485cc352`，T3 `3271cbb58` → `b8e626886`，T4 `614622fd1` → `f09fbd25f`。这些 host/common contracts 和 Android source 编译不构成 Provider/设备验收：未运行 connected/device test、ADB、真实 Provider/用户日历、网络或外部数据库。连续 inbound、`BIDIRECTIONAL`、SyncAdapter、冲突 resolution 与后端多设备收敛仍未实现。

## 13. 给主 Agent 的直接启动指令

开始时依次执行：

1. 阅读本文及第 3 节列出的全部专题文档；
2. 检查客户端与后端两个项目的 Git status、当前分支和 `git worktree list`；
3. 检查 Android Studio 与 GoLand 索引状态；
4. 判断当前是简单单任务还是复杂/大型需求：简单任务在主工作区串行完成；复杂/大型需求先调用并完整阅读 `worktree-workflow-orchestration`；
5. 对复杂需求批量收集候选任务，建立冲突矩阵和依赖 DAG，冻结 integration commit，再由主 Agent 创建持久 worktree 与 task branch；
6. 按 skill 协议启动 Workflow、管理 IDE 项目、串行集成、验证和恢复清单，并启动 `/loop` 动态执行；
7. 持续推进，直到满足第 9 节全部正常完成条件，或触发第 10 节停止条件。
