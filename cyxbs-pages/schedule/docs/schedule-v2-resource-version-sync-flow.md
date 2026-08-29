# Schedule v2 双快照与 typed 资源版本同步流程

> **状态：本文是最新 canonical 合同；后端与客户端已按 typed Sync、日常聚合接口和本文 version 合同完成实现，但尚未部署和真实账号验收。**
>
> 本文定义客户端后续接入流程，不授权本次修改 Android、iOS、Web 代码、UI、测试设备、部署或远端开关。

---

## 1. 总体流程

Schedule v2 使用轻量最终一致模型：服务端只保存三类资源的当前状态和 tombstone；客户端保存完整 `remoteSnapshot` 与至多一份完整 `pendingSnapshot`。

```text
客户端上传：
  - Category / Schedule / OccurrenceOverride 三类 typed inventory；
  - dirty UPSERT 的完整 typed resource；
  - pending DELETE；
  - recurrence 结构变化的 typed atomic batch。

服务端：
  - 在认证 owner 范围依次处理 CREATE、atomic batch、DELETE、PATCH；
  - PATCH 按每个 AtomicField.modifiedAt 独立合并；
  - 校验完整最终资源图；
  - 返回三类 typed inventory delta 和按输入下标对齐的结果。

客户端：
  - 服务端完整资源替换 live remoteSnapshot，typed delete 删除对应 remote row；
  - 本地不保存 tombstone snapshot，也不因响应缺席推断删除；
  - 没有较新 pending 时 compare-and-clear；
  - 请求期间产生较新 pending 时保留它，不做客户端字段 rebase；
  - 下一轮再次完整上传，最终收敛。
```

接口分工固定为：

```text
POST   /v2/schedule-mutations  # 首次进入、网络恢复与 pending 收敛
POST   /v2/schedules           # 日常新增，AtomicBatch 中至少含一个 version=0 资源
PUT    /v2/schedules           # 日常修改，AtomicBatch 中的 upsert 均为正版本
DELETE /v2/schedules           # 日常删除，AtomicBatch 可同时携带父子 closure
```

日常本地提交不触发完整 Sync，而是把本次 pending 及其 Schedule 关系闭包组成一个 `AtomicBatch`。
一次 Schedule 操作可以同时携带其待提交 Category 和 OccurrenceOverride；三类资源自身的命令也立即走
这组聚合接口。请求成功后按 `AtomicBatchResult` 更新 `remoteSnapshot` 并 compare-and-clear；请求超时、
断网或返回 5xx 时保留当前 typed pending，下一次完整 Sync 再上传。HTTP 400 与 HTTP 200 +
`status=20101` 都是明确业务失败：只清除仍匹配 uploaded revision 的 R，R 请求期间产生的 U 保留。

不使用 `/v2/schedule-sync`、`protocolVersion`、cursor、receipt、事件历史、`settledSnapshotToken` 或通用 mutation union。

---

## 2. UTC 时间与 identity

所有 wire 时间都是 JSON 有符号 `int64` Unix 毫秒。合同不额外限制业务日期范围；`0` 是合法值，JSON 字段缺失不能靠零值推断。

```text
Category identity            = categoryId
Schedule identity            = scheduleId
OccurrenceOverride identity = scheduleId + occurrenceDate
```

owner 不在请求 JSON 中，由认证上下文的 `redid` 提供。`OccurrenceOverride` 没有独立 ID；`occurrenceDate` 是 parent recurrence 真正生成的 UTC 午夜逻辑日期槽。

重复 Schedule 保存实际 timing 与稳定 `recurrence.anchorDate`。客户端展开公式：

```text
startOffset = parent.startAt - parent.recurrence.anchorDate
occurrence.startAt = occurrenceDate + startOffset

endOffset = parent.endAt - parent.recurrence.anchorDate
occurrence.endAt = occurrenceDate + endOffset

dueOffset = parent.dueAt - parent.recurrence.anchorDate
occurrence.dueAt = occurrenceDate + dueOffset
```

offset 可以为负数或大于等于 24 小时，禁止取模或截断。客户端选择“本次”时必须复用展开结果已有的 `occurrenceDate`，不得按设备显示日期、时区或实际时间重算。

后端不保存 occurrence 物化结果，也不提供实际时间物化 API；后端只验证 `occurrenceDate` 是否属于 parent recurrence。上述 offset 是客户端与跨端领域合同。

