# Schedule v2 iOS EventKit 导出边界

> **分类：PARTIALLY_HISTORICAL。** Full-access gateway、显式设置页、process-resident 单向 runtime 与 KMP iOS 宿主用途说明已经落地；但这些代码仍建立在旧客户端时间、recurrence 与 occurrence exception 模型上，尚未迁移到当前 typed Schedule / OccurrenceOverride canonical 合同。
>
> 当前权威语义以 [完整资源 AtomicField 合并与原子批次设计](schedule-v2-field-group-sync-design.md)、[重复日程单次覆盖与周期破坏能力矩阵](schedule-v2-recurrence-override-capability-matrix.md) 和 [双快照与 typed 资源版本同步流程](schedule-v2-resource-version-sync-flow.md) 为准。本文只记录 iOS EventKit 平台边界、既有实现事实和迁移门禁，不得反向扩展 wire 字段或覆盖 canonical 合同。
>
> **真实 EventKit 验收仍未完成。** 现有 iOS 测试使用纯 mapper、阶段状态机或内存 fake，不构造真实 `EKEventStore`、不读写用户日历。任何真机 full-access 授权、source 枚举、创建/删除专用测试 calendar 或 event 的操作仍属于 #282，执行前必须获得用户对该次真实隔离日历操作的单独明确授权；产品 full-access 合同不等于测试或自动化操作授权。

## 1. 审计基线

### 1.1 分支与提交事实

审计分支：

```text
guoxiangrui/feature/schedule_claude/w14-ios-eventkit-full-access
```

该分支当前指向 `fd82a1a570f884e3b18a121869d961bc32a696dd`，不是只包含一个 EventKit commit 的孤立分支。EventKit 能力由以下祖先提交分阶段落地：

| 提交 | 已验证内容 |
| --- | --- |
| `b64331bf1c6341d11400bbeddb3afe8431a98c33` | 冻结纯 Kotlin/Native EventKit mapper、canonical URI、受限 RRULE、时间/提醒 fail-closed 与 iosTest。 |
| `fe774c6bb4b9b11e7fb91f30b18b53d79baa2230` | 接入 full-access gateway、`EKEventStore` bridge、source/calendar authority、原子 calendar + first-event 提交与 fake 合同测试。 |
| `3cb28bd3ed6de82b624fd2e71463718f4a18a4c1` | 接入 iOS Compose 显式权限/source 设置、账号级偏好和 exact-session controller。 |
| `048db97514fc1f5a2c085c11c12d2bae8379429c` | 接入 process-resident 单向导出 runtime、locator ledger、unknown-outcome recovery proof 与 lifecycle fence。 |
| `783728c6eea2ca53ec400a4b35fd5b7657494205` | 为 KMP `iosApp` 宿主补齐 `NSCalendarsFullAccessUsageDescription`。 |

当前 checkout 的 EventKit Kotlin 实现与 KMP `Info.plist` 相比该分支 tip 没有后续代码差异。后续 canonical 文档已经改变 recurrence/Override 权威合同，因此“代码存在”不能再等同于“客户端已满足当前合同”。

### 1.2 Swift 与 Kotlin 边界

Swift 侧没有 EventKit 实现或 EventKit 专用 façade：

- `cyxbs-applications/multiplatform/iosApp/iosApp/ContentView.swift` 只用 `UIViewControllerRepresentable` 承载 `IOSAppKt.MainViewController()`；
- Swift `find_symbol` / `search_text` 未发现 EventKit、`requestFullAccessToEvents` 或 Schedule calendar bridge；
- `cyxbs-applications/multiplatform/src/iosMain/kotlin/IOSApp.kt` 导出 `MainViewController(): UIViewController`，Compose 设置页和 EventKit runtime 都位于 Kotlin/Native `iosMain`；
- 当前 Swift 宿主不会直接调用 `IosEventKitFullAccessGateway`。`IosEventKitSettingsGateway` 是 Kotlin 设置层窄 seam，不是新的 Swift wire 或业务接口。

因此后续迁移应继续优先修改 common/iosMain 投影与 adapter 合同，不应为了“iOS 原生化”在 Swift 侧复制一套 recurrence 或 identity 规则。

## 2. 一期方向与能力边界

当前已接入的数据方向仍是：

```text
Schedule local snapshot → EventKit
```

已实现的平台执行能力：

