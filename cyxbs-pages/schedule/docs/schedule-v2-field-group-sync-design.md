# Schedule v2 完整资源 AtomicField 合并与原子批次设计

> **状态：本文是最新 canonical 目标；后端与客户端已按 typed Sync、日常 Schedule 接口和本文 version 合同完成实现，但尚未部署或进行真实账号验收。**
>
> 本文是客户端接入目标，不表示生产远端同步已经启用，也不授权本次修改客户端代码、UI、部署或远端开关。

---

## 1. 设计结论

Schedule v2 面向低并发、小体量、多设备极少同时编辑同一资源的场景，采用以下轻量模型：

```text
本地 clean 资源：
  只在 typed inventory 中上传端上仍持有的 live identity 与已确认 version；
  收到 typed delete 后删除本地 live remote row，不保存 deleted inventory 状态。

本地 dirty 资源：
  上传完整 Category / Schedule / OccurrenceOverride 快照；
  每个可独立合并的值都包装为 { data, modifiedAt }。

服务端：
  按每个 AtomicField 独立合并；
  返回完整 canonical typed 资源或显式 tombstone；
  不下发局部字段 patch。

客户端：
  remoteSnapshot 只保存最后确认的 live 完整云端资源；
  typed delete 在响应事务中删除对应 remote row；
  pendingSnapshot 保存当前完整本地临时资源；
  localRevision 只用于响应 compare-and-clear。

R → U：
  R 响应只更新 live remote 状态：upsert 替换 remote row，delete 删除 remote row；
  请求期间产生的 U 保留在 pendingSnapshot；
  U 下一轮再次完整上传并最终收敛。
```

不采用：

- 通用 `ResourceKind + ResourceInput` 联合；
- `FieldGroupPatch[]`、普通编辑操作历史或 mutation event stream；
- cursor、receipt、`settledSnapshotToken`、HLC 或客户端 rebase 状态机；
- `schedule_structure_operations`、SplitSeries receipt、A→B lineage；
- 同时间冲突交给用户二选一；
- 为未观测到的极端规模设置应用层请求、列表、ID 或时间上限。

完整时序见 [schedule-v2-resource-version-sync-flow.md](schedule-v2-resource-version-sync-flow.md)，重复日程能力见 [schedule-v2-recurrence-override-capability-matrix.md](schedule-v2-recurrence-override-capability-matrix.md)。

---

## 2. 时间与 UTC date-slot

所有 wire 日期和时间都是 JSON 有符号 `int64` Unix 毫秒，不使用字符串日期或时区字段。合同不增加业务日期范围检测；`0` 也是合法时间戳，字段缺失必须通过 JSON presence 判断，不能通过数值零判断。

```text
TIMED      → 实际 startAt/endAt
DEADLINE   → 实际 dueAt
ALL_DAY    → UTC 午夜起止，endAt exclusive
UNSCHEDULED→ 不携带时间字段，且不能启用 recurrence
```

重复 Schedule 同时保存实际 timing 与稳定 `recurrence.anchorDate`。`anchorDate` 是 UTC 午夜逻辑槽位，第一次启用 recurrence 后保持不变；即使 recurrence 暂时清除，服务端仍保存首次 anchor history。

客户端展开 occurrence 时先得到稳定 UTC `occurrenceDate`，再叠加 parent 相对 anchor 的完整 offset：

```text
startOffset = parent.startAt - parent.recurrence.anchorDate
occurrence.startAt = occurrenceDate + startOffset

endOffset = parent.endAt - parent.recurrence.anchorDate
occurrence.endAt = occurrenceDate + endOffset

dueOffset = parent.dueAt - parent.recurrence.anchorDate
occurrence.dueAt = occurrenceDate + dueOffset
```

offset 可以为负数或大于等于 24 小时，禁止取模、截断，也不能从显示日期、设备时区或修改后的实际时间反推 `occurrenceDate`。

客户端展开结果必须同时携带稳定 `occurrenceDate` 和实际时间。用户选择“本次”时复用展开结果已有的 `occurrenceDate`；parent timing 变化不迁移 Override identity。