---

## 3. 客户端保存的两份完整状态

### 3.1 remoteSnapshot

```text
RemoteResourceSnapshot {
  typed identity
  fullCanonicalResource
  version
  remoteModifiedAt
}
```

语义：

- 只保存最后一次明确确认的服务端 live 完整资源；
- 完整 current 替换 remote row，typed delete 删除 remote row；
- 客户端不保存本地 tombstone snapshot；
- 只能由服务端响应更新，本地编辑不能直接修改；
- 客户端不能自行递增 `version`；
- 每次 inventory 都从仍存在的 live remote row 生成。

### 3.2 pendingSnapshot

```text
PendingResourceSnapshot {
  typed identity
  operation: UPSERT | DELETE
  fullLocalResource       // UPSERT 时存在，包含全部 AtomicField
  localRevision           // 仅客户端本地 compare-and-clear
  localModifiedAt         // DELETE 时上传
}
```

语义：

- 一个 identity 最多一条 pending；
- 普通编辑直接覆盖完整 pending，不保存不可变操作队列；
- 每次本地编辑递增 `localRevision`；
- 只更新真正改变的 AtomicField 的 `data` 与 `modifiedAt`；
- 其它 AtomicField 保留旧值与旧时间；
- `localRevision` 不上传服务端，也不参与跨设备冲突。

当前展示按 operation 分支：

```text
pending UPSERT 存在 → 展示 pending 完整资源
pending DELETE 存在 → 该 identity 本地不可见
无 pending           → 展示 remote live 资源；remote 也不存在则不可见
```

pending 可以暂时没有吸收最新 remote 中的其它原子，下一轮完整上传时由服务端再次合并。

---

## 4. 三类版本/时间不能混用

| 值 | 所有者 | 作用 |
| --- | --- | --- |
| `version` | 服务端 live 资源 | 表示完整资源当前版本；服务端实际改变 live 资源时递增。tombstone 的共享列固定写 `0`，不赋予版本语义。 |
| `deletedAt` / `reason` | 服务端 tombstone | typed delete 的删除时间与可选原因；不参与 live 版本比较。 |
| `localRevision` | 客户端本地 | 判断响应是否仍对应当前 pending。 |
| `AtomicField.modifiedAt` | 客户端原子，服务端保存 | 决定同一原子的 incoming 与 stored 谁胜出。 |
| `remoteModifiedAt` | 服务端 | 展示最后实际资源变化时间，不参与原子胜负。 |
| `localModifiedAt` | 客户端 DELETE | DELETE 请求必填时刻；不参与删除排序，也不生成服务端 tombstone 时间。 |

```text
客户端编辑一个原子：
  localRevision + 1
  仅该 AtomicField.modifiedAt 更新
  version 不变

服务端接受一次完整 PATCH 的实际变化：
  version + 1（无论本次接受几个原子）
  remoteModifiedAt 更新

完整快照已满足：
  不写库
  version 不变
```

---

## 5. typed 请求

```text
SyncRequest {
  syncRequestId

  categories {
    confirmed[]
    upserts[]
    deletes[]
  }

  schedules {
    confirmed[]
    upserts[]
    deletes[]
  }

  occurrenceOverrides {
    confirmed[]
    upserts[]
    deletes[]
  }

  atomicBatches[] {
    batchId
    categories { upserts[]; deletes[] }
    schedules { upserts[]; deletes[] }
    occurrenceOverrides { upserts[]; deletes[] }
  }
}
```

所有列表必须显式存在且为非 null 数组；空列表发送 `[]`。同一 identity 在整次请求的普通列表和所有 atomic batch 中最多出现一次 mutation，不能让两个列表同时声明不同目标。

### 5.1 confirmed inventory

```text
ConfirmedCategory            { id, version }
ConfirmedSchedule            { id, version }
ConfirmedOccurrenceOverride { scheduleId, occurrenceDate, version }
```

`confirmed[]` 只描述端上仍持有的 live remoteSnapshot。即使同 identity 还有 pending，也仍上传最后确认的 live 版本；pending 描述当前本地目标。端上应用远端 tombstone 后直接删除该资源，下一轮不再上传 confirmed 条目，不保留 `deleted` 确认状态。

### 5.2 upsert

