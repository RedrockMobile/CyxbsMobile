# CyxbsLog

独立 KMP 日志模块，包名 `com.cyxbs.components.log`。平台控制台复用 Kermit；Android、iOS 与 Desktop 的文件层使用自有协议和 Okio，不依赖 utils、config、account 或 Compose。

业务模块在 `commonMain.dependencies` 添加：

```kotlin
implementation(projects.cyxbsComponents.log)
```

## 控制台日志

应用启动时按构建类型与产品策略配置；未配置时启用 Info 及以上平台控制台日志：

```kotlin
CyxbsLog.configure(
  LogConfig(
    enabled = true,
    minLevel = LogLevel.Debug,
    defaultTag = "Cyxbs",
  )
)

CyxbsLog.d("Schedule", "加载课表")
CyxbsLog.e("Schedule", "课表加载失败", error)
CyxbsLog.log(
  level = LogLevel.Info,
  tag = "ScheduleSync",
  context = LogContext(
    operation = "schedule.refresh",
    route = "/schedule/{week}",
    method = LogHttpMethod.Get,
    durationMillis = 320,
    result = LogResult.Success,
    correlationId = "schedule-sync-a1b2",
  ),
  message = "课表刷新完成",
)
```

- `v/d/i/w/e/a` 和通用 `log` 直接接收 `String`，参数顺序为 `tag, message, throwable`；无 TAG 时可使用 `message = "..."`。字符串在调用前构造。
- TAG 保持调用方自定义的值。`showLocation` 控制消息前是否添加真实调用位置，如 `LessonRepository.kt:166 -> 请求课表失败`；只有 `e` 默认 `true`，其他入口（包括 `log`、`eBounded`）默认 `false`。可以用 `CyxbsLog.d(TAG, "消息", showLocation = true)` 开启，或用 `CyxbsLog.e(TAG, "消息", error, showLocation = false)` 关闭。控制台和 JSONL 使用相同前缀，只保留文件名，不记录构建机绝对路径。
- Android/Desktop 跳过门面和旧日志适配器帧，读取调用方文件和行号；iOS/JS 从运行时堆栈中读取可用的源码位置，JS 可能显示打包后的脚本位置。未保留调试信息、符号被裁剪或 Wasm 无可用调用帧时显示 `Unknown:0`，不会虚构行号。只有 `showLocation=true` 且日志通过输出过滤时才采集位置。
- `configure` 原子替换不可变配置，一次日志调用使用同一份配置快照。控制台与文件均不接收时，不进行异常格式化和输出。
- 控制台输出同步执行；消息、TAG 与异常堆栈在进入任意 sink 前统一执行 UTF-8 有界快照和防御性脱敏，异常格式化和平台 writer 的异常会被日志边界隔离，不改变业务控制流。
- 原始 `Throwable` 不直接交给平台控制台；控制台输出脱敏后的有界堆栈，文件协议仍将消息与堆栈分字段保存。
- 普通异常在遍历时限制为最多 8 个异常、4 层深度、每个异常 4 个 suppressed、32 帧和总计 16 Ki 字符；不会先展开完整异常树。异常属性或堆栈读取失败时丢弃本条，不向业务抛出格式化异常。Wasm 无公开的有界帧接口，仅输出异常树摘要并标记截断。
- 需要机器可读诊断时，为 `log` 传入可选的 `context: LogContext`。
- `LogContext` 只提供固定白名单标量，不接受任意 Map。`route` 会移除 query/fragment，`correlationId` 仅允许 1～64 位 ASCII 字母、数字及 `._:-`，两者都不得承载学号、手机号、设备标识或凭据。
- Kermit 是内部实现，公开 API 不暴露其类型，也不修改其全局 Logger。

## 文件日志

宿主负责提供平台私有且由当前进程独占的日志目录，以及独立的受控导出目录。Android 推荐 `noBackupFilesDir` 与 `cacheDir` 下的专用子目录；iOS 推荐 Application Support 与 Caches 下的专用子目录。业务模块不能自行传入路径。

```kotlin
val result = CyxbsLog.startFileStorage(
  FileLogConfig(
    directory = privateLogDirectory,
    exportDirectory = privateExportDirectory,
    minLevel = LogLevel.Debug,
  )
)

CyxbsLog.flushFileStorage()
val export = CyxbsLog.exportFileStorage()
CyxbsLog.clearFileStorage()
CyxbsLog.closeFileStorage()
```

