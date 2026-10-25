# Schedule 接口协议

> 本文是客户端与后端当前协议的事实源。历史 batch、receipt、cursor、墓碑和套壳资源模型均已废弃。

## 1. 接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/schedule/sync` | 首次进入、存在 pending 时的网络恢复、用户主动同步 |
| `POST` | `/schedule` | 日常新增产生的逐资源操作 |
| `PUT` | `/schedule` | 日常修改产生的逐资源操作 |
| `DELETE` | `/schedule` | 日常物理删除产生的逐资源操作 |
| `DELETE` | `/schedule/all` | 三次确认后物理清空当前认证账号全部数据 |

客户端实际通过 Ktorfit 请求 `magipoke-todo` 服务前缀下的这些路径。所有成功处理的响应使用项目统一结构：

```json
{
  "data": {},
  "status": 10000,
  "info": "success"
}
```

日常三个接口共用 `MutationRequest` 与 `MutationResponse`。一次请求可以带分类、日程和单次调整，但每条资源独立返回结果；不提供跨资源远端事务。

## 2. 标识与版本

| 资源 | 客户端本地标识 | 服务端标识 | 创建规则 |
| --- | --- | --- | --- |
| Category | UUID v7 | 自增 `Long` | 创建时临时上传 `localId`，响应后保存远端 `id` |
| Schedule | UUID v7；旧数据迁移为确定性 UUID v5 | 同一个 UUID 字符串 | 客户端创建并直接上传，服务端不改写 |
| OccurrenceAdjustment | UUID v7 | 自增 `Long` | 创建时临时上传 `localId`，响应后保存远端 `id` |

Category 与 OccurrenceAdjustment 的 `localId` 只在首次创建请求中临时上传，用于把响应映射回客户端本地行；它们都不写入服务端数据库。新日程或单次调整通过 `categoryLocalId` 引用同请求刚创建的分类。已有远端资源更新时上传 `id + version`，不再上传 `localId`。

Schedule 使用客户端 UUID 是为了让旧清单和旧事务可重复迁移：同一个旧资源始终生成相同 UUID v5，服务端因此不会新增第二条。普通新建和拆分出来的新系列使用 UUID v7。

每个远端资源只有一个递增 `version`。创建上传 `version=0`，成功响应从 `version=1` 开始；更新上传客户端最后确认的版本。删除不上传版本，采用物理删除且重复删除仍返回成功。

## 3. 原子字段

需要独立处理并发的字段统一使用：

```json
{
  "data": "字段值",
  "modifiedAt": 1788000000100
}
```

服务端按每个字段的 `modifiedAt` 执行 LWW 合并：时间戳更早的输入不会覆盖当前值；时间戳相同但值不同的输入按本次请求覆盖。一次资源更新只要有字段胜出，就写入完整 canonical JSON，并仅把资源 `version` 增加一次。客户端正常编辑会生成递增时间戳，不额外为极少出现的同毫秒并发编辑增加仲裁字段。

分类的 `color.data` 是可空 JSON 字符串。服务端不理解其中的背景色和文字色结构，只保存和返回字符串；客户端 UI 负责解析。

## 4. 日常请求示例

下例在同一个请求中创建分类并创建引用该分类的日程。`categoryLocalId` 只引用本请求 `categories.upserts[].localId`：

```json
{
  "categories": {
    "upserts": [
      {
        "localId": "019d0000-0000-7000-8000-000000000001",
        "version": 0,
        "name": { "data": "学习", "modifiedAt": 1788000000000 },
        "color": {
          "data": "{\"lightBackground\":\"#FFEFEFEF\",\"lightContent\":\"#FF8E8E8E\",\"darkBackground\":\"#8C434752\"}",
          "modifiedAt": 1788000000000
        },
        "sortOrder": { "data": 0, "modifiedAt": 1788000000000 }
      }
    ],
    "deletes": []
  },
  "schedules": {
    "upserts": [
      {
        "id": "019d0000-0000-7000-8000-000000000101",
        "version": 0,
        "kind": "TODO",
        "title": { "data": "复习高数", "modifiedAt": 1788000000100 },
        "description": { "data": "第二章", "modifiedAt": 1788000000100 },
        "categoryId": { "data": null, "modifiedAt": 1788000000100 },
        "categoryLocalId": "019d0000-0000-7000-8000-000000000001",
        "timing": {
          "data": { "kind": "DEADLINE", "dueAt": 1788048000000 },
          "modifiedAt": 1788000000100
        },
        "recurrence": { "data": null, "modifiedAt": 1788000000100 },
        "reminder": {
          "data": { "minutesBefore": 10 },
          "modifiedAt": 1788000000100
        },
        "todoState": { "data": "OPEN", "modifiedAt": 1788000000100 },
        "linkedToCourse": { "data": true, "modifiedAt": 1788000000100 }
      }
    ],
    "deletes": []
  },
  "occurrenceAdjustments": {
    "upserts": [],
    "deletes": []
  }
}
```

服务端固定先处理分类，再处理日程和单次调整。分类创建成功后，后两类资源中的 `categoryLocalId` 会解析成刚生成的远端分类 ID；分类失败时，只拒绝依赖它的资源，不影响其他资源。

## 5. 日常响应示例

响应列表与请求的 `upserts`、`deletes` 按下标严格对应：