```text
Category upsert            = 完整 CategoryInput，其中包含 version
Schedule upsert            = 完整 ScheduleInput，其中包含 version
OccurrenceOverride upsert = 完整 OccurrenceOverrideInput，其中包含 version

version == 0 → CREATE
version > 0  → 完整 PATCH
```

UPSERT 的 `version` 必须显式出现，使用 0/正数区分 CREATE 与 PATCH；DELETE 不携带资源版本。

不再分别上传 `pendingCreates[]`、`pendingPatches[]`，也没有 `kind`、`ResourceIdentity` 或 nullable resource body。

### 5.3 delete

```text
CategoryDelete            { id, localModifiedAt }
ScheduleDelete            { id, localModifiedAt }
OccurrenceOverrideDelete { scheduleId, occurrenceDate, localModifiedAt }
```

### 5.4 AtomicField 资源形状

```text
AtomicField<T> {
  data: T
  modifiedAt: int64 Unix millis
}
```

原子清单：

```text
Category:
  name
  color
  sortOrder

Schedule:
  kind（不可变，不参与 LWW）
  title
  description
  categoryId
  timing
  recurrence
  reminders
  todoState
  linkedToCourse

OccurrenceOverride:
  status
  timing patch
  title patch
  description patch
  categoryId patch
  reminders patch
```

Override 的六个原子独立合并；timing 只能 INHERIT/REPLACE，categoryId 的 REPLACE 必须引用 live Category。
非重复 Schedule 使用 `recurrence: {data:null, modifiedAt:...}`。

OccurrenceOverride PATCH 示例：

```json
{
  "version": 6,
  "scheduleId": "schedule-42",
  "occurrenceDate": 1776038400000,
  "status": {
    "data": "CANCELLED",
    "modifiedAt": 1775995200123
  },
  "timing": {
    "data": { "mode": "INHERIT" },
    "modifiedAt": 1775995200123
  },
  "title": {
    "data": { "mode": "INHERIT" },
    "modifiedAt": 1775995200123
  },
  "description": {
    "data": { "mode": "INHERIT" },
    "modifiedAt": 1775995200123
  },
  "categoryId": {
    "data": { "mode": "INHERIT" },
    "modifiedAt": 1775995200123
  },
  "reminders": {
    "data": { "mode": "INHERIT" },
    "modifiedAt": 1775995200123
  }
}
```

---

## 6. 服务端实际执行阶段

一次 HTTP 请求的普通操作不是总事务。service 按以下阶段执行，各普通 mutation 自己拥有 SERIALIZABLE owner 事务：

```text
0. 普通 parent DELETE 预检（请求含 Schedule/Category DELETE 时）
   读取当前 owner graph；若仍有 live 或 incoming child，首个 mutation 前保持零写，
   并在 HTTP 200 的 data 中把全部输入结果按下标标为 REJECTED，外层 status=20101

1. CREATE（version=0 的 upsert）
   Category → Schedule → OccurrenceOverride

2. atomicBatches
   按 batch 请求顺序

3. 普通 DELETE
   OccurrenceOverride → Schedule → Category

4. 普通 PATCH（version>0 的 upsert）
   Category → Schedule → OccurrenceOverride

5. 读取一次最终 owner graph
   生成 inventory delta 和 atomic related 状态
```

每类 `upserts[]` 可以同时包含 CREATE 与 PATCH。虽然内部被拆到两个阶段，响应的 `upsertResults[]` 仍严格回填原输入下标。

如果中途发生内部错误，之前已经提交的普通 mutation 不会回滚。客户端重试当前完整请求/当前 pending 即可：稳定 CREATE identity、完整快照 no-op、tombstone 和 atomic 状态判断保证最终收敛，不需要 request receipt。

---

## 7. CREATE 流程

### 7.1 客户端

新资源没有 remoteSnapshot：

```text
remoteSnapshot 不存在
pendingSnapshot = 完整 typed resource
version = 0
→ 放入对应 typed upserts[]
```

OccurrenceOverride 的稳定 identity 直接使用 parent `scheduleId + occurrenceDate`。

### 7.2 服务端

```text
identity 不存在
→ CREATED，创建 version 1。

identity 已 live
→ ALREADY_EXISTS，返回当前 canonical 资源；旧 CREATE 不覆盖。

identity 已 tombstone
→ RESOURCE_DELETED，返回 tombstone；禁止复活。
```

