# Schedule v2 延后边缘场景记录

> **状态：PARTIALLY_HISTORICAL。** 本文同时保存仍有效的延后项、已经被新 canonical 合同消解的旧问题、随旧 cursor/event/receipt 后端计划取消的事项，以及已经完成且可追溯的修复。
>
> 当前合同以 [完整资源 AtomicField 设计](schedule-v2-field-group-sync-design.md)、[双快照与资源版本同步流程](schedule-v2-resource-version-sync-flow.md)、[重复日程能力矩阵](schedule-v2-recurrence-override-capability-matrix.md)，以及 `magipoke-todo` 的 `guoxiangrui/schedule` 分支和 `SCHEDULE_BACKEND_DESIGN.md` 为准。后端功能分支已提交 typed Sync、日常 Schedule 接口和统一 version 合同，但尚未部署；Android、iOS 与 Web 客户端尚未迁移。

本文不是完成路线图、生产门禁或默认待办列表。它的目标是让后续 Codex 明确判断：某个极端场景现在是否仍存在、不做会产生什么影响、什么事实出现后才值得重新投入，以及哪些旧要求不得因历史代码仍在仓库中而恢复。

## 1. 状态定义与处理规则

| 状态 | 含义 | 后续动作 |
| --- | --- | --- |
| `ACTIVE` | 问题在当前 canonical 合同与实现中仍真实存在，但尚无证据值得扩大架构。 | 仅在条目列出的可观察触发条件成立后重新进入 master DAG。 |
| `RESOLVED_BY_CANONICAL` | 新合同通过删除旧概念或改变数据模型消除了原问题。 | 不补旧兼容层；产品重新引入被删除能力时按新需求重新设计。 |
| `CANCELLED_BACKEND_PLAN` | 条目依赖已取消的 cursor、event group、candidate status、receipt 或 semantic command 后端。 | 不继续实现，也不能用旧 Android 客户端代码反向覆盖 canonical 合同。 |
| `COMPLETED_TRACEABLE` | 已有提交、实现和聚焦测试可以追溯。 | 保留证据与边界，不把已完成项重新写成待办。 |

可以延后的场景通常同时满足：

- 不造成当前常用路径的数据损坏、越权、隐私泄露或不可逆外部副作用；
- 已有 fail-closed、幂等重试或可接受的短期降级；
- 当前没有真实容量、调用方或线上故障证据；
- 立即修复会显著扩大 schema、事务模型、平台运行时或验证范围；
- 可以给出明确、可观察的重新评估触发条件。

以下问题不得仅记录后延：

- 当前常用路径的数据丢失、错误写入、崩溃或账号隔离失效；
- 鉴权、权限、隐私或真实用户数据安全问题；
- 会重复产生不可逆远端或系统日历副作用的问题；
- 阻塞当前 canonical 客户端接入、真实 MySQL 验证、部署前验收或用户功能闭环的问题。

## 2. 当前仍有效的延后项

### ACTIVE-001：普通 mutation 逐项加载完整 owner 图的容量成本

**场景**

当前 canonical 后端中，每个普通 CREATE、PATCH 或 DELETE 都在独立的 SERIALIZABLE owner 事务里加载并锁定完整 owner 图；每个 atomic batch 也各自加载一次，service 最后还会再读取最终图生成 typed inventory delta。owner 有 `R` 个资源、一次请求有 `M` 个普通 mutation 时，最坏工作量约为 `O(M×R)`。

这与旧 `DEFER-003` 的“每条 occurrence 最多扫描 100,000 个 recurrence 周期”不是同一个问题。新后端不物化全部 occurrence，只遍历当前资源图中的 live Override，并用 UTC date-slot 算术直接判断 DAILY/WEEKLY membership；旧 proof-limit 放大已经消失。

**当前影响与不做的代价**

- 目标应用当前预期是低并发、小体量资源图，尚无真实集成或性能数据证明出现可感知延迟；
- 极端大 owner 或大请求可能增加数据库 CPU、锁等待、死锁或超时概率，并让客户端发生额外重试；
- 一次 HTTP 请求的普通 mutation 不是总事务，中途内部错误不会回滚此前已提交项，但稳定 CREATE identity、完整 AtomicField 快照、tombstone 和 typed 结果允许后续请求继续收敛；
- 当前没有正确性证据要求通过任意请求数量、ID 长度或时间范围上限来掩盖性能问题。