不保存时区的明确代价是：recurrence 按 UTC 日期和完整 offset 运行，DST 或跨时区后不保证维持相同本地墙钟时间，设备只负责把实际时间戳本地化展示。

---

## 3. Typed 请求，而不是资源枚举联合

完整 Sync 与日常 Schedule mutation 共用同一套 typed 资源合同：

```text
POST   /v2/schedule-mutations
POST   /v2/schedules
PUT    /v2/schedules
DELETE /v2/schedules
```

`/v2/schedule-mutations` 在首次进入、网络恢复或 pending 收敛时平铺三类 typed 资源：

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

所有列表必须显式存在且不能为 `null`；没有内容时发送 `[]`。同一 identity 在整次请求的普通列表和所有 atomic batch 中最多出现一次 mutation。请求不携带 `protocolVersion`、owner、cursor 或 receipt。

### 3.1 typed inventory

```text
ConfirmedCategory {
  id
  version
}

ConfirmedSchedule {
  id
  version
}

ConfirmedOccurrenceOverride {
  scheduleId
  occurrenceDate
  version
}
```

`confirmed[]` 只上传端上仍持有的 live `remoteSnapshot`。同一 identity 即使存在 pending，也仍上传最后确认的 live 版本；pending mutation 另放入对应 typed mutation 列表。端上应用远端 tombstone 后直接删除该资源，下一轮不再上传 confirmed 条目，不保留 `deleted` 确认状态。

### 3.2 typed upsert

每类 `upserts[]` 元素就是对应的完整 typed resource：

```text
{
  version
  identity
  完整 AtomicField...
}

version == 0 → CREATE
version > 0  → 完整 PATCH
```

UPSERT 的 `version` 必须显式出现；PATCH 使用构造请求时 `remoteSnapshot.version`，DELETE 不携带资源版本。

不再分别维护 `pendingCreates[]` 和 `pendingPatches[]`，也不上传操作枚举。正数 version 来自构造请求时 `remoteSnapshot.version`。

### 3.3 typed delete

```text
CategoryDelete            { id, localModifiedAt }
ScheduleDelete            { id, localModifiedAt }
OccurrenceOverrideDelete { scheduleId, occurrenceDate, localModifiedAt }
```

`localModifiedAt` 必须出现在 JSON 中，但任意有符号 `int64` 值都合法，包括 `0`。当前服务端不以它决定删除顺序，也不把它写成 tombstone 时间；服务端 `deletedAt` 使用服务端时钟。

---

## 4. AtomicField

每个原子都把完整值和客户端修改时刻绑定在一起：

```json
{
  "data": "完整原子值",
  "modifiedAt": 1786669323000
}
```

`data` 与 `modifiedAt` 必须同时存在。客户端不能因为“上传完整资源”而刷新全部时间；只更新真正被编辑的原子，未编辑原子继续携带 remote/pending 中原有的值与时间。

### 4.1 Category

| JSON 字段 | `data` 语义 |
| --- | --- |
| `name` | 完整名称 |
| `color` | 完整颜色 |
| `sortOrder` | 排序整数 |

Category 示例：

```json
{
  "id": "category-course",
  "name": { "data": "课程", "modifiedAt": 1786669323000 },
  "color": { "data": "#4A90E2", "modifiedAt": 1786669323001 },
  "sortOrder": { "data": 10, "modifiedAt": 1786669323002 }
}
```

### 4.2 Schedule

| JSON 字段 | `data` 语义 |
| --- | --- |
| `title` | 完整标题 |
| `description` | 完整详情 |
| `categoryId` | 完整分类引用 |
| `timing` | `kind/startAt/endAt/dueAt` 合法完整组合 |
| `recurrence` | 完整规则；`data: null` 表示非重复 |
| `reminders` | 完整提醒列表 |
| `todoState` | `OPEN / COMPLETED / null`；null 表示当前不进入清单 |
| `linkedToCourse` | 是否请求投射到课表 |

`kind=TODO|AFFAIR` 是 Schedule 的 required、不可变创建来源，不是 AtomicField，也不参与 LWW 合并。
TODO 必须拥有非空 `todoState`；AFFAIR 必须使用 TIMED 且 `linkedToCourse=true`，只有关联清单后才拥有
非空 `todoState`。

