# Schedule v2 新增、更新与同步数据流

> [!CAUTION]
> **文档状态：ARCHIVE-ONLY。** 本文描述的旧 graph/outbox/semantic 远端流程已经从当前客户端删除。当前新增、更新、删除与同步流程以 [Codex 交接](./schedule-v2-codex-handoff.md) 为准；不得从本文恢复 cursor、receipt、candidate 或旧 dispatcher。
>
> 当前远端合同以三篇 canonical 文档为准：
>
> 1. [完整资源 AtomicField 合并与原子批次](./schedule-v2-field-group-sync-design.md)
> 2. [双快照与 typed 资源版本同步流程](./schedule-v2-resource-version-sync-flow.md)
> 3. [重复日程单次覆盖与周期破坏能力矩阵](schedule-v2-recurrence-override-capability-matrix.md)
>
> 客户端跨端迁移、旧表处置、切换顺序和验收任务统一进入已经建立的 [Schedule v2 Codex 交接](./schedule-v2-codex-handoff.md)。禁止从本文历史章节恢复 cursor、receipt、semantic command、旧 delta 或 command-specific settlement 计划。

---

## 1. 状态标签与事实源

本文使用四类状态：

| 标签 | 含义 |
| --- | --- |
| **ACTIVE** | 已进入当前客户端 production 组装或本地命令执行链，可作为现状维护依据。 |
| **PARTIAL** | 源码已接线或具备受限调用方，但后端未部署、协议已过时或链路不完整；不能宣称端到端可用。 |
| **ARCHIVE** | 被 canonical 合同取代的旧远端设计，只保留作迁移和风险证据。 |
| **TARGET** | canonical 客户端目标，当前 Kotlin/Room/Web 尚未实现。 |

发生冲突时，按以下顺序判断未来远端行为：

1. 三篇 canonical 文档；
2. `/Users/guoxiangrui/GolandProjects/magipoke-todo` 的 `guoxiangrui/schedule` checkout 与 `SCHEDULE_BACKEND_DESIGN.md`；
3. 当前客户端已提交代码，只用于确认本地能力和旧数据迁移输入；
4. 本文的 ARCHIVE 章节；
5. `lane-03` 的临时历史提交 `ff660779c36501f4a45993047b43166913bd5ccd` 只用于保存 W17 旧架构与测试语料，未集成且不能作为 canonical 事实。

### 1.1 审计快照

| 范围 | 审计事实 | 判定 |
| --- | --- | --- |
| 当前客户端 checkout | 主工程已检出 integration branch `guoxiangrui/feature/schedule`；本文提交前基线为 `a41208c454f1ca1e6027ee4a264f685852b96d60` | 当前已提交客户端仍使用旧 graph/outbox/semantic 存储合同。 |
| Android production | `ScheduleRoomSemanticBootstrapSyncRunner` 已接显式 `RequestSync` 的受限 bootstrap、allowlist drain、单页 delta/reset 和 UNKNOWN status-resume | **PARTIAL**；只说明旧 source wiring，不是 canonical 接入。 |
| iOS/Desktop production | 仍保留 legacy Room outbox dispatcher、mutation receipt 与 bootstrap/paged-delta consumer | **ARCHIVE/PARTIAL**；旧 DTO 与当前后端不兼容。 |
| Web production | `RemoteRequiredScheduleRepository` unavailable façade，无 Schedule 持久化、无网络 I/O | **ACTIVE fail-closed**；canonical Web transport 尚未实现。 |
| 当前后端 checkout | `guoxiangrui/schedule` 已提交 typed Sync、日常 Schedule 接口和统一 version 合同，尚未部署 | **TARGET backend source**；不能描述为线上可用。 |
| `lane-03` W17 | `guoxiangrui/feature/schedule_claude/w17-split-following-semantic-lifecycle` 已用 `ff660779c36501f4a45993047b43166913bd5ccd` 临时保存旧合同实现 | **已提交但未集成**；仅作只读恢复与测试语料，不得写成 current production，也不应继续实现。 |

### 1.2 同名 endpoint 不代表协议兼容

当前客户端旧 `KtorScheduleMutationGateway` 和 canonical 后端都使用路径：

```text
POST /v2/schedule-mutations
```

但两者不是同一协议：

