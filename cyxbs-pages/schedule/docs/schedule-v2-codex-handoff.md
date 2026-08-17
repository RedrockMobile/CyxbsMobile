# Schedule v2 Codex 交接

> 更新时间：2026-08-17。后端接口已基本定型，客户端按最终 typed 协议完成主体重写；尚未部署，也未进行真实账号、生产数据库或跨端网络验收。

## 1. 当前结论

Schedule v2 不再沿用旧 cursor、单条 outbox、receipt 或 semantic command 方案。客户端当前实现只包含：

- typed Category、Schedule、OccurrenceOverride；
- 资源内部 `version` 与逐字段 `AtomicField`；
- 每个 identity 一份 remote snapshot 和至多一份 pending；
- 本地 `localRevision` 的 R→U 保护；
- Schedule 日常新增、修改、删除接口；
- 首次进入、网络恢复或手动触发的一次完整 Sync；
- Android/iOS 单向导出到系统日历。

旧 graph、outbox、receipt、tombstone 表、cursor、checkpoint、candidate、journal、reservation、settlement、双向 Calendar 冲突链和 P0 探针均已删除，不应恢复兼容层。

## 2. 后端最终合同

后端设计事实源：

```text
/Users/guoxiangrui/GolandProjects/magipoke-todo/SCHEDULE_BACKEND_DESIGN.md
```

接口：

```text
POST   /v2/schedule-mutations
POST   /v2/schedules
PUT    /v2/schedules
DELETE /v2/schedules
```

关键约束：

- Sync 请求包含三类资源的 `confirmed/upserts/deletes` 和不可拆分 `atomicBatches`；
- upsert 直接上传完整 typed Input，`version` 在资源内部；
- CREATE 使用 `version=0`，PATCH 使用当前正版本；
- DELETE 只上传 identity 与 `localModifiedAt`，不上传 version；
- tombstone 不含 version；
- DELETE 优先级最高，客户端不尝试复活相同 identity；
- OccurrenceOverride identity 是 `scheduleId + occurrenceDate`，只有 status/title/description/reminders 四个原子；
- Category color 可为 `null`，表示没有自定义颜色；
- DAILY/WEEKLY 是当前支持的 recurrence；MONTHLY/YEARLY 不支持。

所有 HTTP 响应使用：

```json
{
  "data": {},
  "status": 10000,
  "info": "ok"
}
```

HTTP 200 的 `status=10000` 和 `status=20101` 都必须解码 `data`。`20101` 表示 data 内含 aligned `REJECTED`，不是 transport failure；HTTP 400 才是 strict 请求不合法。401/403、5xx、超时和连接失败没有可证明的业务执行结论。

## 3. 客户端结构

### 3.1 Wire

当前协议位于 `data/remote/v3`：

- `ScheduleV2WireModels.kt`：typed DTO 与字段中文说明；
- `ScheduleV2ApiService.kt`：返回公共 `ApiWrapper<T>` 的四个 Ktorfit 接口；
- `KtorScheduleV2Gateway.kt`：通过 KtProvider 获取接口实现后，对统一外壳和 HTTP 异常做一次分类。

网络 JSON 由项目统一 Ktorfit/ContentNegotiation 配置负责，不再维护 Schedule 私有 JSON 扫描器。`status=20101`
时不能访问会执行成功校验的 `ApiWrapper.data`，gateway 在确认该状态后读取 `rawData`，把 aligned `REJECTED`
结果交给 applier；其他模块仍按原规则使用 `data`。

### 3.2 Common 同步逻辑

`data/repository/v3` 包含纯逻辑：

- domain/wire mapper；
- local command reducer；
- Sync capture/planner；
- response applier；
- daily mutation bridge；
- snapshot projector。

planner 只上传当前完整 pending。applier 先更新 remote，再按捕获的 `uploadedRevision` compare-and-clear；请求期间形成的更高 revision U 必须保留到下一次同步。

### 3.3 Room

Room 只注册四张表：

```text
schedule_v2_account_metadata
schedule_v2_category_state
schedule_v2_schedule_state
schedule_v2_occurrence_override_state
```

三张 state 表的 remote/pending 字段使用 typed Kotlin 类，Room converter 严格编码为 JSON 列。账号表只保存 `localRevisionCounter`。

详细字段与 R→U 语义见 [客户端存储设计](./schedule-v2-platform-storage-upgrade.md)。

## 4. Repository 行为

### 4.1 初始化与完整 Sync

`RoomScheduleRepository.initialize()`：

1. 读取本地四表并先发布可见快照；
2. 在 Room mutex 外调用一次 `/v2/schedule-mutations`；
3. 响应回来后重新读取当前状态；
4. common applier 处理 result、related、inventory 和 tombstone；
5. 用一个 Room write transaction 替换账号三类完整状态。

