# CyxbsLog 主流方案差距分析

> 审计日期：2026-10-17
>
> 范围：对照 Android、Apple、OpenTelemetry、Sentry、Crashlytics、Kermit 与 Logback 的一手资料，审计 CyxbsLog 的隐私、可靠性、诊断能力和演进成本；原始风险基线后追加实施跟进。

## 初次审计结论（实施前基线）

1. **当前方案可以继续演进，不需要推翻重写。** 有界队列、单 worker、JSONL schema、轮转配额、恢复、退避、显式用户开关和流式导出已经构成可用的本地诊断基线。
2. **没有发现必须立即停用的 P0。** 但存在两个应优先处理的 P1：队列压力下没有为 Warn/Error/Assert 保留容量；隐私处理只覆盖文件 sink，控制台和历史自由文本调用仍可输出原文。
3. **下一阶段不应直接引入完整 OpenTelemetry SDK 或远端 Collector。** 先在现有 API 上增加受限结构化 attributes、可选 correlation context 和 JSONL schema v2，收益更直接，兼容成本更低。
4. **崩溃、ANR 与 hang 是独立能力。** Kermit 官方也把 logging 与 crash reporting 视为不同领域。应通过可选适配层接 Android `ApplicationExitInfo`、现有 `CrashMonitor` 和 iOS MetricKit，而不是把文件引擎改造成崩溃平台。
5. **已有需求地图仍未关闭。** iOS 锁屏文件保护、真机磁盘故障、强杀、多进程、移动端 CPU/分配/能耗和产品允许字段仍需真实设备与产品决策。

## 实施跟进（2026-10-17）

本报告后续已按阶段 A、B 实施；下文风险分析保留发现问题时的基线和决策依据：

- 阶段 A 已由 `57c2fad54` 完成：低等级事件最多使用 80% 条数和字节预算，所有实际丢弃按等级统计；控制台与文件共用有界脱敏 payload，原始 `Throwable` 不再直接交给平台 writer；三处高风险历史调用已迁移。
- schema v2 已增加 `eventTimestampMillis`、`observedTimestampMillis` 和固定白名单 `LogContext`；旧文本 API 保持兼容，历史 v1 文件仍可恢复、配额管理和导出。结构化字段的 UTF-8 负载计入 2 MiB 总预算与 80% 低等级字节预算。
- v2 文件使用独立于 wall clock 的持久化 generation 做选源与配额排序；年龄清理仍是 wall-clock best effort。
- v2 manifest 增加 payload 原始字节的 SHA-256 和日志行数；哈希只验证完整性，不提供来源认证。
- `FileLogHealth` 已增加最近写入成功/失败、失败类型、连续失败、flush 耗时及受管文件数/字节数，`health()` 不扫描目录。
- Android 已接最近一次 `ApplicationExitInfo` 的受限元数据；`CrashMonitor` 把原始 `Throwable` 交给日志框架，但紧急路径只生成有界类型/消息摘要，不展开 stack/cause/suppressed，并仅在现有引擎运行时对 buffered flush 等待最多 250 ms；不读取系统 exit trace，也不承诺崩溃日志必达。
- iOS 日志与导出目录使用 `CompleteUntilFirstUserAuthentication`；目录准备和元数据校验在后台 dispatcher 完成，任一保护或备份属性设置失败都会回滚文件层启动。MetricKit 订阅严格跟随用户采集策略并绑定策略代际，只汇总 crash/hang/cpu/disk 数量，不读取调用栈、终止原因或原始 payload；过期回调在读取诊断元数据前退出。首次解锁、锁屏后台写入、MetricKit 实际投递、导出与清理仍需真机验收。
- 尚未实施：重复错误指纹限频、临时 Debug/采样、远端 exporter、durable fsync、应用层加密；这些继续保持 P3 或产品决策项。

## 当前方案已经做对的部分