| 旧客户端 gateway | canonical 后端 |
| --- | --- |
| 单条 generic mutation | 一次请求包含 Category/Schedule/OccurrenceOverride 三类 typed 列表与 `atomicBatches[]` |
| `mutationId + resourceKind + nullable resource + base revision` | `syncRequestId + confirmed[] + upserts[] + deletes[]` |
| 返回 mutation receipt | 返回逐输入 typed current resource/tombstone 与逐 batch 结果 |
| 依赖 outbox receipt 分类 | 依赖 remote/pending 双快照和 `localRevision` compare-and-clear |

因此不得因为 URL 相同就复用旧 DTO、codec、receipt、dispatcher 或状态机。canonical 客户端 gateway 必须按新 wire 独立实现并进行跨语言互操作验证。

### 1.3 日常接口与 Sync 分工（TARGET）

canonical 客户端不为每次本地编辑调用完整 Sync：

```text
POST   /v2/schedules           # 新增完整 Schedule，version=0
PUT    /v2/schedules           # 修改完整 Schedule，version>0
DELETE /v2/schedules           # 删除 ScheduleDelete，delete-wins
POST   /v2/schedule-mutations  # 首次进入、网络恢复或 pending 收敛
```

更新接口与 Sync 使用同一份完整 Schedule 快照和 AtomicField 合并规则。服务端返回
`ScheduleUpsertResult.current` 后，客户端直接接受该 canonical 合并结果；若请求期间又发生本地编辑，
current 只更新 remote，较高 `localRevision` 的 pending 继续保留。

请求超时、断网或 5xx 时，本地保存同一 typed mutation，下一次 Sync 直接放入
`schedules.upserts[]` / `schedules.deletes[]`。HTTP 400 不能原样重试；HTTP 200 +
`status=20101` 是明确业务拒绝，不按传输失败生成新的 pending。

---

## 2. 当前本地创建与更新

### 2.1 平台入口（ACTIVE）

```text
UI / Feed / 课表编辑入口
→ ScheduleEditRouting
→ ScheduleCommand
→ 稳定 AccountSwitchingScheduleRepository
→ exact AccountSession + generation 绑定的 immutable delegate
→ Android / iOS / Desktop：RoomScheduleRepository
→ ScheduleRoomLocalCommandAdapter
→ ScheduleRoomStore 唯一 SQLite writer transaction
→ commit 后 strict re-read
→ snapshot / calendar event 经 binding identity 门禁发布
```

当前边界：

- Android、iOS、Desktop 的用户修改均 local-first；
- Web 没有本地 fallback，远端未接入时保持只读 unavailable；
- factory/create/initialize 和普通本地命令不会隐式发网络；
- 本地命令成功后不会自动调用 `RequestSync`；
- 网络 I/O 不得进入 repository mutex 或 SQLite writer transaction。

### 2.2 CREATE（ACTIVE）

```text
EditScheduleModelState / ScheduleDraft
→ 生成正式 UUIDv7 ScheduleId
→ ScheduleDraft.toNewDomain(now)
→ ScheduleCommand.Create
→ transaction 内 strict graph/device identity read
→ ScheduleLocalCommandReducer.create(...)
→ 完整 graph + legacy CREATE outbox + 必要 tombstone 清理
→ direct semantic intent/ref sidecar（仅当前已支持的命令集合）
→ durable generation compare-and-set + SQLite commit
→ transaction 外 strict re-read
→ 发布 ScheduleSnapshot
```

仍有效的本地语义：

- 编辑态占位 ID 不进入持久化，正式 identity 在提交前稳定生成；
- reducer 执行文本规范化、领域校验、分类引用和 ID 唯一性检查；
- graph、旧 outbox、tombstone、semantic sidecar 与 generation 在同一个 writer transaction 中提交；
- reducer 的内存结果不能直接公开，必须在 commit 后重新 strict read；
- 后端不可用不回滚已完成的本地创建。

需要重构的语义：

- 当前 `revision = 0` 只是旧模型中“未获得服务端确认”的标记，不等于 canonical `version`；
- 旧 CREATE outbox、mutationId、deviceId 和 receipt 不是 canonical CREATE 状态；
- canonical CREATE 应表示为“无 live `remoteSnapshot` + 一份完整 `pendingSnapshot` + `version = 0`”。

### 2.3 完整 UPDATE（ACTIVE）

