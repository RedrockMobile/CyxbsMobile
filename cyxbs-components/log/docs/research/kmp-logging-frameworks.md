# KMP 日志框架源码调研

## 调研口径与结论

仅调研，不修改业务代码。实际读取 GitHub 固定源码与 Gradle 配置，不以 README 代替实现证据。未运行性能基准或各平台链接测试。GitHub REST API 后续 `403 rate limit exceeded`；未核验的 release 日期不推测。

| 候选 | API 分发 | 文件管理 | 复用判断 |
| --- | --- | --- | --- |
| Kermit core + kermit-io | core 同步分发；IO 格式化在 caller，文件处理在单消费者线程 | IO 自带滚动文件 | 门面与滚动 writer 可复用；需补生命周期与导出边界 |
| Napier | caller 同步 Antilog fan-out，惰性重载有区别 | 默认平台输出，无统一跨平台文件引擎 | 可复用门面，文件 sink 自建 |
| KmLogging | caller 同步遍历 logger | 默认平台/PrintLogger 无统一文件管理 | 可复用门面；JVM SLF4J backend 能力不能外推至 iOS |
| HackWebRTC/kmp-xlog | Kotlin 包装 native Mars；API caller 与 native 写盘线程分别判断 | 原生后端实际管理文件 | 真实候选，需承担 cinterop、制品、格式及平台差异成本 |

## Kermit 2.2.0