重要拆分：

- `title` 与 `description` 是两个原子，各有自己的 `modifiedAt`；
- `timing` 与 `recurrence` 是两个原子，不再共享时间；
- timing 内部仍保持一个原子，因为 kind 与对应时间字段必须一起满足合法组合；
- recurrence 内部仍保持一个原子，因为 frequency、interval、anchor、count/until 和 weekdays 共同定义一个日期集合；
- reminders 没有稳定子 ID，因此完整列表仍是一个原子。

Schedule 示例：

```json
{
  "id": "schedule-42",
  "kind": "TODO",
  "title": { "data": "高等数学", "modifiedAt": 1786669323000 },
  "description": { "data": "第三章", "modifiedAt": 1786669323001 },
  "categoryId": { "data": "category-course", "modifiedAt": 1786669323002 },
  "timing": {
    "data": {
      "kind": "TIMED",
      "startAt": 1786672800000,
      "endAt": 1786678200000
    },
    "modifiedAt": 1786669323003
  },
  "recurrence": {
    "data": {
      "frequency": "WEEKLY",
      "interval": 1,
      "anchorDate": 1786579200000,
      "weekdays": ["MO", "WE"]
    },
    "modifiedAt": 1786669323004
  },
  "reminders": {
    "data": [{ "minutesBefore": 10, "message": "" }],
    "modifiedAt": 1786669323005
  },
  "todoState": { "data": "OPEN", "modifiedAt": 1786669323006 },
  "linkedToCourse": { "data": true, "modifiedAt": 1786669323007 }
}
```

`ScheduleInput` 包含不可变 `kind` 与上述八个客户端原子。服务端下发的 `ScheduleCurrent` 另外携带可选 `firstRecurrenceAnchorDate`：它是首次启用 recurrence 后固定的 UTC 日期槽，不参与 LWW 合并；规则清除后仍下发，客户端再次启用同一 identity 时必须把它作为新的 `recurrence.data.anchorDate`。

课表可见性不是简单等于 `linkedToCourse`：CANCELLED occurrence 永不展示；AFFAIR 在关联课表后不受
完成态影响；TODO 只有在关联课表且 occurrence 为 ACTIVE 时展示。这样事务关联清单并完成后不会丢失
事务身份，而普通清单完成后会暂时退出课表。

非重复 Schedule 仍必须携带 recurrence 原子：

```json
{
  "recurrence": {
    "data": null,
    "modifiedAt": 1786669323004
  }
}
```

### 4.3 OccurrenceOverride

OccurrenceOverride identity 直接位于 typed resource：

```text
scheduleId + occurrenceDate
```

它有六个原子：

| JSON 字段 | `data` 语义 |
| --- | --- |
| `status` | `ACTIVE / COMPLETED / CANCELLED` |
| `timing` | `FieldPatch<Timing>`，只允许 `INHERIT / REPLACE` |
| `title` | `FieldPatch<string>` |
| `description` | `FieldPatch<string>` |
| `categoryId` | `FieldPatch<string>` |
| `reminders` | `FieldPatch<Reminder[]>` |

没有独立 exception ID 或 recurrence 规则。timing REPLACE 携带完整 union 并保持父系列 kind，categoryId
REPLACE 必须引用同 owner 的 live Category；两者都不改变 occurrence identity。

`FieldPatch<T>` 三态：

```text
INHERIT → 使用 parent 当前值，不携带 value
CLEAR   → 显式覆盖为空，不携带 value
REPLACE → 使用完整替换值，必须携带 value
```

完整例子：

```json
{
  "scheduleId": "schedule-42",
  "occurrenceDate": 1787184000000,
  "status": {
    "data": "CANCELLED",
    "modifiedAt": 1786669323000
  },
  "timing": {
    "data": { "mode": "INHERIT" },
    "modifiedAt": 1786669323000
  },
  "title": {
    "data": { "mode": "INHERIT" },
    "modifiedAt": 1786669323001
  },
  "description": {
    "data": { "mode": "REPLACE", "value": "本周改为线上" },
    "modifiedAt": 1786669323002
  },
  "categoryId": {
    "data": { "mode": "INHERIT" },
    "modifiedAt": 1786669323002
  },
  "reminders": {
    "data": { "mode": "CLEAR" },
    "modifiedAt": 1786669323003
  }
}
```