```json
{
  "data": {
    "categories": {
      "upsertResults": [
        {
          "result": "SUCCESS",
          "resource": {
            "id": 41,
            "version": 1,
            "name": { "data": "学习", "modifiedAt": 1788000000000 },
            "color": {
              "data": "{\"lightBackground\":\"#FFEFEFEF\",\"lightContent\":\"#FF8E8E8E\",\"darkBackground\":\"#8C434752\"}",
              "modifiedAt": 1788000000000
            },
            "sortOrder": { "data": 0, "modifiedAt": 1788000000000 }
          }
        }
      ],
      "deleteResults": []
    },
    "schedules": {
      "upsertResults": [
        {
          "result": "SUCCESS",
          "resource": {
            "id": "019d0000-0000-7000-8000-000000000101",
            "version": 1,
            "kind": "TODO",
            "title": { "data": "复习高数", "modifiedAt": 1788000000100 },
            "description": { "data": "第二章", "modifiedAt": 1788000000100 },
            "categoryId": { "data": 41, "modifiedAt": 1788000000100 },
            "timing": {
              "data": { "kind": "DEADLINE", "dueAt": 1788048000000 },
              "modifiedAt": 1788000000100
            },
            "recurrence": { "data": null, "modifiedAt": 1788000000100 },
            "reminder": { "data": { "minutesBefore": 10 }, "modifiedAt": 1788000000100 },
            "todoState": { "data": "OPEN", "modifiedAt": 1788000000100 },
            "linkedToCourse": { "data": true, "modifiedAt": 1788000000100 }
          }
        }
      ],
      "deleteResults": []
    },
    "occurrenceAdjustments": {
      "upsertResults": [],
      "deleteResults": []
    }
  },
  "status": 10000,
  "info": "success"
}
```

单条操作结果只有：

- `SUCCESS`：成功创建、合并、更新或删除；upsert 必须返回完整 canonical resource。
- `REJECTED`：该项没有落库，携带稳定 `reason` 和可安全展示的 `info`；其他项仍可成功。
- `DELETED`：客户端尝试更新一个已确定在服务端不存在的资源，客户端应清除对应远端快照和 pending。

分类名称保存前只去除首尾空白，保留用户输入的大小写、中文和标点。判重时忽略大小写及首尾空白，但不折叠全角/半角或其他不同标点。分类创建遇到同 owner 的同名分类时不新增数据，返回已有分类的 canonical resource；客户端把本地引用合并到该远端 ID。分类更新产生同名冲突时返回 `REJECTED / DUPLICATE_CATEGORY_NAME`。

单次调整创建遇到同一个 `scheduleId + originalOccurrenceDate` 时不新增第二条，服务端按字段时间戳合并并返回已有调整的自增 ID。

## 6. 同步请求与响应

每种资源的同步请求均有三个列表：

```json
{
  "confirmed": [{ "id": 41, "version": 3 }],
  "upserts": [],
  "deletes": []
}
```

- `confirmed`：客户端已持有的远端 ID 与版本。
- `upserts`：当前全部待提交完整快照。
- `deletes`：当前全部待提交物理删除。

对应响应为：

```json
{
  "confirmedResults": [
    { "id": 41, "result": "CONFIRMED" }
  ],
  "discoveredResults": [],
  "upsertResults": [],
  "deleteResults": []
}
```

- `confirmedResults` 与 `confirmed` 对齐：`CONFIRMED` 表示版本一致，`CHANGED` 携带完整新资源，`DELETED` 表示远端已不存在。
- `discoveredResults` 下发服务端存在、但不在客户端 confirmed/upsert/delete 中的完整资源。
- `upsertResults` 与 `deleteResults` 分别与请求同名列表位置对齐。

客户端先把本地命令写入 Room，再尝试日常接口。网络失败、超时或 5xx 时保留 pending；下次进入、网络恢复且确有 pending，或用户主动同步时由 `/schedule/sync` 收敛。响应只能清除其捕获的 `localRevision`，请求期间产生的更高 revision 必须保留。

## 7. 删除和业务约束

- 分类、日程和单次调整都使用物理删除，不保存服务端墓碑。
- 删除日程级联删除其单次调整；关闭重复规则也删除该日程的全部单次调整。
- 分类仍被日程或单次调整引用时返回 `CATEGORY_IN_USE`；客户端删除入口也要先拦截。
- `/schedule/all` 只使用 token 对应 owner，不接受账号参数，并在同一数据库事务中清空三张表。
- Schedule 标题和分类名称 trim 后不能为空；同 owner 分类名称按忽略大小写和首尾空白的规则不能重复。
- timing 支持 `TIMED`、`DEADLINE`、单日 `ALL_DAY` 和迁移兼容的 `UNSCHEDULED`。
- 新 UI 不创建 `UNSCHEDULED`；该类型不能重复、提醒或关联课表。
- recurrence 支持 `DAILY`、`WEEKLY`、`MONTHLY`、`YEARLY`。
- reminder 只有一条；`minutesBefore=0` 表示准时提醒，负数非法。
- `TODO` 必须属于清单；`AFFAIR` 必须是 `TIMED` 且关联课表，事务关联清单后才具有完成态。

## 8. 错误边界

- 结构合法并进入逐项处理：HTTP 200、`status=10000`，具体失败见单项 `REJECTED`。
- 畸形 JSON、未知或重复字段、缺少必须的列表等无法逐项对应的请求：HTTP 400、`status>=20000`。
- 认证失败：HTTP 401/403。
- 服务端内部错误：HTTP 5xx，不向客户端回显数据库、堆栈、凭证或完整 payload。

`reason` 只使用稳定枚举：`INVALID_REQUEST`、`RESOURCE_CHANGED`、`CATEGORY_NOT_FOUND`、`DUPLICATE_CATEGORY_NAME`、`CATEGORY_IN_USE`、`SCHEDULE_NOT_FOUND`、`UNSUPPORTED_RECURRENCE`。`info` 可以说明纯业务字段约束，但不得包含 token、Authorization、Cookie、数据库细节或用户实际输入值。
