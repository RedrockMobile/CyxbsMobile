# CyxbsLog：独立模块、封装边界与用户侧保存调研

日期：2026-10-13。范围：第二轮设计调研与本地需求规划；没有创建 Gradle 模块、接入依赖、修改业务或执行性能测试。延续[第一轮选型报告](./kmp-logging-research.md)，本轮将模块名称确定为 `cyxbs-components/log`。

## 1. 结论

建议新增独立 `:cyxbs-components:log`，包名 `com.cyxbs.components.log`。业务只接触自有 CyxbsLog API；Kermit 作为内部平台输出适配，自有文件引擎承担有界队列、配额、刷出、清理与导出协议。旧 utils 入口做单向兼容桥。

**可以实现用户设备本地保存。** Android/iOS/desktop 使用应用私有目录滚动写入；用户主动选择时导出副本并通过系统分享或文件保存。私有日志不是默认暴露在下载目录的文件，也不等于服务器上传。浏览器首版使用有界内存，持久化另行通过 IndexedDB 验证，不能直接套用移动端文件系统。

本轮本地需求按仓库 MattSkills 的 map、Notes、Decisions-so-far、Fog、子任务与阻塞关系组织。插件检查显示当前 backend 为 GitHub；按用户“仅本地”的要求，使用普通 Markdown，不创建远端 issue、不切换插件配置。这些本地编号不是 deck issue 编号。

## 2. 为什么独立 log 优于 utils

| 方案 | 优点 | 成本与结论 |
|---|---|---|
| 放 utils | 旧入口迁移少，已有大量调用方 | utils 已承载 Compose、网络、config、account 等；日志生命周期、持久化和导出将继续耦合大模块。不推荐长期方案 |
| 独立 log | 独立测试存储故障和性能；调用方只依赖小 API；底层后端可替换 | 需要模块配置、应用集成和兼容桥。推荐，复杂度集中在内部实现 |
| 立即拆 log-api/log-core/log-file 多模块 | 后端可单独发布 | 当前没有独立发布需求；模块和接口增多，先一个 log 模块、内部按职责分包 |

证据：[utils 构建](<../../../../cyxbs-components/utils/build.gradle.kts>) 同时使用 manager.lib、kmp.compose、网络及 provider，并依赖 init/config/account；[config 构建](<../../../../cyxbs-components/config/build.gradle.kts>) 又依赖账号和登录。日志不能为了拿版本或目录直接依赖它们。

推荐依赖方向：业务 → log；utils 兼容桥 → log；应用集成层 → log 和现有初始化设施。log 不反向依赖 utils/config/account/页面。初始化设施若需要自身打日志，必须先检查依赖环，不能让 log 核心依赖 init 再让 init 依赖 log。

构建可从[manager.lib](<../../../../build-logic/manager/src/main/kotlin/manager.lib.gradle.kts>) 起步，不额外使用 kmp.compose。[kmp.base](<../../../../build-logic/plugin/kmp/src/main/kotlin/kmp.base.gradle.kts>) 已有协程、序列化、datetime、atomicfu 与 noWebMain/webMain；仍注入部分基础依赖，所以这里只是避免额外 Compose/业务依赖，并非绝对零依赖。