| 能力 | 当前实现 | 判断 |
|---|---|---|
| API 隔离 | `CyxbsLog` 隐藏 Kermit 类型，消息惰性求值，异常不影响业务 | 保留 |
| 有界内存 | 1024 条与 2 MiB 双预算，单条 16 KiB | 保留，但需分级背压 |
| 文件写入 | 单 worker、64 KiB 批次、约 1 秒 buffered flush | 合理的非阻塞基线 |
| 磁盘配额 | 默认 24 MiB、7 天、8 文件 | 合理，待真机性能定稿 |
| 文件协议 | JSONL schema v2、session、sequence、双时间戳、受限上下文、generation、manifest hash/count；兼容 v1 | 已完成阶段 B 核心协议 |
| 故障隔离 | 文件异常计数，250 ms 到 30 s 指数退避，丰富健康快照 | 保留，待真机故障验收 |
| 恢复与导出 | active 尾部恢复、短读处理、流式导出、原子发布、只保留一个导出副本 | 基线完整 |
| 隐私开关 | 主进程、隐私同意、用户显式开启后才写文件 | 符合最小化原则 |
| 平台目录 | Android no-backup 私有目录；iOS Application Support、排除备份并显式 Data Protection | 代码完成，iOS 真机行为待验收 |
| 能力声明 | `Durable` 明确 Unsupported；不承诺强杀、断电或原生崩溃 | 边界诚实，应继续保持 |

## 风险分级

### P1：队列压力下没有保障高等级事件

当前 [`FileLogEngine.offer()`](../../src/noWebMain/kotlin/com/cyxbs/components/log/storage/FileLogEngine.noWeb.kt) 的所有等级共享同一个 1024 条/2 MiB FIFO 预算。Info 洪峰可以先占满预算，使后到的 Warn/Error/Assert 与普通 Info 一样被丢弃；持续 I/O 退避期间也不区分等级。

这与已有策略文档“Error/Fatal 可靠保留、Info 作为有界 breadcrumb”的目标不一致。Logback `AsyncAppender` 的官方默认策略会在队列剩余不足 20% 时丢弃 TRACE/DEBUG/INFO，保留 WARN/ERROR，说明异步日志可以在不阻塞业务线程的前提下做等级感知背压。[Logback AsyncAppender][logback-async] Crashlytics 的 Android custom logs 每个 session 也只有 64 kB，超限删除更老内容，说明移动端错误上下文本身必须有界且应优先保留高价值事件。[Crashlytics Android][crashlytics-android]

**建议：**

- 为 Warn/Error/Assert 预留独立条数和字节预算，例如总预算最后 10% 到 20% 只允许高等级使用。
- 队列进入高水位后先拒绝 Info，再拒绝 Warn，Error/Assert 最后丢弃；仍然不得阻塞业务线程。
- 写失败退避期间允许一个很小的高等级内存环，恢复后优先写出；必须有严格上限。
- 健康统计增加 `droppedByLevel`，测试覆盖 Info 洪峰后 Error 仍可导出。
- 对重复错误增加按指纹限频或聚合，避免错误风暴反过来吃满保留预算。Logback 的重复消息过滤和 Sentry 的有界 breadcrumb 都支持“保留诊断价值而非保留所有重复文本”。[Logback Filters][logback-filters] [Sentry Breadcrumbs][sentry-breadcrumbs]

### P1：隐私边界没有覆盖所有 sink 和历史调用

[`CyxbsLog.log()`](../../src/commonMain/kotlin/com/cyxbs/components/log/CyxbsLog.kt) 只在文件路径执行 64 KiB 快照、正则脱敏和 16 KiB 限制；控制台仍把原始 message 与 Throwable 交给 Kermit。Release 控制台虽然从 Warn 开始，但 Warn/Error 仍可能含敏感原文。Android 官方明确把敏感信息写入系统日志列为信息泄露风险；Apple Unified Logging 也提供显式 privacy 机制。[Android Log Info Disclosure][android-log-disclosure] [Apple OSLog Privacy][apple-oslog-privacy]

