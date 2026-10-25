# 重复日程与单次调整

## 1. 基本模型

重复规则属于 Schedule，支持日、周、月、年四种频率。规则以 UTC 零点日期槽作为 `anchorDate`，并使用 `count`、`untilDate` 或永不结束三种互斥结束方式。

`OccurrenceAdjustment` 只表达某个规则实例相对父系列的差异：

```text
本地身份：localId（UUID v7；首次创建请求临时上传，服务端不保存）
远端身份：id（服务端自增 Long）
逻辑槽：scheduleId + originalOccurrenceDate
并发版本：version
```

`originalOccurrenceDate` 是该实例由父规则最初生成的日期槽，创建后保持不变。用户把实例从周一移动到周三时，只修改 `date` 字段，不改变这个逻辑槽。

服务端对 `(scheduleId, originalOccurrenceDate)` 建唯一约束，因此两个设备首次修改同一次时会合并到同一条远端记录，而不是生成两个实例。

## 2. 字段补丁

单次调整的日期、时间、标题、描述、分类和提醒使用 `AtomicField<FieldPatch<T>>`：

| mode | 含义 | value |
| --- | --- | --- |
| `INHERIT` | 继续继承父系列当前字段 | 不上传 |
| `CLEAR` | 明确清空可空字段 | 不上传 |
| `REPLACE` | 使用单次值 | 必须上传；同请求新分类引用改用 `categoryLocalId` |

日期、时间和标题不允许 `CLEAR`。描述、分类和提醒允许 `CLEAR`。状态使用独立原子字段：`ACTIVE`、`COMPLETED`、`CANCELLED`。

示例：只把一次日程移动到当天 18:00–19:00，其余字段继续继承：

```json
{
  "localId": "019d0000-0000-7000-8000-000000000201",
  "scheduleId": "019d0000-0000-7000-8000-000000000101",
  "originalOccurrenceDate": 1788048000000,
  "version": 0,
  "status": { "data": "ACTIVE", "modifiedAt": 1788000000300 },
  "date": { "data": { "mode": "INHERIT" }, "modifiedAt": 1788000000300 },
  "time": {
    "data": {
      "mode": "REPLACE",
      "value": {
        "kind": "TIME_RANGE",
        "startMinuteOfDay": 1080,
        "durationMinutes": 60
      }
    },
    "modifiedAt": 1788000000300
  },
  "title": { "data": { "mode": "INHERIT" }, "modifiedAt": 1788000000300 },
  "description": { "data": { "mode": "INHERIT" }, "modifiedAt": 1788000000300 },
  "categoryId": { "data": { "mode": "INHERIT" }, "modifiedAt": 1788000000300 },
  "reminder": { "data": { "mode": "INHERIT" }, "modifiedAt": 1788000000300 }
}
```

## 3. 用户操作映射

| 用户操作 | 保存方式 |
| --- | --- |
| 修改仅此次 | upsert 一条单次调整，只改用户实际触碰的字段 |
| 完成/取消完成仅此次 | upsert 同一逻辑槽的 `status` |
| 删除仅此次 | upsert `status=CANCELLED`，同时清除旧 patch，避免还原后旧时间等内容复活 |
| 还原单次调整 | 物理删除该 OccurrenceAdjustment，实例重新完全继承父系列 |
| 修改整个系列 | 更新 Schedule；单次显式 patch 保留，未覆盖字段继承新的系列值 |
| 修改此次及以后 | 客户端截断旧 Schedule，创建一个 UUID v7 新 Schedule，并迁移边界后的调整 |
| 删除此次及以后 | 截断旧 Schedule，并删除边界后的调整；若边界是第一项则直接删除系列 |
| 关闭重复 | 更新 Schedule 并物理删除它的全部调整 |
| 删除系列 | 物理删除 Schedule，服务端级联删除全部调整 |

有限系列只剩最后一个可见实例时，“删除仅此次”等价于删除整个 Schedule，避免留下永远不会展示的空系列。

## 4. 整系列变化

Schedule 的重复规则保存当前 `anchorDate`。修改整个系列的实际日期时，父系列 timing 与规则 anchor 一起移动，但已有单次调整的 `originalOccurrenceDate` 不改：

- 单次明确修改过日期：继续显示单次日期。
- 单次明确修改过时间：继续显示单次时间。
- 未覆盖的字段：继承修改后的父系列。
- 新规则暂时不再命中某个原始槽：调整保持在同步状态中休眠，不进入 UI、提醒或课表投影。
- 规则以后再次命中该槽：同一调整重新生效。
- 用户完全关闭重复：调整被物理删除，不再保留休眠数据。

“单次调整”管理列表只显示当前规则仍能命中的项目，不展示休眠项。

## 5. 合并与失败

服务端按每个 `AtomicField.modifiedAt` 合并同槽数据，并返回唯一的 canonical `id + version + 完整字段`。客户端保留自己的 `localId`，只更新该行的远端 ID 和版本。

客户端版本低于服务端时仍按各字段 `modifiedAt` 合并；只有客户端声称的版本高于服务端时，才返回 `REJECTED / RESOURCE_CHANGED` 和当前 canonical resource。客户端应用返回的远端快照，同时保留请求期间产生的更高 `localRevision`。父 Schedule 不存在时返回 `SCHEDULE_NOT_FOUND`；分类引用不存在时返回 `CATEGORY_NOT_FOUND`。
