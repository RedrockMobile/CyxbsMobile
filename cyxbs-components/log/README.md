# CyxbsLog

## 模块介绍

CyxbsLog 是掌邮统一的 KMP 日志入口，包名为 `com.cyxbs.components.log`。它提供等级过滤、可选调用位置、异常记录、结构化上下文，以及受控的文件采集、清理和导出。

模块不依赖 utils、config、account 或 Compose。Kermit 仅作为内部的平台控制台实现，文件层使用 Okio 和自有 JSONL 协议；公开 API 不暴露 Kermit 类型，也不修改其全局 Logger。

| 平台 | 控制台日志 | 文件日志 |
| --- | --- | --- |
| Android / iOS / Desktop | 支持 | 支持 |
| JS / WasmJS | 支持 | 管理 API 返回 `Unsupported` |

适合记录启动、状态变化、请求结果和异常等业务诊断信息。避免在循环、动画、重组或高频回调中持续输出日志。

## 如何使用

### 添加依赖

业务模块在 `commonMain.dependencies` 中添加：

```kotlin
implementation(projects.cyxbsComponents.log)
```

### 记录日志

快捷方法使用 `tag, message, throwable` 参数顺序，消息直接传 `String`，异常可省略：

```kotlin
private const val TAG = "Schedule"

CyxbsLog.d(TAG, "开始刷新课表")
CyxbsLog.i(TAG, "课表刷新完成")
CyxbsLog.w(TAG, "课表刷新失败，使用本地缓存", error)
CyxbsLog.e(TAG, "课表加载失败", error)

// 不指定 TAG 时使用配置中的 defaultTag。
CyxbsLog.i(message = "应用初始化完成")
```

TAG 由调用方自定义，同一文件或类保持一致；跨文件业务链路可以共享 TAG。不要把用户信息、动态 URL 或请求 ID 拼入 TAG，需要关联并发事件时在消息或 `LogContext` 中使用非敏感关联 ID。

字符串在调用前构造。普通短文本直接传入即可；若需要序列化大对象等昂贵诊断工作，先判断 `CyxbsLog.isEnabled(level)`。诊断内容不得包含业务副作用。

### 使用 utils 日志快捷方法

已有业务代码可以继续使用 `com.cyxbs.components.utils.extensions` 中的 `log` / `logg`，底层统一转发到 CyxbsLog，无需逐个替换调用点：

```kotlin
logg("请求课表数据异常")         // Debug，固定 TAG 为 ggg；也接受 Any?。
log("调试信息")                 // Debug，固定 TAG 为 ggg。
log("Schedule", "开始同步")     // Debug，使用指定 TAG。
log(LogLevel.Warn, "Schedule", "同步失败，使用缓存")
```

`log` 的等级重载参数顺序为 `level, tag, msg`；这些快捷方法保留旧日志展示调用文件和行号的行为，由 CyxbsLog 统一采集位置，不在调用方重复拼接。

### 选择日志等级

| 方法 | 适用场景 | 示例 |
| --- | --- | --- |
| `v` | 临时排查细粒度执行过程，严格控制频率 | 内部处理步骤、状态流的详细变化 |
| `d` | 开发调试细节 | 缓存命中、分支选择、请求开始 |
| `i` | 正常、低频且有诊断意义的事件 | 启动完成、前后台切换、页面导航、同步成功 |
| `w` | 出现异常，但能够恢复、重试或降级 | 请求失败后使用缓存、准备重试 |
| `e` | 操作最终失败，需要定位原因 | 重试耗尽、数据解析失败、保存失败 |
| `a` | 程序内部不变量被破坏 | 按设计不可能出现的状态 |

使用时遵循以下约定：

- 是否还能恢复，是区分 `w` 和 `e` 的主要依据；不要把普通网络失败一律记为 `e`。
- 用户取消、正常输入校验失败通常不需要记 `e`；协程正常取消也不应作为业务异常记录。
- 同一个异常尽量在负责处理它的边界记录一次，避免层层重复。通过 `throwable` 传入原始异常，不自行拼接堆栈。
- 日志不能代替错误处理。`a` 只输出日志，不会主动抛异常或终止程序；需要阻止错误状态继续执行时，仍要使用 `check` 等处理。
- 文件层默认接收 `Info` 及以上，`Verbose/Debug` 用于开发细节。实际采集还受隐私同意、用户开关和宿主配置控制。

### 展示文件和行号

`showLocation` 控制消息前缀，业务侧只传正文：