仓库还存在绕过统一语义的历史调用：[`EmptyRoomViewModel`](../../../../cyxbs-pages/emptyroom/src/androidMain/kotlin/com/cyxbs/pages/emptyroom/viewmodel/EmptyRoomViewModel.kt) 把完整 `data.toString()` 写日志，[`TimeUtil`](../../../../cyxbs-pages/mine/src/androidMain/kotlin/com/cyxbs/pages/mine/util/TimeUtil.kt) 只写 `e.toString()`，[`TrackingUtils`](../../../../cyxbs-components/utils/src/androidMain/kotlin/com/cyxbs/components/utils/logger/TrackingUtils.kt) 直接 `printStackTrace()`。因此 [`FileLogSanitizer`](../../src/commonMain/kotlin/com/cyxbs/components/log/storage/FileLogSanitizer.kt) 只能算防御性兜底，不能证明全仓日志安全。

**建议：**

- 在门面进入各 sink 前实施共同的数据最小化策略；文件 sink 可继续做第二次防御性脱敏。
- 优先迁移用户运行时路径中的直接 Android Log、Kermit、`printStackTrace()`、完整对象/body/header 输出。
- CI 先加轻量扫描，禁止新增 `printStackTrace()`、直接平台日志、明显的完整 body/header/token 输出；观察误报率后再决定是否引入 Detekt/Lint 规则。
- Throwable 应作为参数传入，不在 message 中重复拼接堆栈；业务字段只允许白名单键和低敏感标量值。
- 对正则脱敏建立攻击/误伤测试集；不要宣称其可识别任意 PII。

### P2：事件仍以自由文本为主，缺少受限结构化关联

当前 [`FileLogEvent`](../../src/commonMain/kotlin/com/cyxbs/components/log/storage/PlatformFileLog.kt) 只有 timestamp、level、tag、message、stackTrace 与截断标记；JSONL v1 有 session/sequence，但没有 attributes、correlation ID、trace/span 或 observed timestamp。

OpenTelemetry Logs Data Model 使用 Timestamp、ObservedTimestamp、Severity、Body、Attributes，并允许 TraceId/SpanId 关联；Sentry breadcrumb 同样是带 category、level、message/data 的有界结构化轨迹。[OpenTelemetry Logs Data Model][otel-log-model] [Sentry Breadcrumbs][sentry-breadcrumbs]

**建议：**

- 保留现有 `CyxbsLog.d/e { text }`，新增可选 `attributes` 和 correlation context，避免破坏调用方。
- attributes 只允许固定白名单键、String/Boolean/Long/Double 等标量值，并限制键数和单值长度。
- 首批字段可限定为 `operation`、归一化 `route`、`status`、`durationMs`、匿名 `correlationId`、`retryCount`、`degraded`。
- JSONL 升级为 schema v2；阅读器按 `schemaVersion` 分支，v1 文件继续可读。
- 不在这一阶段引入完整 OTel SDK、Exporter 或远端 Collector。

### P2：原生崩溃、ANR 与 hang 上下文没有桥接

当前首版明确不捕获原生崩溃，[`AppLogManager`](../../../../cyxbs-components/config/src/commonMain/kotlin/com/cyxbs/components/config/log/AppLogManager.kt) 只在进入后台时请求 buffered flush；[`Durable`](../../src/commonMain/kotlin/com/cyxbs/components/log/model/LogStorageResult.kt) 明确 Unsupported。现有 Android [`CrashMonitor`](../../../../cyxbs-components/base/src/androidMain/kotlin/com/cyxbs/components/base/crash/CrashMonitor.kt) 也未把 Throwable 和最后缓冲接入 CyxbsLog。

Android `ApplicationExitInfo` 可以在下次启动读取历史进程退出原因，并对部分崩溃/ANR 提供 trace；iOS MetricKit 提供 diagnostic payload 与 hang diagnostic。Kermit 官方则明确把 crash reporting 放在独立工具中，只提供 breadcrumb/handled exception 的日志适配。[Android ApplicationExitInfo][android-exit-info] [Apple MetricKit Diagnostic Payload][apple-metrickit-payload] [Apple MXHangDiagnostic][apple-hang] [Kermit Crash Reporting][kermit-crash]

