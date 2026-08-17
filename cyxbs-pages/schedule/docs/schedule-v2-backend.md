# Schedule v2 后端资源与同步服务设计

> [!CAUTION]
> **状态：已取消的旧后端方案，仅供历史追溯，禁止继续执行。** 本文中的 cursor/event/receipt、七表、迁移、rollout、旧路由与阶段计划均不是当前实现要求，不得据此开发、部署，也不得拆分为新的 TODO。
>
> 当前后端事实源仅为独立后端仓库 `magipoke-todo` 的 `guoxiangrui/schedule` 分支及其 `SCHEDULE_BACKEND_DESIGN.md`。跨端合同以三篇 canonical 文档为准：[AtomicField 与原子批次](schedule-v2-field-group-sync-design.md)、[重复日程单次覆盖能力矩阵](schedule-v2-recurrence-override-capability-matrix.md)、[资源版本同步流程](schedule-v2-resource-version-sync-flow.md)。后续跨仓交接统一从已经建立的 [Schedule v2 Codex 交接](schedule-v2-codex-handoff.md) 进入；不得回退到本文旧方案补写实施计划。
>
> 下文保留原始历史背景，未逐项改写，也不代表当前待办、验收门槛或上线顺序。

## 0. ARCHIVE：旧实现快照

> [!WARNING]
> 本节及后续正文只记录已取消的七表/change/cursor/semantic source 历史，不描述 `guoxiangrui/schedule` 当前 typed 三表 working tree，也不得作为新的实现或迁移入口。

旧后端分支曾落地可独立审查的 GORM 物理模型与启动接入：

- `dao/schedule_v2_models.go` 定义七个 `schedule_v2_*` ORM model，并用 `TableName()` 固定表名；`dao/init.go` 先保持 Todo v1 原有 `AutoMigrate(&ToDoORM{})`，再显式注册全部七个 v2 model；
- v2 新表统一设置 `InnoDB`、`utf8mb4` 与 binary collation；owner-first 复合主键、账号内 change sequence 唯一索引、Schedule/category 与 exception/Schedule 的 `NO ACTION` 复合外键均由 GORM tags 表达；
- change/tombstone 的 canonical `resource_key` 使用 `VARBINARY(768)`，避免 utf8mb4 复合索引超过 InnoDB 3072-byte 上限；change 资源索引显式为 `(owner_id, resource_type, resource_key, seq)`；
- 两个仅用于生成外键的 association 标记为只读，禁止普通 `Create/Save` 自动写父资源；active category、active parent、timing union、RRULE、patch 三态及 sequence/CAS 等跨字段合同仍必须由后续 service transaction validator fail-closed；
- `MySQLInit` 会传播连接、锁等待设置和 `AutoMigrate` 错误，启动入口在注册 RPC/路由前 fail-fast，不允许以部分 schema 继续服务；
- Schedule v2 尚未上线，只创建全新的 `schedule_v2_*` 表，不迁移、修改或复用 Todo v1/旧 Schedule 数据。`AutoMigrate` 以干净的新表命名空间为前提，不提供显式版本、回滚或既存错误 schema fingerprint；开发期若存在不兼容测试表应清理测试数据库，不建立兼容 ALTER 或双写；
- 当前只完成 GORM schema parser 与 MySQL dialector DryRun DDL contracts：验证七表注册顺序、复合 PK/FK、索引、table options、JSON nullability、只读 association 和 `NO ACTION`。DryRun 不连接数据库，不能替代部署准备阶段的真实 MySQL `AutoMigrate` 与 `information_schema` 验收；
- backend integration `e367445` 新增 DAO 包内、未接线的单 owner retention compactor source capability：调用方显式指定 owner、保留数量和单次推进上限，事务内以 owner 同步状态行 `FOR UPDATE` 串行化，最多推进 10,000 个 sequence，删除 `schedule_v2_changes` 的 `[previousFloor, newFloor)` 半开区间并更新 `min_retained_seq`。它没有 owner enumeration、policy、invoker、scheduler/cron、route、config、metrics、backoff 或部署入口，也未经过真实 MySQL/InnoDB 验收。

`9345ec4` 的最小 mutation API 是旧 source 事实，不再是 production router 行为：W06 Wave 0 已移除静态 `POST /v2/schedule-mutations` 注册，精确旧 POST 在 TokenVerify、handler、service 和 store 之前 route-level `404` 且零副作用。其 `DecodeStrict`/`RequestHash`、owner/device/mutation receipt、Schedule/category 单资源 CAS 与 occurrence `occurrence_exception_unsupported` 仅保留为后续 semantic v2 设计/迁移资料；不得从这些历史 source 推断旧 endpoint 已可访问。

`499fec3` bootstrap v1、`717d37a` runtime config 与 `8738068` old sync 都是被 W06 safety cut 取代的 source/history：production router 不再注册旧 bootstrap/sync，精确 `POST /v2/schedules:bootstrap` 与 `POST /v2/schedules:sync` 在认证、handler、service 和 store 前 route-level `404`、零副作用。其 HMAC cursor、high-water、AES-GCM cursor 与 `410/reset_required` 细节不得被描述为当前 production endpoint。Android/iOS/Desktop 的显式 `RequestSync` source consumer 也不证明旧 endpoint 可达。W5 的 semantic `schedule-commands`、authoritative bootstrap/sync、store/service、cursor/reset 与 disabled capability 已 source/runtime 闭合，但仍 disabled/not deployed；production remote path 只待客户端 factory/repository/capability integration、W7 native migration、真实 gateway/interoperability、deployment/enablement 与显式 cutover，而非等待后端 authoritative bootstrap/sync 再实现。

`go`/`gofmt` 未加入 shell `PATH`，但本轮使用 `/Users/guoxiangrui/sdk/go1.26.4/bin/go` 完成 `./schedulev2wire` 与 `./service -run 'TestScheduleV2Bootstrap'` 两组聚焦测试；不得据此声称已运行完整 service package 或真实数据库验证。未连接 MySQL、未启动完整服务、未执行真实建表或部署环境 migration/rollback 验证。

## 1. 目标与服务边界

后端负责：