**延后理由**

在没有代表性数据分布和真实 MySQL 指标前改为 bulk transaction，会同时改变 DAO 加载方式、锁顺序、普通 mutation 的独立提交边界、结果下标回填和失败恢复验证。先拍脑袋增加应用层列表或资源上限只会把容量假设变成协议拒绝，不能证明数据库路径已经安全。

**重新评估触发条件**

满足以下任一条件时重新评估：

- 真实 MySQL 或代表性 synthetic 数据下出现可重复的 P95/P99 延迟、锁等待、死锁或请求超时；
- 产品明确给出单 owner 资源量 `R`、单请求 mutation 数 `M` 或并发设备数的容量目标，现实现达不到；
- profiling 证明完整 owner 图重复加载或 `ValidateFinalGraph` 是主要热点；
- 跨端集成测试证明额外重试已造成可感知用户体验或后端负载问题。

**届时优先考虑的最小方案**

先建立真实 MySQL 基准并分别测量图加载、锁等待、最终图校验和响应投影；只有证据指向重复加载时，才考虑在保持 typed 输入/输出、AtomicField 合并、atomic batch 回滚和 tombstone 语义不变的前提下，合并为单次加载的 staged graph/bulk transaction。不要先增加任意 wire 上限，也不要引入 cursor、receipt 或事件历史。

**状态**：`ACTIVE`，延后；不因抽象极端规模进入当前 master DAG。

## 3. 原始条目审计结论

原文曾出现两个 `DEFER-002`。这里保留这个编号事实，避免后续把历史提交误读为同一问题。

| 原条目 | 当前状态 | 结论 |
| --- | --- | --- |
| `DEFER-002` foreign-device/旧库手工 SQLite 异常 | `CANCELLED_BACKEND_PLAN` | K4/K7 reservation、candidate、单条 outbox receipt 和 UNKNOWN status 属于旧 semantic command 客户端。canonical 客户端目标是 typed `remoteSnapshot + pendingSnapshot`，不再实现这套 reservation 恢复状态机。 |
| `DEFER-001` 跨进程 durable status-repoll ownership | `CANCELLED_BACKEND_PLAN` | canonical 后端没有 command status API、candidate receipt 或 durable repoll；响应丢失后重发当前完整 pending，由资源当前状态和 tombstone 收敛。 |
| `DEFER-002` future raw writer group closure | `CANCELLED_BACKEND_PLAN` | canonical 后端只保存三类当前状态表，不保存 authoritative event history、group 或 cursor；当前后端也没有 `AppendGroup`。 |
| `DEFER-003` occurrence 全图证明成本基准 | `RESOLVED_BY_CANONICAL` | 旧 semantic proof 的 100,000 周期扫描不进入新后端。当前真实容量问题已改写为 `ACTIVE-001` 的 `O(M×R)` owner 图加载成本。 |
| `DEFER-004` Backend 与旧客户端 tzdb 前向兼容 | `RESOLVED_BY_CANONICAL` | wire 不再保存时区；recurrence 与 Override identity 都是 UTC 毫秒 date-slot，不再由服务端和旧设备分别解析 IANA zone。 |
| `DEFER-005` 公开字节切片 API 的 caller ownership | `CANCELLED_BACKEND_PLAN` | 原条目针对旧 semantic planner/authoritative writer 的 `[]byte` 借用边界。canonical HTTP 边界使用严格 typed JSON，service/DAO 接收 typed DTO；没有该公开 raw writer API。 |

### 3.1 foreign-device/旧库 SQLite 漂移

原问题担心同一 direct 调用冻结 fresh claim 或 K4/K7 reservation 后，外部设备、手工 SQLite 或旧库工具在短窗口改写行，导致 exact fresh-read/CAS 拒绝 cleanup。

- **当前不做的影响**：对 canonical 目标没有缺失能力；旧 Android semantic 表和实现仍在当前分支，只能作为历史代码，不能据此要求新客户端保留 K4/K7、candidate 或 generic dispatcher。
- **历史保护仍成立**：旧路径已经选择 fail-closed，漂移行保留且不伪造 submit、UNKNOWN、status 或 cleanup；这比猜测 foreign transport 事实安全。
- **重新打开条件**：仅当迁移方案明确要求读取这些旧表，或产品正式撤销 canonical 方案并重新批准旧 semantic runtime 时重新设计。普通客户端迁移不构成触发条件。
- **最小处理**：迁移时先保留数据库副本并只读识别旧行；是否丢弃、导出或人工恢复必须由迁移合同决定，不能恢复通用 replay/dispatcher。

