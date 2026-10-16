# 导出日志阅读指南

掌邮诊断日志导出文件使用 JSON Lines（JSONL）格式：**每一行都是一个独立、完整的 JSON 对象**，文件整体不是 JSON 数组。首行固定为导出清单，后续行为日志事件。

## 标准示例

```jsonl
{"recordType":"manifest","schemaVersion":1,"generatedAtMillis":1792182031523,"sourceFileCount":1,"exportedLogBytes":2486,"truncated":false}
{"recordType":"log","schemaVersion":1,"session":"8f3a1c9e2b7d4f10","sequence":1,"timestampMillis":1792181900123,"level":"Info","tag":"AppLifecycle","message":"application started, platform=Android, version=7.2.0","truncated":false}
{"recordType":"log","schemaVersion":1,"session":"8f3a1c9e2b7d4f10","sequence":2,"timestampMillis":1792181902517,"level":"Info","tag":"Navigation","message":"navigate: route=mine/setting/log, source=mine","truncated":false}
{"recordType":"log","schemaVersion":1,"session":"8f3a1c9e2b7d4f10","sequence":3,"timestampMillis":1792181906124,"level":"Warn","tag":"CourseRepository","message":"request retry: route=/magipoke-jwzx/kebiao, attempt=2, reason=timeout","truncated":false}
{"recordType":"log","schemaVersion":1,"session":"8f3a1c9e2b7d4f10","sequence":4,"timestampMillis":1792181909458,"level":"Error","tag":"CourseRepository","message":"course refresh failed after retries","stackTrace":"java.net.SocketTimeoutException: timeout\n\tat com.cyxbs...CourseRepository.refresh(CourseRepository.kt:86)\n\tat com.cyxbs...CourseViewModel.load(CourseViewModel.kt:42)","truncated":false}
```

示例仅展示协议形态；实际日志必须遵守项目根目录 `AGENTS.md` 的隐私与等级约束。

## 首行：导出清单

`recordType=manifest` 表示该行描述整次导出：

| 字段 | 含义 |
| --- | --- |
| `schemaVersion` | JSONL 协议版本；当前为 `1` |
| `generatedAtMillis` | 生成导出文件时的 Unix 毫秒时间戳 |
| `sourceFileCount` | 本次合并的原始日志文件数 |
| `exportedLogBytes` | 合并的原始日志字节数，不含 manifest 自身 |
| `truncated` | 是否因“最多 3 个源文件”或“最多 32 MiB”限制而省略了更早的数据 |

以下内容表示导出成功，但当时没有可导出的日志事件：

```jsonl
{"recordType":"manifest","schemaVersion":1,"generatedAtMillis":1792182031523,"sourceFileCount":0,"exportedLogBytes":0,"truncated":false}
```

常见原因包括：刚清理过日志、诊断开关刚开启且尚未产生 `Info` 以上事件，或本次会话没有满足文件等级策略的记录。

## 后续行：日志事件

`recordType=log` 表示一条日志事件：

| 字段 | 含义 |
| --- | --- |
| `schemaVersion` | 日志记录协议版本 |
| `session` | 文件引擎本次运行会话的匿名标识；用于区分重启前后的事件 |
| `sequence` | 同一 `session` 内单调递增的序号；判断事件顺序时优先使用它 |
| `timestampMillis` | 事件发生时的 Unix 毫秒时间戳；系统时钟可能被校准或回拨 |
| `level` | `Verbose`、`Debug`、`Info`、`Warn`、`Error` 或 `Assert` |
| `tag` | 稳定的类名、文件名或业务链路名 |
| `message` | 已经过文件层防御性脱敏的诊断文本 |
| `stackTrace` | 可选；调用方传入 `Throwable` 时记录，未传入时字段省略 |
| `truncated` | 该条记录是否因脱敏前 64 KiB 快照或最终 16 KiB 单条预算而被截断 |

出现 `[REDACTED]` 表示文件层识别并替换了疑似凭据、个人信息或长数字。脱敏是兜底机制；不能据此推断原值，也不应尝试还原。

## 推荐阅读顺序

### 1. 先检查 manifest

首先回答三个问题：

1. `sourceFileCount` 是否为 `0`？为 `0` 时文件没有日志事件。
2. `exportedLogBytes` 是否大于 `0`？用于判断导出是否包含原始日志。
3. `truncated` 是否为 `true`？为 `true` 时最早的部分日志可能未包含在导出文件中，不能据此断言异常链完整。

### 2. 定位高等级事件

优先查找 `Error` 和 `Assert`，其次查找紧邻它们之前的 `Warn`：

- `Error`：操作已经失败，通常应结合 `stackTrace` 阅读。
- `Assert`：程序状态违反了内部不变量。
- `Warn`：发生重试、降级或可恢复异常，常是最终失败的前兆。

不要只截取错误行；至少向前查看同一 session 的 10～30 条事件，寻找故障前因。

### 3. 按 session 和 sequence 串联事件

在同一 `session` 内，使用 `sequence` 还原真实写入顺序。不要只依赖 `timestampMillis`，因为系统时钟可能校准或回拨。

跨 session 比较时才主要参考 `timestampMillis`。session 改变通常表示文件引擎或应用经历过重启。

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

### 格式化查看每一行

```bash
jq . cyxbs-log-export-v1-*.jsonl
```

### 只看日志事件

```bash
jq -c 'select(.recordType == "log")' cyxbs-log-export-v1-*.jsonl
```

### 只看 Warn、Error 和 Assert

```bash
jq -c 'select(.recordType == "log" and (.level == "Warn" or .level == "Error" or .level == "Assert"))' cyxbs-log-export-v1-*.jsonl
```

### 按表格输出关键字段

```bash
jq -r 'select(.recordType == "log") | [.session, .sequence, .timestampMillis, .level, .tag, .message] | @tsv' cyxbs-log-export-v1-*.jsonl
```

### 查看某个 tag

```bash
jq -c 'select(.recordType == "log" and .tag == "CourseRepository")' cyxbs-log-export-v1-*.jsonl
```

### 查看被截断的记录

```bash
jq -c 'select(.truncated == true)' cyxbs-log-export-v1-*.jsonl
```

### 将毫秒时间戳显示为 UTC 时间

```bash
jq -r 'select(.recordType == "log") | [(.timestampMillis / 1000 | strftime("%Y-%m-%dT%H:%M:%SZ")), .level, .tag, .message] | @tsv' cyxbs-log-export-v1-*.jsonl
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