网络失败、HTTP 400 或业务 REJECTED 都不会清除无法确认的 pending。

### 4.2 日常命令

本地命令先写 Room、发布快照，再决定是否调用日常接口：

- 普通 Schedule CREATE → POST `/v2/schedules`；
- 普通 Schedule UPDATE → PUT `/v2/schedules`；
- 普通 Schedule DELETE → DELETE `/v2/schedules`；
- Category、OccurrenceOverride 和带 `localBatchId` 的父子闭包只等待完整 Sync。

网络调用期间不持有 repository mutex。响应应用前重新读库，所以 R 请求期间产生的 U 不会被旧响应覆盖。

### 4.3 错误可见性

客户端公共错误区分：

- `MutationRejected`：业务拒绝，reason 与后端 ResultReason 对齐；
- `InvalidResponse`：HTTP 200 body 或服务端状态无法按合同解释；
- `Timeout`；
- `Server(status)`；
- `Unexpected`；
- `BackendNotDeployed`：当前 Web 只读实现。

最近远端错误只保留在 repository 进程内，用于让 UI 继续显示 Unavailable；它不写入 Room，也不是重试状态机。只有成功且无 REJECTED 的完整响应才清除该错误。

## 5. 平台接线

- Android、Desktop、iOS：进程唯一 Room 数据库 + exact-session `KtorScheduleV2Gateway`；
- Web：最小 `READ_ONLY` unavailable repository，不持久化、不联网；
- Schedule ID 生成器只属于编辑入口，不再作为 repository 工厂参数；
- 不再生成或持久化 stableDeviceId；
- 数据库 builder 不注册旧 migration，旧开发库需要清库或重装。

账号切换由 `AccountSwitchingScheduleRepository` 维护稳定 façade。切号会取消旧 delegate 的初始化和快照收集；迟到快照和 Calendar change 在发布前按 binding identity 与 accountId 拦截。

## 6. Calendar 边界

当前只保留单向导出：

```text
ScheduleRepository.snapshot/calendarChanges
  -> ScheduleCalendarProjectionFactory
  -> CalendarExportPlanner
  -> Android Calendar Provider / iOS EventKit
```

不再支持系统日历到 Schedule 的入站写回、三方合并、冲突选择、link detachment 或手动 conflict executor。

Android/iOS 初始化由账号 façade 在当前 delegate 初始化完成后调用 `onScheduleRepositoryInitialized`。平台 factory 不再携带旧 reconciliation capability 或 initialized hook。

## 7. 明确不支持

为避免再次过度设计，当前不实现：

- MONTHLY/YEARLY、RDATE；
- 单次 occurrence 的 timing/category override；
- `SplitSeries`、`DeleteThisAndFollowing` 的远端因果命令；
- 同 identity DELETE 后恢复；
- Web 离线编辑与持久 pending；
- 旧数据库数据推断迁移；
- receipt/history/cursor/protocol rollout/自动重试框架。

现有 UI 命令若落入这些情况，应明确返回 Unsupported/Rejected，不扩展 wire。

## 8. 当前验证状态

已完成的验证：

- common metadata 与 Room KSP 多轮通过；
- Desktop production 源码在平台切换后编译通过；
- typed wire、mapper、reducer、planner/applier、daily bridge、Room mapper/store/repository 有聚焦测试；
- nullable Category color 已覆盖 wire、domain、Room 与 repository；
- `git diff --check` 通过。

仍需在最终集成前重跑：

- Desktop 聚焦测试与完整 test compile；
- Android host compile/test；
- iOS metadata/test compile；
- JS/Wasm test compile；
- 当前四表 schema export 核对。

真实 HTTP、真实账号、Android Provider、iOS EventKit 和生产数据库均未执行，也不在本轮默认授权范围内。

## 9. 当前 Git 集成边界

主集成分支：

```text
guoxiangrui/feature/schedule
```

当前客户端候选在固定 lane01/02/03 并发开发，由主 Agent 串行审查并合入。最终步骤：

1. 清空剩余旧符号和旧测试；
2. 完成跨平台编译与聚焦测试；
3. 更新本文验证结果；
4. 将候选提交合入 `guoxiangrui/feature/schedule`；
5. 核对最终 commit body、工作树和分支状态。

禁止把历史 Claude semantic 分支、旧 cursor/outbox 实现或 archive 文档重新合入。

## 10. 后续真实验收

代码集成完成后，另行授权并执行：

- 后端真实 MySQL/HTTP 合同测试；
- Android/iOS/Desktop 使用测试账号的首次 Sync、日常 CRUD、断网 pending 与 R→U；
- Android Calendar Provider 与 iOS EventKit 的隔离测试数据单向导出；
- 服务端 20101 REJECTED、400 strict invalid、401/403、5xx 和 timeout 场景。