```text
编辑整个 Schedule / series
→ draft.toUpdatedDomain(origin, now)
→ 无业务变化时短路
→ ScheduleCommand.Update
→ transaction 内 strict read
→ ScheduleLocalCommandReducer.update(...)
→ 完整 Schedule graph replace
→ legacy PATCH outbox merge
→ direct semantic intent/ref sidecar
→ generation compare-and-set + commit
→ strict re-read
→ 发布 snapshot，再发布 calendar event
```

仍有效的本地语义：

- 保留 `id`、`createdAt`，更新业务字段和 `updatedAt`；
- no-op 不写库、不产生远端 artifact；
- Schedule、owned reminder、recurrence selector 与相关引用必须作为可信完整 graph 校验和写入；
- revision 不允许倒退，但当前 reducer 不自行推进服务端 revision；
- 更新仍是 local-first，远端失败不回滚本地事实。

需要重构的语义：

- 当前资源级单值 `Schedule.revision` 无法表达 server `version`、客户端 `localRevision` 和各 `AtomicField.modifiedAt`；
- 旧 PATCH outbox 的合并和 frozen delivery identity 不等于可覆盖的一份完整 pending；
- canonical 编辑只能更新实际改变原子的 `data/modifiedAt`，未编辑原子必须保留原值和原时间；
- 远端响应只能更新 remote，不能覆盖请求期间产生的更高 local revision pending。

### 2.4 本地原子事务（ACTIVE）

`ScheduleLocalCommandReducer` 仍是本地命令的单一领域语义源。`ScheduleRoomLocalCommandAdapter` 在唯一 Store writer transaction 中：

1. strict 读取完整初始 graph；
2. 调用 reducer 得到完整目标 state 和存储无关 operation plan；
3. 按依赖顺序写 category、Schedule graph、Override、旧 outbox/tombstone 和 sidecar；
4. 对 durable generation 做 compare-and-set；
5. commit 后由 repository 在新事务 strict re-read；
6. 只有重新读取成功后才发布 snapshot/calendar event。

任一初始读取、领域校验、graph replay、outbox/tombstone 写入、generation CAS 或 commit 失败都应整体回滚。取消可能与 SQLite commit 竞态；commit 后读取被取消时不能假设“未提交”，后续必须重新 strict read durable state。

这套事务骨架可继续复用，但旧 outbox/tombstone/semantic 表的含义不能直接映射为 canonical remote/pending。

---

## 3. 可复用领域语义与必须重构边界

### 3.1 可复用语义

| 领域能力 | 当前可复用内容 | canonical 表达 |
| --- | --- | --- |
| local-first | 用户修改先持久化，再异步同步 | 完整 `pendingSnapshot`，而不是不可变 mutation 队列。 |
| exact-session 隔离 | delegate、scope、request lease、发布与 generation 都绑定完整 session | 新 gateway 和同步循环继续冻结 exact session。 |
| 稳定客户端 ID | Category/Schedule 在首次提交前确定 identity | 支持 CREATE 响应丢失后以同 identity 重试。 |
| 单一 reducer | Create/Update/Delete/Override/Split/Following 先归约成完整目标图 | 普通操作生成完整 typed pending；结构变化生成 atomic batch 最终图。 |
| child-before-parent closure | whole Schedule delete 先处理 owned Override，再处理 parent | 作为 typed atomic batch 的依赖闭包。 |
| SplitSeries | 截断 A、创建 B、迁移受影响 Override | 不上传 `SplitSeries` 命令，只上传 A/B/Override 最终资源图。 |
| DeleteThisAndFollowing | 首个 occurrence 边界归约 whole delete；非首边界截断并删除后续 Override | typed atomic batch。 |
| 完成与取消区分 | `COMPLETED` 可物化，`CANCELLED` 抑制 occurrence | 保留。 |
| commit 后 strict re-read | 不把 reducer 内存结果直接发布 | 扩展到 remote/pending response settlement。 |
| 平台 adapter 隔离 | Android `ORIGINAL_*`、EventKit occurrence/span 只解决平台对象关联 | 不得反向决定 wire identity 或扩展 canonical 字段。 |

提交 `804e0bf49` 已把“从第一次 occurrence 起删除后续”对齐为 whole-Schedule delete。该领域结论仍有效，但不授权沿用旧 following semantic lifecycle。

### 3.2 OccurrenceOverride 不是直接兼容

当前客户端 identity：

```text
scheduleId
+ originalDateTime
+ timeZoneId
+ allDay
```

