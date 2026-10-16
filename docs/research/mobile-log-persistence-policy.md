# 移动端日志持久化策略调研

> 调研日期：2026-10-17
>
> 范围：移动端客户端日志的本地持久化等级、错误上下文、容量控制、按需诊断与隐私。本文区分“平台/框架事实”和“掌邮工程建议”；各平台并不存在统一强制的 Release 最低等级。

## 结论先行

1. **不建议全量、无限期持久化客户端日志，也不建议只保存 Warn/Error。** 高质量实践是分层处理：Error/Fatal 作为故障事件可靠保留；少量 Info 作为错误前的 breadcrumb（上下文轨迹）进入有界缓冲；Debug/Verbose 默认不持久化，只在开发环境或针对特定问题限时开启。
2. **掌邮 Release 默认仍应关闭自管文件日志。** 系统日志、崩溃上报和掌邮可导出的诊断文件是不同通道，不能把 Android Logcat 或 Apple Unified Logging 的行为等同于应用私有文件策略。
3. **用户显式开启“诊断日志”后，建议默认 `minLevel=Info`。** `Warn` 会丢失导航、生命周期、状态迁移、请求结果等定位故障必需的前因；`Debug` 又容易带来明显噪声、性能和隐私成本。`Debug` 应是第二层、限模块且自动过期的临时开关。
4. **持久化必须有界且最近优先。** 使用环形/滚动文件，按总容量与时间双重淘汰最旧数据；高频成功事件采样或聚合，Warn/Error/Fatal 与关键会话边界不采样。
5. **脱敏必须发生在入队/落盘之前。** 导出前复核只能是第二道防线；开启本地诊断与上传日志应是两个独立、可撤回的用户决定。

## 一手来源说明了什么

| 来源 | 官方行为/能力 | 对掌邮的含义 |
|---|---|---|
| Apple Unified Logging | `Debug` 用于开发或特定问题，需配置启用才进入内存；`Info` 先进入内存缓冲，缓冲满可被清除，但发生 fault（以及配置允许时的 error）可连同上下文捕获；`Default` 会在内存缓冲满时压缩并移入 data store；`Error`/`Fault` 始终进入 data store。所有进入 data store 的消息仍受配额控制，超额淘汰最旧消息。[Apple Debug][apple-debug]、[Info][apple-info]、[Default][apple-default]、[Error][apple-error]、[Fault][apple-fault] | 成熟平台采用“分级 + 内存上下文 + 配额淘汰”，而不是所有等级永久保存。Apple 的系统 data store 不是掌邮自管、可导出的文本文件，不能直接照搬保留期。 |
| Android logging | `logd` 维护 `main`、`system`、`crash` 等**结构化环形缓冲**。日志还会经过编译期、系统属性、应用、显示四层过滤；官方举例 ProGuard 可删除 `Log.d`，未设置属性时 `liblog` 默认最低优先级为 `INFO`。[Android Logcat][android-logcat] | 不能声称“Android Release 自动只保留 Warn/Error”。是否移除 D/V、写哪些等级到掌邮文件，由构建配置和 sink 决定；系统环形缓冲也不是跨重启的应用文件存储。 |
| Sentry | breadcrumb 是错误发生前的结构化轨迹，等级可为 debug/info/warning/error 等，建议包含 HTTP、UI、system、navigation；SDK 必须以环形缓冲保存，超过 `max_breadcrumbs` 删除最旧项，推荐默认值 100，并可用 `before_breadcrumb` 丢弃或改写。[Sentry Breadcrumbs][sentry-breadcrumbs] | 错误上下文应小而结构化，不必把全量 Debug 文本持久化。 |
| Sentry | 错误事件可用 `sample_rate` 采样；PII 发送由 `send_default_pii` 控制。SDK 自身 debug mode 默认关闭，且不建议在生产环境启用，因为有额外开销。[Sentry Configuration][sentry-config] | “采样错误事件”“采样普通日志”和“SDK 自诊断模式”是三件事，策略与开关应分开。 |
| Firebase Crashlytics | custom logs 为崩溃前上下文服务；每个 session 限 64 kB，超限删除更老记录；custom keys 最多 64 个、每项 1 kB。Android 非致命异常仅保留最近 8 个；可关闭自动收集，待用户同意后再发送。[Crashlytics Android][crashlytics-android]（iOS 同样使用 64 kB 有界日志，并提醒后续大量日志可能轮转掉 NSError 附近的上下文：[Crashlytics iOS][crashlytics-ios]） | 业界崩溃工具也明确限制容量和数量。过多低价值日志反而会挤掉真正相关的故障上下文。 |
| Kermit | `LoggerConfig.minSeverity` 会忽略低于阈值的消息。[Logger setup][kermit-setup]；其 crash writer 的 breadcrumb cache 默认 `minSeverity=Info`，而 handled exception 默认从 `Warn` 且带 Throwable 才上报。[Crash reporting][kermit-crash] | 直接支持“Info 做上下文，Warn/Error 做异常事件”的分层；这也是掌邮诊断模式选择 Info 的最直接框架依据。 |
| Napier | 提供 V/D/I/W/E/ASSERT；官方 README 示例在 Debug build 使用 `DebugAntilog` 且关闭 Crashlytics，在非 Debug build 使用 `CrashlyticsAntilog`。[Napier README][napier-readme] | 构建类型可选择不同 sink，但 Napier 示例不是统一文件持久化阈值规范，不能据此推导 Release 必须只留某一级。 |
| Logback | `ThresholdFilter` 可丢弃阈值以下事件，appender 可独立配置过滤器；`DuplicateMessageFilter` 默认只允许同一消息重复 5 次、缓存 100 个。[Filters][logback-filters]。滚动策略用 `maxHistory` 和 `totalSizeCap` 限制历史与总量并清理旧档。[Rolling][logback-rolling] | 文件 sink 应同时具备等级过滤、重复抑制、滚动与总量上限，而不是单纯“写文件”。 |

