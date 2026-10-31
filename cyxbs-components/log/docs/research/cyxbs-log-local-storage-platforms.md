# CyxbsLog：设备本地保存与手动导出的平台契约

范围：计划中的 `cyxbs-components/log` 独立模块，`CyxbsLog` 包装 Kermit。本报告补充平台存储与导出边界，不重做框架比较。前期结论见 [KMP 日志研究](./kmp-logging-research.md)。下文区分官方事实与**建议契约**；建议不表示当前工程已经实现。

## 1. 自动保存与手动导出

**自动本地保存**：脱敏后日志自动写入应用管理的本地存储，按容量与时间滚动。它不等于上传、备份、永久保存或写入用户可见的下载目录。各平台规则见 [Android][A1]、[Apple][I1]、[Browser][W1]。

**手动导出/分享**：用户主动操作后，把限定范围的日志生成一致快照和独立包，再交给平台 UI。Android FileProvider 可提供受限文件的临时 URI 读取权限。[A3] 快照、租约和清理是模块自己的职责。

**建议契约**：CyxbsLog 结构化事件 → 有界非阻塞队列 → 单写入者/滚动文件，同时适配 Kermit 平台输出；平台适配器负责目录与文件属性，宿主负责分享 UI。普通日志调用只代表尝试接收，不宣称已落盘。日志与导出包分别设置配额，禁止直接分享活动文件。

## 2. Android

| 问题 | 官方事实 | 建议契约 |
| --- | --- | --- |
| 私有文件 | `filesDir` 是应用专属内部目录；应用卸载后专属文件被移除。[A1] | 自动保存使用内部私有目录，不写公共 Downloads 或共享外部目录。 |
| 缓存 | `cacheDir` 可能因存储压力被系统清理；应用仍应自行清理。[A1] | 缓存用于可重建导出包，不承担希望跨启动保留的日志唯一存储。 |
| 备份 | Auto Backup 默认包含内部文件；`getNoBackupFilesDir()` 与缓存目录属于排除范围。Android 12+ 与旧版本规则存在差异。[A2] | 自动日志优先放 `context.noBackupFilesDir/cyxbs-log/`；导出放 `context.cacheDir/cyxbs-log-export/`。若改用 `filesDir`，验证云备份与设备迁移排除，不只依赖 `allowBackup=false` 假设。 |
| 分享 | FileProvider 生成 `content://` URI，通过声明路径和临时 URI 权限提供访问。[A3] | 仅声明专用导出子目录的 `cache-path`，provider 不对外导出并启用 URI 授权；分享入口授予临时读取权限，不授予写权限。 |

**建议契约**：路径白名单不覆盖整个缓存、内部文件或文件系统根；不直接暴露自动日志目录。流程为有序 flush barrier → rotate/seal → 租用封口文件 → 复制/压缩到独立包 → 确认完整 → FileProvider 与分享入口。租约期间不回收源文件。接收方可能延迟读取，分享启动后不立即无条件删除包；设置租约上限并在后续启动清理过期包。这些时序不是 FileProvider 自动提供的保证。

## 3. iOS

### 目录、备份与文件保护

Apple 将 `Library/Application Support` 用于应用管理的支持数据，此类目录默认备份。`Library/Caches` 不备份，可能被系统清理；`tmp` 也不承担长期保留。`NSURLIsExcludedFromBackupKey` 可排除不应备份的数据；通过系统 API 取得路径，不硬编码沙箱绝对路径。[I1]

**建议契约**：自动日志放 `Application Support/CyxbsLog/`，显式设置并核验 backup exclusion；导出放 `Caches/CyxbsLogExport/`。不为导出而把所有日志放 Documents 或开放整个文档目录。创建、轮转、替换文件后核验最终对象属性；属性失败可观察，不静默宣称已限制在设备内。

`FileProtectionType.completeUntilFirstUserAuthentication` 在重启后首次解锁前不可访问；首次解锁后，即使再次锁定仍可访问。同页关联符号说明 `complete` 在锁定或启动时不能读写。[I2]

**建议契约**：需首次解锁后的锁屏后台日志时采用 `completeUntilFirstUserAuthentication`，对每个新文件显式设置并测试。若采用更严格的 `complete`，允许锁屏写入失败或丢弃，不以无界内存补偿。保护属性不替代脱敏。锁定读取失败时，导出返回可解释状态并允许解锁后重试。

### 后台与终止 flush

Apple 说明后台 app delegate 回调约有五秒返回，随后应用会挂起；可用 `beginBackgroundTask` 申请额外时间，但必须处理 expiration 并及时 `endBackgroundTask`，不能假设无限后台运行。[I3] 支持后台运行的应用通常不会仅因退出前台收到 `applicationWillTerminate`；即使收到，也仅有约五秒执行时间。[I4]

**建议契约**：正常运行期间小批量或定时写出，后台转换时执行有上限的 flush；较长任务按 UIKit 规则申请时间并处理到期。最后写出不能只依赖终止回调。flush 成功仅保证调用前已接收事件完成约定屏障；强杀、崩溃、挂起、系统回收和存储失败仍可损失尾部。stream flush 不等同于断电耐久性。

**分享建议**：iOS 宿主把独立快照包交给系统分享面板；common 层提供结果和租约，不依赖 UIKit 页面。取消、UI 不可用、生成失败分别返回状态；导出包设有限寿命和独立配额。具体分享 API、iPad 展示和延迟读取留给实施验证，本轮未核验。