固定 [commit 774cc1ca81c73b7f14ef2b49b0dcd6dda213d1c6](https://github.com/touchlab/Kermit/commit/774cc1ca81c73b7f14ef2b49b0dcd6dda213d1c6)。已读 [RollingFileLogWriter.kt](https://github.com/touchlab/Kermit/blob/774cc1ca81c73b7f14ef2b49b0dcd6dda213d1c6/kermit-io/src/commonMain/kotlin/co/touchlab/kermit/io/RollingFileLogWriter.kt)、[config](https://github.com/touchlab/Kermit/blob/774cc1ca81c73b7f14ef2b49b0dcd6dda213d1c6/kermit-io/src/commonMain/kotlin/co/touchlab/kermit/io/RollingFileLogWriterConfig.kt)、[IO 构建](https://github.com/touchlab/Kermit/blob/774cc1ca81c73b7f14ef2b49b0dcd6dda213d1c6/kermit-io/build.gradle.kts)。源码包路径为 `co/touchlab/kermit/io/`。

- core 是 LogWriter 门面。IO writer 接收已求值 String；格式化、timestamp、throwable.stackTraceToString()、Buffer 构造发生在 caller，然后入 channel。
- 文件处理使用 newSingleThreadContext("RollingFileLogWriter")、SupervisorJob、单消费者。LOGGING_CHANNEL_CAPACITY=64，满队列可阻塞 caller；Native 也存在背压。
- append 写入；name.log 为活动文件，name-1.log 等为历史。轮转条件 `max(currentSize, fileSizeOrZero(logFilePath)) > config.rollOnSize` 在下一条写入前检查，因此单条可超阈值，非硬字节上限。
- 删除最高 index，再倒序 atomicMove。maxLogFiles 按数量保留；无按天保留、目录总字节限额。
- 每条消费后 currentLogSink.flush()。没有额外 fsync 承诺；单条 flush 不等于排空队列，异常退出仍可丢未消费消息。
- 没有公开 flush/close、消费者 join、文件快照或暂停轮转导出 API；scope/线程生命周期与上传前 drain 需补。
- IOException 时关闭当前 sink、继续循环并尝试恢复；不可访问期间消息会丢弃。非 IOException 不保证消费者自动重启。
- iOS 锁屏文件保护可阻止后台写入，目录保护属性需平台配置验证。

IO 构建调用 androidJvmTarget/jvmTarget/nativeTargets，无 JS/Wasm；依赖 **kotlinx.io**，不是 Okio。core 平台覆盖不能外推到 IO。许可 [Apache-2.0](https://github.com/touchlab/Kermit/blob/774cc1ca81c73b7f14ef2b49b0dcd6dda213d1c6/LICENSE)。[Maven IO 元数据](https://repo1.maven.org/maven2/co/touchlab/kermit-io/maven-metadata.xml) 已读 release=2.2.0，列出 2.0.5/2.0.6/2.0.8/2.1.0/2.2.0，lastUpdated=20260908182716；这是元数据时间，不等于 GitHub release 日期，证明 IO 已有发布。

## Napier

固定 [50835e2e12a61f1175e5fce770420f72caed5b08](https://github.com/AAkira/Napier/commit/50835e2e12a61f1175e5fce770420f72caed5b08)。已读 [Napier.kt](https://github.com/AAkira/Napier/blob/50835e2e12a61f1175e5fce770420f72caed5b08/napier/src/commonMain/kotlin/io/github/aakira/napier/Napier.kt)、[Antilog.kt](https://github.com/AAkira/Napier/blob/50835e2e12a61f1175e5fce770420f72caed5b08/napier/src/commonMain/kotlin/io/github/aakira/napier/Antilog.kt)、[Android DebugAntilog](https://github.com/AAkira/Napier/blob/50835e2e12a61f1175e5fce770420f72caed5b08/napier/src/androidMain/kotlin/io/github/aakira/napier/DebugAntilog.kt)、[构建](https://github.com/AAkira/Napier/blob/50835e2e12a61f1175e5fce770420f72caed5b08/napier/build.gradle.kts)。

Antilog.log 先 isEnable，再 performLog；rawLog 直接 performLog。AtomicMutableList 保护列表，不产生后台分发。lambda 参数不自动保证过滤前零计算：顶层 log { ... } 先 message() 再 Napier.log，各级别重载应分别评价。Android 默认 android.util.Log，JVM java.util.logging，其他平台输出各异；系统日志不是应用可控的文件轮转/保留/flush。文件 Antilog 的队列、轮转与关闭须自建。

构建列 Android/JVM/JS browser+node/wasmJs/Darwin iOS、macOS、watchOS、tvOS；部分 target 受 IDEA/宿主条件影响。未证明 Windows/Linux Native。许可 [Apache-2.0](https://github.com/AAkira/Napier/blob/50835e2e12a61f1175e5fce770420f72caed5b08/LICENSE)。[releases](https://github.com/AAkira/Napier/releases) 日期尚未完整复核，不宣称最新版本/停更时间。

## KmLogging

[LighthouseGames/KmLogging](https://github.com/LighthouseGames/KmLogging) 当前重定向 [DiamondEdge1/KmLogging](https://github.com/DiamondEdge1/KmLogging)。固定 [c504dfd6e1bcbf87795bc6798854ee2bf58b0a27](https://github.com/DiamondEdge1/KmLogging/commit/c504dfd6e1bcbf87795bc6798854ee2bf58b0a27)。已读 [KmLogging.kt](https://github.com/DiamondEdge1/KmLogging/blob/c504dfd6e1bcbf87795bc6798854ee2bf58b0a27/logging/src/commonMain/kotlin/com/diamondedge/logging/KmLogging.kt)、[PrintLogger](https://github.com/DiamondEdge1/KmLogging/blob/c504dfd6e1bcbf87795bc6798854ee2bf58b0a27/logging/src/commonMain/kotlin/com/diamondedge/logging/PrintLogger.kt)、[JVM PlatformLogger](https://github.com/DiamondEdge1/KmLogging/blob/c504dfd6e1bcbf87795bc6798854ee2bf58b0a27/logging/src/jvmMain/kotlin/com/diamondedge/logging/PlatformLogger.kt)、[iOS PlatformLogger](https://github.com/DiamondEdge1/KmLogging/blob/c504dfd6e1bcbf87795bc6798854ee2bf58b0a27/logging/src/iosMain/kotlin/com/diamondedge/logging/PlatformLogger.kt)、[构建](https://github.com/DiamondEdge1/KmLogging/blob/c504dfd6e1bcbf87795bc6798854ee2bf58b0a27/logging/build.gradle.kts)。

按各 logger 的 isLoggingVerbose/Debug/Info/Warning/Error 同步遍历调用，总开关由注册 logger 汇总；原子容器不是异步队列。lambda 入口不保证格式化与输出在后台。默认平台/PrintLogger 没有统一路径、轮转、保留、flush/close。JVM SLF4J API 借实际 binding/backend 获得文件能力；策略、异步及关闭来自 backend，不能外推到 iOS。

当前构建列 Android/JVM/iOS Arm64+SimulatorArm64/wasmJs；JS 源码目录不证明当前发布 target。artifactVersion=2.1.2 仅为源码配置，不等于发布证明。许可 [Apache-2.0](https://github.com/DiamondEdge1/KmLogging/blob/c504dfd6e1bcbf87795bc6798854ee2bf58b0a27/LICENSE)。维护判断使用迁移后 [releases](https://github.com/DiamondEdge1/KmLogging/releases)，不依据旧 owner 判废弃。

## kmp-xlog

真实仓库 [HackWebRTC/kmp-xlog](https://github.com/HackWebRTC/kmp-xlog)，固定 [004c98fae65adedf47dda46867c771969597c32e](https://github.com/HackWebRTC/kmp-xlog/commit/004c98fae65adedf47dda46867c771969597c32e)。已读 Kotlin/platform binding、构建及 Mars 文件实现；底层语义需与实际绑定 revision 对齐。

[.gitmodules](https://github.com/HackWebRTC/kmp-xlog/blob/004c98fae65adedf47dda46867c771969597c32e/.gitmodules) 指向 HackWebRTC/mars fork，不能只用 Tencent/mars README 证明此制品。[实现构建](https://github.com/HackWebRTC/kmp-xlog/blob/004c98fae65adedf47dda46867c771969597c32e/kmp-xlog/build.gradle.kts)、[API 构建](https://github.com/HackWebRTC/kmp-xlog/blob/004c98fae65adedf47dda46867c771969597c32e/kmp-xlog-api/build.gradle.kts) 列 Android/JVM/Apple/JS+Wasm/linuxX64/mingwX64，但 API target 不等于每个平台都有 Mars 文件能力。

Apple cinterop/ObjC + 静态 framework；Android JNI/native；Linux 静态 archive；Windows 构建 Mars/zlib/zstd 并含 XLOG_NO_CRYPT。JS/Wasm/JVM actual 应确认是否 console fallback，不能宣传全平台等价文件能力。native 同步/异步 appender 与 Kotlin caller 是不同阶段；写入、日期/大小分段、清理、flush/close 属于原生引擎。异步 flush 不自动等于上传前排空，须补 revision、同步 flush 完成语义、ABI、初始化与解码工具证据。

封装 [MIT](https://github.com/HackWebRTC/kmp-xlog/blob/004c98fae65adedf47dda46867c771969597c32e/LICENSE) 不覆盖 Mars/zlib/zstd 全部许可与 notice。[releases](https://github.com/HackWebRTC/kmp-xlog/releases) 日期仍待补证，不虚构版本或性能排名。

## Kotlin/Native 与 Swift

commonMain 引入库不自动向 Swift 导出全部 API；需核对本项目 framework api/export、静态/动态链接及 cinterop 选项。Napier sample 显式 export，kmp-xlog framework export API 并链接 z/SystemConfiguration，不意味着消费者零配置。Swift closure、Throwable、枚举、默认参数体验以导出产物为准。

目录/沙盒、锁屏保护、后台生命周期、快照导出是平台职责。Kermit 已有滚动 writer 但无公开 drain/close；xlog 已有 native 文件路线但需验证 flush、制品与解码。崩溃信号不能假设安全执行普通 Kotlin lambda、锁、channel 或复杂格式化。

Kermit IO 2.2.0 已验证发布，KmLogging 2.1.2 仅核验源码配置；其他候选 release 日期/artifact 对应尚待补证。“可复用”表示存在相关实现，不代表项目编译/设备验证已经完成。
