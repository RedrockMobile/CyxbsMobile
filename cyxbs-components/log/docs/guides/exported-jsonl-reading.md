# 导出日志阅读指南

掌邮诊断日志导出文件使用 JSON Lines（JSONL）格式：**每一行都是一个独立、完整的 JSON 对象**，文件整体不是 JSON 数组。首行固定为导出清单，后续行为日志事件。

## 标准示例

```jsonl
{"recordType":"manifest","schemaVersion":2,"generatedAtMillis":1792182031523,"sourceFileCount":1,"exportedLogBytes":1659,"exportedRecordCount":4,"payloadSha256":"ac4a990ce4189f5e921a7bbe3ad61aa6a35678b6dc8d3e619438c772110eafd8","truncated":false}
{"recordType":"log","schemaVersion":2,"session":"8f3a1c9e2b7d4f10","sequence":1,"eventTimestampMillis":1792181900123,"observedTimestampMillis":1792181900123,"level":"Info","tag":"AppLifecycle","message":"application started, platform=Android, version=7.2.0","operation":"app.start","result":"Success","truncated":false}
{"recordType":"log","schemaVersion":2,"session":"8f3a1c9e2b7d4f10","sequence":2,"eventTimestampMillis":1792181902517,"observedTimestampMillis":1792181902517,"level":"Info","tag":"Navigation","message":"navigate to log settings","operation":"navigation","route":"mine/setting/log","fromState":"mine","toState":"log_settings","correlationId":"nav-a1b2c3","truncated":false}
{"recordType":"log","schemaVersion":2,"session":"8f3a1c9e2b7d4f10","sequence":3,"eventTimestampMillis":1792181906124,"observedTimestampMillis":1792181906124,"level":"Warn","tag":"CourseRepository","message":"request retry after timeout","operation":"course.refresh","route":"/magipoke-jwzx/kebiao","method":"Get","durationMillis":5000,"result":"Failure","retryCount":2,"correlationId":"course-sync-17","truncated":false}
{"recordType":"log","schemaVersion":2,"session":"8f3a1c9e2b7d4f10","sequence":4,"eventTimestampMillis":1792181909458,"observedTimestampMillis":1792181909459,"level":"Error","tag":"CourseRepository","message":"course refresh failed after retries","stackTrace":"java.net.SocketTimeoutException: timeout\n\tat com.cyxbs...CourseRepository.refresh(CourseRepository.kt:86)\n\tat com.cyxbs...CourseViewModel.load(CourseViewModel.kt:42)","operation":"course.refresh","result":"Failure","retryCount":3,"correlationId":"course-sync-17","truncated":false}
```

示例仅展示协议形态；实际日志必须遵守项目根目录 `AGENTS.md` 的隐私与等级约束。

## 首行：导出清单

`recordType=manifest` 表示该行描述整次导出：

| 字段 | 含义 |
| --- | --- |
| `schemaVersion` | JSONL 协议版本；新导出当前为 `2`，引擎仍可合并历史 v1 源文件 |
| `generatedAtMillis` | 生成导出文件时的 Unix 毫秒时间戳 |
| `sourceFileCount` | 本次合并的原始日志文件数 |
| `exportedLogBytes` | 合并的原始日志字节数，不含 manifest 自身 |
| `exportedRecordCount` | manifest 后日志行的数量 |
| `payloadSha256` | manifest 后所有原始字节（包括每行换行符）的 SHA-256 小写十六进制摘要 |
| `truncated` | 是否因“最多 3 个源文件”或“最多 32 MiB”限制而省略了更早的数据 |

`payloadSha256` 只能检查分享或传输后内容是否变化，不是数字签名，不能证明文件来源或真实性。

以下内容表示导出成功，但当时没有可导出的日志事件：

```jsonl
{"recordType":"manifest","schemaVersion":2,"generatedAtMillis":1792182031523,"sourceFileCount":0,"exportedLogBytes":0,"exportedRecordCount":0,"payloadSha256":"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855","truncated":false}
```

常见原因包括：刚清理过日志、诊断开关刚开启且尚未产生 `Info` 以上事件，或本次会话没有满足文件等级策略的记录。

## 后续行：日志事件

`recordType=log` 表示一条日志事件：

| 字段 | 含义 |
| --- | --- |
| `schemaVersion` | 日志记录协议版本；v1 使用单个 `timestampMillis`，v2 使用下列双时间戳 |
| `session` | 文件引擎本次运行会话的匿名标识；用于区分重启前后的事件 |
| `sequence` | 同一 `session` 内单调递增的序号；判断事件顺序时优先使用它 |
| `eventTimestampMillis` | 事件实际发生时的 Unix 毫秒时间戳；补记历史事件时可早于观察时间 |
| `observedTimestampMillis` | CyxbsLog 收到该事件时的 Unix 毫秒时间戳 |
| `level` | `Verbose`、`Debug`、`Info`、`Warn`、`Error` 或 `Assert` |
| `tag` | 稳定的类名、文件名或业务链路名 |
| `message` | 进入任意 sink 前已执行有界快照和防御性脱敏的诊断文本 |
| `stackTrace` | 可选；调用方传入 `Throwable` 时记录，未传入时字段省略 |
| `operation` | 可选；固定白名单内的低敏感操作名 |
| `route` | 可选；已移除 query 与 fragment 的归一化路由模板 |
| `method` / `statusCode` / `durationMillis` | 可选；受范围约束的请求方法、状态码与耗时 |
| `result` / `retryCount` | 可选；固定结果枚举与重试次数 |
| `fromState` / `toState` | 可选；低敏感状态迁移名称 |
| `correlationId` | 可选；仅允许 1～64 位 ASCII 字母、数字及 `._:-` 的匿名关联 ID |
| `truncated` | 该条记录是否因脱敏前 64 KiB 快照或最终 16 KiB 单条预算而被截断 |