- 持久化账号下 Schedule、occurrence exception 和 category 当前资源；
- 使用 revision CAS 处理资源并发；
- 使用 mutationId 实现交付未知后的幂等重放；
- 提供 bootstrap 和固定高水位 change feed；
- 保存删除 tombstone/change snapshot；
- 支撑多设备、Android 双向日历和 Web remote-required repository 收敛。

后端不负责：

- 修改旧 Todo 表或旧接口；
- 接收 Calendar Provider/EventKit 原始对象；
- 用设备时间戳做 LWW；
- 生成或信任客户端提交的 owner；
- 把系统日历 event ID 作为业务 ID；
- 保存 Android/iOS 平台私有 CalendarLink；
- 近似转换无法无损表达的 RRULE 或秒级业务时间。

## 2. 身份与授权

必须分离：

- `owner`：服务端从认证 context 获取的资源所有者；
- `accountId`：客户端本地分区使用的账号键；
- `deviceId`：客户端持久化设备命名空间；
- `CalendarExportScope`：平台投影命名空间；
- `scheduleId`：客户端生成的 canonical UUIDv7；
- `mutationId`：单次持久化 mutation 的幂等身份。

规则：

- owner 不出现在可覆盖的 body/query 字段中；
- URL 中的 scheduleId/recurrenceKey 必须在认证 owner 下查找；
- scope、学号、UUID 和 URI 都不是授权凭据；
- 日历 scope 无需进入后端 Schedule 主键；如保存仅作客户端配置，不参与资源所有权。

## 3. Wire grammar

不能只声明时间字段为 `String`，必须冻结 canonical grammar：

| 语义 | Wire grammar | 校验 |
|---|---|---|
| `Date` | `yyyy-MM-dd` | 有效公历日期、领域允许年份 |
| `MinuteTimeDate` | `yyyy-MM-dd HH:mm` | 严格分钟精度；空格是 v1 canonical 分隔符，拒绝 `T`、秒、offset 与 zone |
| IANA 时区 | `Area/Location` | 必须能由支持的 tzdb 解析 |
| 同步时间戳 | RFC 3339 Instant | 可携带秒/亚秒；不属于业务墙上时间 |
| UUIDv7 | canonical 小写带连字符 | 拒绝其他文本别名 |
| RRULE | 结构化 DTO | 集合规范化；COUNT/UNTIL 互斥 |

领域 API 的 UNTIL 始终传 `Date`；Android/iOS RFC 5545 的 UTC `23:59:59` 不进入后端领域协议。

DTO、Record 和数据库字段统一使用 `originalDateTime`，不再新增 `originalLocalDateTime` 命名。由于尚未上线，可直接更新开发期 schema 和 fixture，不建立旧字段 reader。

### 3.1 Mutation canonical envelope v1

共享 mutation codec 进一步冻结以下 presence 与 hash 合同：

- `baseRevision` 字段必须显式出现：CREATE 只能编码 JSON `null`；PATCH/DELETE 必须编码当前已知的非负 revision，`0` 是尚未被服务端接受资源的合法基线；字段省略、负数或错误 operation 组合一律拒绝；
- Schedule payload 的 `description` 是必需且非 null 的字符串；空字符串表示无描述。后端物理列当前保持 nullable 只用于 schema/开发期边界，v1 service validator 不得接受或写入 SQL `NULL`，也不得把 SQL nullability 扩大为 wire 取值域；
- canonical JSON 对每一层 object 的 key 按 ASCII 升序排列；RRULE 的 `byWeekDays/byMonthDays/byMonths` 是集合语义，编码前按数值升序规范化并拒绝重复项，其他数组保持协议定义的业务顺序；unknown field、trailing JSON 与任意嵌套层级 duplicate key 必须在 canonicalize/hash 前拒绝；
- Schedule 与 occurrence payload 的 `createdAt/updatedAt` 必须使用 `Instant.toString()` 对应的唯一 UTC RFC 3339 文本，并满足 `updatedAt >= createdAt`；offset alias、非法时间和逆序时间不得进入 request hash；
- DELETE 虽然携带 JSON `null` payload，仍必须按 `resourceType` 独立校验 canonical `resourceId`；Schedule 使用 UUIDv7，occurrence 使用完整 scheduleId、原始分钟时间、时区/全天标志的稳定 identity，不能因缺少 payload 跳过校验；
- Schedule payload 继续执行完整领域不变量：RRULE selector 范围、WEEKLY/BYMONTHDAY 排斥、COUNT 正数、重复 Schedule 必须 `PENDING`、Unscheduled 不得携带 recurrence/reminder；occurrence timing `REPLACE` 禁止 Unscheduled；
- request hash 输入精确为 UTF-8 `schedule-v2-mutation-hash-v1 + NUL + canonicalJson`，结果为小写十六进制 SHA-256；认证 context 派生的 owner 不进入 body、canonical JSON 或 hash，owner 隔离由幂等存储主键承担；
- 共享 golden vector 位于后端 `schedulev2wire/testdata/mutation_contract_vectors.json`，客户端 `ScheduleMutationWireContractTest` 必须对同一 canonical bytes/hash 逐字节断言。任一端变更 vector 都必须同步另一端与本文，不能单边生成新的 hash grammar。

客户端领域 `MinuteTimeDate.toString()`、Record 与既有 SQL canonical 值均使用空格分隔，因此 v1 延续 `yyyy-MM-dd HH:mm`。测试中曾出现的 `yyyy-MM-dd'T'HH:mm` 只是未校验 fixture，不是已冻结协议；严格 codec 必须拒绝该 alias，而不是反向修改领域、Room 与现有文档事实。

## 4. recurrence key

occurrence exception 由以下值共同定位：

```text
scheduleId
+ originalDateTime
+ timeZoneId（Timed/Deadline）
+ allDay
```

API `{recurrenceKey}` 不能直接拼接带空格和分隔符的内部 resourceId。必须定义独立 canonical URL-safe 编码，例如版本化 base64url 结构或多个经过严格编码的 path/query 参数，并满足：

- 唯一 canonical 文本；
- decode 后完整领域校验；
- 非 canonical 编码拒绝；
- allDay 与 timeZoneId 互斥规则；
- 能证明该 identity 由目标 Schedule 的 RRULE 生成；
- 不能仅凭 recurrenceKey 绕过 owner 和 scheduleId 校验。

编码细节在 API 初稿中冻结，并与客户端共享 contract test vectors。