```kotlin
// e 默认开启，输出类似 LessonRepository.kt:166 -> 课表加载失败。
CyxbsLog.e(TAG, "课表加载失败", error)
CyxbsLog.e(TAG, "课表加载失败", error, showLocation = false)

// 其他入口默认关闭，包括通用 log 和 eBounded。
CyxbsLog.d(TAG, "检查缓存", showLocation = true)
```

控制台和文件使用相同的位置前缀，只保留文件名。Android/Desktop 读取调用方文件和行号；iOS/JS 使用运行时可用的源码位置，JS 可能显示打包后的脚本位置。符号或调试信息不可用，以及 Wasm 无可用调用帧时显示 `Unknown:0`。

只有日志通过输出过滤且 `showLocation=true` 时才采集位置；采集调用栈有额外成本，避免在高频日志中普遍开启。

### 记录结构化上下文

需要机器可读诊断时，使用通用入口的 `context`：

```kotlin
CyxbsLog.log(
  level = LogLevel.Info,
  tag = "ScheduleSync",
  message = "课表刷新完成",
  context = LogContext(
    operation = "schedule.refresh",
    route = "/schedule/{week}",
    method = LogHttpMethod.Get,
    durationMillis = 320,
    result = LogResult.Success,
    correlationId = "schedule-sync-a1b2",
  ),
)
```

`LogContext` 只提供白名单标量，不接受任意 Map。`route` 应使用归一化路径，框架会移除 query/fragment；`correlationId` 仅允许 1～64 位 ASCII 字母、数字及 `._:-`，不得承载个人信息或凭据。日志分别保存事件时间 `eventTimestampMillis` 和采集时间 `observedTimestampMillis`。

### 配置与文件管理：由宿主负责

应用启动时按构建类型和产品策略配置；未配置时启用 Info 及以上平台控制台日志，框架不会自动识别 Debug/Release：

```kotlin
CyxbsLog.configure(
  LogConfig(
    enabled = true,
    minLevel = LogLevel.Debug,
    defaultTag = "Cyxbs",
  )
)
```

业务模块只记录日志。掌邮由 `AppLogManager` 统一管理文件层的隐私同意、用户开关、主进程限制和生命周期，业务模块不得自行创建 `FileLogConfig`、拼接日志目录或启动文件引擎。

以下 API 供宿主接入，管理方法为挂起函数，应检查返回的结构化结果：

```kotlin
val started = CyxbsLog.startFileStorage(
  FileLogConfig(
    directory = privateLogDirectory,
    exportDirectory = privateExportDirectory,
    minLevel = LogLevel.Info,
  )
)

val flushed = CyxbsLog.flushFileStorage()
val exported = CyxbsLog.exportFileStorage()
val health = CyxbsLog.fileStorageHealth()
val cleared = CyxbsLog.clearFileStorage()
val closed = CyxbsLog.closeFileStorage()
```

目录必须为应用私有、由当前进程独占，导出使用独立受控目录。隐私撤回或关闭采集时，宿主先调用 `setFileStorageCollectionEnabled(false)` 阻止后续文件采集，再异步关闭引擎；该开关不影响控制台。

掌邮现有接入：

| 平台 | 接入方式 |
| --- | --- |
| Android | 主进程使用 `noBackupFilesDir` 保存日志，cache 配合 FileProvider 分享；API 30+ 只采集历史退出的 reason/status/importance/timestamp，不读取 trace 或 description |
| iOS | 使用排除备份的 Application Support 和 Caches，在后台准备目录并验证 `CompleteUntilFirstUserAuthentication`；失败则回滚启动。MetricKit 仅记录 crash/hang/cpu/disk 四类诊断数量，关闭采集时退订 |
| Desktop | 使用 `~/.cyxbs` |

“记录诊断日志”默认开启，文件采集仅在主进程且用户已同意隐私政策后启动；用户手动关闭后保留关闭状态，重启或再次登录不会自动覆盖。设置页提供开启、清理和导出，应用层负责系统分享或保存；导出本身不会上传，也无法撤回已经分享的副本。

## 设计框架与理念

### 处理流程

```text
业务调用 CyxbsLog
  → 配置快照与输出等级判断
  → 按需采集调用位置，有界处理消息 / TAG / 异常并脱敏
  ├─ 平台控制台：同步输出
  └─ 文件层：非阻塞入队 → 单 worker 编码 JSONL → 缓冲写入 / 轮转 / 清理
```

`configure` 原子替换不可变配置，一次调用使用同一份快照。两个输出端均不接收时，跳过异常格式化和输出。文件写入异步执行，但脱敏、异常格式化、位置采集和控制台输出仍在调用线程完成。