- 用户显式请求 full access；
- 枚举可写 source，并由用户明确选择；
- 创建受管 calendar 与首个 event；
- 对既有受管 master/single event 做严格查询、创建、更新、删除和写后回读；
- 使用 canonical URL、账号 scope 和 fingerprint 识别受管事件；
- 使用账号级 calendar/event identifier ledger 加速定位，但不把 identifier 当 ownership proof；
- 在当前进程内处理首次原子提交“可能已经成功、locator 尚未完整持久化”的窄恢复窗口；
- exact-session、owner、generation、取消和不确定写后终态 fail-closed。

尚未实现或尚未迁移：

- EventKit → Schedule inbound 或双向同步；
- `EKEventStoreChangedNotification`、后台 worker、静默推送或准实时保证；
- 当前 canonical OccurrenceOverride 的正式 EventKit 投影；
- typed `scheduleId + occurrenceDate` 与旧 `recurrenceId`/本地墙钟模型之间的迁移；
- canonical UTC date-slot recurrence 与现有 `MinuteTimeDate + IANA timeZoneId` 投影之间的迁移；
- `.thisEvent` / `.futureEvents` 与 canonical A/B/Override atomic batch 的正式映射；
- RDATE；
- #282 真实系统授权、source、calendar、event 和恢复验收。

## 3. Full access 合同

### 3.1 为什么不能使用 write-only

当前 gateway 依赖以下能力：

- 扫描受管 calendar 中的 canonical URL；
- identifier 失效后重建 locator；
- 精确更新/删除已存在事件；
- 写后回读并比较 source/calendar/URL/fingerprint；
- 检测重复 calendar、重复 event、foreign scope 和只读 calendar；
- 处理首次 calendar + event 原子提交的未知终态。

因此 `WRITE_ONLY` 在 `IosEventKitFullAccessStatus` 中被明确视为权限不足，不能一边申请 write-only，一边承诺完整恢复或幂等 reconcile。

### 3.2 系统版本与用途说明

`IosEventKitStoreBridge.requestFullAccess()` 的实现事实：

- iOS 17+ 调用 `requestFullAccessToEventsWithCompletion`；
- 更早系统调用 legacy `requestAccess(to: .event)`；
- completion 的 `granted` 只作信号，最终重新读取 `authorizationStatus`；
- `NOT_DETERMINED`、`FULL_ACCESS`、`WRITE_ONLY`、`DENIED`、`RESTRICTED` 和未知状态都有显式映射；
- Kotlin 协程取消只阻止迟到 completion 恢复旧 continuation，不能取消系统弹窗本身。

当前 KMP `iosApp` 宿主的 deployment target 是 iOS 18.2，`Info.plist` 只配置：

```text
NSCalendarsFullAccessUsageDescription
```

没有配置 `NSCalendarsWriteOnlyAccessUsageDescription`，也没有为该 iOS 18.2 宿主配置 legacy `NSCalendarsUsageDescription`。bridge 中的 pre-iOS 17 分支是共享模块的兼容实现事实，不表示当前 KMP 宿主会运行到该路径。

### 3.3 只有用户操作才能触发权限请求

权限请求路径固定为：

```text
Compose 设置页用户点击
→ IosScheduleCalendarSettingsController.requestFullAccess()
→ IosEventKitSettingsGateway.requestFullAccess()
→ IosEventKitStoreBridge.requestFullAccess()
```

以下路径不得自动弹窗：

- gateway 构造；
- repository 初始化；
- runtime 注册或首次 reconcile；
- 设置页普通加载/刷新；
- source/calendar 只读检查；
- 后台回调。

设置 controller 只持有权限状态、source picker 和缓存选择检查 seam，不持有 EventKit upsert/delete 能力。

## 4. Source、calendar 与身份

### 4.1 Source 选择

- 不默认写入用户默认日历；
- 由用户从稳定 source identifier 列表中明确选择；
- 展示名只用于 picker，不参与 ownership；
- 不硬编码 iCloud，设备可能没有 iCloud source；
- 应用不能创建 `EKCalendarSource`；
- source 消失时 fail-closed，不回退到默认 source；
- `allowsContentModifications` 是平台事实，不能把普通 EventKit calendar 宣称为“仅掌邮可写”。

### 4.2 Calendar/event authority

`calendarIdentifier` 与 `eventIdentifier` 都只是 cache hint。受管身份至少要求：