## 5. Patch 三态

后端协议冻结前必须解决 occurrence patch 的三态：

```text
INHERIT  不覆盖，继续继承系列
CLEAR    显式清空
REPLACE  覆盖为值
```

至少覆盖：

- category；
- reminders；
- 未来允许清空的可空字段。

不能让 JSON `null` 同时表示“继承”和“清空”。协议已冻结为紧凑三态：patch 对象内字段省略表示 `INHERIT`，字段值为 JSON `null` 表示 `CLEAR`，字段有非 JSON-null canonical value 表示 `REPLACE`。数据库中的 `patch_json` 使用 SQL `NULL` 表示 `patch = null`，使用 JSON `{}` 表示显式全 `INHERIT`；二者不得合并。Go DTO 必须保留字段 presence，不能用普通 nullable 字段猜测语义。对于 reminders，JSON `null` 是 `CLEAR`，JSON `[]` 是合法的 `REPLACE(emptyList())`；两者在 request hash、`patch_json`、change snapshot 和幂等重放结果中始终保持可区分。

## 6. 核心表

以下是七个 GORM model 必须一次性表达的服务端物理合同。当前固定使用 7 张全新表；客户端 Room3 为本地查询和事务采用规范化 selector/reminder/patch 列，不要求与后端物理镜像。

公共约定：

- 所有表使用 `InnoDB`、`utf8mb4`；owner、UUID、枚举、canonical 时间和 resource key 使用区分大小写的 binary collation；
- `owner_id` 使用认证 context 中的 `redid`，不得从请求体或 URL 接收；
- UUID 使用 canonical 小写 `CHAR(36)`；`Date` 使用 `CHAR(10)`；`MinuteTimeDate` 使用 `CHAR(16)`；同步时间使用 UTC `DATETIME(6)`；
- `revision` 和 sequence 使用非负整数；创建资源的初始 revision 固定为 `1`。category `sort_order` 保留客户端有符号 `Int` 全范围，不能擅自收窄为非负；
- JSON 列只保存经过严格 codec 校验并 canonicalize 的完整领域值。MySQL 的 `JSON_VALID` 不能替代 Go 层的 duplicate-key、未知字段、枚举、三态和跨字段校验；
- 当前资源行和 tombstone/idempotency receipt 都不物理清理；内部 retention compactor 也只删除已过期 `schedule_v2_changes`。资源 ID 一经 owner 接受后永久禁止复用，不能仅靠 change 已过期判断可复用；未来若要清理 tombstone/receipt，必须先另行冻结覆盖所有离线重试和 accepted receipt 窗口的不可复用 registry；
- 七个 model 必须全部显式传给 GORM；`AutoMigrate` 不会自动发现未注册 struct。当前仅允许它创建尚未上线的全新 `schedule_v2_*` 表，不承担旧数据迁移、版本回滚或既存不兼容结构修复；部署准备阶段必须在隔离 MySQL 执行后用 `information_schema` 核验列、主键、索引、外键 action、engine 与 collation；
- 当前只做 GORM schema parser 与 MySQL dialector DryRun contracts；它能验证生成 DDL 的结构和索引预算，但不能证明真实 MySQL 执行、锁、权限或线上启动行为，因而不得据此声称数据库已建表。

### 6.1 `schedule_v2_categories`

```text
owner_id
category_id UUID
revision
last_change_seq
name
color nullable
sort_order
created_at
updated_at
deleted_at nullable
PK(owner_id, category_id)
UNIQUE(owner_id, last_change_seq)
```

`color` 必须可空，以无损表达客户端 `color = null`。分类是软删除资源；删除事务必须先按第 8 节处理所有 active Schedule 和 exception patch 引用。

### 6.2 `schedule_v2_schedules`

```text
owner_id
schedule_id UUID
revision
last_change_seq
title
description nullable
category_id nullable
timing_type TIMED|DEADLINE|ALL_DAY|UNSCHEDULED
start_local nullable
duration_minutes nullable
due_local nullable
all_day_start nullable
duration_days nullable
time_zone nullable
recurrence_json nullable
reminders_json
completion PENDING|COMPLETED
created_at
updated_at
deleted_at nullable
PK(owner_id, schedule_id)
UNIQUE(owner_id, last_change_seq)
FK(owner_id, category_id) -> schedule_v2_categories NO ACTION
```

约束：

- Timed 仅使用 `start_local + duration_minutes + time_zone`；Deadline 仅使用 `due_local + time_zone`；AllDay 仅使用 `all_day_start + duration_days`；Unscheduled 不携带任何 timing 列；
- `duration_minutes` 与 `duration_days` 必须落在正 `Int` 范围，reminder offset 必须落在非负 `Int` 范围，不能只依赖 MySQL unsigned 上限；
- `Unscheduled + recurrence` 拒绝；Unscheduled 的 `reminders_json` 必须是 canonical `[]`，Schedule 与 occurrence effective timing 校验都拒绝未排期时间上的任何非空 reminder list；重复 Schedule 的 completion 必须为 `PENDING`；
- `recurrence_json` 为 SQL `NULL` 时表示不重复，否则保存完整受限 RRULE canonical JSON；服务端 validator 必须与客户端 `ScheduleValidator` 和共享 contract vectors 同义：interval 至少为 1，BYMONTHDAY 只能是 `-31..-1` 或 `1..31`，BYMONTH 只能是 `1..12`，COUNT 为正数且与 UNTIL 互斥，WEEKLY 禁止 BYMONTHDAY；集合按 canonical 顺序编码，子集外组合直接拒绝而非静默忽略；
- `reminders_json` 始终保存完整、按 reminder identity 稳定排序的 canonical list，空列表写 `[]`；reminder identity 必须唯一，offset 落在非负 `Int` 范围，channel 只接受当前协议已支持值；
- 复合 FK 只能证明分类行存在，不能证明其 active。所有创建、更新、split 和分类重分配事务必须锁定同 owner 的 category 行并要求 `deleted_at IS NULL`。

### 6.3 `schedule_v2_occurrence_exceptions`