出现 `[REDACTED]` 表示日志边界识别并替换了疑似凭据、个人信息或长数字。脱敏是兜底机制；不能据此推断原值，也不应尝试还原。

## 推荐阅读顺序

### 1. 先检查 manifest

首先回答五个问题：

1. `sourceFileCount` 是否为 `0`？为 `0` 时文件没有日志事件。
2. `exportedLogBytes` 是否大于 `0`？用于判断导出是否包含原始日志。
3. `exportedRecordCount` 是否等于实际日志行数？
4. `payloadSha256` 是否与 manifest 后原始字节的摘要一致？
5. `truncated` 是否为 `true`？为 `true` 时最早的部分日志可能未包含在导出文件中，不能据此断言异常链完整。

### 2. 定位高等级事件

优先查找 `Error` 和 `Assert`，其次查找紧邻它们之前的 `Warn`：

- `Error`：操作已经失败，通常应结合 `stackTrace` 阅读。
- `Assert`：程序状态违反了内部不变量。
- `Warn`：发生重试、降级或可恢复异常，常是最终失败的前兆。

不要只截取错误行；至少向前查看同一 session 的 10～30 条事件，寻找故障前因。

### 3. 按 session 和 sequence 串联事件

在同一 `session` 内，使用 `sequence` 还原真实写入顺序。不要只依赖 `eventTimestampMillis`，因为系统时钟可能校准或回拨；`observedTimestampMillis` 用于区分“事件发生”和“稍后观察并记录”。

跨 session 比较时可参考 `observedTimestampMillis`，但它仍是 wall clock，不提供全局严格顺序。session 改变通常表示文件引擎或应用经历过重启。历史 v1 行只有 `timestampMillis`，阅读器应按 `schemaVersion` 分支。

### 4. 通过 tag 缩小业务链路

从错误事件的 `tag` 出发，查找相同 tag，以及与其相邻的生命周期、导航、网络和状态迁移事件。理想链路通常类似：

```text
应用启动
  → 页面导航
  → 状态迁移
  → 请求开始/完成
  → 超时或异常
  → 重试/降级
  → Error + stackTrace
```

### 5. 检查记录是否完整

- manifest 的 `truncated=true`：整次导出不包含全部候选源文件。
- log 的 `truncated=true`：该条的 tag、message 或 stackTrace 被截断。
- `stackTrace` 缺失：只表示调用方没有传入 Throwable，不等价于没有异常。
- sequence 跳号：可能有事件因队列、编码、写盘失败或故障退避被丢弃；结合应用内文件日志健康统计判断。

## 命令行阅读

以下命令假设已安装 `jq`，并将路径替换为实际文件。

### 校验 v2 payload 的摘要和行数

manifest 的摘要覆盖首行之后的**原始字节**，包括每条记录结尾的换行符：

```bash
tail -n +2 cyxbs-log-export-v2-*.jsonl | shasum -a 256
tail -n +2 cyxbs-log-export-v2-*.jsonl | wc -l
jq -c 'select(.recordType == "manifest") | {payloadSha256, exportedRecordCount}' cyxbs-log-export-v2-*.jsonl
```

前两条结果应分别等于 manifest 的 `payloadSha256` 和 `exportedRecordCount`。一次只校验一个导出文件，避免 shell 通配符把多个文件串接。

### 格式化查看每一行

```bash
jq . cyxbs-log-export-v2-*.jsonl
```

### 只看日志事件

```bash
jq -c 'select(.recordType == "log")' cyxbs-log-export-v2-*.jsonl
```

### 只看 Warn、Error 和 Assert

```bash
jq -c 'select(.recordType == "log" and (.level == "Warn" or .level == "Error" or .level == "Assert"))' cyxbs-log-export-v2-*.jsonl
```

### 按表格输出关键字段

```bash
jq -r 'select(.recordType == "log") | [.session, .sequence, .eventTimestampMillis, .observedTimestampMillis, .level, .tag, .message] | @tsv' cyxbs-log-export-v2-*.jsonl
```

### 查看某个 tag

```bash
jq -c 'select(.recordType == "log" and .tag == "CourseRepository")' cyxbs-log-export-v2-*.jsonl
```

### 查看被截断的记录

```bash
jq -c 'select(.truncated == true)' cyxbs-log-export-v2-*.jsonl
```

### 将毫秒时间戳显示为 UTC 时间

```bash
jq -r 'select(.recordType == "log") | [(.eventTimestampMillis / 1000 | strftime("%Y-%m-%dT%H:%M:%SZ")), .level, .tag, .message] | @tsv' cyxbs-log-export-v2-*.jsonl
```

## 阅读时的安全边界

- 不要把日志粘贴到公开 Issue、群聊或第三方在线格式化网站。
- 分享前仍需人工检查是否包含未被规则识别的业务敏感信息。
- 不要根据 `[REDACTED]` 猜测或恢复原值。
- 日志只能辅助定位问题，不能代替错误处理、业务状态核验和服务端证据。
- 框架不承诺强杀或断电零丢失；缺少某条日志不能证明对应业务步骤从未发生。

## 一条高质量日志应回答什么

标准日志链应尽量帮助排障者回答：

1. 应用和用户操作进行到哪个阶段？
2. 哪个状态发生了迁移？
3. 哪一步开始异常？
4. 是否发生重试、降级或恢复？
5. 最终失败原因和异常栈是什么？
6. 这次导出或单条记录是否被截断？

目标不是记录得最多，而是在有限预算内保留稳定、低敏感、能够串联故障前因的事件。