仅修改本次分类写入 categoryId patch；仅修改本次时间写入 timing patch。两者都保留原始
`scheduleId + occurrenceDate` identity，不把实际移动后的日期反写为 occurrenceDate。

---

## 5. 客户端双快照

### 5.1 remoteSnapshot

```text
RemoteResourceSnapshot {
  typed identity
  fullCanonicalResource
  version
  remoteModifiedAt
}
```

约束：

- 只保存服务端明确返回的 live 完整资源；
- typed delete 删除对应 remote row，不写本地 tombstone snapshot；
- 本地编辑不能直接修改；
- 客户端不能自行递增 `version`；
- inventory 始终从仍存在的 live remoteSnapshot 生成。

### 5.2 pendingSnapshot

```text
PendingResourceSnapshot {
  typed identity
  operation: UPSERT | DELETE
  fullLocalResource       // UPSERT 时存在，包含全部 AtomicField
  localRevision           // 仅客户端本地使用
  localModifiedAt         // DELETE 时上传
}
```

约束：

- 一个 identity 最多一条 pending；
- 第一次编辑从当前 remote 或 pending 复制完整资源；
- 后续编辑覆盖同一 pending，并递增 `localRevision`；
- 只更新真正修改的 AtomicField；
- `localRevision` 不上传服务端，不参与跨设备冲突判断。

客户端有效展示必须按 operation 分支，DELETE 没有可展示的资源 payload：

```text
pending UPSERT 存在       → 展示 pending 的完整资源
pending DELETE 存在       → 该 identity 本地不可见
否则且 remoteSnapshot 存在 → 展示 remote 的 live 完整资源
两者都不存在              → 该 identity 本地不可见
```

### 5.3 捕获与 compare-and-clear

发送前捕获不可变副本：

```text
uploadedPending  = pendingSnapshot 完整不可变副本
uploadedRevision = pendingSnapshot.localRevision
UPSERT version = remoteSnapshot.version；CREATE 为 0
DELETE         = typed identity + localModifiedAt，不携带 version
```

响应到达后在本地事务中：

```text
1. 先按 typed identity 汇总普通 result、atomic related 状态与 inventory delta；若存在 tombstone，直接按 delete-wins 删除 remote row；否则仅对 live observations 按最高 `version` 归并，归并后的完整 current 替换 remoteSnapshot。

2. current pending.localRevision != uploadedRevision
   → 请求期间产生了 U；保留 pending，不 merge、不覆盖。

3. current pending.localRevision == uploadedRevision
   → 仍需确认结果已经应用、满足或明确终结 uploaded operation，才能删除 pending。

4. CREATE 返回内容不同的 ALREADY_EXISTS
   → 保留 pending；下一轮使用返回的正数 version 改为完整 PATCH。

5. REJECTED 或需要 atomic batch
   → 保留 pending，并按返回的 current/related 状态重新规划。
```

revision 相等只说明请求期间没有新本地编辑，不能单独证明 uploadedSnapshot 已被服务端满足。结果终结映射固定为：UPSERT 的 `CREATED/APPLIED/ALREADY_SATISFIED/SERVER_WON` 在完整 canonical current 已成功应用后清 pending；`RESOURCE_DELETED` 在 tombstone 已应用后清同 revision UPSERT，删除胜出且不得自动换 identity 复活；CREATE `ALREADY_EXISTS` 仅在 current 与 uploaded resource 语义相同时清理，不同时保留并转 PATCH；DELETE 的 `DELETED/ALREADY_DELETED` 清理；`REJECTED`、缺失必要 current/tombstone/related 状态或非法响应一律不清。atomic batch 只有 `APPLIED/ALREADY_SATISFIED`、related 状态完整且所有 member revision 未变化时整体清理。

---

## 6. 服务端原子合并

每个 AtomicField 独立执行：

```text
incoming.modifiedAt > stored.modifiedAt
→ incoming 胜出。

incoming.modifiedAt < stored.modifiedAt
→ stored 胜出。

时间相同且语义值相同
→ no-op。

时间相同但语义值不同
→ 后到服务端的 incoming 胜出。
```