### 7.3 CREATE 请求期间继续编辑

```text
R CREATE 已发出
请求期间 pending localRevision 增加形成 U

R 响应：
  建立/刷新 remoteSnapshot
  U 保留

下一轮：
  U 使用正数 version 作为完整 PATCH 上传
```

响应丢失时重发同一 identity；服务端返回已有当前状态，不重复创建。

---

## 8. 普通完整 PATCH 流程

### 8.1 捕获请求

```text
uploadedResource = 当前 pending 完整不可变副本
uploadedRevision = pending.localRevision
version = remoteSnapshot.version
→ 与本次关系闭包一起放入 AtomicBatch，日常编辑优先 PUT /v2/schedules
```

客户端上传完整 resource，但只有真正修改过的 AtomicField 更新了 `data/modifiedAt`。

### 8.2 服务端合并

每个 AtomicField 独立判断：

```text
incoming.modifiedAt > stored.modifiedAt
→ incoming 胜出。

incoming.modifiedAt < stored.modifiedAt
→ stored 胜出。

时间相同、值相同
→ no-op。

时间相同、值不同
→ 后到服务端的 incoming 胜出。
```

例子：

```text
incoming title 较新       → 采用 incoming title
incoming description 较旧 → 保留 server description
incoming reminders 相同   → no-op
```

只要至少一个原子实际写入，整个资源版本增加一次并返回 `APPLIED`。没有写入时：

```text
全部值和时间已满足 → ALREADY_SATISFIED
至少一个 incoming 较旧 → SERVER_WON
```

即使部分原子 server-won、部分原子写入，整体仍为 `APPLIED`，响应中的 `current` 是最终完整混合结果。

客户端收到 PUT 响应后从 `AtomicBatchResult.relatedUpserts/relatedDeletes` 接受整个批次的 canonical
状态，再按每个成员的 `uploadedRevision` compare-and-clear。若请求期间已有更高
`localRevision`，只更新 remote 并保留较新 pending，不能用 current 覆盖它。

普通 PATCH 的 `version` 表示客户端构造快照时的确认版本，不要求等于事务中的最新服务端版本；各原子的 `modifiedAt` 决定字段级合并结果。需要改变 recurrence 日期集合的结构修改仍必须进入 atomic batch。

### 8.3 recurrence 特殊规则

Schedule 的 timing 与 recurrence 是不同原子：

- 普通 timing 修改可以直接 PATCH；
- incoming recurrence 实际改变日期集合时，普通 PATCH 整项 `REJECTED`，必须改用 atomic batch；
- 完整快照中较旧、没有胜出的 recurrence 不阻止其它普通原子合并。

---

## 9. DELETE 流程

### 9.1 客户端

pending DELETE 只需保存 typed identity、`localRevision` 和 `localModifiedAt`。首次创建 DELETE pending 时，identity 必须当前有 live remoteSnapshot 或 pending UPSERT；若 identity 已本地缺失且没有既存 DELETE pending，重复删除是本地幂等 no-op，不能凭任意 identity 合成 blind DELETE。合法 DELETE 已持久化后，即使 response 删除 remote，也继续按 revision 规则保留和结算：

- DELETE 始终只上传 typed identity 与 `localModifiedAt`；服务端统一按 delete-wins 处理。

一旦 DELETE pending 已持久化，撤销删除或重新创建不得把同 identity 改回 UPSERT；必须生成新 identity，并在需要重写引用时使用 typed atomic batch。

### 9.2 普通 DELETE

```text
目标 live 且最终图允许删除
→ DELETED，生成 tombstone。

目标不存在或已 tombstone
→ ALREADY_DELETED，不写库。
```

DELETE 不携带资源版本。因此在客户端读取后只有普通字段被其它设备修改的情况下，合法删除仍可优先产生 tombstone。目标从未存在时直接幂等完成，不为这个低价值场景创建无 payload tombstone。

删除 Schedule 或 Category 可能破坏 live 引用；需要同步删除依赖资源时必须放入同一个 atomic batch。若普通 parent DELETE 当前仍有 live child，或本次请求还会 upsert child，服务端在任何 mutation 前拒绝整次请求，不能依赖普通 child DELETE 的执行顺序模拟 closure。已 tombstone identity 不能通过 CREATE/PATCH 复活。