```text
用户明确选择的 source
+ canonical v2 URL
+ 当前本地账号 scope
+ 可逆的 canonical fields/fingerprint
```

标题“邮子清单”不是 ownership proof。已变成只读的 canonical calendar 仍必须参与身份检查并阻止重复创建；同 scope 多 calendar、跨 source 同 scope calendar、重复 event、foreign identity 或读取不确定都必须 fail-closed。

当前 URL 中的账号 scope 是 adapter-local 受管身份与缓存分区，不是 wire owner 字段。canonical 请求不上传 owner，服务端 owner 来自认证上下文 `redid`；不得用旧客户端 URL/scope 设计反向规定 typed wire。

## 5. 当前 EventKit adapter 实现事实

### 5.1 Foundation

`IosEventKitCalendarAdapterFoundation` 当前会：

- 校验 canonical v2 URI、scope、projection kind 与 fingerprint；
- 映射 Single、Series master、Deadline、AllDay、相对 DEVICE alarm 和受限 RRULE；
- 把 EventKit `eventIdentifier` 仅作为 opaque locator；
- 对非整分钟 instant、不可逆 DST wall time、unsupported RRULE、fractional alarm 和 occurrence exception 返回 typed Unsupported；
- 明确拒绝 `CalendarProjectionKind.OCCURRENCE_EXCEPTION` 和 raw detached occurrence。

这些是旧投影的安全基线，不是当前 canonical 时间/Override 已迁移的证据。

### 5.2 Store bridge

`IosEventKitStoreBridge` 当前会：

- 在同一待提交队列创建 calendar 与首个 canonical event，再显式 `commit`；
- 只有真正进入 commit 后的 unknown outcome 才分类为 `ATOMIC_COMMIT_OUTCOME_UNKNOWN`；
- 普通 create/update/delete 使用 `commit=false` 后显式 commit，并在失败时保守返回 ambiguous/access-lost；
- 递归 master 的普通 save/remove 使用 `EKSpanFutureEvents`，非递归事件使用 `EKSpanThisEvent`；
- 完整替换 URL、title、notes、timing、alarm 与 recurrence rule，再写后回读。

这里的 `EKSpanFutureEvents` 只是当前平台 master CRUD 的实现细节，不能被解释为 canonical “从本次起编辑/删除后续”已经接入。canonical 后续操作必须先形成 Schedule A/B/OccurrenceOverride 最终资源图并通过 typed atomic batch；EventKit span 只能是该最终图的 adapter 内部执行手段。

### 5.3 Settings 与 runtime

`IosScheduleCalendarSettingsController` 已实现：

- `NOT_DETERMINED / DENIED / RESTRICTED / WRITE_ONLY / FULL_ACCESS` 状态呈现；
- explicit request、source picker、source/calendar 精确只读检查；
- 账号与同账号新 generation 隔离；
- source → 清理失效 locator → enabled 的 durable intent 顺序；
- 关闭仅停止未来导出，不删除 EventKit 数据。

`IosScheduleCalendarExportRuntime` 已实现 process-resident exact-session 串行 Full reconcile、strict locator preflight、planner CRUD、ledger 持久化、首次原子提交 recovery proof、ack/retirement 和 terminal-uncertain fence。

但 runtime 调用 `ScheduleCalendarProjectionFactory.project(...)` 时没有声明 native occurrence exception capability；common projection 遇到 exception 会整体返回 Unsupported，iOS foundation 也拒绝 detached occurrence。该保守行为必须保留到 canonical Override adapter 正式迁移完成。

## 6. Canonical 时间与 recurrence 迁移边界

当前 canonical 合同与既有 EventKit 代码之间存在明确断层：

| 主题 | 当前 canonical 合同 | 既有 iOS 实现 |
| --- | --- | --- |
| Wire 时间 | 有符号 `int64` Unix 毫秒，不保存时区。 | common projection 使用 `MinuteTimeDate + IANA timeZoneId`，foundation 进行 DST wall-time 可逆校验。 |
| All-day | UTC 午夜 `[startAt, endAt)`，结束 exclusive。 | 旧 `Date + durationDays`，bridge 以 UTC 午夜写 EventKit。 |
| Recurrence anchor | 稳定 `recurrence.anchorDate`，UTC 午夜 date-slot；首次 anchor history 不可替换。 | 旧 `RecurrenceRule` 与实际 timing 直接编码 RRULE，没有接入 typed `firstRecurrenceAnchorDate`。 |
| Occurrence identity | `scheduleId + occurrenceDate`，其中 `occurrenceDate` 是 parent rule 生成的 UTC 午夜 date-slot。 | 旧 common exception 使用 `recurrenceId`，并允许旧模型的 timing/category patch。 |
| Timing 变化 | 改 actual timing，不迁移 anchor 或已有 Override identity。 | 旧 projection 可能从本地墙钟/时区材料化 occurrence。 |
| DST | 不保存时区；UTC date-slot + 完整 offset 是权威。 | 旧 foundation 以 IANA gap/overlap resolver 保证墙钟往返。 |