一次完整 PATCH 可以同时接受部分客户端原子并保留部分服务端原子。只要有实际变化，整个资源 `version` 最多增加一次；所有原子都已满足时不递增。

普通 PATCH 结果码：

```text
APPLIED           至少一个 incoming 原子写入
ALREADY_SATISFIED 完整快照已满足，无写入
SERVER_WON        没有 incoming 写入，且至少一个原子较旧
REJECTED          wire 已合法解码，但该 mutation 的基线、业务 identity 或最终图非法
RESOURCE_DELETED  目标已是 tombstone
```

JSON shape、未知字段、缺失必填字段、非法枚举、重复 identity 等 strict wire 错误不会生成上述逐项结果：整个 HTTP 请求返回 400，且没有 `SyncResponse`。只有请求已经通过严格解码，随后在基线、staged graph、引用或 recurrence 业务校验中失败，才返回与输入下标对齐的 `REJECTED`。

无论结果中有多少原子由哪一端胜出，服务端都返回完整 canonical typed 资源，客户端不解释逐原子结果。

`remoteModifiedAt` 是服务端展示时间，不参与原子胜负。

---

## 7. CREATE、DELETE 与 typed 响应

### 7.1 CREATE

```text
version = 0

identity 不存在 → CREATED，创建 version 1
identity 已 live → ALREADY_EXISTS，返回当前完整资源
identity tombstone → RESOURCE_DELETED
```

CREATE 响应丢失时重发同一稳定 identity，不需要 receipt。

### 7.2 DELETE

客户端只能在 identity 当前有 live remoteSnapshot 或 pending UPSERT 时首次创建 DELETE pending；identity 已经本地缺失且也没有既存 DELETE pending 时，重复删除按本地幂等 no-op 处理，不能凭任意 identity 新建 blind DELETE。DELETE 一旦合法持久化，即使后续 response 删除 remote，也按 `localRevision` 规则继续保留和结算。

DELETE 只显式携带 typed identity 与 `localModifiedAt`，不上传资源版本。

服务端处理：

```text
目标 live          → DELETED，生成 tombstone
目标不存在/已删除  → ALREADY_DELETED，不写库
```

普通和 atomic DELETE 都采用 delete-wins；owner、引用与最终图校验仍然执行。目标不存在时直接幂等完成，不额外保存无 payload tombstone。

一旦本地 DELETE pending 已经持久化，后续“撤销删除/重新创建”不得把同 identity 改回 UPSERT；必须生成新 identity，并在需要重写 Category/Schedule/Override 引用时提交 typed atomic batch。这样不会与可能已经生成的永久 tombstone 竞争。

DELETE 不读取资源版本，因此普通字段在此期间发生变化也不阻止合法删除。涉及 parent/Override closure 的删除进入同一个 atomic batch；若请求仍把 live 或 incoming child 与普通 parent DELETE 拆开，服务端会在首个 mutation 前拒绝整次请求，避免 child tombstone 已提交后 parent 才失败。

### 7.3 typed 响应

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

每类 `upsertResults[]` 与请求的对应 `upserts[]` 按下标对齐；`deleteResults[]` 同理。服务端内部可把同一 upsert 列表分成 CREATE 与 PATCH 两阶段执行，但响应仍回填原下标。

inventory delta：

- 最终是 live，且客户端未知或 `version` 不同：完整 typed upsert；
- 最终是 tombstone，且客户端仍 confirmed 同 identity 的 live 资源：typed delete；
- live 版本相同，或 tombstone identity 未出现在 confirmed 中：省略。

因此首次空 inventory 只收到该 owner 的全部当前 live 资源，不接收与本地无关的历史 tombstone。客户端只能根据明确的 typed delete 删除本地资源，不能通过响应中“没有出现”推断删除。

同一响应中，普通 mutation result、atomic `relatedUpserts/relatedDeletes` 与最终 inventory delta 可能观察到同一 identity 的不同版本。客户端必须先按 typed identity 归并，再修改 remote：

