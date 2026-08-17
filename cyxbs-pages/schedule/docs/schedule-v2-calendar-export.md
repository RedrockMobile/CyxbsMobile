# Schedule v2 Android 单向日历导出

## 1. 定位

系统日历是用户可选开启的受管投影，Schedule repository 是唯一事实源。当前实现只支持 `Schedule -> Calendar`，不支持 Calendar 入站、冲突合并或反向修改 Schedule。

## 2. 数据流

```text
ScheduleRepository.snapshot / calendarChanges
  -> ScheduleCalendarProjectionFactory
  -> CalendarExportPlanner
  -> ScheduleCalendarExportCoordinator
  -> AndroidScheduleCalendarGateway
  -> 当前账号的受管 Calendar
```

- 初始化或远端同步完成后执行全量投影。
- 本地日程提交后，只重算受影响的 Schedule ID。
- 日程删除后，planner 对该身份现存的受管事件生成 Delete。
- Provider 的现有事件只用于和目标投影做幂等比较，不能回写 Schedule。

## 3. 生命周期

- 导出必须同时满足：exact `AccountSession`、账号 scope 有效、用户已开启、日历读写权限有效。
- 同一个 `CalendarExportScope` 同时只允许一个 worker。替换、停止与启动在注册表同一临界区完成，避免并发恢复产生重复 writer。
- 关闭开关只停止后续导出，不主动删除已经导出的事件。
- 用户明确确认“清空并删除”时，才删除当前账号完整匹配的受管 Calendar。

## 4. 协议边界

- Calendar 事件 ID、Calendar row 和投影缓存不上传后端。
- Provider 事件缺失不能推断远端 DELETE。
- 后端 tombstone 只由 typed Schedule v2 响应产生；客户端应用后再通过最终 repository 快照更新 Calendar 投影。
- Calendar 导出失败只更新导出状态，不修改 pending state，也不触发新的同步协议。

旧双向设计只保留归档说明，见[双向方案归档](schedule-v2-calendar-bidirectional.md)。
