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

CyxbsLog.d(tag = "Schedule") { "加载课表" }
CyxbsLog.e(tag = "Schedule", throwable = error) { "课表加载失败" }
```

- `v/d/i/w/e/a` 和通用 `log` 使用惰性消息，控制台与文件均不接收时不会调用消息 lambda。
- 接受输出时消息只求值一次；`configure` 原子替换不可变配置，一次日志调用使用同一份配置快照。
- 控制台输出同步执行；消息 lambda 和平台 writer 的异常会被日志边界隔离，不改变业务控制流。
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
- 单条记录最多 16 KiB；队列默认最多 1024 条且估算负载不超过 2 MiB。超限时丢弃并可通过 `fileStorageHealth()` 查看计数。
- 每 64 KiB 或最长约 1 秒执行 buffered flush；这只表示交给操作系统，不承诺断电持久化。`Durable` 当前明确返回 `Unsupported`。
- 文件按字节轮转，并按总字节、文件数和年龄清理严格命名前缀的本模块文件。默认总量 24 MiB、最长 7 天。
- 导出先封存当前文件，只选最新 3 个源文件且最多 32 MiB，再发布稳定 JSONL 副本；首行是 `recordType=manifest` 的协议元数据，后续行为 `recordType=log`，被数量或字节预算裁剪时 `truncated=true`。应用层负责调用系统分享或保存界面。导出不会上传，也不会撤回已经分享出去的副本。
- 文件入队前会对常见 token、Authorization、Cookie、密码、邮箱和 8-19 位长数字做防御性脱敏；这是兜底，不替代调用方禁止记录个人数据和业务明文。
- `clear`、`flush`、`export`、`close` 通过可靠控制命令与记录队列排序；管理操作返回结构化结果。瞬态文件操作失败会计入健康状态并尝试重建 active 文件；持续失败时从 250 ms 指数退避到最多 30 s，期间丢弃数由 `droppedWriteBackoff` 暴露，避免逐条 I/O 重试拖累应用。失败中的当前记录可能丢失。

## 应用接入

`AppLogManager` 在主进程初始化控制台策略，并仅在用户已同意隐私政策且主动开启“记录诊断日志”后启动文件层。Android 使用 `noBackupFilesDir` 和 cache + FileProvider；iOS 使用 Application Support（排除备份）和 Caches；Desktop 使用 `~/.cyxbs`；JS/WasmJS 不提供文件层。设置页提供开启、清理、导出并交给系统分享，隐私撤回立即停止后续文件写入。

文件日志不捕获原生崩溃，不承诺强杀或断电零丢失。不得记录学号、凭据、完整请求体等敏感信息；真机锁屏、磁盘满、能耗和性能阈值仍由[本地需求地图](docs/requirements/map.md)跟踪。

## 阅读导出日志

导出文件采用 JSONL：首行为 manifest，后续每行是一条独立日志事件。字段含义、标准示例、空导出判断、排障顺序和 `jq` 筛选命令见[导出日志阅读指南](docs/guides/exported-jsonl-reading.md)。