1. 若存在 tombstone，直接删除 remote row；tombstone 永久生效且 delete-wins，不参与版本比较；
2. 没有 tombstone 时，仅对 live observations 选择最高 `version`；同一最高版本出现多个 live payload 时必须语义完全一致；
3. 较低 live 版本不得覆盖较高 live 版本；同版本 live payload 冲突时整次响应 fail-closed；
4. 任一冲突或非法 live 版本序列都回滚整次本地响应事务，不清任何 pending；
5. 归并后的 live current 替换 remote row，不保存本地 tombstone。

mutation result 仍用于判断 uploaded operation 是否已经终结；上述归并只决定最终写入哪一个 remote 观察值。

---

## 8. recurrence 结构变化使用 atomic batch

`timing` 与 `recurrence` 是独立原子：

- parent 只修改实际 `startAt/endAt/dueAt`：普通完整 Schedule PATCH；
- incoming recurrence 原子真正改变日期集合：必须进入 atomic batch；
- 普通 PATCH 可携带较旧但未胜出的 recurrence 快照，不会因此阻止其它原子合并；
- 第一次 recurrence anchor 一经建立，atomic batch 也不能换 anchor；
- WEEKLY 规则的 `weekdays` 必须始终包含 immutable first anchor 的 weekday；需要移除该 weekday 时必须创建新 Schedule identity；
- 同 identity recurrence 变化后的最终 staged graph 不得遗留失去 membership 的 live Override；客户端可在同一 atomic batch 中删除或迁移这些 Override。

每条 live Override 必须引用 live recurring parent，且 `occurrenceDate` 必须由 parent 当前规则真实生成。规则变化会使 live Override 失效时，客户端必须在同一个 atomic batch 中删除或迁移这些 Override；最终 staged graph 仍不合法则整批回滚。保留 anchor weekday 时允许同 identity recurrence batch；需要移除 anchor weekday 时使用新 Schedule identity。当前不检测历史 tombstone date-slot 重入，也不定义专用原因码。

典型最终图：

```text
从第 8 周起编辑后续：
  PATCH full Schedule A      // 截断旧系列
  CREATE full Schedule B     // 新 identity 与新 anchor
  UPSERT/DELETE affected Overrides

从第 8 周起删除后续：
  PATCH/DELETE full Schedule A
  DELETE affected Overrides

从第一次 occurrence 起删除后续：
  DELETE Schedule A
  DELETE all live Overrides

Override 跨父或跨日期：
  DELETE old scheduleId + occurrenceDate
  CREATE new scheduleId + occurrenceDate
```

同一个 atomic batch 按 typed 列表提交。精确命中 version 后，完整 PATCH 中未 rebase 的无关旧原子仍可由服务端胜出，不能仅因出现 server-won 原子而拒绝其它实际胜出的 incoming 原子。服务端模拟完整最终图，只有引用、membership、identity 和 tombstone 约束全部成立才写入；否则整批 `REJECTED`。客户端根据返回的 related typed 当前状态重新规划并自动重试，不要求用户处理普通冲突。

批次状态：

```text
APPLIED           至少一个目标实际写入
ALREADY_SATISFIED 全部目标状态已经满足
REJECTED          整批不写入
```

`batchId` 只用于请求内去重和响应关联，不持久化 receipt。

---

## 9. R → U 只保证最终一致

```text
R = 已从 pendingSnapshot 捕获并发出的完整资源或 atomic batch
U = R 请求期间继续编辑形成的更高 localRevision pendingSnapshot
```

R 返回时：

```text
current 结果 → remoteSnapshot = 服务端完整 canonical 资源
typed delete → 删除对应 remote row
pendingSnapshot = U，保持不动
```

U 可能没有吸收 R 响应中的其它设备变化，这不需要客户端 rebase：

- U 真正修改过的原子携带更晚 `modifiedAt`，下一轮可胜出；
- U 未修改的旧原子携带旧值与旧时间，下一轮由服务端较新原子保留；
- 服务端再次返回完整资源；
- 当前 pending revision 未再变化时 compare-and-clear。

R 明确失败或响应丢失时同样保留当前 U。下一轮直接提交当前完整 pending；状态型 CREATE、AtomicField no-op、tombstone 和 atomic 最终图判断保证最终收敛。