## 4. Desktop 与 Browser

macOS 同样区分用户域 Application Support 和 Caches；沙箱应用应通过系统 API 取得容器目录。[I1]

**Desktop 建议契约**：适配器提供稳定、当前用户拥有的应用数据与缓存目录；禁止相对工作目录写日志，不自动写 Desktop、Documents 或下载目录。macOS 使用系统目录 API。Windows/Linux 的具体 API 与卸载保留规则本轮未核验，纳入首期时补验证。手动导出才用用户选择路径；用户配置的同步/备份不受日志模块全面控制，不能宣称桌面永不云同步。

WHATWG 定义基于 storage key/origin 的存储、默认 best-effort bucket、persistent 模式和配额。`navigator.storage.persist()` 返回布尔值，权限可能不获准；persistent 限制浏览器自动清理，不排除用户清站点数据。`estimate()` 是估算，不是精确可用磁盘；origin 或策略也可能使存储不可用。[W1]

**Browser 建议契约**：不复用 native 目录语义。可采用 IndexedDB 分块/事务写入，persistent 只是增强；暴露已保存、尚未写入、配额不足、存储不可用状态，容忍历史被清理。避免每条日志同步写 localStorage。导出从一致快照生成下载包；下载/Web Share 能力探测待实施核验。native flush 与 browser 事务完成保证分别说明。

## 5. 生命周期与故障契约建议

以下均为需求建议，不是平台自动行为。

| 场景 | 建议契约 |
| --- | --- |
| 配额与保留 | 限制单文件、总量、时长、队列及导出总量；先删最老且无租约的封口文件。前期 20–30 MiB、3–7 天仅是候选量级，需结合事件速率确定。 |
| 手动清理 | 排队执行清理屏障，关闭/轮转后重建文件；明确是否含导出包，返回成功/部分成功/失败。租约保留需明示；无法撤回外部副本。 |
| 登出 | 建议默认删除旧账号日志及未交出的导出包；保留设备诊断必须无可识别账号内容。 |
| 账号切换 | 用不含明文账号的 session/generation；切换屏障后接收新事件。旧队列不得进入新账号文件，导出默认不混合账号。 |
| 磁盘满/配额满 | 一次有界清理重试后仍失败则丢弃/停用文件写入，保留其他 writer；统计 dropped/error 并限频恢复。禁止阻塞业务、无界缓存、递归记录磁盘失败。 |
| 初始化与属性失败 | 自动日志失败不使业务崩溃；目录、backup exclusion、protection 错误分别可观察。 |
| flush/snapshot | flush 是同一序列的屏障，返回 success/timeout/failure；snapshot 只含封口文件/一致视图。队列拒收事件不能声称已 flush。 |
| 导出失败/取消 | 取消与生成失败分开；仅完整包进入分享 UI。TTL 与启动清理处理过期及中断遗留包。 |
| 脱敏 | 入队/落盘前去除 token、密码、Cookie、完整请求体、可识别账号信息；导出不能成为首次脱敏环节。结合业务字段验证。 |

建议验收：跨启动可读、滚动回收、缓存删除后重建、账号切换竞态、租约与清理并发、磁盘满不阻塞业务、iOS 首次解锁前/再次锁定、后台超时、Browser 配额不足/清站点数据。Android 验证路径、只读权限与真实接收应用；iOS 验证每次轮转后的保护和备份排除。

## 6. 证据边界

- 未进行真机、备份恢复、锁屏、分享、磁盘压力测试。
- Apple 文件系统指南为归档资料，仅使用目录/备份规则；保护与生命周期使用现行文档，动态正文通过官方 DocC JSON 读取。
- Android 网页抓取返回导航截断，下列官方链接需在实施前复核；IDE 定义查询未成功取得 AndroidX 源码，不将内部实现或当前依赖行为标为源码已验证。
- 未核验工程 manifest/backup rules/FileProvider 版本、Apple entitlements/真实属性、iOS 分享 UI、Windows/Linux 目录实现、Browser 下载/Web Share 兼容性；不能宣布平台集成已完成。

## 官方来源（8 项）

1. [Android：应用专属文件][A1]。
2. [Android：Auto Backup][A2]。
3. [AndroidX：FileProvider][A3]。
4. [Apple：File System Basics][I1]。
5. [Apple：completeUntilFirstUserAuthentication][I2]。
6. [Apple：Extending background execution time][I3]。
7. [Apple：applicationWillTerminate][I4]。
8. [WHATWG：Storage Standard][W1]。

[A1]: https://developer.android.com/training/data-storage/app-specific
[A2]: https://developer.android.com/identity/data/autobackup
[A3]: https://developer.android.com/reference/kotlin/androidx/core/content/FileProvider
[I1]: https://developer.apple.com/library/archive/documentation/FileManagement/Conceptual/FileSystemProgrammingGuide/FileSystemOverview/FileSystemOverview.html
[I2]: https://developer.apple.com/documentation/foundation/fileprotectiontype/completeuntilfirstuserauthentication
[I3]: https://developer.apple.com/documentation/uikit/extending-your-app-s-background-execution-time
[I4]: https://developer.apple.com/documentation/uikit/uiapplicationdelegate/applicationwillterminate(_:)
[W1]: https://storage.spec.whatwg.org/