### 有界成本与故障隔离

以可控的诊断成本为目标，不追求无条件保留每条日志：

| 环节 | 当前边界 |
| --- | --- |
| 异常格式化 | 最多 8 个异常、4 层深度、每个异常 4 个 suppressed、32 帧，总计 16 Ki 字符；Wasm 仅记录异常树摘要 |
| 文件记录 | 每条 JSONL 最多 16 KiB |
| 记录队列 | 最多 1024 条，估算负载不超过 2 MiB；低等级最多占用 80%，给 Warn/Error/Assert 预留 20% |
| 缓冲刷新 | 每 64 KiB 或最长约 1 秒执行 buffered flush |
| 文件保留 | 单文件最多 4 MiB，最多 8 个受管文件；默认总量 24 MiB、保留年龄 7 天 |
| 目录清理 | 普通写入只检查内存字节计数；即将超出总量、轮转、启动、恢复或导出时扫描。持续写入时距上次清理满 1 分钟后，在下一条写入时检查过期文件；时钟回拨时重新检查 |

队列超限直接丢弃，不阻塞业务等待磁盘。文件层由一个 worker 顺序处理记录和管理命令；瞬态 I/O 失败后尝试恢复，持续失败从 250 ms 指数退避到最多 30 秒，避免逐条重试。异常格式化或平台 writer 失败不会向业务抛出；格式化失败或写入失败中的记录可能丢失。

普通异常先进行有界遍历，再脱敏和输出，原始 `Throwable` 不直接交给控制台。崩溃紧急路径使用 `eBounded`，只记录有界类型与消息摘要，不展开堆栈、cause 或 suppressed。现有 `CrashMonitor` 仅在文件引擎运行时等待 buffered flush，最多 250 ms，不在崩溃线程启动存储。

### 隐私与采集边界

调用方首先遵循最小化原则：禁止记录姓名、学号、手机号、邮箱、凭据、完整请求/响应 body、完整 query/header、动态 URL 或其他业务明文。网络日志只记录 method、归一化 route、status、耗时和非敏感关联 ID。

消息、TAG 和异常在进入任意输出端前统一执行 UTF-8 有界快照与防御性脱敏，覆盖常见 token、Authorization、Cookie、密码、邮箱和 8～19 位长数字。框架脱敏仅作兜底，不能代替调用方选择安全内容。

日志在格式化前取得绑定引擎与采集版本的许可，入队时复核。跨越隐私开关关闭再开启、清理或引擎替换的在途调用，不能重新落盘。

### 可观察性与导出

`fileStorageHealth()` 提供无 I/O 的内存快照，包括队列、接收数、按原因和等级统计的丢弃数、截断、写失败、最近成功/失败时间、flush 耗时和受管文件数/字节数。异常文本和路径不会进入健康状态。`droppedByLevel` 表示各等级丢失数量，不是输出数量。

导出先封存当前文件，选择最新 3 个源文件且最多 32 MiB，生成独立 JSONL 副本。首行为 manifest，随后为 schema v2 日志行；manifest 包含采集策略、当前引擎会话健康统计，以及 payload 的字节数、行数和 SHA-256。

哈希覆盖 manifest 后的精确原始字节（含换行），用于检查传输完整性，不证明日志真实性。`truncated=true` 表示源文件被导出预算裁剪；`false` 不代表采集期间没有丢失。健康统计仅覆盖当前引擎会话，不能重建历史会话缺口。

v2 文件通过持久化单调 generation 排序，时钟回拨不会改变新文件的导出和配额优先级；历史 v1 文件仍可恢复、清理和导出。清理只操作严格命名前缀的本模块文件。

buffered flush 只表示数据已交给操作系统；`Durable` 当前返回 `Unsupported`。本模块不承诺原生崩溃捕获、强杀或断电零丢失，历史退出和崩溃记录仅提供诊断线索。

## 进一步阅读与验证

- [导出日志阅读指南](docs/guides/exported-jsonl-reading.md)：字段含义、空导出判断、排障顺序与 `jq` 示例。
- [持久化等级与隐私依据](docs/research/mobile-log-persistence-policy.md)：采集策略的依据。
- [本地需求地图](docs/requirements/map.md)：API、设计和验证记录；真机磁盘满、iOS 首次解锁/锁屏保护、能耗与性能阈值的待验证事项。

修改脱敏、JSONL、队列、轮转、恢复、导出或清理语义时，应补充对应边界和故障回归测试，并至少执行：

```shell
./gradlew :cyxbs-components:log:desktopTest
```