**建议：**

- Android：现有 `CrashMonitor` 记录 Error+Throwable 后做短超时的 best-effort buffered flush；下次启动读取 `ApplicationExitInfo`，生成一条结构化“上次退出”事件。
- iOS：通过独立适配层消费 MetricKit diagnostic payload，不把平台对象暴露到 log 核心。
- 若接 Crashlytics/Sentry，只把 Info breadcrumbs 与 Warn+Throwable 送入其有界缓存；上传许可与本地诊断开关保持独立。
- 文案继续明确：buffered flush 不是 durable，不能保证强杀或断电零丢失。

### P2：wall-clock 和文件 mtime 参与选源、淘汰与展示

worker 的 flush 周期已使用 monotonic time，这是正确的；但事件时间、session 名、源文件选取和清理仍依赖 wall-clock/mtime。系统时间回拨可能让导出选错“最新”文件，或让年龄淘汰顺序偏离真实写入顺序。容量与文件数上限仍有效，因此该问题不会造成无限增长，但可能丢失更相关的诊断片段。

**建议：**

- 文件名或文件头加入跨当前存储实例单调递增的 generation；导出和清理优先按 generation，再用 mtime 兼容旧文件。
- 每条事件保留 wall-clock 供人阅读，可在 schema v2 增加进程内 monotonic offset 供排序和耗时分析。
- 增加“源日志时间回拨”测试；现有测试只覆盖导出副本替换，不覆盖源文件选取与淘汰。

### P2：iOS 文件保护等级未显式设置

当前 iOS 目录已排除备份，但没有显式设置 Data Protection 等级。Apple 建议对包含敏感信息的文件使用文件保护属性；具体等级会影响锁屏后可读写性。[Apple File Protection][apple-file-protection]

**建议：**

- 与产品确认锁屏后是否需要继续写入，再选择保护等级；不要仅凭代码默认值推断。
- 对目录及新建/轮转文件设置一致属性，并在真机验证首次解锁前、锁屏后台写入、导出和清理。
- 这项与需求地图已有 Fog 合并跟踪，不另作“已完成”声明。

### P2：健康状态不足以支持用户侧诊断

[`FileLogHealth`](../../src/commonMain/kotlin/com/cyxbs/components/log/model/LogStorageResult.kt) 已有状态、接收数、各类丢弃数、写失败数和队列占用，但缺少按等级丢弃、最后成功写入时间、最近失败类别/时间、当前原始日志字节、文件数与最近 flush 时延。

**建议：**

- 增加稳定错误码而非直接暴露平台异常文本。
- 增加 `lastSuccessfulWriteAt`、`lastFailureAt`、`lastFailureCode`、`storedBytes`、`fileCount`、`lastFlushDurationMs` 和 `droppedByLevel`。
- 设置页只展示可操作状态，例如“正常/空间不足/暂时不可写/存在丢弃”，详细数值仅进入导出 manifest。

### P2：导出可解析，但缺少内容完整性摘要

当前 manifest 已记录源文件数、日志字节数和裁剪标记，发布过程也使用 partial 文件和原子移动；这些足以防止读到半成品。但 manifest 没有内容哈希，跨分享或人工传输后无法机械确认内容是否被截断或改写。

**建议：**

- schema v2 manifest 增加对后续 log 行原始字节计算的 SHA-256 与 record count。
- 哈希只用于完整性校验，不用于身份认证；若未来需要真实性证明，应单独设计签名与密钥生命周期。
- 阅读工具先验证 hash/count，再解析事件；空导出仍保持合法。

### P3：高级策略应后置

以下能力有价值，但当前不应优先于 P1/P2：