```text
owner_id
schedule_id
recurrence_original_datetime MinuteTimeDate
recurrence_time_zone
recurrence_all_day
revision
last_change_seq
status ACTIVE|COMPLETED|CANCELLED
patch_json nullable
created_at
updated_at
deleted_at nullable
PK(owner_id, schedule_id, recurrence_original_datetime,
   recurrence_time_zone, recurrence_all_day)
UNIQUE(owner_id, last_change_seq)
FK(owner_id, schedule_id) -> schedule_v2_schedules NO ACTION
```

约束：

- 全天 identity 的 `recurrence_original_datetime` 仍是 16 字符午夜 `MinuteTimeDate`，例如 `2026-07-16 00:00`，不得缩成 `Date`；其 `recurrence_time_zone` 使用空字符串。Timed/Deadline identity 必须使用 canonical IANA zone，且 `recurrence_all_day = 0`；
- recurrence identity 必须由 active 父 Schedule 的当前 RRULE 生成。FK 不能证明父行 active，因此 exception mutation、delete 和 split 必须先锁父行并要求 `deleted_at IS NULL`；
- `patch_json` 为 SQL `NULL` 表示 `patch = null`，JSON `{}` 表示显式全 `INHERIT`；对象字段省略/JSON `null`/value 分别表示 `INHERIT/CLEAR/REPLACE`；
- `patch_json` 是 exception patch 的完整核心事实，包含完整 canonical reminder list 和原子 timing patch；本阶段不另建 exception reminder 表；
- occurrence timing 的 `REPLACE` 只允许 Timed、Deadline 或 AllDay，禁止替换为 Unscheduled；kind、zone 和全天午夜规则必须与 recurrence identity/父系列兼容；
- 父 Schedule 更新 RRULE、series timing kind、anchor 或 zone 时，必须按 recurrence identity 稳定顺序锁定全部未软删除（`deleted_at IS NULL`）的 exception，无论其 status 是 `ACTIVE`、`COMPLETED` 还是 `CANCELLED`，并用变更后的父系列重新验证每个 identity。只要存在不再生成或 kind/zone 不兼容的 child，就拒绝普通父更新；需要改变这些 identity 的操作必须使用显式 split/删除语义，不得遗留当前请求无法再写入的未软删除 child；
- 删除父 Schedule 时，必须在同一 transaction 先按稳定顺序软删除全部 `deleted_at IS NULL` 的 exception，无论其 status：每个 child revision 加一、写独立 tombstone 和 immutable DELETE change，最后再软删除父 Schedule 并写父 DELETE。这样客户端即使按页应用 change，也只会先移除 child，不会暂时产生未软删除 orphan。

### 6.4 `schedule_v2_sync_state`

```text
owner_id PK
next_seq
min_retained_seq
updated_at
```

账号级 sequence 分配必须串行且位于资源写 transaction 内。`next_seq` 是下一个待分配值；`min_retained_seq` 是 change retention 后仍可增量拉取的最小 sequence。backend integration `e367445` 已提供 DAO 包内、未接线的显式 compactor：它锁定精确 owner 的本行后，以调用方给定且不超过 10,000 的单次推进上限计算新 floor；当前没有 policy、owner enumeration、invoker 或调度入口，因此不会自动执行。

首次 mutation 不能对不存在的行执行无效的 `SELECT ... FOR UPDATE`。写事务必须先以 `INSERT ... ON DUPLICATE KEY UPDATE owner_id = VALUES(owner_id)` 原子建立 owner 行，再读取并锁定该行分配 sequence；同 owner 的并发首次写必须等待同一行锁。只读 bootstrap 遇到不存在的 owner 行时不落库，定义为 `next_seq = 1`、`min_retained_seq = 1`、固定高水位 `until = 0` 的空账号；随后 mutation 的 seq 1 由下一轮 change feed 正常返回。

### 6.5 `schedule_v2_changes`

```text
owner_id
seq
resource_type SCHEDULE|OCCURRENCE_EXCEPTION|CATEGORY
resource_key
revision
operation UPSERT|DELETE
resource_data LONGBLOB NOT NULL
snapshot_hash SHA-256(resource_data)
occurred_at
PK(owner_id, seq)
INDEX(owner_id, resource_type, resource_key, seq)
```

`resource_key` 是按 resource type 定义的唯一 canonical binary 文本；exception 使用完整且版本化的 recurrence identity 编码，不能只使用 scheduleId。

`resource_data` 以 `NOT NULL LONGBLOB` 原样保存事件发生时的 exact immutable canonical bytes：Schedule snapshot 必须包含完整 `recurrence_json` 和稳定排序的 `reminders_json`；exception snapshot 必须保留 `patch_json` 的 SQL NULL 与 `{}` 差异以及完整 reminder/timing patch；DELETE 保存私有 internal tombstone snapshot bytes。`snapshot_hash` 是这些实际存储字节的 SHA-256，禁止经 MySQL JSON 或其他重序列化归一化后再计算。拉取时不能再读取当前资源行，否则分页期间后续修改会提前污染当前 change；私有 DELETE snapshot 不建立任何 public bootstrap/change-feed wire contract。

### 6.6 `schedule_v2_tombstones`

```text
owner_id
resource_type
resource_key
deleted_revision
delete_seq
deleted_at
PK(owner_id, resource_type, resource_key)
UNIQUE(owner_id, delete_seq)
```

该表不建立业务 FK，删除当前资源后仍保留。创建资源前必须同时检查当前行（包括软删除行）和 tombstone；任一存在都拒绝 identity 复用。

### 6.7 `schedule_v2_idempotency`

```text
owner_id
device_id
mutation_id UUID
request_hash
first_result_seq
last_result_seq
result_json
created_at
PK(owner_id, device_id, mutation_id)
```

同幂等键、同 request hash 返回原 canonical result；同幂等键、不同 hash 必须拒绝，不能重复执行。使用首尾 sequence 而不是单个 `result_seq`，以无损表达 split 等一次 mutation 产生多个连续 change 的结果；`result_json` 保存首次 Accepted 的完整响应。

## 7. API

当前 semantic v2 仅定义以下三条 POST endpoint：

```text
POST /v2/schedule-commands
POST /v2/schedules:authoritative-bootstrap
POST /v2/schedules:authoritative-sync
```