### 3.2 durable status repoll ownership

原问题讨论进程重启后是否重复或遗漏 candidate-bound 只读状态查询。

- **当前不做的影响**：canonical 没有 command status 概念，因此不存在“漏查 receipt”的产品状态；请求失败或响应丢失时保留当前 pending，下一轮完整重发。
- **旧代码事实**：当前 Android 分支仍有 `SemanticCursor`、`SemanticCommandReceiptV1`、`DELIVERY_UNKNOWN`、K4/K7 和一次 status 查询实现，但它们属于未部署且已取消的后端合同。
- **重新打开条件**：只有未来 canonical 协议经产品审批新增独立异步作业与 status API，才按新的 job identity、唤醒来源和 at-least-once/at-most-once 目标重新设计；不得直接复用旧 candidate 状态机。

### 3.3 raw writer group closure

原问题假设服务端存在 append-only authoritative history，未来非 semantic planner 的 raw writer 可能写出需要跨 group 才恢复合法性的中间图。

- **当前不做的影响**：canonical 后端没有 event group、分页 cursor 或 `AppendGroup`，不存在按 group/page 恢复闭包的问题；每个 atomic batch 直接模拟并校验最终 owner 图。
- **重新打开条件**：只有未来明确新增事件历史或外部 raw writer API 时，才在那个新 writer 的合同中证明原子最终图；“为了防未来”不是恢复 event stream 的理由。
- **最小处理**：优先保持 typed current-state API 与 atomic batch；若新需求确实需要审计历史，审计日志也不应自动成为客户端同步真相源。

### 3.4 occurrence proof 与时区

旧 semantic 客户端以本地墙钟、IANA zone 和可过滤 recurrence 证明 membership，过滤型 `COUNT` 最多扫描 100,000 个周期；这同时产生 proof-limit 和 Backend/旧设备 tzdb 漂移问题。

canonical 通过以下方式消解原问题：

- recurrence 只支持 DAILY/WEEKLY、正 interval、UTC `anchorDate`、可选 count/until 和 WEEKLY weekdays；
- live Override 只以 `scheduleId + UTC occurrenceDate` 定位；
- 后端 `ContainsOccurrence` 使用整数 UTC 日序号直接计算 membership，不扫描远期周期；
- wire 不保存时区，设备只把实际 Unix 毫秒本地化展示。

**明确代价**：DST 或跨时区后不保证维持相同本地墙钟时间。这是 canonical 产品取舍，不是待补 bug。若产品未来要求“始终保持当地 09:00”或跨 tzdb 版本兼容，应作为新增时区语义重新审批，并同时更新 Backend、Android、iOS、Web、展开向量和迁移合同；不能只在某端重新加 `timeZoneId`。

### 3.5 raw byte-slice ownership

原问题针对旧 Go semantic planner 对公开 `[]byte` decision/payload 的借用约定。当前 canonical service 接收三类 typed DTO，DAO 内部使用具体 typed resource wrapper，严格 JSON decoder 负责重复 key、未知字段、null 和 presence 校验。

- **当前不做的影响**：没有现存公开 raw writer caller，因此不需要通用 ownership framework 或全链路重复深拷贝。
- **重新打开条件**：未来若新增跨 goroutine 的公开 `[]byte` API，且 race detector 或真实调用链证明借用期间存在并发 mutation，再在最外层一次性复制，并让校验、hash 与持久化消费同一快照。

## 4. 已完成且可追溯的修复

### COMPLETED-001：旧 authoritative reducer 的 occurrence 图闭包

提交 `998cda06b5550b5356febc0139e0938cecbd91b3` 已让旧 Android authoritative bootstrap/sync 对 live occurrence 证明父系列生成资格，并在整页归约后校验 split/following 最终图；遗漏 Override 迁移时整页原子拒绝，图、cursor、high-water、settlement provenance 与 fences 不推进。聚焦测试覆盖合法生成、非法 identity、时序分支、任意 group effect 顺序和原子回滚。

**边界**：这是旧 cursor/event 客户端的已完成防护，不代表 canonical typed 双快照客户端已经迁移，也不构成恢复 event history 的理由。