迁移前必须遵守：

1. 不得把旧 `MinuteTimeDate + timeZoneId`、DST resolver 或 local `recurrenceId` 写回 canonical wire；
2. 不得从 EventKit `occurrenceDate`、设备时区或显示日期反算 wire `occurrenceDate`；
3. 不得让平台 `.thisEvent` timing 能力增加 canonical Override timing 字段；
4. 不得让旧 Android/iOS adapter 字段覆盖 typed AtomicField 划分；
5. 必须先由客户端 materializer 以 `occurrenceDate + parent offset` 得到实际时间，再生成平台投影。

## 7. OccurrenceOverride 与 span 能力

当前 canonical OccurrenceOverride 只有：

```text
identity = scheduleId + occurrenceDate
status = ACTIVE | COMPLETED | CANCELLED
title = INHERIT | CLEAR | REPLACE
description = INHERIT | CLEAR | REPLACE
reminders = INHERIT | CLEAR | REPLACE
```

明确没有：

- 独立 exception ID；
- category override；
- timing / 改期字段；
- 时区；
- occurrence 序号。

EventKit 平台能力与项目状态：

| 操作 | EventKit 平台 | 当前项目状态 |
| --- | --- | --- |
| 取消本次 | `.thisEvent` 删除可表达。 | 尚未接入；foundation/runtime 继续 Unsupported。 |
| 修改本次标题/描述/提醒 | detached `.thisEvent` 可表达。 | 尚未接入。 |
| 完成本次 | EventKit 没有 Schedule completion 状态。 | 不能映射为删除；至少必须保留 occurrence 可见。 |
| 修改本次分类 | EventKit 不承载应用分类。 | canonical 也不支持。 |
| 改期本次 | `.thisEvent` 可改 start/end。 | 只能是 adapter-only 平台能力，canonical 不支持。 |
| 从本次起编辑后续 | `.futureEvents` 可在平台内拆分。 | 尚未建立 canonical A/B/Override atomic batch 映射。 |
| 从本次起删除后续 | `.futureEvents` 可截断。 | 尚未建立 canonical 最终资源图映射。 |

`EKEvent.occurrenceDate` 与 `isDetached` 只能用于 adapter 内部定位，不得进入 wire identity。恢复默认字段必须以 canonical neutral live Override 表达，不能把删除平台 detached event 直接等同于远端 tombstone。

## 8. RDATE / EXDATE 平台差异

### 8.1 RDATE

Canonical Schedule v2 **不支持 RDATE**：live Override 必须引用 parent recurrence 真正生成的 `occurrenceDate`，不能用 Override 增加规则外日期。

平台差异：

- Android Calendar Provider 有 RDATE 字段，但项目不使用；
- EventKit 公共 API 没有公开与任意 RDATE 集合等价的 recurrence API；当前 bridge 也只映射一个受限 `EKRecurrenceRule`；
- 不得为了弥补 EventKit 差异，把规则外日期展开成有限未来 singleton、隐藏 detached event 或新的 wire 字段；
- 如产品未来需要规则外日期，必须先修改 canonical 合同并重新做跨端设计，不能由 iOS adapter 单方面决定。

### 8.2 EXDATE

Canonical 不提供直接 EXDATE 字段，指定 occurrence 的排除通过 `status=CANCELLED` 的 typed Override 表达。Android Provider 虽有 EXDATE、EventKit 虽可操作 `.thisEvent`，平台字段都只是 adapter 实现细节。

当前 iOS 尚未接入 CANCELLED Override 到 `.thisEvent`；在迁移完成前必须整体 Unsupported，不能只导出 master 而漏掉取消项。

## 9. Identifier、未知提交与恢复边界