W5 已完成这些 route 与 authoritative store/service、cursor/reset、disabled capability 的 source/runtime closure；它们仍 **BLOCKED/DISABLED**、未部署/未启用。早期资源风格接口及 `GET /v2/schedules/bootstrap`、`GET /v2/schedules/sync`、`POST /v2/schedule-mutations` 均为 rejected history，不是当前 contract；W06 Wave 0 已移除旧 POST route，精确旧 POST 在 TokenVerify、service 和 store 之前返回 route-level `404`，不产生认证或持久化副作用。既有 Android/iOS/Desktop `RequestSync` source contract 仍不能据此访问 production endpoint；Web production 已是无持久化、无 I/O 的 unavailable façade。

### 7.1 W06 remote semantic protocol 目标（backend pure wire 已实现，production 未启用）

冻结的目标路由为 `POST /v2/schedule-commands`、`POST /v2/schedules:authoritative-bootstrap` 与 `POST /v2/schedules:authoritative-sync`。前者请求顶层只允许 `schemaVersion`、`candidateId`、`baseCursor` 与 `command`；`RequestSync` 不属于写 union，只通过 authoritative bootstrap 表达 `requiredCandidateId = null`。

`command` 是闭合的 11 个 union：`CREATE_SCHEDULE`、`UPDATE_SCHEDULE`、`DELETE_SCHEDULE`、`COMPLETE_NON_REPEATING`、`UPSERT_OCCURRENCE_EXCEPTION`、`DELETE_OCCURRENCE_EXCEPTION`、`SPLIT_SERIES`、`DELETE_THIS_AND_FOLLOWING`、`CREATE_CATEGORY`、`UPDATE_CATEGORY`、`DELETE_CATEGORY`。#166 已在 backend `schedulev2wire` 完成各 command payload、共享 DTO、严格 decoder、canonical encoder/hash、receipt/error、authoritative bootstrap/sync envelope，以及 `semantic_command_v1_vectors.json` 和 `authoritative_read_v2_vectors.json` 两份 backend fixture corpus；#167 已在客户端 commonMain 完成未接线的 Kotlin strict mirror，并通过两份不可变 backend fixture corpus 逐字节验证 canonical output、SHA-256 request hash 与隔离单缺陷错误分类。多缺陷输入仍只要求严格拒绝，所选 category/path 不属于跨语言合同。因此 W1 pure-wire contract/goldens 已关闭；W5 已将 route、DAO/service、authoritative store、cursor/reset 与 disabled capability wiring 闭合，但这些 artifacts 仍不代表客户端 gateway/capability integration、部署/enablement、MySQL/network interoperability 或 production cutover 已完成。

权威 universe 固定为 `CATEGORY`、`SCHEDULE`、`OCCURRENCE_EXCEPTION`。一个 changed command 只能生成一个服务端拥有的 `commitSequence/group`，delta 不得切开 group。公开 command receipt 不泄露 group、commitSequence 或 high-water；服务端内部的 `committedAuthoritativeHighWater` 只能被冻结 bootstrap high-water 覆盖，并回显精确 `confirmedCandidateId`。

W2 已完成 Schedule-owned storage isolation：`SettingsScheduleLocalStore` 与 `createSettingsScheduleRepositoryFactory` 仅在 noWebMain，Web 没有 Schedule-owned durable persistence 或 Web-visible durable adapter；既有 account/profile/tourist Settings 不在此范围。future Web 页面/文档/repository 生命周期内至多保存 active `candidateId`，以及一个原子 confirmed 单元（opaque cursor + authoritative graph/cache），重建即整体丢弃。W2 不接线这份 semantic state，当前 W0 unavailable façade 仍无网络 I/O。