当前 patch 可覆盖：

```text
timing + title + description + categoryId + reminders
```

canonical identity：

```text
scheduleId + occurrenceDate
occurrenceDate = parent recurrence 真实生成的 UTC 午夜 date-slot
```

canonical OccurrenceOverride 包含：

```text
status + timing patch + title patch + description patch + categoryId patch + reminders patch
```

canonical 明确没有：

- 独立 exception ID；
- timeZoneId / allDay identity；
- occurrence 序号；
- 独立 recurrence 规则。

`RecurrenceId`、Room exception 主键与远端 identity 仍只由原始 date-slot 定位；THIS_ONLY 改期/分类写入
独立原子，平台原始实例字段只能留在 adapter 映射层，不能进入远端 identity。

### 3.3 “恢复系列默认”必须改语义

当前旧接口 `DeleteOccurrenceException(scheduleId, recurrenceId)` 会物理删除本地 `ScheduleOccurrenceException`，并可能生成旧 DELETE mutation/tombstone。

canonical tombstone 不可复活，因此“恢复默认”必须写入 neutral live OccurrenceOverride：

```text
status = ACTIVE
timing = INHERIT
title = INHERIT
description = INHERIT
categoryId = INHERIT
reminders = INHERIT
```

否则同一 `scheduleId + occurrenceDate` 被 tombstone 后，将无法再次编辑。现有命令、UI 和本地存储不能原样复用。

### 3.4 recurrence 与时间边界

当前客户端和 canonical 后端还存在以下结构差异：

| 当前客户端 | canonical/当前后端 | 迁移要求 |
| --- | --- | --- |
| recurrence 可表达 MONTHLY/YEARLY | 当前后端只接受受限 DAILY/WEEKLY 子集 | 不支持的规则必须 fail-closed，不得静默降级。 |
| 没有稳定 UTC `anchorDate` 历史 | membership 和结构变化依赖稳定 anchor/first-anchor history | 新 storage 必须持久化 anchor history。 |
| timing 使用本地墙钟和 IANA zone | wire 时间为 signed int64 Unix millis | 保留 UI/domain 类型，新增严格 mapper 和范围校验。 |
| reminder 有本地 identity/channel | wire reminders 是完整列表原子 | 明确映射，不把本地 ID/channel 偷渡进 wire。 |

### 3.5 版本与时间模型必须拆分

| 值 | canonical 职责 |
| --- | --- |
| `version` | 服务端 live 资源版本，只保存在 remote snapshot，客户端不递增。 |
| `deletedAt` / `reason` | 服务端 tombstone metadata；不携带资源版本。 |
| `localRevision` | 客户端 compare-and-clear；不上传。 |
| `AtomicField.modifiedAt` | 每个业务原子的冲突时间，只在该原子真正编辑时刷新。 |
| `remoteModifiedAt` | 服务端最后实际改变资源的时间，不参与原子胜负。 |
| `localModifiedAt` | DELETE 请求必填的客户端时刻，不决定服务端 tombstone 时间。 |

当前 `revision/createdAt/updatedAt` 不能同时承担这些职责。

---

## 4. 旧远端链路（ARCHIVE）

### 4.1 legacy mutation outbox/receipt

当前 Room 仍保存旧：

- `schedule_outbox`：`QUEUED / IN_FLIGHT / DELIVERY_UNKNOWN`；
- `schedule_tombstone`：客户端 retained delete proof；
- `schedule_sync_state.sync_cursor`；
- frozen mutation ID、payload、base revision 与 attempt metadata。

早期 iOS/Desktop consumer 使用“短 claim transaction → 锁外 dispatch → 短 receipt transaction → bootstrap/paged delta”。Accepted/Rejected/DeliveryUnknown、mutationId 幂等重试、opaque cursor 和 pending/tombstone 保护只可用于旧数据迁移与失败边界审计。

它们不能成为 canonical storage 外形，也不能继续增加 retry、receipt、cursor 或 replay 语义。

### 4.2 semantic command/cursor/settlement

当前 Android 旧 runner 及其存储曾围绕以下概念扩展：

- `POST /v2/schedule-commands`；
- authoritative bootstrap/sync；
- opaque cursor/checkpoint/fence；
- candidate ID、status observation、accepted-changed confirmation；
- K3 terminal settlement；
- K4 immutable pre-submit journal；
- K7 submit-call reservation；
- retained unknown status-resume；
- K13 discovery / K14 durable repoll 规划；
- semantic intent/ref sidecar。