- `calendarIdentifier` / `eventIdentifier` 只用于快速定位，不保证跨安装永久稳定；
- full access 允许通过受管 calendar + canonical URL 重建 locator；
- source 消失、calendar 删除、权限撤销或 authority 歧义只暂停/关闭平台投影，不删除 Schedule；
- 首次 calendar + first-event atomic commit 的 recovery eligibility 只驻留当前进程与 store universe；
- process death 会丢失该资格，不能把它描述为跨进程、重启或重装恢复保证；
- Room commit、EventKit commit、calendar hint 和 event-ref ledger 写入是独立持久化边界，不共享 transaction/CAS；
- ambiguous、取消或 lost return 不自动 retry、replay、compensate，也不能把 locator cache 升格为 ownership/commit proof。

后续 canonical 客户端迁移不得复用旧 ledger 来证明 typed remoteSnapshot、version、AtomicField.modifiedAt 或 atomic batch 已提交；两套状态的职责完全不同。

## 10. 触发与后台边界

当前 runtime 只响应：

- repository 初始化完成后的 one-shot handoff；
- `Initialized` / `SchedulesCommitted` 合并后的串行 Full；
- 用户显式完成 source/enable intent 后的新 generation。

当前不实现：

- `EKEventStoreChangedNotification`；
- App 前后台补偿；
- `BGAppRefreshTask` / `BGProcessingTask`；
- 静默推送；
- inbound、双向、冲突归并；
- occurrence exception 正式导出；
- “手动同步”入口。

EventKit notification 不提供可靠 event delta；未来即使接入，也只能触发受管范围重查。BackgroundTasks 不准点，不能宣传实时同步。

## 11. 测试证据与真实验收

### 11.1 已有自动化证据

现有测试覆盖：

- foundation：canonical URI/fingerprint、Single/Deadline/AllDay、受限 RRULE、alarm、分钟精度、DST 与 Unsupported；
- gateway：显式权限请求、source/calendar authority、有界扫描、首次原子提交、unknown outcome、identifier 恢复、严格删除与歧义拒绝；
- settings：权限状态、source picker、账号/generation 隔离、取消和 durable intent 顺序；
- runtime：disabled/missing source/no full access 零副作用、Create/Update/NoOp/Delete、ledger、proof/ack/retirement、late completion 与 lifecycle fence。

这些测试只证明旧 adapter 基线的 fail-closed 行为，不能证明：

- canonical typed recurrence/Override 已迁移；
- Swift 侧存在 EventKit 实现；
- 真实 full-access 弹窗和用户选择流程已验收；
- iCloud/source/calendar/provider 行为与 fake 一致；
- 真机 recurring span、identifier 变化或读回行为已验收。

### 11.2 #282 真实 EventKit 验收清单

获得该次真实隔离日历操作的单独明确授权后，使用专用测试账号、专用测试 calendar 和可清理测试数据验证：

- full-access 请求、拒绝、撤销和用途说明；
- source 枚举与无 iCloud 场景；
- 创建/发现专用受管 calendar；
- Single、Timed、AllDay、Deadline；
- 受限 RRULE、COUNT/UNTIL；
- reminder 与 canonical URL；
- calendar/event identifier 变化后的恢复；
- source 消失、calendar 只读或删除；
- 重复导出幂等；
- 更新/删除不触碰其他 calendar；
- 秒级或不支持的外部修改被诊断；
- 账号隔离与撤权后的零业务删除。

在 canonical 客户端迁移完成前，#282 不应把旧 recurrence/Override 结果签成当前合同 acceptance。

## 12. 当前完成定义

以下条件全部满足前，iOS EventKit 只能标记为“full-access 平台基线已实现，canonical 迁移和真实验收未完成”：

- full access 仍只由用户明确操作触发；
- write-only 不被误报为可管理权限；
- source/calendar/event 不按标题或默认项认领；
- identifier 只作 locator；
- 失败不删除或改写 Schedule；
- typed UTC timing、stable anchor 与 `scheduleId + occurrenceDate` 已进入 iOS 投影；
- canonical Override 的取消/标题/描述/提醒策略已实现，完成/分类/改期边界保持正确；
- RDATE 继续不支持，EXDATE 只由 CANCELLED Override 语义驱动；
- `.futureEvents` 只消费已形成的 canonical A/B/Override 最终图；
- #282 已在单独授权下完成真实隔离日历验收；
- notification/background/inbound/bidirectional 仍不被夸大承诺。