[settings](<../../../../settings.gradle.kts#L24>) 自动发现含构建脚本的模块；真正实现时需要验证自动纳入和 type-safe accessor，通常不需手工 include。[Multiplatform](<../../../../build-logic/config/src/main/kotlin/Multiplatform.kt#L13>) 根据属性开启 iOS/web/desktop，iOS 还要求 macOS。只做 Android 默认构建不代表其他目标通过。

源码归属建议：

- commonMain：公开 API、不可变事件、等级和策略、队列协议、状态及结果类型、平台无关测试。
- noWebMain：文件引擎、JSONL 编码、批写/轮转/清理、快照协调，复用 Okio；线程调度能力需按目标验证。
- androidMain/iosMain/desktopMain：私有目录、平台输出、存储能力和应用生命周期适配。
- webMain：平台输出和有界内存；IndexedDB 是后续独立能力。
- 应用层：构建模式、版本、用户设置、匿名会话、网络采集和系统分享界面。分享 UI 不进入核心。

## 3. 包装 CyxbsLog 应隐藏哪些复杂性

CyxbsLog 的价值是统一采集政策和行为保证，而不仅是换一个方法名。不要把 Kermit 类型、Okio Path/FileSystem、Channel、CoroutineScope 或文件名规则暴露给业务。

建议区分普通业务面和管理面。以下是待 PoC 的接口形态，非已实现 API：

```kotlin
CyxbsLog.d(tag = "Schedule") { "sync started" }
CyxbsLog.e(tag = "Schedule", throwable = error) { "sync failed" }
// 受控事件字段可附 requestId、错误码、耗时；不接收任意业务对象。
// 管理面：configure / flush / snapshot / clear / close / health。
```

首版公开对象尽量少：LogLevel、LogConfig、有限字段事件、LogHealth、FlushResult、SnapshotResult、ClearResult。用于平台存储能力的内部适配缝有实际变化依据；不要为每个内部类先建 interface。日志记录返回与错误策略需要统一，日常调用不要求业务检查每条返回值，统计与管理操作则必须可观察。

| 边界 | 需要定义的行为 |
|---|---|
| 过滤与惰性 | 分别判断控制台和文件是否接受；两个目的地均关闭时不执行 message lambda；被接受的消息只求值一次 |
| 调用成本 | 普通调用不做磁盘 I/O、不 runBlocking、不默认采调用位置；lambda 仍在调用线程运行，业务不得在其中执行重计算或请求 |
| 事件快照 | 入队时固定时间、等级、匿名 session/request 上下文和字段；限制数量、类型、长度；禁止把可变 Map、账号对象和大对象图交给 worker |
| 异常 | 保留类型及受控堆栈，限制深度、cause/suppressed 数量与消息；Throwable 保留对象也有内存成本，不能只计算最终字符串 |
| API 故障 | 文件和平台 writer 的普通失败不破坏业务；消息 lambda 的异常需要约定和验证。不要吞掉管理协程的取消，也不承诺 OOM 等致命错误可恢复 |
| 多目的地 | 一个 writer 的故障不阻止其他 writer；避免全局启用后所有输出无条件记录，脱敏应在平台输出前也生效 |
| 上下文 | 使用显式或协程上下文快照，不使用全局可变 currentRequestId；账号切换创建新匿名 session，核心不查询学号 |
| 日志递归 | worker 健康报告走独立状态/限频诊断，不重新调用 CyxbsLog；平台 writer 内部也防止反馈环 |
| 初始化 | 明确未初始化、重复配置、正在关闭、关闭后调用；首版未初始化只做受控控制台或计数丢弃，不偷偷建无界缓存 |
| 策略变化 | 用版本/epoch 控制；撤回或关闭时拒绝旧事件写入，后台暂停与彻底 close 是不同状态 |

Kermit 2.2.0 的 [LogWriter 文件实现](https://github.com/touchlab/Kermit/blob/774cc1ca81c73b7f14ef2b49b0dcd6dda213d1c6/kermit-io/src/commonMain/kotlin/co/touchlab/kermit/io/RollingFileLogWriter.kt) 没有公开 flush/close/snapshot，队列满会阻塞；外围包装不能据此提供可靠回执。平台输出可以复用 Kermit，文件层按掌邮协议独立实现。结构化事件直接交文件层，同时映射到 Kermit，避免序列化后再解析。

## 4. 用户侧本地保存与可见性

平台一手资料和目录细节见[平台保存专项报告](./cyxbs-log-local-storage-platforms.md)，汇总选择如下；实际目录创建和导出还需真机验证。

| 平台 | 首版保存策略 | 用户获取方式与边界 |
|---|---|---|
| Android | 私有 noBackupFilesDir 下独立目录，按进程/session 分文件；不申请广泛外部存储权限 | 导出脱敏副本到受控 cache 子目录，由 FileProvider 的临时 content URI 授权分享；提供保存文件可使用系统文件选择器 |
| iOS | 私有 Application Support 子目录，明确排除备份；根据锁屏写入需求验证文件保护等级 | snapshot 导出到临时目录，由宿主 UI 分享/保存；Caches 可被系统回收，不作为唯一保留承诺 |
| desktop | 应用数据目录独立子目录，遵循宿主路径约定 | 用户选择保存位置；处理多实例独立文件与占用冲突 |
| browser JS/Wasm | 有界内存环，重载后可丢；需要跨重启再做 IndexedDB | 用户手动下载；配额、清理、隐身和浏览器驱逐均可能导致持久化失效 |

“本地保存”应拆开两项验收：运行中的诊断记录在应用私有空间滚动保留；用户操作可获得可解析的脱敏导出副本。分享给外部应用或手动保存后产生的副本不受本应用清理控制。自动上传、远程拉取和上传服务器不在首版范围。

## 5. 持久化引擎必须补的边界

### 5.1 有界采集与批写

普通事件非阻塞入队，同时限制记录条数和负载字节，统计按等级丢弃、超长、写入失败、队列高水位。字节预算不是 Kotlin 堆内存硬上限，还应限制字段/堆栈和被保留对象。有限队列不可能保证无限流量下 ERROR 永不丢。

单 worker 独占文件操作；批量编码写入，静默期间也通过定时器刷出尾批。候选起点仍为队列 1024 条/约 2 MiB 负载、单条 16 KiB、32–64 KiB 或 0.5–1 秒批写、2–5 MiB 文件、20–30 MiB 总量和 3–7 天保留；参数不是实测结论，需按业务日志量调整。

### 5.2 刷出、关闭与一致性

flush 表示“操作开始前已成功接收的事件”达到声明的状态，不代表从未入队或已丢弃的日志也存在。并发入口需要定义接收的线性化点与屏障目标；不能仅观察 channel 为空。控制消息可靠投递并返回结果/超时，不能随满队列丢弃。共享 mailbox 或独立控制通道均可，但后者必须有事件序号屏障，不能绕过尚未写完的记录。

区分 buffered flush 与 durable sync：普通刷出将库内缓冲写给系统；存储同步需调用真实平台原语并报告 unsupported/failure，仍不承诺一切硬件故障下零丢失。取消或超时只说明调用者停止等待，不应误报 worker 已完成或已撤销操作。

close 幂等：停止接收、处理已接收事件、刷出、关闭文件并释放 worker。手机进入后台只做限时尽力 flush；不能每次后台 close 后忘记重启，也不能依赖进程终止 callback。原生崩溃日志捕获属于另一个能力。

### 5.3 配额与恢复

以 UTF-8 字节检查“当前文件 + 下一条”再轮转；文件名加入进程/session/序号，应对时间回拨。配额同时涵盖 active、封存、租约占用与临时导出预算，明确总磁盘预算的组成；空间不足时降级并退避，不能为了导出阻止容量上限长期生效。

首版 JSONL 有 schemaVersion、序号、采集时间、等级、tag、匿名 session、受控字段和异常；换行转义，尾部半条在恢复时忽略/截断。业务不可决定文件路径；扫描仅管理本组件认可的文件，防路径穿越和误删。线程单 worker 不提供跨进程锁，多进程默认独立文件，主进程汇总另验证。

### 5.4 snapshot、clear 和用户开关

snapshot 经 worker 屏障刷出、封存、切新文件，然后对封存文件持有限租约；打包在后台进行，超时/失败/取消都释放租约和临时文件。并发导出数量、字节和租约寿命必须有限。返回可读副本及 manifest（时间范围、schema、截断/丢弃和健康信息），不要暴露正在写的 active 文件。

clear 是有序命令：明确等待/取消哪些导出，清空已管理记录和旧队列，必要时切新 session；不能直接在另一线程 deleteRecursively 后让 worker 重建旧内容。用户关闭采集时停止新事件、处理或丢弃旧 epoch 并清理；用户只点“清理”且采集仍开时，应说明之后新日志仍会继续产生。

应用仅能清理自己的日志与临时副本，已经交给系统分享或外部应用的副本无法撤回。进程退出、磁盘满、锁屏不可写、浏览器驱逐都会产生诊断缺口，导出元数据要体现已知缺口。

## 6. 应用接入与策略

[InitialService](<../../../../cyxbs-components/init/src/commonMain/kotlin/com/cyxbs/components/init/InitialService.kt#L21>) 提供主/其他进程与隐私同意回调。核验[InitialManager](<../../../../cyxbs-components/config/src/commonMain/kotlin/com/cyxbs/components/config/init/InitialManager.kt#L51>)：已同意启动分支先设置 hasInvokedPrivacyAgree=true，再调用会在该值为 true 时返回的 tryPrivacyAgree；撤回方法只更新设置。日志不能仅依赖 onPrivacyAgreed 启动，也不能指望此处已有撤回通知。此处只记录接入风险，没有修复现有实现。

建议应用启动主动读取当前采集政策并 configure；首次同意、用户开关变化、撤回、账号切换显式通知日志管理面。隐私同意与“附加日志到反馈”是两个不同决策，框架不内置它们必然等价的假设。首版建议生产本地日志默认关闭，用户开启诊断后采集；Debug 的开发输出按构建策略配置。若产品需要默认采集最小诊断信息，必须单独定义允许字段、时长和告知，这仍是 Fog。

生产允许名单优先记录事件码、耗时、状态、去标识路由和 requestId；不默认记录学号、姓名、token/cookie、日程标题或完整请求/响应。网络 query、动态 path 段、异常消息也可能含个人信息。[Ktor sanitizeHeader](https://ktor.io/docs/client-logging.html) 不能代替 body/query/业务字段过滤。

旧 [ApiGenerator](<../../../../cyxbs-components/utils/src/androidMain/kotlin/com/cyxbs/components/utils/network/ApiGenerator.kt#L257>) 的 BODY 同时进 LogUtils/LogLocal；迁移时先改采集策略并关闭重复链路，不能只把文件 writer 换掉。旧 LogLocalHelper 的学号派生密钥不复用；如果产品需要额外静态加密，再单独验证平台密钥与导出解码，不把私有目录称为加密。

## 7. 本地需求与下一步验证

[需求地图](../requirements/map.md) 记录目的地、已定结论、Fog 和进度，关联本地子任务。已完成调研不等于准入实施：模块名称与独立性已确定推荐，平台编译/存储行为、产品采集策略及性能指标仍需验证。所有实现任务保持 planned/needs-triage。

最小 PoC 应先验证 Kermit 发布变体与 Kotlin 2.4.10/协程 1.11.0 的组合、Android+iOS 真机私有目录、安静时 flush、锁屏后台、强杀尾条、快照清理竞争。每个平台的支持矩阵分别报告，不因 commonMain 编译通过宣称全面可用。

测试围绕可观察协议：过滤 lambda 不执行、消息只求值一次、并发不重不乱（按定义的接收顺序）、溢出统计、异常截断、静默 flush、大小/总量配额、磁盘失败、撤回后旧事件不能重现、clear/export/close 并发、旧入口无重复、导出能解析。记录调用方 p50/p95/p99、分配、峰值内存、能耗与丢弃；验收阈值由 PoC 建立基线后确定，当前没有性能通过结论。