把某次 occurrence 恢复为系列默认时，不要删除 Override：保存 `ACTIVE + title/description/reminders 全部 INHERIT` 的 neutral live Override，避免不可复活 tombstone 阻止以后再次编辑同一 date-slot。

---

## 10. typed 响应与 inventory delta

```text
SyncResponse {
  syncRequestId

  categories {
    upserts[]
    deletes[]
    upsertResults[]
    deleteResults[]
  }

  schedules { ...同样四个 typed 列表... }
  occurrenceOverrides { ...同样四个 typed 列表... }

  atomicBatchResults[]
}
```

typed delete/tombstone 只包含 typed identity、`deletedAt` 和可选 `reason`：

```text
CategoryDeleteCurrent            { id, deletedAt, reason? }
ScheduleDeleteCurrent            { id, deletedAt, reason? }
OccurrenceOverrideDeleteCurrent { scheduleId, occurrenceDate, deletedAt, reason? }
```

不携带 `version`；客户端收到后直接删除对应 remote row。

`CategoryCurrent`、`ScheduleCurrent` 和 `OccurrenceOverrideCurrent` 的版本都位于 `current.resource.version`；`current.meta` 只保存 `createdAt` 与 `remoteModifiedAt`，不再重复版本字段。`ScheduleCurrent` 还携带可选 `firstRecurrenceAnchorDate`，它是服务端保留的首次 recurrence anchor history；规则清除后仍下发，客户端再次启用同一 identity 时必须复用。

普通结果严格按对应 typed 输入列表下标对齐：

```text
categories.upsertResults[i] ↔ request.categories.upserts[i]
categories.deleteResults[i] ↔ request.categories.deletes[i]
```

Schedule 与 Override 同理。

inventory delta 规则：

```text
最终是 live：
  客户端未知或 version 不同
  → 返回完整 typed upsert。

最终是 tombstone：
  客户端仍 confirmed 同 identity 的 live 资源
  → 返回 typed delete。

live 版本相同，或 tombstone identity 未出现在 confirmed 中
  → 省略。
```

因此首次空 inventory 只收到 owner 的全部当前 live 资源，不接收与本地无关的历史 tombstone。客户端只能根据明确的 typed delete 删除本地资源，不能根据响应中“没有出现”推断删除。

客户端不能按响应数组出现顺序直接写 remote。同一 identity 可能同时出现在普通 mutation result、atomic `relatedUpserts/relatedDeletes` 与最终 inventory delta 中，必须先归并：

```text
1. 按 typed identity 收集本响应全部 live current / typed delete 观察值。
2. 若存在 typed delete，直接删除 remote row；tombstone 永久生效且 delete-wins，不参与版本比较。
3. 没有 typed delete 时，仅对 live observations 选择最高 `version`；同一最高版本的 live payload 必须完全一致。
4. 较低 live 版本不得覆盖较高 live 版本；同版本 live payload 冲突时整次响应 fail-closed。
5. 任何冲突或非法 live 版本序列都让整次响应事务回滚，不清 pending。
```

归并后应用：

```text
identity 无 pending：
  有 typed delete 则直接删除 remote row；否则最高 live version 的完整 current 替换 remoteSnapshot。

identity 有 pending UPSERT：
  同样更新或删除 remote；pending UPSERT 保持原样继续展示并在下一轮上传。

identity 有 pending DELETE：
  同样更新或删除 remote；identity 仍保持本地不可见，未收敛 DELETE 下一轮继续上传。
```

mutation result 负责证明 uploaded operation 是否终结；归并后的 live 观察值负责最终 remote 写入，tombstone 直接 delete-wins；两者不能互相替代。

---

## 11. 响应 compare-and-clear

响应到达后，对每个已上传 pending 在本地事务中：

```text
1. 先按 typed identity 汇总本响应的普通 result、atomic related 状态与 inventory delta；若存在 typed delete，直接按 delete-wins 删除 remote row；否则仅按最高 live `version` 归并，归并后的完整 current 替换 remoteSnapshot，不保存本地 tombstone snapshot。

2. current pending 不存在
   → 无需清理。

3. current pending.localRevision != uploadedRevision
   → 请求期间产生了 U，保留 pending。

4. current pending.localRevision == uploadedRevision
   → 还必须确认结果已经应用、满足或明确终结 uploaded operation，才能删除 pending。

5. CREATE 返回 ALREADY_EXISTS
   → 服务端已明确拒绝重复 identity；应用返回的 canonical current，并清除仍匹配的 uploaded R。

6. REJECTED
   → 服务端已经明确拒绝业务输入；清除仍匹配 uploaded revision 的 R，保留请求期间形成的 U。
```