- 按模块临时开启 Debug、自动过期和远程支持指令。
- 高频成功 Info 的采样/聚合，以及错误指纹的时间窗去重。
- Web 的 IndexedDB 持久化、端到端上传、远程配置或完整 OpenTelemetry exporter。
- 真正的 durable fsync：需分别评估 Android/iOS API、能耗和延迟，不能只把返回值改成 `Durable`。
- 文件加密：先完成威胁模型、密钥可用性与锁屏行为设计；平台 Data Protection 与应用层加密不是同一件事。

## 推荐路线

### 阶段 A：先消除 P1

1. 实现等级感知的队列预留和高水位丢弃策略。
2. 增加按等级丢弃与重复错误聚合测试。
3. 将隐私最小化前移到统一门面，迁移高风险历史调用。
4. 在 CI 加轻量扫描，阻止新直接平台日志和明显敏感输出。

**完成标准：** Info 洪峰与 I/O 退避测试中 Error 仍可保留；控制台与文件都不输出测试用 token、Cookie、邮箱、手机号、完整 body/header。

### 阶段 B：提升可诊断性

1. 设计受限 attributes/correlation API 和 JSONL schema v2。
2. 扩展健康状态与 manifest 完整性字段。
3. 修复源文件时间回拨排序，补 generation 与迁移兼容测试。
4. Android 接 `ApplicationExitInfo`；iOS 接 MetricKit；CrashMonitor 只做 best-effort flush。

**完成标准：** v1/v2 导出均可读；同一操作能按匿名 correlation 串联；上次异常退出有结构化记录；不对 durable 作虚假承诺。

### 阶段 C：真机与产品验收

1. iOS Data Protection、锁屏/首次解锁、后台写入真机验证。
2. Android/iOS 磁盘满、不可写、强杀、多进程与分享接收端验证。
3. 测量 CPU、分配、I/O、能耗和启动影响，据实定队列、批次、容量和采样阈值。
4. 产品确认允许字段、隐私文案、本地诊断许可与上传许可。

**完成标准：** 需求地图中的真机、产品政策和移动性能项全部有证据关闭。

## 不建议做的事

- 不因为缺少结构化字段就替换整个 CyxbsLog 或暴露 Kermit/OpenTelemetry 类型给业务。
- 不把所有 Debug 长期持久化，也不把文件阈值直接降到 Warn 而丢失故障前因。
- 不把正则脱敏描述为“彻底匿名化”。
- 不为“绝不丢 Error”而在业务线程同步写磁盘；应是有界优先保留，不是无界或阻塞保证。
- 不把 buffered flush、atomic move 或 SHA-256 误称为断电持久化、事务或真实性签名。

## 最终判断

CyxbsLog 的基础架构方向正确，当前主要问题不是“有没有日志文件”，而是**在压力下保住最有价值的事件、在所有 sink 上执行同一隐私边界、让事件从自由文本演进为受限结构化诊断数据**。按阶段 A → B → C 推进即可，无需重写文件引擎；完成阶段 A 后，才适合扩大业务埋点覆盖。

## 一手资料

[android-log-disclosure]: https://developer.android.com/privacy-and-security/risks/log-info-disclosure
[android-exit-info]: https://developer.android.com/reference/android/app/ApplicationExitInfo
[apple-oslog-privacy]: https://developer.apple.com/documentation/os/oslogprivacy
[apple-file-protection]: https://developer.apple.com/documentation/uikit/encrypting-your-app-s-files
[apple-metrickit-payload]: https://developer.apple.com/documentation/metrickit/mxdiagnosticpayload
[apple-hang]: https://developer.apple.com/documentation/metrickit/mxhangdiagnostic
[otel-log-model]: https://opentelemetry.io/docs/specs/otel/logs/data-model/
[sentry-breadcrumbs]: https://develop.sentry.dev/sdk/foundations/state-management/scopes/breadcrumbs/
[crashlytics-android]: https://firebase.google.com/docs/crashlytics/android/customize-crash-reports
[kermit-crash]: https://kermit.touchlab.co/docs/crashreporting/
[logback-async]: https://logback.qos.ch/manual/appenders-async-sift.html#AsyncAppender
[logback-filters]: https://logback.qos.ch/manual/filters.html