canonical 后端不接收或保存这些客户端命令历史，也不需要：

- command receipt/status endpoint；
- lineage、candidate 或 settlement token；
- client-side R/U 因果链；
- opaque cursor/checkpoint；
- generic `ResourceKind + nullable resource` union；
- command-specific recovery coordinator。

这些代码只可提取 exact-session、取消、strict codec、事务和 lost-return 经验。任何 helper 复用都必须证明其不携带 command/cursor/receipt/revision-CAS 假设。

### 4.3 lane-03 W17 的准确定位

`lane-03` 的临时历史提交 `ff660779c36501f4a45993047b43166913bd5ccd` 为旧架构的 `SplitSeries` 与 `DeleteThisAndFollowing` 保存了：

- original-parent root candidate；
- root 与后继 U 的独立 K4/K7/status/K3 生命周期；
- ordered retained outbox refs；
- `R.base + 1` authority revision；
- submit-entered unknown 分组；
- candidate-bound authoritative confirmation。

审计结论：

- 代码已在 lane-03 临时提交保存，但未集成，不是 current production；
- reducer 重放、first-boundary、closure 与 R→U 场景可转写为 canonical 测试；
- candidate、cursor、receipt、checkpoint、successor lifecycle 和对应表结构必须丢弃；
- 不应因旧实现“已接近完成”而继续投入或合入。

该 diff 反而证明：命令历史 + cursor + receipt 会让 recurrence 结构变化产生大量 command-specific 状态；canonical 的替代方案是一次 typed atomic batch 提交最终资源图。

---

## 5. canonical 客户端目标流（TARGET）

### 5.1 每个 identity 的最小本地状态

```text
RemoteResourceSnapshot {
  typed identity
  fullCanonicalResource
  version
  remoteModifiedAt
}

PendingResourceSnapshot {
  typed identity
  operation: UPSERT | DELETE
  fullLocalResource       // UPSERT 时包含全部 AtomicField
  localRevision
  localModifiedAt         // DELETE 时存在
}
```

约束：

- `remoteSnapshot` 只能由服务端响应更新；
- 一个 identity 至多一份 pending；
- 普通编辑覆盖完整 pending，不追加不可变 mutation 队列；
- 每次编辑递增 `localRevision`；
- 只更新实际编辑的 `AtomicField.data/modifiedAt`；
- pending UPSERT 展示完整 pending，pending DELETE 让 identity 本地不可见；无 pending 才展示 remote；
- 应用远端 tombstone 后删除 live remote，下一轮 confirmed 中不再出现该 identity。

### 5.2 本地编辑

```text
读取 identity 的 remoteSnapshot 与 pendingSnapshot
→ pending 已存在则复制 pending，否则复制 live remote
→ 只更新实际编辑的 AtomicField.data + modifiedAt
→ localRevision + 1
→ 原子保存完整 pendingSnapshot
→ 对外展示 pending；remote 保持不变
```

新建资源没有 remote 基线，以 `version = 0` UPSERT。已有 live remote 即使同时有 pending，也继续把最后确认的 live `version` 放入 `confirmed[]`；pending 另进 typed upsert/delete。首次 DELETE 只能从当前 live remote 或 pending UPSERT 形成；identity 已本地缺失且没有既存 DELETE pending 时，重复删除按本地幂等 no-op 处理，不能凭任意 identity 合成 blind DELETE。DELETE 始终只上传 typed identity 与 `localModifiedAt`，不从 remote 派生版本；服务端对 absent/live/tombstone 都按 delete-wins 幂等收口。DELETE pending 一旦持久化，同 identity 不得转回 UPSERT；重新创建使用新 identity。

### 5.3 typed 请求

```text
POST /v2/schedule-mutations

SyncRequest {
  syncRequestId
  categories { confirmed[]; upserts[]; deletes[] }
  schedules { confirmed[]; upserts[]; deletes[] }
  occurrenceOverrides { confirmed[]; upserts[]; deletes[] }
  atomicBatches[]
}
```

```text
version == 0 → CREATE
version > 0  → 完整 PATCH
```

每个 UPSERT 上传完整 typed resource。服务端按每个 `AtomicField.modifiedAt` 做 LWW，并返回完整 canonical current resource 或 tombstone，不返回字段 patch。