`uploadedRevision` 只能证明“请求期间没有新的本地编辑”，不能证明服务端已经满足 uploadedSnapshot。尤其不能只按 identity、operation 类型或 revision 相等清除 CREATE pending。

同 revision 的终结映射必须穷举：UPSERT `CREATED/APPLIED/ALREADY_SATISFIED/SERVER_WON` 在完整 canonical current 成功应用后清理；`RESOURCE_DELETED` 在 tombstone 成功应用后清理，删除胜出且不得自动换 identity 复活；DELETE `DELETED/ALREADY_DELETED` 清理；`REJECTED` 与 HTTP 400 明确丢弃匹配的 uploaded R；缺失必要 current/tombstone/related 状态或无法解释的响应不清。atomic batch 只有在全部 member revision 和 localBatchId 均仍匹配时整体清理；任一成员出现 U 就整批保留。

---

## 12. R → U

```text
R = 已捕获并上传的 pending 完整快照
U = R 请求期间产生的更高 localRevision pending 快照
```

### 12.1 R 返回时没有 U

```text
currentPending.localRevision == uploadedRevision
且服务端结果已应用、满足或明确终结 uploaded operation
→ 更新 remote
→ 删除 pending
→ 资源 clean

若 CREATE 返回 ALREADY_EXISTS
→ 更新 remote
→ 清除仍匹配 uploadedRevision 的 R
→ 由用户基于当前 canonical 状态重新发起后续编辑
```

### 12.2 R 返回时已有 U

```text
currentPending.localRevision != uploadedRevision
→ 更新 remote
→ 保留 U
→ 不把 R 响应 merge/rebase 到 U
→ 下一轮上传完整 U
```

U 可以暂时没有吸收 R 返回的其它设备变化：

- U 真正修改过的原子有更晚 modifiedAt，下一轮可采用 U；
- U 未修改的旧原子仍带旧 modifiedAt，下一轮由服务端保留较新 remote；
- 服务端返回新的完整 canonical 资源；
- revision 没再变化时清除 U。

### 12.3 operation 类型变化

```text
UPSERT R → DELETE U
  保留更高 revision DELETE。
  下一轮仍只上传 identity 与 localModifiedAt：既覆盖未确认 CREATE，
  也覆盖 R response 已删除 remote、但 U 仍需结算；服务端对 live/absent/tombstone 幂等收口。

DELETE R → UPSERT U
  禁止同 identity 复活。
  DELETE 一旦成为 durable pending，重新创建必须生成新 identity；
  需要改写 Category/Schedule/Override 引用时提交 atomic batch。
```

该规则不需要持久化 `transport-entered`。客户端只保存当前 pending；无版本 DELETE 与永久 tombstone 负责覆盖 CREATE 响应未知和 typed delete 的重试，新的 identity 负责覆盖删除后恢复。

### 12.4 R 响应丢失或失败

```text
R 可能已在服务端成功，也可能未成功
客户端保留当前 U/当前 pending
下一轮直接上传当前完整状态
```

无需保存 `RESPONSE_UNKNOWN`、receipt 或 R/U 因果链。

---

## 13. recurrence atomic batch

以下目标必须用 atomic batch：

- recurrence rule、weekday、count/until 等改变 `occurrenceDate` 集合；
- SplitSeries 的最终 A/B/Override 资源图；
- DeleteThisAndFollowing；
- whole-series delete 与 Override closure；
- Override 跨 parent 或跨 `occurrenceDate`，即 `DELETE old + CREATE new`。

第一次 recurrence `anchorDate` 建立后不可改变；清除 recurrence 后仍保留历史。WEEKLY `weekdays` 必须始终包含 immutable first anchor 的 weekday。需要另一 anchor 或移除 anchor weekday 时，必须创建新 Schedule identity。

同 identity recurrence batch 只要求全部当前 live Override 继续属于新规则；规则变化会使 live Override 失效时，客户端应在 atomic batch 中同步处理 closure。当前不检测历史 tombstone date-slot 重入，也不定义 `SERIES_REPLACEMENT_REQUIRED`。