W3 authoritative reads/genesis/grouped sync 与 W4 recurrence proof/all 11 planners 的 pure source/proof 已完成并集成：backend dev/test HEAD 为 `27469b4e0139c435673a16388972dd2dda66320a`，Android integration HEAD 为 `b7b418efbf1171b791c4f15f3cd96c9592dc223f`。W4 backend 现有 immutable authoritative graph validation、七个 direct planners、occurrence exception planners 与 split/delete-following planners、11-branch dispatcher、closed server-values 与精确 canonical ordered effects；Kotlin 已对齐 command projection、recurrence membership proof、closed semantic-plan result/effect/server-values、strict corpus/graph validation、sparse patch presence 及 defensive lifetime immutability。recurrence corpus SHA-256 为 `472e6cbb9218786be451d2034d3492e1a9c4bb7ad183c588195b8c92cf264973`；17-vector semantic-plan corpus（全 11 command，加一个 decoder-only `RequestSync`）SHA-256 为 `39a02726f9703b867eda507b49cd1c7563e0371abd95a3087abdeed29aea6849`。backend G0 `e0011068e9592cc3483c1604278260a9f795f3cb`→`4b505f49a68fc77d0247018f442a63cf03cc02e9`、G1 `d8f4f687a6451850948c25f1f9f794633d9a98fa`→`90a5f04460e2809b008d1691f4371b14f3bae368`、G2 `0918b4c88b393736cb32b9f845432b967a2f7172`→`7d582577955312028b97b068de76b4e42134549d`、G3 `7b255d8d15d2afcd6cb89916f521c6900917359b`→`18a14ac82172b44fae64e2fb51cb5884a01df81e`、G4 `6f990d2ecfa8ff4020b1f12b2f6cfeb2189fbddb`→`689d65bd7851ae6a8a8467c3811ddea5ede39d1f`、G5 `a546ceb9ebbef023d64d11f3d982aebe533ceda5`→`27469b4e0139c435673a16388972dd2dda66320a`；历史完整跨端映射曾由旧总路线图 W06 维护；该子路线已归档，当前依赖和剩余工作统一见 [Codex 交接总文档的 master DAG](schedule-v2-codex-handoff.md#9-剩余-master-dag)。这些 W3/W4 proof artifacts 本身未接线，`RequestSync` 也始终只是 local-only/decoder-only，不是 remote semantic dispatcher branch；W5 已完成 route、authoritative persistence、service/startup wiring 与 disabled capability 的 source/runtime closure，但不得由此宣称客户端 gateway/migration/capability cutover、网络/数据库互操作、部署或启用。

W6 shared-client K0/K0b/K1/K2/K3 已在 common/Desktop 完成 source/tests：opaque cursor 保持纯 wire，bootstrap/delta authoritative graph page 原子归约并受 private revision fences 保护；accepted 仅在 candidate-bound settled authoritative snapshot 后发布。已知 changed acceptance 后，ordinary cancellation 以 `NonCancellable` 精确一次尝试/发布 `AcceptedButUnconfirmed` cleanup，随后关闭并重抛原 cancellation；exact-session account replacement/result-fence rejection 则关闭至 `ClosedNeedsReset` 并重抛 `ResultFenceRejectedCancellationException`，不发布 `AcceptedButUnconfirmed`；两路径均不 retry confirmation 或 replay command。只有非 cancellation confirmation failure 返回 `Failure`。该能力未接入 repository/factory/capability 或 native runtime；W5 backend route/store/runtime 已闭合，但 production cutover 仍未完成。

future W8 只可在已完成 W6 source、W7 native migration/capability，以及 W7 后、W8 前的独立 required deployment/enablement gate 后，将该确认语义接入 Web real remote-required path；仍禁止 Settings、Room、SQL、IndexedDB、localStorage、sessionStorage、durable outbox/device/candidate/cursor、retry/replay、service worker 或后台 continuation。

协议继续只支持 RRULE，不增加 RDATE/EXDATE；genesis high-water 为 0、next 为 1。目标错误分型为 `400 invalid_cursor`、`409 stale_base`、`409 candidate_id_reused`、`410 reset_required`、`503 command_disabled`、`503 authoritative_read_disabled`。W3/W4 已将相应 pure read/planner grammar 证明为 source contract，W5 已完成 route、authoritative persistence 与服务闭环的 source/runtime 实现；仍不复活或兼容旧路由，且未部署/未启用。

### 7.2 W08 opaque cursor 合同（backend pure-wire 已实现，production 未启用）

W08 将 `baseCursor`、authoritative bootstrap `cursor` 与 authoritative sync request/response `cursor` 从公开 `{after, highWater}` DTO 改为受服务端签名的 opaque string。严格 wire decoder 只验证 nonblank UTF-8 字符串和 `2048` bytes 上限，绝不尝试解析、验签或推断 sequence；这使 JSON grammar 不能成为 cryptographic oracle。

服务端使用 `SemanticV2CursorState { after, fixedHighWater }`，以每个独立部署 key 的 HMAC-SHA-256 签发 owner-bound token。payload 固定为 `magipoke.schedule.semantic-v2.cursor-state` domain、version、UTF-8 owner byte length/bytes、两个无符号 big-endian `int64`；MAC 输入独立以 `magipoke.schedule.semantic-v2.cursor-mac + 0x00 + payload` domain 分隔。wire format 是 canonical raw unpadded base64url `payload.mac`，拒绝 padding、非 canonical encoding、短/全零 key、blank/oversized owner、越界 state 与超过 `2048` bytes 的 token。MAC failure 与 owner mismatch 必须对外返回同一 verification error，不能披露“token 有效但绑定其他 owner”。

`VerifySemanticV2Cursor` 只恢复经过验证的 server-only state。bootstrap service 还必须调用 `ValidateSemanticV2BootstrapCursorState`，要求 `after == fixedHighWater == response highWater`；sync service 必须调用 `ValidateSemanticV2CursorPage`，要求 continuation 固定 window 不漂移、组 sequence 连续、output 紧跟末组且不越过 response high-water。W08 不启用 route/store/gateway/capability 或 production endpoint，也不把这些 service-side semantic checks 下放到 wire decoder。

## 8. 写事务与 CAS

固定顺序：

```text
从认证 context 获取 owner
→ 严格解析、限制大小并校验 canonical request
→ 原子建立 owner 的 schedule_v2_sync_state 并锁定该行
→ 查询并锁定 idempotency receipt
→ 锁目标当前资源；exception/split 同时锁 active 父 Schedule
→ 父系列结构更新/删除/split 按 recurrence identity 稳定顺序锁全部 `deleted_at IS NULL` 的 child exceptions（不按 status 过滤）
→ 锁所有被引用的 category 并要求 deleted_at IS NULL
→ 校验 baseRevision、父子生成关系、资源未删除和 identity 从未被接受/复用
→ 分配本 mutation 使用的连续 seq 区间
→ 更新当前资源；分类删除和父子资源变化按同一事务处理全部引用
→ 按 seq 顺序插入完整 immutable change snapshot 与必要 tombstone
→ 插入包含完整结果及首尾 seq 的 canonical idempotency receipt
→ commit
```

分类删除/重分配必须区分三态：

- 当前 Schedule 的直接 `category_id` 根据请求原子清空或替换为另一 active category；
- exception `patch_json` 中清除分类必须编码为 `category: null`（`CLEAR`），不能删除该字段后退化为 `INHERIT`；
- 重分配必须编码为 `category: <activeCategoryId>`（`REPLACE`）；
- `patch_json` 为 SQL `NULL`、JSON `{}` 和包含 category 三态的对象必须分别保持其原语义。

任何业务写失败都必须 rollback，不能先产生 sequence 空洞或 accepted receipt。网络调用不得进入数据库 transaction。

冲突返回稳定错误，例如：

```text
409 revision_conflict
+ 当前服务端 canonical resource
+ 当前 revision
```

不提供无条件 force 覆盖。客户端通过 common merge/conflict 流程生成新的 mutation。

DeliveryUnknown 后必须使用完全相同的 mutationId、deviceId 和 payload 重试。

## 9. Bootstrap 与 change feed

### 9.1 Bootstrap

返回账号当前 canonical 资源、必要 tombstone/同步元数据和从该快照继续拉取的 cursor。生成 snapshot 与高水位必须具备一致性，不能在资源扫描期间混入无法排序的新写入。

### 9.2 Opaque cursor

逻辑包含：

```text
version
owner binding/hash
after seq
fixed until seq
signature
```

客户端不得自行修改或推断 sequence。分页查询：

```sql
seq > after AND seq <= until
ORDER BY seq
LIMIT ?
```

固定高水位避免分页期间新写入导致漏项或无限翻页。完成当前窗口后，服务端签发下一轮 cursor。

### 9.3 Retention 与 cursor expired

backend integration `e367445` 已提供内部、未接线的单 owner compactor source capability。它在同一 transaction 以 `SELECT ... FOR UPDATE` 锁定精确 owner 的 `schedule_v2_sync_state`，拒绝缺失/损坏状态，按最多 10,000 个 sequence 的单次上限删除 `schedule_v2_changes` 的 `[previousFloor, newFloor)` 并推进同一行 `min_retained_seq`；事务失败、取消或状态更新非恰好一行都 fail-closed。它不创建 owner 状态，不删除当前资源、tombstone 或 idempotency receipt，不改变资源 identity 永不复用规则。当前没有 policy、owner enumeration、invoker、scheduler/cron、route/config、metrics/backoff、真实 MySQL/InnoDB 验收或部署接线。

早期设计使用 `cursor_expired` 描述 retention 越界；旧 `POST /v2/schedules:sync` source contract 统一返回 HTTP `410 reset_required`。其 retention floor、genesis、state/history 损坏、客户端一次 bootstrap、same-cursor/cycle 与 `400 invalid_cursor` terminal 细节只作为 legacy migration 资料保留；W06 Wave 0 后该 route 已从 production router 移除，客户端旧 `RequestSync` 恢复也不是当前可用远端路径。W3 已完成 grouped-history/genesis 的 pure contract，W6 已完成客户端 opaque-cursor/confirmation source/tests；future `POST /v2/schedules:authoritative-sync` 的 W5 service、cursor 与 reset runtime 合同已闭合但未部署/未启用；仍须经 W7 native migration/capability、真实互操作与 deployment/enablement 才能成为可用路径，不能把旧 source route 写成当前公开 endpoint。

## 10. Legacy mutation result 与客户端收敛（历史 source）

本节只记录旧 mutation lane 的 source 设计，不适用于 future semantic v2 public command receipt，也不适用于 Web production。future accepted receipt 只表达 `schemaVersion`、`outcome`、`candidateId`、`commandType` 与 `changed`，不得公开 group、commitSequence 或 high-water。

旧 Accepted 结果曾规划至少提供：

- mutationId；
- canonical resource 或明确可拉取的 revision；
- resource revision；
- change sequence/cursor；
- DELETE tombstone 信息。

Rejected 区分：

- validation；
- authentication/authorization；
- revision conflict；
- not found；
- unsupported schema/version；
- retryable server failure。

网络 timeout/connection loss 属于客户端 DeliveryUnknown，不应被伪装成服务端明确 Rejected。

旧设计曾考虑 mutation receipt/status 查询与幂等重放。该方案不进入 future semantic v2 Web 合同：Web 禁止 durable candidate、delivery state、retry/replay 或刷新后恢复；页面生命周期结束即丢弃内存 candidate/cursor。

## 11. occurrence 与 split 原子性

- exception 是独立 revision 资源；
- upsert/delete exception 分配独立 change sequence；
- recurrence identity 必须由当前 active RRULE 生成；
- split series 在一个事务内截断旧系列、创建新 UUIDv7 系列，并按稳定 identity 顺序处理后半段 exception。由于 scheduleId 是 exception identity/resource key 的组成部分，“迁移”必须表达为旧 exception revision 加一后的软删除 + tombstone + immutable DELETE snapshot，以及新 scheduleId 下 revision 1 的新 exception + immutable UPSERT snapshot，禁止直接更新主键或只写新 key；
- split 的 immutable change 顺序必须保证任意分页前缀都不产生 orphan：先按稳定 identity 顺序写全部旧 child DELETE+tombstone，再写旧 Schedule 截断 UPDATE，然后写新 Schedule CREATE，最后按稳定 identity 顺序写新 child UPSERT。禁止在旧 child DELETE 前发布旧父截断，也禁止在新父 CREATE 前发布新 child；
- 上述旧 child DELETE、旧 Schedule UPDATE、新 Schedule CREATE 和新 child UPSERT 使用同一 mutation 连续分配的 sequence 区间；idempotency receipt 的 `first_result_seq/last_result_seq` 与 `result_json` 必须覆盖完整结果，DeliveryUnknown 重放不得再次分配 sequence 或重复创建 child；
- 任一步失败完整 rollback；
- “此次及以后删除”同样不能由多个无事务 API 拼装。

## 12. 客户端首次接入

后端上线时：

1. 客户端保留已有 Schedule UUIDv7、mutationId、deviceId 和 payload；
2. `revision=0` 的资源按原 ID CREATE；
3. 按 outbox 既定顺序和合并结果派发；
4. Accepted 后更新 revision 并清理对应 mutation/tombstone；
5. 拉取 bootstrap/change feed；
6. 通过 merger 合并 remote 与未确认本地事实；
7. 不重新生成 ID，不用 bootstrap 无条件覆盖 pending。

非 Web 平台的持久化实现见 [分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)。后端文档不重复客户端 SQL 表设计。

## 13. Web remote-required 边界

当前 Web production 仍是 W0 无持久化、无 I/O unavailable façade，不执行 bootstrap、Accepted/reconcile、refresh、cursor advancement、retry/replay、真实 transport 或 graph application。W2 仅完成 Schedule-owned storage isolation：legacy `SettingsScheduleLocalStore` 与 factory 已隔离到 noWebMain，Web 不含 Schedule-owned durable persistence 或 Web-visible durable adapter；account/profile/tourist Settings 仍在 W2 范围外。

Web 仅可在 W5 backend source/runtime closure 已完成但仍 disabled/not deployed、W6 shared-client source 已完成、W7 native migration 和 client repository/factory/capability integration 已完成，并在 W7 后、W8 前通过独立 required deployment/enablement gate 后，于 W8 以非持久化方式支持；W8 后依次进行 W9 最终跨平台验收/发布，随后 W10 operational cutover：

- 页面初始化 authoritative bootstrap；
- command 已确认后才替换 canonical 当前状态；
- Rejected 恢复 previous canonical snapshot；
- DeliveryUnknown 的页面内受限处理；
- 重建后重新读取，绝不依赖浏览器持久化伪装离线成功。

届时页面/文档/repository 内存仅可含 active `candidateId` 和一个原子 confirmed 单元（opaque cursor + authoritative graph/cache），重建即整体丢弃；W2 没有接线这份状态。Web 状态机和缓存生命周期由存储专题维护。

## 14. 安全、审计与隐私

- owner 仅来自认证 context；
- 所有查询、写入、idempotency 和 change feed 均按 owner 隔离；
- 请求体中的 owner/account 字段不能覆盖认证身份；
- 日志不打印标题、备注、payload、token 或完整学号；
- mutation/resource 日志仅保留必要 ID、错误类别和脱敏 owner；
- cursor 与 recurrenceKey 防篡改或严格 canonical 校验；
- 请求大小、批量条数、RRULE 复杂度和 change page size 有上限；
- recurrence 展开必须有界，不能成为 DoS 入口；
- 当前建表复用项目既有 GORM `AutoMigrate`；七个 v2 model 必须显式注册，初始化错误必须阻止服务继续启动，且业务 validator 不能依赖数据库 nullable/tag 猜测跨字段合法性。

## 15. 验收

### 可在代码库完成

下列为当前或后续需要覆盖的代码级合同。W5 backend 已完成 semantic command/read route、authoritative store/service、cursor/reset 与 disabled capability 的 source/runtime 聚焦验证；W6 K0–K3 已完成 common/Desktop shared-client source/tests。早期 `9345ec4` mutation/bootstrap 记录只属历史，且本轮现有 `/Users/guoxiangrui/sdk/go1.26.4/bin/go` 聚焦 `./schedulev2wire` 与 `./service -run 'TestScheduleV2Bootstrap'` 不等同于 unrestricted service、真实 MySQL、服务启动、网络互操作或端到端验证。

- DTO/wire grammar contract tests；
- mutation request hash 与幂等；
- revision CAS；
- sequence 并发单调，包括 sync_state 不存在时的同 owner 并发首次 mutation 与空账号 bootstrap；
- fixed-high-watermark 分页，以及 split change 区间跨页时任意前缀不产生 orphan；
- cursor 篡改/过期，包括 `after = min_retained_seq - 1` 可读与更早 cursor 过期的半开边界；
- tombstone、资源 identity 不复用与 bootstrap；
- 父系列结构更新对全部未软删除 exception（无论 status）的生成性校验，以及父删除 child tombstone/change 级联；
- exception/split transaction rollback、旧/new identity 的 DELETE+UPSERT change 集、旧 cursor 增量同步和 DeliveryUnknown 幂等重放；
- owner 伪造拒绝；
- change snapshot 不被当前行污染；
- 七个 GORM model 的显式注册、owner-first PK/FK、账号内 sequence 索引、JSON nullability、只读 association 与 MySQL DryRun DDL contracts。

### 需要部署环境完成

- 真实数据库锁与 isolation；
- 真实 MySQL `AutoMigrate`、`information_schema` fingerprint 与清理/回退流程；
- 认证 context；
- 多实例并发写；
- retention policy、owner enumeration、invoker/调度、metrics/backoff，以及真实 MySQL 下的行锁、分批压缩、reset/重新 bootstrap 与灾难恢复验收；
- 多设备离线/DeliveryUnknown 收敛；
- Android SyncAdapter、iOS 和 Web 端到端。

真实 MySQL 集成验收按 D-012 延后到部署准备阶段：当前不得为赶进度伪造已编写或已通过；准备部署前也不得跳过，必须另开任务提供隔离数据库、执行入口、Secret 和清理流程。

## 16. 与双向日历的交接

[双向日历同步设计](schedule-v2-calendar-bidirectional.md) 依赖本后端至少具备：

- durable mutation idempotency；
- revision CAS；
- bootstrap；
- change feed/cursor；
- tombstone；
- canonical mutation result。

本段随后的最小 mutation、bootstrap/sync route 与 runtime-config 内容只保留为历史 source/迁移资料：W06 Wave 0 已移除三条旧 production route，精确旧 POST 在认证、service 与 store 前 route-level `404`，不构成当前最小写入、全量读取或增量读取前提。sync 使用冻结 high-water、加密 owner-bound cursor、严格 UPSERT/DELETE 重建与 `410/reset_required`；backend integration `e367445` 另提供内部未接线、单 owner、每次最多推进 10,000 个 sequence 的 retention compactor source capability，但 policy、owner enumeration、invoker/调度、真实 MySQL 和部署仍未实现。客户端 production source consumer 已接线，但旧 endpoint 已不可达：同一 exact-session read transport 在显式 `RequestSync` 的 push-success 后调用 bootstrap/delta appliers；page graph+cursor 原子提交、严格 reread 后发布且入站无 calendar publication。delta v1 继续拒绝 occurrence resources；Schedule UPSERT 保留未提及 occurrence children，受接受且未受保护的 Schedule DELETE 删除 owned children、不制造本地 tombstone。该客户端能力不表示 endpoint 已部署、实际 enable、MySQL/canary 或完成网络互操作。Android 当前已在本机接线**有限双向同步**：启动时有界的 Provider → Schedule `TO_SCHEDULE`、finalized worker 的受限 Schedule → Provider 出站，以及普通删除后的 Room 本地 `DETACHED`（普通删除不删 Provider）；这不经过后端，不构成远端入站、连续同步或多设备收敛。iOS S205-04 / #281 已在 #279 full-access gateway/真实 bridge 与 #280 settings 上接入 process-resident、单向 Schedule → EventKit runtime；#306 的 source `9da50596c` → integration `db0e85f82` 已将 iOS Schedule repository 切换为进程级 Room3。EventKit enabled/source intent、calendar hint 与 event-ref ledger 仍是独立 Settings-backed cache；Room、Settings cache 与 EventKit 非原子。runtime 通过 post-initialize one-shot handoff 启动单 serialized/conflated Full，并以 durable settings、FULL_ACCESS、Ready/Recovered snapshot 与 exact session/scope/owner/generation 门禁。unknown effect、terminal uncertainty、cache/ack/retirement 或 locator 失败均终结当前 generation，不自动 retry/replay/compensation；atomic recovery eligibility 不跨进程。它不经过本后端，也不提供 notification/background/manual-sync/inbound/bidirectional/occurrence exception；#282 真实 EventKit/真机验收仍未完成，fake/in-memory 与编译证据不构成真实 acceptance。后端 occurrence mutation 继续返回 `occurrence_exception_unsupported`；不能因客户端 #208 host-only foundation 改写为后端已支持。Android 与 iOS 均不能对外承诺可靠双向日历或多设备一致性。