operation 发生变化时采用以下封闭规则：

- `UPSERT R → DELETE U`：保留更高 revision 的 DELETE；下一轮仍只上传 identity 与 `localModifiedAt`，由服务端对 live/absent/tombstone 幂等收口；
- `DELETE R → UPSERT U`：禁止恢复同 identity。删除一旦成为 durable pending，重新创建就生成新 identity，并按引用 closure 需要进入 atomic batch；
- response apply 始终先按 delete-wins 应用 tombstone；没有 tombstone 时按最高 live `version` 归并并更新 remote，再依据 uploaded operation result 与 `uploadedRevision` 决定是否清 pending。

R→U 是极少场景，设计目标是最终一致，不是一次网络往返立即合并全部变化。

---

## 10. 服务端当前状态持久化

后端只保存三张 owner 当前状态表，不保存客户端 remote/pending、事件历史或 receipt：

```text
schedule_v2_categories
schedule_v2_schedules
schedule_v2_occurrence_overrides
```

三张表都使用自增 `row_id` 代理主键。无上限业务 ID 原文保存在 longtext，查询索引使用 `owner + SHA-256 key`，命中后仍比较完整原文；Override 索引再加 `occurrence_date_ms`。

每行 `payload_json` 直接保存完整 canonical AtomicField：

```text
Category payload:
  name / color / sortOrder

Schedule payload:
  kind / title / description / categoryId / timing / recurrence / reminders / todoState / linkedToCourse

Override payload:
  status / title / description / reminders
```

原子 `data` 和 `modifiedAt` 不拆成散落的 ORM `*_modified_at_ms` 列。Schedule 另外投影认证 `stu_num`、当前 category ID/hash 和 `first_recurrence_anchor_ms`；读取时校验 category projection 与 canonical payload 一致。由 live 资源删除得到的 tombstone 保留 typed identity、`deletedAt`、可选 `reason` 与 canonical payload，不携带版本语义；共享 `version` 列在 tombstone 中固定写 `0`。DELETE 命中从未存在的 identity 时直接幂等完成，不创建无 payload tombstone。

---

## 11. 严格 JSON 与实现边界

后端严格解码：

- 递归拒绝完全重复 key，以及同一对象内大小写折叠后会绑定到同一 DTO 字段的别名 key；
- 拒绝未知字段与尾随 JSON；
- 除 `recurrence.data: null` 外拒绝 `null`；
- 所有 typed 列表和完整资源必填原子都必须存在；
- AtomicField 的 `data` 与 `modifiedAt` 必须成对出现；
- UPSERT 的 `version` 和 Reminder 的 `minutesBefore` 必须出现；
- DELETE 的 `localModifiedAt` 必须出现；
- presence 与数值零分离。

分层错误合同：

- 上述 JSON/shape/presence/duplicate/enum 错误：HTTP 400，无 `SyncResponse`、无 aligned result、零 mutation；
- 认证失败：沿用认证层 401/403，无 `SyncResponse`；
- 已通过严格解码后的 owner graph、parent closure、membership 或最终图业务失败：HTTP 200，外层 `status=20101`，`data` 返回与输入下标对齐的 `REJECTED`；
- 若普通 parent DELETE 的 request-wide preflight 在首个写入前发现 closure 非法，则整次请求零写，并将该请求的普通 mutation 与 atomic batch 结果全部按原下标标为 `REJECTED`；
- 内部错误：HTTP 5xx；普通 mutation 可能已经按既定独立事务提交，客户端保留当前 pending 并幂等重试。

应用层不设置请求体大小、列表数量、业务 ID 长度或时间数值最大值。后端当前按每个普通 mutation 分别加载 owner 完整资源图，这是面向小体量应用接受的初始性能取舍；只有后续真实集成或性能数据证明存在问题时，才重构为 bulk transaction，不为此提前增加协议上限。

当前客户端代码仍是旧合同，仅可作为业务参考。接入时必须以本文 typed 形状、AtomicField 划分和 `/v2/schedule-mutations` 为准，不能继续发送 `ResourceKind`、`ResourceInput` 联合、散落 `*_modified_at`、带 category override 的旧 OccurrenceException 或 `/v2/schedule-sync`。