atomic batch 内仍是三类 typed `upserts[]/deletes[]`，不是 generic `operations[]`。服务端暂存顺序：

```text
Category upsert
→ Schedule upsert
→ OccurrenceOverride upsert
→ OccurrenceOverride delete
→ Schedule delete
→ Category delete
```

未满足的 PATCH 必须精确匹配当前 version；DELETE 不携带版本，live 时删除，absent 或已 tombstone 时按已满足处理。已完整满足的重试先判 `ALREADY_SATISFIED`。精确命中版本后，完整 PATCH 中未 rebase 的无关旧原子仍按 AtomicField 规则由服务端胜出，不会仅因此拒绝整批；其它较新 incoming 原子可形成合法混合结果。服务端模拟完整最终图，确认：

- Schedule 引用 live Category；
- live Override 引用 live recurring parent；
- occurrenceDate 是 UTC 午夜且由 parent rule 生成；
- recurrence 变化后没有遗留失效 live Override；
- tombstone identity 不被复活。

结果：

```text
APPLIED           至少一项实际写入
ALREADY_SATISFIED 全部目标已满足
REJECTED          任一项不安全或最终图非法，整批不写
```

每个 batch 结果按 Category/Schedule/Override 分块，六个结果列表分别与输入下标对齐，并只返回该 batch 输入 identity 对应的 `relatedUpserts/relatedDeletes`，不会额外扩展为完整引用闭包。客户端基于 related 当前状态和普通 inventory delta 自动重建并重试，不要求用户处理普通冲突。

---

## 14. 严格解码与当前实现边界

后端 fail-closed：

- 所有 typed list 必须存在且非 null；
- 完整 resource 的每个必填 AtomicField 必须存在；
- AtomicField 的 `data` 与 `modifiedAt` 必须同时出现；
- UPSERT 的 `version` 和 Reminder 的 `minutesBefore` 必须出现；
- DELETE 的 `localModifiedAt` 必须出现；
- 仅 `recurrence.data: null` 与 `category.color.data: null` 合法，其它 null 一律拒绝，可选字段应省略；
- 完全重复 key、同一对象内大小写折叠别名 key、未知字段、尾随第二个 JSON、非法枚举和重复 mutation identity 一律拒绝；
- presence 与数值 `0` 分离。

错误分层必须稳定：

- JSON/shape/presence/unknown-field/duplicate/enum 错误：HTTP 400，无 `SyncResponse`、无 aligned result、零 mutation；
- 认证失败：认证层 401/403；
- 已通过 strict decode 后的资源状态、parent closure、membership 或最终图业务失败：HTTP 200，外层 `status=20101`，并在 `data` 原输入下标返回 `REJECTED`；
- request-wide 普通 parent DELETE preflight 若在首个写入前发现 closure 非法，则整次请求零写，所有普通 mutation 与 batch 结果都按各自输入下标返回 `REJECTED`；
- 内部错误：HTTP 5xx；此前已由独立普通事务提交的 mutation 不保证回滚，客户端保留当前 pending 并幂等重试。

Schedule v2 transport 必须保留完整 `ApiWrapper<SyncResponse>`：`status=10000` 时正常消费 `data`，`status=20101` 时同样消费 `data` 中的 aligned `REJECTED`。该接口不能先调用通用 `mapOrThrowApiException()`，也不能把 `20101` 加入全局 `isSuccess()`；其它状态才进入普通网络异常处理。

应用层不设置请求体大小、列表数量、业务 ID 长度或时间数值上限。

当前后端每条普通 mutation 分别在 SERIALIZABLE owner 事务中加载完整 owner graph。对含 `M` 条普通 mutation、owner 有 `R` 条资源的极端大请求，工作量约为 `O(M×R)`；这是小体量初始版本接受的非阻塞性能限制，不通过协议上限规避。只有后续真实集成/性能数据证明需要时再改 bulk transaction。

当前客户端已经切换到 typed request/response、AtomicField、日常聚合 `/v2/schedules` 和完整 `/v2/schedule-mutations`；不得重新引入 `ResourceKind`、`ResourceInput`、`pendingCreates/pendingPatches`、散落 `*_modified_at` 或带 category override 的旧 OccurrenceException。
