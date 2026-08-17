# Schedule v2 客户端存储设计

> 文档状态：当前实现。Schedule v2 尚未上线，允许 break change；旧开发数据库需要清库或重装，不提供旧 schema 迁移。

## 1. 设计结论

客户端只保存三类资源的远端快照和本地临时快照：

- Category
- Schedule
- OccurrenceOverride

Room 只注册四张表：

```text
schedule_v2_account_metadata
schedule_v2_category_state
schedule_v2_schedule_state
schedule_v2_occurrence_override_state
```

没有下列旧同步结构：

- cursor、bootstrap/delta page；
- 逐条 outbox、receipt、in-flight、delivery-unknown；
- tombstone 表；
- candidate、checkpoint、journal、reservation、settlement；
- 设备 ID 或 mutationId。

## 2. 账号元数据表

`schedule_v2_account_metadata` 每个账号一行：

```text
account_id              TEXT PRIMARY KEY
local_revision_counter  INTEGER NOT NULL
```

`local_revision_counter` 只为本地编辑分配单调 revision。它不上传服务端，也不是资源 `version`。

revision 出现空洞没有业务含义：命令可能 NoOp、Rejected 或在落库前取消。调用方只要求后一次成功编辑取得更大的值。

## 3. 三张资源状态表

三张表使用各自 typed identity：

```text
Category:             account_id + category_id
Schedule:             account_id + schedule_id
OccurrenceOverride:  account_id + schedule_id + occurrence_date
```

每行表达同一 identity 的两侧状态：

```text
remote_snapshot       typed Current?      // 服务端最后确认的 live 资源
pending_operation     UPSERT | DELETE | null
pending_snapshot      typed Input?        // 仅 UPSERT 存在
pending_local_modified_at Long?           // 仅 DELETE 存在
local_revision        Long?
local_batch_id        String?
```

Kotlin Entity 字段直接使用 typed DTO。Room3 `ColumnTypeConverter` 负责把它们严格编码为 JSON 列；业务代码不直接维护裸 `String` JSON。

`remote_snapshot.resource.version` 必须大于 0。pending CREATE 的资源 version 为 0；pending PATCH 在 capture 时使用当前 remote version 生成 wire 请求，不反写 pending 内容。

`localBatchId` 是本地不可拆分分组键，Sync capture 时映射为当次 `AtomicBatch.batchId`。它不是 receipt，也不证明服务端处理进度。

## 4. 有效展示状态

每个 identity 的可见值只有三个分支：

```text
pending UPSERT  -> 展示 pendingSnapshot
pending DELETE  -> 不展示
无 pending      -> 展示 remoteSnapshot；remote 也为空则不存在
```

客户端不保存远端 tombstone。收到明确 tombstone 后删除 `remoteSnapshot`；若该行也没有 pending，则物理删除整行。

## 5. localRevision 与 R→U

请求 capture 会记录本次上传的 `localRevision`。网络请求期间用户可以继续编辑，形成更高 revision 的 U：

```text
R(revision=1) 正在请求
  -> 用户编辑形成 U(revision=2)
  -> R 响应到达
```

应用 R 响应时先更新 `remoteSnapshot`，然后比较当前 revision：

- 当前 revision 仍等于 1：响应证据满足合同后可清 pending；
- 当前 revision 已是 2：无论 R 成功、被服务端合并还是请求结果不确定，都保留 U；
- 下一次同步再上传 U，使两端收敛。

CREATE 的 R 成功后可能短暂形成 `remote version=1 + pending U version=0`。下一次 capture 只在 wire 上把 U 投影为 remote version 1，pending 本身仍保持原始本地快照。

## 6. 原子批次

需要父子闭包的命令在相关行写入同一 `localBatchId`。planner 只会把完整批次放入 `atomicBatches`，不会拆成普通 mutation。

服务端 batch 响应由 common applier 一次计算出三类完整账号状态；Room 使用一个 write transaction 调用 `replaceAccountState`：

1. 删除账号现有三类状态行；
2. 写入 applier 输出的完整集合；
3. 保留账号的 `local_revision_counter`。

事务失败时不发布半批结果。

## 7. 资源版本与 DELETE

- `version` 是服务端资源属性，位于资源内部；客户端不自行递增。
- DELETE 请求只包含 typed identity 和 `localModifiedAt`，不上传 version。
- tombstone 不含 version。
- DELETE 优先级最高；明确 tombstone 会清除 remote 侧，并按 uploaded revision 决定是否清 pending。
- 同 identity 一旦成为 pending DELETE，不再转回 UPSERT；重新创建使用新 identity。

## 8. 数据库创建与升级

Schedule v2 未部署，因此当前代码不注册 migration，也不使用 destructive fallback。Android、Desktop、iOS builder 都直接创建当前 schema。

旧开发数据库由开发者显式清库或重装。禁止从旧 graph、outbox、tombstone、cursor 或 semantic sidecar 猜测新 `remoteSnapshot` / `pendingSnapshot`。

schema export 只保留当前四表版本，用于审查实际列和 converter 结果，不表示存在旧版本升级路径。

## 9. 主要实现入口

- `ScheduleV2RoomEntities.kt`：四表 Entity 与字段注释；
- `ScheduleV2RoomConverters.kt`：typed snapshot JSON converter；
- `ScheduleV2RoomDao.kt`：账号级查询、替换与 revision 分配；
- `ScheduleV2RoomStateStore.kt`：完整状态事务；
- `ScheduleV2RoomStateMapper.kt`：Room 与 common 同步状态互转；
- `RoomScheduleRepository.kt`：local-first 命令、日常接口和完整 Sync 协调。

## 10. 验证重点

- remote-only、pending CREATE/PATCH/DELETE 的投影；
- R→U compare-and-clear；
- Category nullable color 和 recurrence nullable data 的 JSON 往返；
- Schedule 与 Override 原子批次一次落库；
- tombstone 删除 remote row 且下一轮不 confirmed；
- RequestInvalid、timeout、5xx、HTTP 200 + REJECTED 都保留未确认 pending；
- 账号切换后旧 delegate 的迟到快照和日历事件不能污染新账号。
