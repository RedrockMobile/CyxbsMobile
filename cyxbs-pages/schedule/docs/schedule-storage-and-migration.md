# Schedule 存储与旧数据迁移

## 1. 客户端 Room

客户端仅保存四张表：

| 表 | 作用 |
| --- | --- |
| `schedule_account_metadata` | 当前账号本地 revision 计数器 |
| `schedule_category_state` | 分类的远端确认快照和本地 pending 快照 |
| `schedule_state` | 日程的远端确认快照和本地 pending 快照 |
| `schedule_occurrence_adjustment_state` | 单次调整的远端确认快照和本地 pending 快照 |

每个资源状态行只包含：

- `remoteSnapshot`：最后一次确认的完整服务端快照。
- `pendingOperation`：`UPSERT`、`DELETE` 或空。
- `pendingSnapshot`：待提交的完整资源；DELETE 时为空。
- `pendingLocalModifiedAt`：删除操作的本地诊断时间，不上传。
- `localRevision`：compare-and-clear 标识，不上传。

没有逐条 outbox、batch、receipt、cursor 或删除墓碑表。协议对象通过 Room TypeConverter 以 JSON 存储，但 Kotlin 业务代码直接使用有类型的对象。

`localRevision` 解决请求期间继续编辑的问题：请求 R 捕获 revision=7，用户随后产生 U=8；R 返回后只能清除 7，U 无论远端是否合并出新快照都保留到下一轮同步。

当前功能尚未正式上线，数据库版本固定为 1。schema 不匹配且没有迁移文件时使用 destructive migration 重建；服务端保存完整远端数据，本地尚未上传的临时数据允许丢弃。

## 2. 服务端数据库

服务端只维护三张当前状态表：

### `schedule_categories`

```text
id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
owner_id     VARBINARY(128), index idx_owner
payload_json JSON
version      BIGINT UNSIGNED
```

### `schedules`

```text
id           CHAR(36) PRIMARY KEY
owner_id     VARBINARY(128), index idx_owner
payload_json JSON
version      BIGINT UNSIGNED
```

Schedule ID 由客户端生成：正常新增为 UUID v7，旧数据迁移为 UUID v5。服务端校验规范小写 UUID 后直接保存。

### `schedule_occurrence_adjustments`

```text
id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY
owner_id                    VARBINARY(128), index idx_owner
schedule_id                 CHAR(36), index idx_schedule
original_occurrence_date_ms BIGINT
payload_json                JSON
version                     BIGINT UNSIGNED
unique(schedule_id, original_occurrence_date_ms)
```

三张表均不保存客户端 localId、创建时间、删除时间、远端修改时间、摘要键或幂等请求记录。分类和日程删除为物理删除；日程删除级联清理单次调整。

## 3. 本地优先写入

新增、编辑、删除首先在一个 Room 事务中更新 effective 状态和 pending，再发起日常接口：

```text
UI 命令
  -> 分配 localRevision
  -> reducer 更新 remote/pending 双快照
  -> Room 提交并发布 UI
  -> 捕获本次 revision 对应请求
  -> 调用 POST/PUT/DELETE
  -> 按位置应用响应
```

网络失败时 UI 数据仍保留，pending 等待后续同步。纯业务拒绝同样保留 effective 数据，并创建失败记录；用户再次编辑成功或删除该资源后，关联失败记录自动清理。

## 4. 旧数据迁移 ID

旧数据迁移使用 UUID v5，输入包含账号和旧资源稳定特征：

```text
旧清单：accountId | todo | legacyTodoId
旧事务：accountId | affair | 旧事务身份 | 周次/星期/时间位置
```

这些内容只参与 UUID 摘要，不能从 UUID 反向恢复，也不会把学号或标题明文发送为 ID。同一账号同一旧资源重复迁移会得到相同 Schedule ID；不同账号不会互相覆盖。

普通用户新增、拆分重复系列和客户端 Category/OccurrenceAdjustment localId 均使用 UUID v7。

## 5. 旧清单映射

| 旧数据 | 当前 Schedule |
| --- | --- |
| 无日期、无通知时间 | `UNSCHEDULED`，无提醒、无重复、不关联课表 |
| 有截止时间 | `DEADLINE` |
| 仅有通知时间 | 该时间作为 `DEADLINE`，提醒为准时 |
| 通知早于截止 | `minutesBefore = 截止 - 通知` |
| 通知晚于截止或无法解析 | 丢弃提醒，不阻塞该清单迁移 |
| 非重复且已完成 | `todoState=COMPLETED` |
| 重复且旧数据标记完成 | 父系列保持 `OPEN`，不把整个系列标为完成 |
| 学习/生活/其他或旧别名 | 映射并复用对应默认分类 |
| 日/周/月/年重复 | 能无损表达时映射当前规则；无法无损表达时降为下一次单实例 |

客户端新建 UI 不提供 `UNSCHEDULED`，但迁移后能正常查看和编辑；用户显式选择日期或时间后转换为全天、时间点或时间段。

## 6. 旧事务映射

只迁移已经上线的旧 `Transaction`，未上线的 `TimeTransaction` 不参与。

| 旧数据 | 当前 Schedule |
| --- | --- |
| 全学期事务 | `AFFAIR + TIMED + WEEKLY` |
| 指定周事务 | 按连续或稀疏周次生成可表达规则/实例 |
| 一个事务含多个时间位置 | 每个位置拆成独立稳定 Schedule UUID v5 |
| 旧节次 | 根据当前学期课表时间换算为 `TIMED` |
| 标题、周次、星期、节次非法 | 只跳过该脏项，不阻塞其他数据 |

事务固定 `linkedToCourse=true`。它只有在用户关联到清单后才具有 `todoState`；即使关联后完成，仍保留事务来源和课表展示语义。

## 7. 执行窗口和完成标记

迁移协调器由 Android/iOS 各自入口调用，共享 commonMain 逻辑。账号 `AccountSettings` 保存整数迁移方案版本：缺失或 0 表示未成功完成，达到当前版本表示该账号不再重复执行。

旧接口任一读取失败不会写完成版本。local-first 保存已成功但远端暂时不可用时，只要最终本地快照包含全部确定性 ID，就可写完成版本；pending 以后继续同步。

迁移入口在 `2028-09-01T00:00:00Z` 后停止访问旧服务。该截止仅关闭迁移，不删除已经迁移的数据。
