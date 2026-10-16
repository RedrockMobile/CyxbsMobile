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
- 控制台输出同步执行，消息 lambda 和平台 writer 的异常仍向调用方传播。
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
- 导出先封存当前文件，再发布稳定 JSONL 副本；应用层负责调用系统分享或保存界面。导出不会上传，也不会撤回已经分享出去的副本。
- `clear`、`flush`、`export`、`close` 通过可靠控制命令与记录队列排序；管理操作返回结构化结果。瞬态文件操作失败会计入健康状态并尝试重建 active 文件，但失败中的当前记录可能丢失。

文件日志不自动脱敏，也不捕获原生崩溃。不得记录学号、凭据、完整请求体等敏感信息；接入应用前仍需确定采集政策和目录保护策略。后续集成与真机验证由[本地需求地图](../../docs/requirements/cyxbs-log/map.md)跟踪。