### 5.4 响应 compare-and-clear

发送前冻结：

```text
uploadedPending = 完整 pending 副本
uploadedRevision = pending.localRevision
UPSERT version = 当前 live remote.version；CREATE 为 0
DELETE = typed identity + localModifiedAt
```

响应到达后，在本地事务中：

1. 按 typed identity 汇总普通 result、atomic related 状态与 inventory delta；若存在 tombstone，直接按 delete-wins 删除 remote row；否则仅对 live observations 选择最高 `version`，完整 current 替换 `remoteSnapshot`，不保存本地 tombstone snapshot；同版本 live 冲突或 live 版本倒退时整次响应事务回滚；
2. 当前 pending 不存在时结束；
3. `currentPending.localRevision != uploadedRevision` 时说明请求期间已有 U，保留 U；
4. revision 相等时，也只有结果明确应用、满足或终结该 uploaded operation 才清 pending；
5. CREATE 返回内容不同的 `ALREADY_EXISTS` 时保留 pending，使用返回的正版本重发完整 PATCH；
6. `REJECTED` 或需要 atomic batch 时保留 pending，基于返回的完整 current/related typed 状态重新规划。

同 revision 的终结码按 canonical 固定映射：UPSERT `CREATED/APPLIED/ALREADY_SATISFIED/SERVER_WON` 清理；`RESOURCE_DELETED` 应用 tombstone 后清理且不自动复活；DELETE `DELETED/ALREADY_DELETED` 清理；CREATE `ALREADY_EXISTS` 内容不同和所有 `REJECTED` 保留。batch 还要求 related 状态完整且所有 member revision 未变化。

R→U 的核心是：**R 响应遇到 tombstone 直接按 delete-wins 删除 remote；没有 tombstone 时仅按最高 live version 更新 remote；U 保持原样；下一轮完整上传 U。** `UPSERT R → DELETE U` 下一轮只上传 typed identity 与 `localModifiedAt`，不依赖 live remote 是否仍存在。`DELETE R → UPSERT U` 生成新 identity，不复活旧 identity。客户端不保存 receipt、response-unknown、lineage 或 rebase 状态机。

### 5.5 recurrence 结构变化

以下操作必须提交 typed atomic batch 的最终资源图：

- recurrence 日期集合变化；
- SplitSeries；
- DeleteThisAndFollowing；
- whole-series delete + Override closure；
- Override 跨 parent 或跨 `occurrenceDate` 的 `DELETE old + CREATE new`。

batch 内仍是 Category/Schedule/OccurrenceOverride 的 typed upsert/delete，不上传命令名。服务端 staged graph 校验 Category 引用、parent closure、UTC date-slot membership 和稳定 anchor history。同 identity recurrence 变化必须保留 WEEKLY anchor weekday，并在同一 atomic batch 中删除或迁移不再属于新规则的 live Override；当前不检测历史 tombstone date-slot 重入。

普通请求允许资源级 mixed result；atomic batch 则全部成功或全部回滚。客户端重试同一批最终状态，不设计补偿命令或 command receipt。

---

## 6. 客户端迁移 handoff

迁移顺序应保持最小可证，不在旧状态机上叠兼容层：

1. **冻结旧远端链路**：停止扩展 semantic command/cursor/receipt/K3/K4/K7；明确 `lane-03` W17 不集成。
2. **建立 canonical model**：定义三类 complete typed resource、`AtomicField<T>`、UTC date-slot identity、server meta、remote/pending snapshot 与 localRevision。
3. **设计 Room schema 和旧行处置**：新 schema 原子保存 remote、至多一份 pending、AtomicField timestamps 和 atomic batch 目标；旧 outbox/tombstone/cursor/semantic tables 必须显式 quarantine、迁移或清理，不能静默解释成新状态。
4. **实现全新 strict typed codec/gateway**：同名 `/v2/schedule-mutations` 不复用旧 DTO；严格拒绝未知字段、重复 key、缺失列表和不完整 AtomicField。
5. **接本地 reducer**：复用完整领域目标图；普通命令归约为完整 pending，recurrence 结构变化归约为 typed atomic batch 最终图。
6. **实现事务性同步循环**：锁外网络；响应事务内更新 remote 并 compare-and-clear；覆盖 R→U、丢响应、tombstone、mixed ordinary result 和 atomic batch retry。
7. **迁移平台 production factory**：Android、iOS、Desktop、Web 分别接 canonical gateway；在后端实际部署与互操作验收前保持 fail-closed。
8. **收口 recurrence/UI/adapter**：identity 改用 `scheduleId + UTC occurrenceDate`；单次 timing/category 作为
   独立 Override 原子上传；系统日历原始实例字段只留 adapter。