## Debug 与 Release 的建议矩阵

以下是基于上述官方机制形成的**掌邮工程建议**，不是 Apple/Android 强制规则：

| 场景 | 控制台/系统日志 | 掌邮自管持久化文件 | 原因 |
|---|---|---|---|
| Debug 开发构建 | 可从 Verbose/Debug 开始，按模块过滤 | 如确有需要可从 Debug 开始，但必须滚动、短留存 | 开发可观察性优先，但仍应避免密码、token、完整响应体等落盘 |
| Release 默认 | 仅保留经过审查的必要输出；故障上报走独立通道 | **关闭** | 最小化隐私、磁盘和性能成本；也与掌邮现有日志模块设计方向一致 |
| Release，用户显式开启诊断 | 系统输出策略不必改变 | **`minLevel=Info`（推荐默认）** | 保留足以重建问题的结构化前因，同时排除大部分实现细节噪声 |
| Release，专项深度排障 | 按支持指令选择 category | Debug，仅限指定模块、限定时长、使用更严容量 | Debug 是短期诊断能力，不是长期默认策略；到期自动回到 Info |

如果未来产品要求 Release 默认也保留本地文件，应采用更保守的 `Warn/Error/Fatal + 极小的 Info breadcrumb 环形缓冲`，而不是打开全量 Info/Debug 文件。但这应视为新的产品与隐私决策，不能伪装成平台默认行为。

## 为什么诊断模式默认选择 Info

- **不是 Warn：** Warn/Error 只能说明“哪里失败”，通常无法说明“如何走到这里”。Sentry、Crashlytics 都把崩溃前操作序列视为诊断信息；Kermit 的 crash breadcrumb 默认也从 Info 开始。
- **不是 Debug：** Apple 将 Debug 定位为开发或特定问题排查；Sentry 不建议在生产开启 SDK debug mode。虽然两者不等价于掌邮业务 Debug 日志，但共同说明 Debug 应按需而非长期默认。
- **Info 必须重新定义为“可诊断、低敏、低基数事件”，而不是任意字符串。** 如果现有 Info 中混有响应体、用户输入或高频循环输出，应先治理埋点，再启用文件 sink；仅调整 minLevel 不能解决内容质量问题。

## Error 之外应保留哪些上下文

建议将下列事件作为 Info breadcrumb，使用固定 schema 和允许字段，而不是自由文本：

- 应用启动、前后台切换、登录态建立/失效、匿名诊断 session 开始/结束；
- 页面/功能导航和用户操作**类型**，但不记录输入内容、日程标题等业务正文；
- 网络请求的 method、归一化 route、结果类别/status、耗时区间、重试次数、匿名 requestId；
- 状态机关键迁移、任务开始/成功/取消、重试/熔断/降级及缓存命中来源；
- 权限状态、网络类型/可用性、存储空间等级等与故障相关的系统状态变化；
- app 版本、构建号、平台/系统版本、匿名 session ID；
- 日志管线自身的丢弃数、采样率、队列溢出、滚动与写入失败统计。

这些上下文应有严格限频。循环进度、动画帧、每次 recomposition、每个列表元素、成功请求的重复细节，应采样、聚合或直接丢弃。

## 环形缓冲、采样与按需开启

### 1. 最近优先的有界存储

- 内存层：固定条数的 breadcrumb 环形缓冲；满后覆盖最旧项。
- 文件层：按文件大小滚动，并以“总容量 + 最长保留时间”双上限删除最旧档。
- 初始容量可沿用仓库既有研究中的 **20–30 MiB、3–7 天**作为实验起点，但这是工程候选值，不是一手来源给出的行业标准；上线前应以真实日志速率、低端机 I/O 和工单定位成功率校准。
- Warn/Error/Fatal 写入应优先于普通 Info；队列过载时先丢 Debug，再丢可采样 Info，并记录丢弃计数。

### 2. 分等级采样