**状态**：`COMPLETED_TRACEABLE`。

### COMPLETED-002：从第一次 occurrence 起删除后续

提交 `804e0bf4909badbd8e5192228950af2540673831` 已让当前 Android 本地 reducer 在执行 `DeleteThisAndFollowing` 前严格证明 recurrence identity：

- 边界是系列第一次 occurrence 时，归约为 whole-Schedule graph delete，并复用 child-before-parent、outbox、tombstone 和原子 operation plan；
- 非第一次边界继续截断旧系列并删除边界及后续 Override；
- 非法边界在生成新 ID 或修改状态前原子拒绝。

聚焦测试覆盖首次 occurrence 有多个 Override、无 Override、非首次边界和非法边界零副作用。canonical 最终图合同也明确“从第一次 occurrence 起删除后续 = DELETE Schedule A + DELETE all live Overrides”。

**边界**：该提交证明本地领域语义已修复；远端 canonical typed atomic batch 客户端仍未接入。

**状态**：`COMPLETED_TRACEABLE`。

### COMPLETED-003：旧 semantic direct caller 的 fresh reservation 取消保护

提交 `f636baff03f054516e296b2900514edd7e5d5ad3` 已为旧 Schedule CREATE semantic direct path 补齐 fresh claim、K4/K7 reservation、terminal settlement、exact generation 回滚和 submit 前取消 cleanup，并用聚焦 Room/runner 测试验证同一调用持有的 fresh 行不会被历史 reservation 重提或误删。

**边界**：它故意不修复 foreign-device、手工 SQLite 或旧库工具造成的行漂移；该极端场景原本 fail-closed。由于整套 candidate/receipt 后端计划已取消，不能继续扩展为 generic replay、durable repoll 或人工修复状态机。

**状态**：`COMPLETED_TRACEABLE`，同时属于旧计划历史。

## 5. Canonical 已接受、不要重新包装成昂贵待办的行为

- **响应丢失不需要 receipt**：CREATE 使用稳定 identity；PATCH 使用完整 AtomicField 快照；DELETE 使用 tombstone；atomic batch 可根据 related 当前状态重建 base 后重试。
- **R → U 不做客户端字段 rebase**：R 只更新 remoteSnapshot，请求期间产生的更高 localRevision pending 保持不动，下一轮完整上传后最终收敛。代价是极少场景多一次网络往返，不是数据丢失。
- **没有时区和本地墙钟承诺**：实际 timing 与 recurrence date-slot 都是 Unix 毫秒/UTC 槽；DST 墙钟漂移是已记录代价。
- **没有单次分类或单次改期**：OccurrenceOverride 只有 status、title、description、reminders 四个原子；平台 detached event 能力不能反向扩展 wire。
- **不通过 DELETE 恢复本次默认**：使用 `ACTIVE + INHERIT` 的 neutral live Override，避免不可复活 tombstone 阻止以后再次编辑同一 date-slot。
- **同一路径名不代表同一协议**：当前 Android `KtorScheduleMutationGateway` 虽然也请求 `/v2/schedule-mutations`，但发送的是旧单条 mutation + receipt 合同；canonical 是一次请求中的三类 typed inventory/upserts/deletes/atomicBatches。客户端迁移时必须整体替换，不能因 URL 相同而复用旧 codec 或 receipt 分类。

## 6. 后续 Codex 判定清单

遇到新的“是否要补这个极端 case”提议时，按以下顺序判断：

1. 它是否仍存在于三篇 canonical 文档和 `magipoke-todo` 当前 typed 实现中？如果只引用 cursor、group、candidate、receipt、K4/K7 或旧 single-mutation codec，直接归为 `CANCELLED_BACKEND_PLAN`。
2. canonical 是否已经通过 UTC date-slot、AtomicField、version、tombstone、neutral Override 或 atomic batch 给出收敛方式？若是，记录明确代价，不增加兼容层。
3. 当前是否有真实用户路径、真实 MySQL、真机、跨端集成或可重复 profiling 证据？没有证据且只影响极端容量时，保留为 `ACTIVE` 延后项。
4. 不做是否会导致数据损坏、越权、隐私泄露或不可逆外部写？若会，不能放在本文，必须进入当前修复与验收。
5. 重新评估时先实现最小可证方案，保持 typed current-state 合同；不得为了旧历史恢复 cursor、receipt、事件流或 generic dispatcher。