- Android、iOS、Desktop：普通日志通过条数和估算字节双预算非阻塞接收，由单 worker 写入 UTF-8 JSONL。
- JS、WasmJS：文件管理 API 返回 `Unsupported`，控制台日志不受影响。
- 单条记录最多 16 KiB；队列默认最多 1024 条且估算负载不超过 2 MiB。`Verbose/Debug/Info` 最多使用 80% 的条数和字节预算，剩余 20% 为 `Warn/Error/Assert` 保留；高等级可使用低等级未占用的空间，但总预算始终不变。所有路径仍使用非阻塞接收。
- 超限时直接丢弃；`fileStorageHealth()` 同时提供总原因计数和 `droppedByLevel`，用于区分普通洪峰与高等级事件丢失。
- 每 64 KiB 或最长约 1 秒执行 buffered flush；这只表示交给操作系统，不承诺断电持久化。`Durable` 当前明确返回 `Unsupported`。
- 文件按字节轮转，并按总字节、文件数和年龄清理严格命名前缀的本模块文件。默认总量 24 MiB、最长 7 天。v2 文件使用持久化单调 generation 排序，系统时间回拨不再改变新文件的导出与配额优先级；历史 v1 文件仍可恢复、导出和清理。
- 导出先封存当前文件，只选最新 3 个源文件且最多 32 MiB，再发布稳定 JSONL 副本；v2 manifest 记录 payload 原始字节数、日志行数和 SHA-256，后续为 schema v2 日志行。哈希覆盖 manifest 后的精确原始字节并包含换行，只用于检查传输完整性，不是数字签名或真实性证明。被数量或字节预算裁剪时 `truncated=true`。
- v2 日志行区分 `eventTimestampMillis` 与 `observedTimestampMillis`，并可携带受限结构化上下文；旧文本调用仍生成合法 v2 事件。应用层负责调用系统分享或保存界面。导出不会上传，也不会撤回已经分享出去的副本。
- 文件入队前会对常见 token、Authorization、Cookie、密码、邮箱和 8-19 位长数字做防御性脱敏；这是兜底，不替代调用方禁止记录个人数据和业务明文。
- `clear`、`flush`、`export`、`close` 通过可靠控制命令与记录队列排序；管理操作返回结构化结果。瞬态文件操作失败会计入健康状态并尝试重建 active 文件；持续失败时从 250 ms 指数退避到最多 30 s，期间丢弃数由 `droppedWriteBackoff` 暴露，避免逐条 I/O 重试拖累应用。失败中的当前记录可能丢失。
- 日志在异常格式化前取得绑定引擎与清理版本的采集许可，入队时复核；跨越关闭再开启、清理或引擎替换的在途调用不能重新落盘。
- v2 manifest 额外携带 `collectionPolicy` 和 `health`：说明当前引擎会话的等级/保留策略、各等级丢弃、写失败与最近成功/失败时间。统计只覆盖当前引擎会话，不能重建历史会话缺口；`truncated=false` 仅表示导出未裁剪源文件，不代表采集期间没有丢失。
- `fileStorageHealth()` 是无 I/O 快照，除队列与丢弃计数外还提供最近成功写入、最近失败时间/异常类型、连续失败数、最近 flush 耗时，以及当前受管文件数/字节数；异常文本和路径不会进入健康状态。

## 应用接入

`AppLogManager` 在主进程初始化控制台策略，并仅在用户已同意隐私政策且主动开启“记录诊断日志”后启动文件层。Android 使用 `noBackupFilesDir` 和 cache + FileProvider；文件层启动后在 API 30+ 读取最近一次 `ApplicationExitInfo` 的 reason/status/importance/timestamp 元数据，不读取 trace 或 description。现有 `CrashMonitor` 将原始 `Throwable` 交给 `eBounded`，只记录有界的异常类型与消息摘要，不同步展开 stack/cause/suppressed；仅在文件引擎已运行时对 buffered flush 等待最多 250 ms，不会在崩溃线程启动存储。iOS 使用 Application Support（排除备份）和 Caches，平台文件准备与元数据校验在后台 dispatcher 完成，并显式验证 `CompleteUntilFirstUserAuthentication`；任一保护或备份属性设置失败都会回滚启动。用户采集策略生效且文件引擎启动成功后订阅 MetricKit，只写入 crash/hang/cpu/disk 四类诊断数量，不读取调用栈、终止原因或原始 payload，关闭开关或撤回隐私时退订。Desktop 使用 `~/.cyxbs`；JS/WasmJS 不提供文件层。设置页提供开启、清理、导出并交给系统分享，隐私撤回立即停止后续文件写入。

文件日志不承诺捕获原生崩溃，也不承诺强杀或断电零丢失；Android 历史退出和 CrashMonitor 只增加诊断线索，不构成崩溃持久化保证。不得记录学号、凭据、完整请求体等敏感信息；iOS 文件保护的首次解锁/锁屏后台行为、真机磁盘满、能耗和性能阈值仍由[本地需求地图](docs/requirements/map.md)跟踪。

## 阅读导出日志

导出文件采用 JSONL：首行为 manifest，后续每行是一条独立日志事件。字段含义、标准示例、空导出判断、排障顺序和 `jq` 筛选命令见[导出日志阅读指南](docs/guides/exported-jsonl-reading.md)。