- Warn/Error/Fatal、会话边界、关键状态迁移：不采样，但应做相同错误的聚合/限频，防止错误风暴占满空间。
- 高频成功 Info：按类别采样或聚合为计数与耗时分布；采样决策尽量在格式化和入队前完成。
- Debug：仅专项开启；限制 category、自动过期时间和更小容量，避免挤掉 Info/Warn/Error 上下文。

### 3. 用户控制

诊断开关页面至少展示：收集内容概述、剩余开启时间、当前占用空间、清除按钮。关闭后立即停止新增，并按明确策略删除已有诊断文件。**本地记录许可与上传许可分离**：用户开启诊断不代表自动上传；每次导出/上传应再次明确确认并可预览说明。

## 隐私与安全底线

1. 使用字段允许名单，禁止记录完整 request/response body、query、header、原始 URL 动态段、token、cookie、密码、验证码、设备广告标识，以及姓名、学号、手机号等直接标识符。
2. 在日志进入内存队列前完成脱敏；不要先写明文、导出时才遮盖。
3. 动态标识符使用会话级匿名 ID 或不可逆、带产品侧密钥/盐的受控映射；不要用可枚举的裸哈希冒充匿名化。
4. 文件保存在应用私有目录，导出采用受控分享；如包含仍可能敏感的数据，应结合平台文件保护/加密能力与密钥生命周期设计。
5. 记录策略版本、启停时间、实际 minLevel/category 和丢弃统计，让收到的诊断包能够解释“哪些日志本来就没采集”。

## 建议落地策略

```text
Release 默认：fileSink = off
用户开启诊断：fileSink = on, minLevel = Info, bounded rolling storage
专项排障：selectedCategories = Debug, autoExpire = on
错误通道：Warn/Error/Fatal + Throwable 独立上报，附最近 Info breadcrumbs
上传：与本地开启分离，再次征得用户同意
```

验收时重点验证：进程被杀/磁盘满/并发写入下仍能滚动；错误风暴不会挤掉全部上下文；关闭或到期会停止采集；敏感字段在进入队列前已被删除；导出包包含策略元数据而不包含明文身份信息。

## 参考资料（一手来源）

- Apple, [Logging][apple-logging]
- Apple, [`OS_LOG_TYPE_DEBUG`][apple-debug]、[`INFO`][apple-info]、[`DEFAULT`][apple-default]、[`ERROR`][apple-error]、[`FAULT`][apple-fault]
- Android Developers, [Logcat command-line tool][android-logcat]、[`android.util.Log`][android-log]
- Sentry Developer Documentation, [Breadcrumbs][sentry-breadcrumbs]、[SDK Configuration][sentry-config]
- Firebase, [Customize Crashlytics crash reports — Android][crashlytics-android]、[iOS][crashlytics-ios]
- Touchlab Kermit, [Logger Setup][kermit-setup]、[Crash Reporting][kermit-crash]
- Napier, [README（固定 commit）][napier-readme]、[`Antilog.kt`（固定 commit）][napier-antilog]
- Logback, [Filters][logback-filters]、[Appenders][logback-appenders]、[Rolling appenders][logback-rolling]

[apple-logging]: https://developer.apple.com/documentation/os/logging
[apple-debug]: https://developer.apple.com/documentation/os/os_log_type_t/os_log_type_debug
[apple-info]: https://developer.apple.com/documentation/os/os_log_type_t/os_log_type_info
[apple-default]: https://developer.apple.com/documentation/os/os_log_type_t/os_log_type_default
[apple-error]: https://developer.apple.com/documentation/os/os_log_type_t/os_log_type_error
[apple-fault]: https://developer.apple.com/documentation/os/os_log_type_t/os_log_type_fault
[android-logcat]: https://developer.android.com/tools/logcat
[android-log]: https://developer.android.com/reference/android/util/Log
[sentry-breadcrumbs]: https://develop.sentry.dev/sdk/foundations/state-management/scopes/breadcrumbs/
[sentry-config]: https://develop.sentry.dev/sdk/foundations/client/configuration/
[crashlytics-android]: https://firebase.google.com/docs/crashlytics/android/customize-crash-reports
[crashlytics-ios]: https://firebase.google.com/docs/crashlytics/ios/customize-crash-reports
[kermit-setup]: https://kermit.touchlab.co/docs/configuration/LOGGER_SETUP/
[kermit-crash]: https://kermit.touchlab.co/docs/crashreporting/
[napier-readme]: https://github.com/AAkira/Napier/blob/50835e2e12a61f1175e5fce770420f72caed5b08/README.md
[napier-antilog]: https://github.com/AAkira/Napier/blob/50835e2e12a61f1175e5fce770420f72caed5b08/napier/src/commonMain/kotlin/io/github/aakira/napier/Antilog.kt
[logback-filters]: https://logback.qos.ch/manual/filters.html
[logback-appenders]: https://logback.qos.ch/manual/appenders.html
[logback-rolling]: https://logback.qos.ch/manual/appenders-rolling.html