以下场景已由 [Codex handoff §9.2](./schedule-v2-codex-handoff.md#92-每个客户端切片的最低测试场景) 收录；实施时必须逐项验证：

- CREATE 请求期间继续编辑；
- R→U、CREATE→DELETE closure、DELETE 后新 identity 与 compare-and-clear；
- 同响应多来源 live current 按最高 `version` 归并并冲突回滚；tombstone 直接按 delete-wins 删除 remote；
- 不同字段多设备 LWW；
- 相同 modifiedAt 的同值/异值；
- live-only confirmed inventory；
- tombstone 不可复活；
- neutral live Override；
- recurrence atomic batch 整体回滚与 retry；
- exact-session 切号/取消；
- SQLite commit 后取消与重开恢复；
- Kotlin 与 Go strict wire 的跨语言互操作。

---

## 7. 当前代码与历史证据

| 位置 | 当前责任 | 迁移定位 |
| --- | --- | --- |
| `data/repository/v2/ScheduleLocalCommandReducer.kt` | 本地命令领域归约和完整目标 graph | 保留领域规则，输出改为 canonical pending/atomic batch。 |
| `data/local/room3/ScheduleRoomLocalCommandAdapter.kt` | 单事务 strict read、reducer、graph/outbox/tombstone/intent replay | 保留事务骨架；远端 durable artifacts 重构。 |
| `data/local/room3/ScheduleRoomEntities.kt` | 当前 graph、old outbox/tombstone/cursor、semantic tables | 不能视为 remote/pending 双快照 schema。 |
| `data/local/room3/ScheduleRoomSemanticBootstrapSyncRunner.kt` | Android 旧 bootstrap/delta、allowlist drain、status-resume | ARCHIVE/PARTIAL；不继续扩张。 |
| `data/remote/semantic/**` | 旧 command/cursor/receipt/authoritative read codec/client | 非 canonical wire。 |
| `data/remote/v2/KtorScheduleMutationGateway.kt` | 同名 URL 上的旧 single-mutation receipt gateway | 必须由新 typed gateway 替代。 |
| `domain/model/ScheduleModels.kt` | 当前 Schedule、Category、Override、RecurrenceId、patch | 业务意图可复用；版本、时间、Override identity/字段需迁移。 |
| `domain/repository/ProductionScheduleRepositoryFactory.*.kt` | 各平台当前 delegate/transport 分流 | 最后按平台显式 cutover。 |

关键已提交历史：

| 提交 | 已提交事实 | 当前判定 |
| --- | --- | --- |
| `57500741b` | 统一旧 mutation endpoint 路径 | 路径字符串不能证明新协议兼容。 |
| `5ec643e11` | 建立 local-first 架构 | 本地优先与账号隔离继续有效。 |
| `a1a343beb` | 抽取本地命令 reducer | 领域归约可复用。 |
| `5042f26f7` | 接入 Android semantic bootstrap | cursor/checkpoint 模型已归档。 |
| `4178c3ade`、`8a1c3e53f`、`f636baff0` | 接入 DELETE、Category CREATE、Schedule CREATE 受限 semantic drain | 业务场景可转测试；candidate/receipt/storage 不复用。 |
| `3e54ec112` | retained UNKNOWN 单次状态确认 | status/receipt recovery 已归档。 |
| `1591ef0bb` | authoritative 单页 delta | 旧 delta/cursor 已归档。 |
| `804e0bf49` | 修正 following 首次边界删除 | 领域语义继续有效。 |

这些提交证明当前本地能力与旧架构演进，不构成 canonical wire 的兼容承诺。

---

## 8. 一句话约束

> **保留 local-first、exact-session、完整领域目标图、单事务写入和 commit 后 strict re-read；重建全部远端 durable state 与 wire。未来客户端只实现 complete typed resource + AtomicField + live-only confirmed + remote/pending 双快照 + localRevision compare-and-clear + typed atomic batch，不再延续 cursor、receipt、semantic command 或 K3/K4/K7 生命周期。**
