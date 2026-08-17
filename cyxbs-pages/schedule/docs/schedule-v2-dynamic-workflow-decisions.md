# Schedule v2 动态工作流决策记录

> [!CAUTION]
> **状态：已归档的 Workflow 历史运行记录与恢复资料。** 本文不是当前 Schedule v2 master DAG、跨端合同、实施计划或待办清单；下文的“当前”“后续”“下一切片”“必须”等措辞只按各条记录形成时的分支与时间点解释，不能直接授权今天的开发、验收、部署或工作流恢复。
>
> 当前跨端合同只认三篇 canonical 文档：[AtomicField 与原子批次](schedule-v2-field-group-sync-design.md)、[重复日程单次覆盖能力矩阵](schedule-v2-recurrence-override-capability-matrix.md)、[资源版本同步流程](schedule-v2-resource-version-sync-flow.md)。当前后端事实只认独立仓库 `/Users/guoxiangrui/GolandProjects/magipoke-todo` 的 `guoxiangrui/schedule` 分支及其 `SCHEDULE_BACKEND_DESIGN.md`；旧 Android 客户端代码与本文记录仅用于识别历史能力和恢复提交，不得反向覆盖 canonical 合同。
>
> D-011～D-015、D-025、D-040、D-042～D-044 等旧 migration、七表、cursor/change-feed、旧 endpoint 与 semantic W06 后端路线已经取消或被替代，保留它们只为审计历史，**不得重新解释为现行 TODO**。后续跨仓工作统一从已经建立的 [Schedule v2 Codex 交接](schedule-v2-codex-handoff.md) 进入；不得从本文抽取新的实施任务。
>
> 下文时间线、决策编号和可追溯提交保持原样；如需恢复某次历史切片，应先核对对应 commit、当前分支差异、canonical 合同与交接文档，而不是更新本文历史状态来模拟当前事实。

> 本文记录 Schedule v2 动态工作流执行期间，由主 Agent 在用户暂时离线时自主作出的非显然方案选择。
>
> 记录目的：让用户后续能够核对选择依据、影响范围、验证证据与回滚方式。本文不是协议或架构事实源；最终事实仍以对应专题文档和当前代码为准。

## 执行原则

1. 优先选择与已冻结专题设计、现有代码惯例、数据完整性和最小权限原则一致的方案。
2. 同等可行时，优先选择改动范围更小、可独立验证、可安全回滚且不引入长期兼容负担的方案。
3. 自主选择不得推翻动态工作流 runbook 第 4 节已经确认的技术决策。
4. 普通文件修改、依赖解析、编译、单元测试、静态检查与只读 IDE 分析可直接执行。
5. 每个完成、验证并独立审查收敛的切片按用户后续明确授权立即独立 commit；不自动 amend、push、创建 PR、部署服务、连接生产数据库、运行真实 migration、操作真实用户日历或执行破坏性设备测试。
6. 若所有候选方案都可能导致不可恢复的数据损坏、越权访问、电脑或开发环境损坏，则暂停受影响切片，保存证据并继续其他安全任务。
7. 每项记录必须包含选择、理由、影响、验证与回滚点；已实施行为再同步到对应专题文档的“当前实现”。

## 决策记录

### D-001：用户离线期间的默认决策与授权边界

- **状态**：已确认
- **背景**：动态工作流预计持续较长时间，用户于 2026-07-14 暂时离线休息，不适合因常规方案选择或工具授权反复中断。
- **候选方案**：
  1. 所有歧义均暂停并等待用户；
  2. 主 Agent 采用推荐方案继续推进，并保留结构化决策记录；
  3. 无限制执行包括不可逆外部操作。
- **选择**：采用方案 2。
- **理由**：能够持续推进本地可验证工作，同时通过记录、测试和回滚点维持可审计性；方案 1 会不必要地阻塞，方案 3 超出安全边界。
- **影响范围**：本轮 Android 有限双向同步、iOS EventKit 单向导出、common 持久化内核、Schedule v2 后端改造、测试与文档同步。
- **授权范围**：允许项目内代码和文档修改、后台 Agent/Workflow、IDE MCP 查询、依赖解析、编译、单元测试、静态检查和本地非破坏性辅助命令。
- **禁止范围**：损坏电脑或开发环境、删除无法确认归属的数据、操作生产环境、真实数据库 migration、真实用户日历、破坏性设备测试，以及自动 commit、push、建 PR或部署。
- **验证方式**：每个实施切片执行适用的单元测试、编译、IDE diagnostics 和独立审查；最终报告列出所有未执行的外部验证。
- **回滚点**：所有业务改动保持为未提交 Git 工作区变更；按文件或切片审阅后可恢复。不得覆盖工作流启动前已存在的用户文档改动。

### D-002：实施顺序先补齐阶段二硬门禁

- **状态**：已采用
- **背景**：只读探索确认 Android 单向导出主体已存在，但 Provider 回读会把秒/毫秒或非 UTC 午夜值收窄到领域精度，可能把外部非 canonical 修改误判为 `NoOp`；SQL、双向、iOS 和后端均依赖可信的 canonical calendar 边界。
- **约束与证据**：总路线图要求阶段二收尾后才能进入 3A；`AndroidScheduleCalendarGateway.reconstructTiming()` 当前在转换前未完整验证分钟和 UTC 午夜精度。
- **候选方案**：
  1. 直接开始 SQL 和双向；
  2. 先完成 Provider 时间精度 fail-closed 与 Unsupported 可见性，再冻结跨端语义；
  3. 同时并发修改 Android 与 common 核心文件。
- **选择**：采用方案 2；Android 收尾由单一 writer 完成，主 Agent 同时只读研究后续语义，不并发写共享文件。
- **理由**：关闭已知数据误判风险，满足路线图门禁；切片小、可独立测试且不会把不可靠读回结果带入三方 baseline。
- **影响范围**：Android Calendar Provider gateway、导出状态/UI、对应纯逻辑或 device-test 源码；暂不引入 SQL、ContentObserver、SyncAdapter 或三方 diff。
- **验证方式**：运行 `desktopTest`、Android 主源码编译、Android device-test 源码编译和 IDE diagnostics；本轮不运行真实 Provider 测试。
- **回滚点**：切片保持未提交，修改范围与后续 SQL/common 内核隔离，可按相关 Android 文件整体回退。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[Android 单向导出收尾](schedule-v2-calendar-export-hardening.md)。

### D-003：共享文件采用主 Agent 串行 ownership

- **状态**：已采用
- **背景**：当前可用 skill 中没有负责 JetBrains ChangeList/共享文件锁定的专用 skill；Android、iOS、common 和文档位于同一工作区。
- **约束与证据**：runbook 要求相关 skill 不可用时停止共享文件并行写入，改由主 Agent 串行处理并记录原因。
- **选择**：平台实现 Agent 仅修改其明确分配的独占文件；common、构建配置、领域模型和文档由主 Agent 串行 owner，交接前完成读取、测试和 Git diff 核对。
- **理由**：不自创无工具保障的锁协议，并避免 Android/iOS writer 覆盖用户或其他 Agent 的改动。
- **影响范围**：本轮客户端仓库全部共享文件；后端位于独立仓库，不受该文件互斥约束。
- **验证方式**：每个切片前后检查 Git status/diff；最终核对 Android/iOS 文件归属及 `.claude/shared-workspace-subagents.md` 未进入业务变更。
- **回滚点**：一次仅有一个共享文件 writer，可按切片回退。
- **关联事实源**：[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-004：DST gap/overlap 使用显式跨端 resolver

- **状态**：已采用，客户端 common resolver 与 Android 导出接入已实施并通过本地验证；iOS/后端接入未在本切片实施
- **背景**：`MinuteTimeDate` 表示无 offset 的墙上时间；DST gap 中部分本地时间不存在，overlap 中同一墙上时间对应两个 instant。当前平台默认解析行为不能作为跨端协议事实。
- **候选方案**：
  1. gap 拒绝、overlap 选择 earlier instant；
  2. gap 按缺口长度向前平移、overlap 选择 earlier instant；
  3. 依赖 Android/iOS/JVM/Go 各自默认；
  4. 保存用户输入时同时固化 UTC offset。
- **选择**：采用方案 2，即 `SHIFT_FORWARD_BY_GAP + EARLIER_INSTANT`，并返回 `Exact / GapShifted / OverlapResolved` 之类的机器可读分类；禁止调用方绕过 resolver 直接依赖平台默认。
- **理由**：gap 平移保留相对缺口的位置，例如 02:30→03:30，比钳制到 03:00 更少损失分钟语义；overlap 选择更早 instant 可稳定复现且避免同一 occurrence 在回拨后延迟一小时。方案 3 跨端不一致；方案 4 会把 adapter offset 泄漏进领域和 wire，并在时区规则更新后形成双事实。
- **影响范围**：RRULE 展开、RFC `UNTIL`、Android/iOS 平台映射、后端校验与 contract vectors；wire 仍传本地墙上时间和 IANA zone，不新增 offset。
- **验证方式**：已由 `ScheduleDstResolverTest` 覆盖 America/New_York 2026 gap/overlap、Asia/Shanghai exact、非法 zone、Australia/Lord_Howe 30 分钟 gap、Europe/Paris 历史秒级 exact offset，以及 Africa/Addis_Ababa 历史非整分钟 gap 的机器可读拒绝；`desktopTest`、`compileAndroidMain` 和 Android device-test 源码编译通过。iOS/后端共享 vectors 仍待对应工作流接入。
- **回滚点**：resolver 作为新纯 common API 独立落地；接入各调用链前可整体回退，不迁移已上线数据。
- **关联事实源**：[Android 单向导出收尾](schedule-v2-calendar-export-hardening.md)、[后端设计](schedule-v2-backend.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)。

### D-005：occurrence patch 采用显式领域三态与紧凑 wire

- **状态**：已采用，客户端 Domain/Record/wire/repository/UI 编辑链已实施并通过本地验证；SQL 与后端 schema 尚未在本切片实施
- **背景**：当前 nullable sparse `OccurrencePatch` 无法统一表达继承、显式清空和替换，尤其分类、描述、提醒及原子 timing 会出现语义歧义。
- **候选方案**：
  1. 继续使用 nullable 字段并按字段约定解释；
  2. domain 使用 `FieldPatch.Inherit/Clear/Replace`，wire 使用字段省略 / JSON `null` / JSON value；
  3. wire 和 domain 均使用 `{mode,value}` tagged wrapper；
  4. 为每个字段增加 `hasX/clearX/value` 平行布尔量。
- **选择**：采用方案 2。Kotlin domain 使用 sealed `FieldPatch<out T>`；专用严格 serializer 保留 JSON presence，拒绝未知字段、任意对象层级重复字段和非法组合。`Clear` 与 `Replace(emptyList())` 都合法且必须可区分。客户端 Room3 SQL 使用每字段 mode + value，reminders 使用 mode + child rows，timing 使用一个原子 mode + 判别列；服务端按后端专题的独立物理合同保存完整 canonical `patch_json`，不要求镜像 Room3 规范化结构。
- **理由**：domain 保持显式且类型安全，wire 紧凑并方便新增可选字段；方案 1 已证明丢语义，方案 3 冗长并增加跨端样板，方案 4 容易形成非法组合。canonical hash 基于专用 strict canonical JSON，不复用 `ignoreUnknownKeys` 的通用 Json。
- **影响范围**：OccurrencePatch、validator、record/mapper、outbox payload、Go DTO、SQL schema 与 contract vectors。业务本身不允许 `Replace(null)`；可清空字段用 `Clear`，不可清空字段在 validator 中拒绝。
- **验证方式**：`OccurrencePatchWireCodecTest` 已覆盖省略/null/value、任意对象层级 duplicate key、nested canonical key order、非法 timing/reminder 组合、稳定 canonical hash 输入及 Clear/Replace(emptyList()) 区分；`ScheduleMutationStrictContractTest` 覆盖显式 schemaVersion、ExceptionBody patch 语义复验与 operation/resource/payload 一致性；mapper/domain/recurrence/repository/store 回归覆盖 timing 原子性、分类/描述清空、提醒三态、Settings 原始 token 流递归 duplicate-key 拒绝、父系列关系完整性和 recurrence identity 真实性。THIS_ONLY 按 initial occurrence 逐字段 dirty 保留未触碰 existing patch；仅修改 RRULE 时，ALL 发 Update、THIS_AND_FOLLOWING 发 SplitSeries、THIS_ONLY 明确 no-op。系列 payload 只合并实际 dirty 字段，不提升 occurrence override；未触碰 RRULE 原样保留 UI 不可表达字段，实际编辑后按当前 UI 子集整体替换。recurrence UI/摘要均使用有效规则与父系列 anchor，支持 YEAR 选项且首次滚轮 emission（包括超 UI 范围的既有 interval）不回写；仅真实日期点击修改 UNTIL，bounds clamp/初始化不产生 mutation。未触碰 timing 原样保留初始领域值，避免 DST overlap fold 丢失；实际编辑时间使用共享 DST resolver，未排期/全天打开时间区无条件跳过首次 wheel emission；模式切换由显式点击路径立即提交，collector 不重启，避免默认时间污染或吞掉真实切换。提醒 dirty 只看提醒控件，timing 改 Unscheduled 时 canConfirm 使用原子清理后的合法视图，系列 scope 清理提醒、THIS_ONLY 拒绝非法替换。title/description（含 occurrence patch）在远端、Store/Record、Repository 与 canonical wire/mutation decode 返回值入口统一 trim。当前 UI 尚无显式“恢复继承”动作，用户真实改动字段按显式 Replace/Clear 保存。完整 desktopTest 及 common metadata、JS、iOS Simulator、Android、Desktop、Android device-test 源码编译已通过。
- **回滚点**：Schedule v2 未上线，不建立 legacy reader；切片保持未提交，可在 SQL/后端 schema 冻结前整体回退。
- **关联事实源**：[后端设计](schedule-v2-backend.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)。

### D-006：平台事件引用改为 opaque string

- **状态**：已采用，代码实施中
- **背景**：common planner 的 `providerEventId: Long` 固化了 Android CalendarContract row ID，而 iOS EventKit 使用可失效的字符串 `eventIdentifier`。
- **选择**：common 使用非空、长度受限的 opaque platform event reference；Android 在 gateway 边界严格编码/解析 canonical 正十进制 Long，iOS 直接保存 identifier。URI 仍是稳定身份，平台引用仅作快速定位且不进入 fingerprint。
- **理由**：消除 Android 类型泄漏，同时不把 EventKit identifier 错当永久业务身份。
- **影响范围**：common planner、Android gateway/coordinator/device-test 和后续 iOS gateway。
- **验证方式**：planner 传递 opaque ref；Android 拒绝前导零、负数、非数字、零和溢出；非法 ref 不执行 Provider 操作。
- **回滚点**：纯类型替换，不涉及已上线持久化数据。
- **关联事实源**：[iOS EventKit 导出设计](schedule-v2-calendar-ios-eventkit.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)。

### D-007：Slice 0 冻结有界 occurrence 投影合同

- **状态**：已采用且已实施；SQL、远端 bootstrap、Android 双向、iOS EventKit 与设备行为验收不包含在本切片。
- **背景**：主页面、Feed 与课表此前各自投影重复日程，边界、跨日、移动实例和教学周页码容易不一致；SQL 候选查询及后续远端入站也需要一个不依赖可见窗口的稳定 identity 事实。
- **候选方案**：
  1. 保留各 UI 以“实例起点落窗”为准的局部筛选；
  2. 在 common 统一冻结严格半开窗口、实际占用和 effective timing，再由各 UI 做展示切片；
  3. 为每个可见窗口生成或持久化 occurrence identity。
- **选择**：采用方案 2。所有 bounded 查询严格使用 `[startInclusive, endExclusive)`：Timed 与 AllDay 以实际占用区间相交；Deadline 的展示占用固定为 1 分钟；Unscheduled 默认排除在 bounded window 外。`Unscheduled + recurrence` 在持久化/校验边界拒绝。重复实例 identity 始终由原始 recurrence anchor、时区/全天属性等生成性事实构成，绝不借用可见窗口；moved-in/moved-out 一律先应用 exception 的 effective timing 后再判定相交。主页面按日将 Timed/AllDay 拆片；课表仅消费 Timed 日片，page 0 没有教学周窗口，教学周从 page 1 起按 1-based 计算、切页重算，片段实际日期参与课表 item identity。
- **理由**：半开区间可消除相邻日/周边界重复；以有效时间判定能同时覆盖原实例移入与移出窗口；展示拆片不污染领域 occurrence；拒绝未排期重复防止“无界规则 + 无时间锚点”进入持久化；课表页码与日期进入 identity 可避免跨日切片被层级 diff 合并。
- **影响范围**：common 时间校验、recurrence 展开与 snapshot 投影；主页面日时间轴、Feed 一年摘要、课表周页装饰及其测试。SQL 未来只能做候选收窄，仍必须交 common 精确展开，不能改变本合同的最终语义。
- **验证方式**：已运行完整 `desktopTest`；`compileCommonMainKotlinMetadata`、`compileKotlinJs`、`compileKotlinIosSimulatorArm64`、`compileAndroidMain`、`compileKotlinDesktop`、`compileAndroidDeviceTestSources` 均通过；已完成 Git diff check 与 IDE diagnostics。新增/更新测试覆盖半开边界、跨午夜 Timed、跨日 AllDay、Deadline 一分钟占用、Unscheduled 默认排除与显式 Feed 纳入、未排期重复拒绝、moved-in/moved-out、effective timing、日拆片、课表 page 0/1-based/切页重算/fragment-date identity，以及同一原子本地 snapshot 的 deep-link identity 解析。device-test 仅完成源码编译，未运行真机或模拟器测试；上述编译结果不构成设备行为验收。
- **回滚点**：工作区仍未提交，未执行 commit/push；按 Slice 0 涉及文件审阅并分别回滚本切片修改，且不得覆盖工作区中用户或其他阶段的既有改动。
- **关联事实源**：[当前创建、更新与同步数据流](schedule-v2-current-data-flow.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[总路线图](schedule-v2-calendar-roadmap.md) 与当前 `RecurrenceEngine`、UI 投影代码及对应测试。

### D-008：非 Web durable store 的 Room3 KMP 3.0.0 P0 已验证 KEEP_SINGLE_MODULE

- **状态**：已确认并完成 P0 toolchain/probe 验证；尚未接入生产 Store。
- **背景**：Settings 的 primary/backup JSON envelope 已保证当前 local-first 语义，但全量 read-modify-write、写放大、进程级锁和无法按窗口查询冷数据不适合后续 outbox、双向日历基线及跨进程同步。Schedule v2 尚未上线，用户已明确选择直接替换 Settings：不迁移、不建 legacy reader、不双写。
- **候选方案**：1. 继续 Settings JSON envelope；2. SQLDelight；3. 在同一 Schedule 模块以 Room3 KMP 验证并按阈值决定保留或拆分。
- **选择**：采用方案 3，并以验证结果维持 `KEEP_SINGLE_MODULE`：同一 Schedule 模块的 `noWeb` 接入 Room3 3.0.0、KSP 2.3.6 与 `sqlite-bundled` 2.7.0。Schedule 通过独立 `useRoom3()` 配置 `androidx.room3` runtime、compiler 与 Gradle plugin；Room3 仅存在于 noWeb，compiler 仅配置给非 Web target。旧 `useRoom()` 与 Room 2.8.4 保持其他既有模块的原语义。P0 只新增非业务 probe、builders 与测试源码。
- **理由**：Room3 KMP 提供 KMP Entity/DAO/Database、`@ConstructedBy`、`RoomDatabaseConstructor` 与各平台 builder，当前 P0 源码已使用 `androidx.room3.*`；此前验证证明单模块没有把 Room3/SQLite 泄漏到 JS/Wasm。继续 Settings 不满足范围查询和跨进程事务；SQLDelight 不再继续评估，不引入 plugin/driver/`.sq`/`.sqm`。
- **影响范围**：P0 不改变领域或 wire 合同，也不改变当前生产数据路径：`SettingsScheduleLocalStore`、Provider、Repository 仍为当前事实；未切 Store、未迁移、未双写。Android/Desktop/iOS 的 Room3 P0 constructor 与平台 builder 源码已存在，Desktop 真 SQLite CRUD 测试源码已建立；Android/iOS 未在设备或模拟器打开验证。Web 的 remote-required 是已选目标但尚未实现，当前仍复用 Settings。
- **验证方式**：历史执行已验证 `@ConstructedBy` constructor 生成、Android/Desktop/iOS builders、`BundledSQLiteDriver`、`schemaDirectory`、Desktop 真 CRUD、Web graph 隔离与 configuration-cache reuse；本次独立复验中，离线 JS/Wasm compile classpath 再次确认无 Room3/SQLite，但 `desktopTest`、JS/Wasm 编译和 configuration cache 复跑在任务执行前被 Maven Central 对 `org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.20` 的 HTTP 403 阻断，不能表述为本次重新通过或 Room3 代码失败。当前工作树尚无受版本控制、可审阅的 Room3 schema JSON 基线。`KEEP_SINGLE_MODULE` 仅代表 P0，业务 schema 接入后必须复验。
- **回滚点**：在未正式接入前可删除 Room3 P0 probe/toolchain，当前 Settings 生产路径不受影响；不导入/迁移任何 Settings 数据，不执行真实 migration。仅在业务接入后真实出现 `RoomDatabaseConstructor` 生成失败、Web 出现 Room3/SQLite，或 configuration cache 无法稳定复用时拆独立 no-Web Room3 模块，保持 durable API 与 Web 边界不变。
- **官方来源**：Room3 3.0.0 的 `androidx.room3:room3-runtime`、`room3-compiler` 与 `room3-gradle-plugin` 坐标和 plugin/API 已通过官方 Google Maven 工件核实；旧 [Room KMP 指南](https://developer.android.com/kotlin/multiplatform/room) 与 [Room release notes](https://developer.android.com/jetpack/androidx/releases/room) 的 Room 2 表述不作为 Room3 兼容性证据。

### D-009：撤销因 configuration cache 假失败作出的 SPLIT 判定

- **状态**：已撤销 SPLIT；维持 `KEEP_SINGLE_MODULE`。
- **背景**：先前单模块验证曾在 configuration cache 阶段失败，按既定拆分阈值暂判为 SPLIT 候选。
- **约束与证据**：排查确认失败源于 `settings.gradle` 根扫描将 `.gradle/configuration-cache` 纳入扫描的基线 bug，而非 Room3 KMP、KSP 或 noWeb source set。修复该基线问题后，工程仍为 65 projects，历史执行记录显示第二次构建 configuration cache reused。
- **候选方案**：1. 依照错误归因继续拆分 Room3 模块；2. 修复根扫描基线 bug 后重验单模块；3. 忽略 cache 失败继续接入业务 Store。
- **选择**：采用方案 2，撤销 SPLIT 判定；本轮未命中单模块失败拆分阈值。
- **理由**：拆分决策必须建立在 Room/toolchain 的真实失败上；将与 Room 无关的根扫描缺陷视为模块边界证据会增加无收益的构建和依赖复杂度。方案 3 会掩盖 build-cache 回归。
- **影响范围**：只修正 P0 构建验证归因和模块边界结论；Room3 仍仅是非业务 probe，`SettingsScheduleLocalStore`、Provider、Repository 继续承担生产路径。
- **验证方式**：修复后确认 65 projects 数量不变、全 target 构建通过且第二次 cache reused；JS/Wasm main/test KSP 与 8 个 compile/runtime graph 继续无 Room/SQLite。IDE 对 noWeb 的 unresolved 为 script/source-set model 假阳性；Gradle buildErrors 为 0，未将该 IDE 假阳性描述为已修复。
- **回滚点**：若后续真实出现 constructor 生成失败、Web 泄漏或 cache 不能收敛，重新触发拆分阈值并拆出 no-Web Room 模块；当前无需迁移或修改业务数据路径。
- **关联事实源**：[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[当前创建、更新与同步数据流](schedule-v2-current-data-flow.md)。

### D-010：Room3 Web runtime 不改变 Schedule Web remote-required 边界

- **状态**：已采用并完成调研记录；Web repository/factory 尚未实现。
- **背景**：Room3 3.0.0 发布 JS/Wasm runtime variants，容易被误解为引入依赖即可在浏览器获得与非 Web 相同的 durable local-first 数据库。
- **约束与证据**：Web `Room.databaseBuilder<T>(name)` 只创建 builder，应用仍须注入 `SQLiteDriver`；`androidx.sqlite:sqlite-web` 的 `WebWorkerSQLiteDriver` 只与调用方提供的 Worker 通信，不负责创建 Worker、加载 SQLite WASM、选择 VFS 或默认持久化。AOSP 内部测试 Worker 使用 SQLite-WASM `OpfsDb(fileName)`，但不是已发布的生产 artifact。
- **候选方案**：1. 因 Room3 Web runtime 存在而直接让 Web 使用本地 SQL；2. 自建 OPFS Worker 并改变产品为 Web local-first；3. 保持既定 remote-required，未来若评估 OPFS 另开产品/同步/安全 RFC。
- **选择**：采用方案 3。当前 Web 仍通过 common Provider 使用 Settings-backed local-first，这是尚待修复的过渡实现；后续必须切为 `RemoteRequiredScheduleRepository`，不得用 Settings、内存或 Room3 runtime 伪装 durable。
- **理由**：Room3 API 支持与持久化后端是不同职责；OPFS 虽通常跨刷新和普通关闭保留，但清站点数据、隐私模式结束、配额驱逐、多 Tab/Worker 锁冲突和浏览器隔离策略均会改变数据可靠性承诺，不能静默纳入现有产品边界。
- **影响范围**：Room3 依赖继续仅进入 noWeb；JS/Wasm graph 不携带 Room3/SQLite。Web remote-required 仍是设计目标且尚未实现，当前事实不能误报为已经分流。
- **验证方式**：官方 Room3/SQLite Web API 与 AOSP 测试实现已只读核对；离线 JS/Wasm compile classpath 再次确认当前 graph 无 Room3/SQLite。本次 JS/Wasm 编译复跑受 Maven Central Kotlin metadata HTTP 403 阻断，未把依赖图检查误报为编译或运行时验收。
- **回滚点**：仅文档和产品边界决策，无用户数据或 migration；未来若正式选择 OPFS，必须以新 RFC 明确 durability、清理、配额、并发、同步与安全语义后再修改代码。
- **关联事实源**：[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[当前创建、更新与同步数据流](schedule-v2-current-data-flow.md)。

### D-011：后端首切片收窄为显式 migration runner，不提交不完整业务 schema

- **状态**：已撤销；原实现已通过后续提交回退，建表机制由 D-015 的现有 GORM `AutoMigrate` 取代。本节仅保留历史决策背景。
- **背景**：后端当前只有 Todo v1 启动期 `AutoMigrate(ToDoORM)`，冻结设计要求 Schedule v2 使用隔离的显式版本 SQL。首版实现同时生成了 runner 与业务 `000001`，独立审查发现该表结构不能无损表达 Timed/Deadline/AllDay/Unscheduled、change revision 等冻结合同；`CREATE TABLE IF NOT EXISTS` 也会把既存同名错误表静默视为成功。
- **约束与证据**：Schedule v2 不得修改 Todo v1 表、路由或启动路径；本地不得执行真实 migration；业务 migration 必须在推进恢复游标前证明既存 schema 的列、主键、关键索引和约束符合预期。MySQL advisory lock 是物理连接/session 级事实，工作 context 取消或 `RELEASE_LOCK` 失败不能把持锁连接放回池。
- **候选方案**：1. 在本切片立即补齐全部业务表并自建真实 MySQL fingerprint 验证；2. 保留不完整 `000001`，后续再 ALTER；3. 将首切片收窄为 runner、显式 CLI 与 fake/contract tests，业务 schema 另开独立切片。
- **选择**：采用方案 3。当前生产 manifest 为空；`Up` 在任何数据库操作前校验 manifest，空 manifest 零 DB 操作。runner 保留 marker 分段、checksum/版本漂移拒绝、逐 statement at-least-once 恢复、同连接 `GET_LOCK/RELEASE_LOCK`；取消后的释放使用独立短超时 context，无法确认释放时以 `driver.ErrBadConn` 废弃物理连接。`validate` 完全离线，`up` 仅接受显式 `SCHEDULE_V2_MYSQL_DSN`。
- **理由**：收窄后可以独立验证迁移基础设施，不提前冻结错误业务 schema，也不以 ALTER 或开发期兼容负担掩盖设计缺口。完整业务 migration 需要一次性对齐冻结 wire/domain 合同，并在真实 MySQL 上验证 fingerprint，超出无数据库 runner 切片的可靠验证范围。
- **影响范围**：后端新增 `dao/schedulev2/migrations` 与 `cmd/schedule-migrate`；不修改 `dao/init.go`、现有 `cmd/main.go`、路由、Todo v1 DAO/model。当前仍没有 Schedule v2 表、API、CAS、幂等、bootstrap/change feed 或远端可用能力。
- **验证方式**：migration 包定向测试、CLI/包组合测试、离线 `validate`、GoLand diagnostics、`git diff --check` 与最终独立审查通过。`go test ./...` 中新增 migration 包通过，但仓库既有 Todo 断言、Nacos/OTel 外部初始化、Umeng 白名单及长等待测试失败/超时；未将其误报为本切片通过。未执行 `up`、真实数据库 migration 或部署环境锁/恢复测试。
- **回滚点**：删除新增 runner/CLI 目录即可回退；当前 manifest 为空且未连接数据库，不存在业务表或数据回滚。下一业务 schema 切片必须重新独立设计、审查，并以真实 MySQL fingerprint 和不兼容既存表拒绝测试作为门禁。
- **关联事实源**：[后端 Schedule v2 设计](schedule-v2-backend.md)、[总路线图](schedule-v2-calendar-roadmap.md)。

### D-012：后端只创建全新 Schedule v2 表并暂缓真实 MySQL 集成门禁

- **状态**：部分保留；“只创建全新 `schedule_v2_*` 表、不迁移旧数据、真实 MySQL 验收延后”继续有效，建表机制以 D-015 为准。
- **背景**：用户明确 Schedule v2 尚未上线，后端无需兼容 Todo v1 或旧 Schedule 数据；当前只有集成环境，准备专用 MySQL 测试库、Secret、清理流程和真实建表验证成本较高，不应阻塞客户端 Room3 或后端离线可验证的物理模型。
- **约束与证据**：新实现必须使用独立的 `schedule_v2_*` 表；不得修改、复用或搬运旧业务表，不建立 legacy reader、兼容 ALTER 或双写。当前入口仅为 D-015 的现有 GORM `AutoMigrate`，尚未连接真实数据库。
- **选择**：直接设计并实现全新的 Schedule v2 七表 ORM model 与代码级 schema contracts；当前不准备或运行真实 MySQL integration suite，也不把 `information_schema` 验收作为本地代码切片完成门禁。
- **理由**：全新表消除了旧数据迁移的当前需求；先完成可离线审查的业务合同可以继续推进同步闭环。真实 MySQL 的权限、engine/collation、索引与外键执行结果仍有部署风险，但应在真正部署准备阶段集中验证，而非误报为已经验证。
- **影响范围**：后端 GORM model、AutoMigrate 启动接入、测试计划与路线图；Todo v1 `AutoMigrate` 保持原有行为且不承担 Schedule v2。客户端 Room3 不受该延期阻塞。
- **验证方式**：当前切片使用 GORM schema parser、MySQL dialector DryRun contracts、Go 单元测试、编译、diff check 与独立审查；部署前必须另开任务准备隔离 MySQL 环境，真实运行 AutoMigrate，并以 `information_schema` 核验物理结构和提供清理/回退流程。
- **回滚点**：当前未执行真实建表、未连接数据库；可在部署前回退七表 model/AutoMigrate 接入。开发期不兼容测试表直接清理测试数据库，不引入兼容迁移。
- **关联事实源**：[后端 Schedule v2 设计](schedule-v2-backend.md)、[总路线图](schedule-v2-calendar-roadmap.md)、D-011、D-015。

### D-013：Room3 业务 schema 基础以 DAO fail-closed + SQLite 关系合同收口

- **状态**：已采用并完成实现、返修、复验与独立只读复审；mapper、transaction Store、Flow、Provider/Repository 不在本切片。
- **背景**：P0 toolchain 已证明 Room3 KMP 可在单模块 noWeb 使用，但业务表必须先冻结 account-first PK/FK、timing union、RRULE selectors、occurrence patch 三态、outbox/tombstone 与 sync state 的物理表达，才能安全实施 mapper 和事务 Store。
- **约束与证据**：所有业务表包含 `account_id`，复合关系不得跨账号；日程删除级联 reminder、selector 与 exception 树，category 使用 `NO_ACTION`，outbox/tombstone 不建业务 FK；全天 identity 的 SQL 时区 key 使用空字符串；公开 DAO 写入口不能通过 `@Update` 或 child insert 绕过领域可恢复性校验。
- **选择**：在 `noWebMain` 建立业务 Entity/DAO/Database，在 Desktop 使用独立 bundled SQLite 数据库；KSP 自动生成并提交 schema JSON。DAO 对 timing/RRULE/patch、父系列结构、completion/status、selector、reminder Int 范围和 outbox 状态机做 fail-closed 校验；父系列存在 selector/exception 时，直接 update 不得修改 recurrence/timing 结构，未来 Store 必须在同一事务中先删旧 child、更新父行再写回新 child。
- **理由**：schema 关系约束负责账号与生命周期完整性，DAO wrapper 负责 SQLite 无法表达的 tagged union、枚举和跨行前置条件；二者分层可在 mapper/Store 尚未接入时阻止不可恢复数据进入 durable store，又不把有限 occurrence 展开塞进 DAO。
- **影响范围**：新增 Schedule Room3 业务 Entity/DAO/Database、Desktop builder、业务 schema JSON 与 Desktop contracts；Settings 仍是当前生产路径，不迁移、不双写，Web 仍待切 remote-required。
- **验证方式**：Room3 KSP、定向业务数据库合同和完整 `desktopTest` 通过；Android、iOS Arm64、iOS Simulator、JS、Wasm 编译通过；JS/Wasm compile/runtime 四个 graph 无 Room3/SQLite；相同多平台构建第二次显示 `Reusing configuration cache`。IDE build errors 为 0，`git diff --check` 通过。首次独立审查提出 update bypass、UNSCHEDULED occurrence、identity kind/zone、selector/reminder 与测试缺口；第二轮提出父系列 child 兼容、completion/status/outbox 枚举、Int 边界和 FK 证据，均返修后由最终只读 Workflow 判定 `CLEAN`。
- **回滚点**：本切片尚未接入生产 Provider/Repository，也未迁移 Settings 数据；可整体回退新增业务 Room3 文件与 schema JSON，P0 toolchain 和当前 Settings 数据路径不受影响。
- **关联事实源**：[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[当前创建、更新与同步数据流](schedule-v2-current-data-flow.md)、[总路线图](schedule-v2-calendar-roadmap.md)。

### D-014：后端七表 schema 保留 canonical JSON 核心事实并冻结分页安全事务拓扑

- **状态**：七表资源/同步拓扑与 canonical JSON 合同继续有效；当前由 D-015 的七个 GORM model 表达，不再维护显式 `.up.sql`、runner 或 manifest。
- **背景**：分域 Workflow 曾提出把 exception patch 拆成多列和 reminder child table，但这会推翻后端专题已冻结的 `recurrence_json`、`reminders_json` 与 `patch_json`，并把客户端 Room3 为本地查询采用的规范化物理结构误当成服务端镜像要求。独立 reviewer 还发现候选遗漏全天 identity、nullable color、active 父子关系、identity 复用及 change snapshot 完整性。
- **约束与证据**：服务端必须无损承载客户端 canonical wire，而不要求与 Room3 同表形；owner 只来自认证 context；资源 revision、owner sequence、幂等 receipt、immutable change、tombstone、固定高水位分页和 split 必须在同一事务合同下收敛。D-012 允许当前只做离线 contracts，但不允许把真实 MySQL 行为误报为已验证。
- **候选方案**：
  1. 后端复制 Room3 selector/reminder/patch 规范化表；
  2. 保留后端 canonical JSON 核心事实，并用 7 张资源/同步表承载服务端特有的 CAS、change 与幂等状态；
  3. 延后全部业务 schema，等待真实 MySQL 环境。
- **选择**：采用方案 2。七个 GORM model 对应 `schedule_v2_categories`、`schedule_v2_schedules`、`schedule_v2_occurrence_exceptions`、`schedule_v2_sync_state`、`schedule_v2_changes`、`schedule_v2_tombstones`、`schedule_v2_idempotency`。Schedule 保留完整 canonical `recurrence_json/reminders_json`，exception 保留 `patch_json`；SQL `NULL` 与 JSON `{}`、`CLEAR` 与 `REPLACE(emptyList())` 必须可区分。全天 recurrence identity 始终使用午夜 `MinuteTimeDate`。
- **理由**：canonical JSON 与现有 wire/hash/重放事实一致，避免服务端与客户端物理 schema 不必要耦合；7 表足以独立表达当前资源、账号级 sequence、不可变事件、删除身份和 mutation receipt。所有同 owner mutation 先原子建立并锁定 sync row；父结构变化覆盖全部 `deleted_at IS NULL` child，无论 occurrence status；split change 顺序固定为旧 child DELETE、旧父 UPDATE、新父 CREATE、新 child UPSERT，保证任意分页前缀不产生 orphan。
- **建表边界**：当前只允许 GORM AutoMigrate 创建尚未上线的全新 `schedule_v2_*` 表，不修复既存不兼容结构，也不提供显式版本回滚。部署准备阶段仍必须在隔离 MySQL 真实执行，并以 `information_schema` 核验 model 生成的列、索引、外键、engine 与 collation。
- **影响范围**：后端 GORM model、后续 Go DTO/validator/repository/service、bootstrap/change feed/split；客户端 Room3 不变。资源 identity 当前永久禁止复用，current row、tombstone 和 accepted receipt 暂不物理清理。
- **验证方式**：领域与 MySQL/同步设计 reviewer 已收敛七表事务合同；D-015 的实现审查进一步修复 resource key 索引预算、change 索引 `seq`、只读 association 与启动 fail-fast。当前仅通过 schema parser/MySQL DryRun/Go 单测，未连接数据库、未执行真实 AutoMigrate，也未准备真实 MySQL suite。
- **回滚点**：真实数据库尚未建表；部署前可回退七表 GORM model 与 AutoMigrate 接入，不修改 Todo v1 表或数据。
- **关联事实源**：[后端 Schedule v2 设计](schedule-v2-backend.md)、[总路线图](schedule-v2-calendar-roadmap.md)、D-005、D-011、D-012、D-013、D-015。

### D-015：后端七表复用现有 GORM AutoMigrate，不保留平行显式 runner

- **状态**：已采用、实现、返修、验证并独立提交；真实 MySQL 建表尚未执行。
- **背景**：Schedule v2 尚未上线，七张表均是全新空表，不迁移旧 Todo/Schedule 数据；后端现有启动路径已经使用 GORM `AutoMigrate(ToDoORM)`。在此条件下，显式 runner 的 version/checksum/cursor/advisory-lock 能力并非首次建表的必要条件，继续维护 ORM 与手写 SQL 会形成两套 schema 事实源。
- **约束与证据**：GORM 不会自动发现 struct，七个 v2 model 必须全部显式注册；AutoMigrate 能创建缺失表、列、索引和外键，但不提供显式版本回滚、不会拒绝全部既存不兼容 schema，也不能替代 timing/RRULE/patch/active parent 等跨字段 service validator。Todo v1 行为必须保留；初始化失败不得让服务在部分 schema 下继续注册 RPC/路由。
- **候选方案**：1. 继续维护显式 runner + `.up.sql`；2. runner 与 AutoMigrate 双轨；3. 回退 runner，使用现有 AutoMigrate 作为唯一物理模型入口。
- **选择**：采用方案 3。先以提交 `f3911b7` 回退 runner/CLI/SQL，再以 `e931ac1` 新增七个 GORM model，并在 Todo v1 之后显式 AutoMigrate；不 amend/reset 已有历史。
- **理由**：当前只需创建尚未上线的新空表，项目惯例和最小复杂度优先。单一 ORM 模型能避免 SQL/tag 漂移，并让后续 repository/service 直接复用同一字段定义。若未来进入正式发布、需要可审计增量迁移或不可逆 ALTER，再基于真实部署需求另开 migration RFC，而不是预先保留未使用框架。
- **关键实现**：固定七个 `schedule_v2_*` 表名、owner-first 复合 PK/FK、账号内 sequence 唯一索引、JSON nullability 与只读 FK association；canonical resource key 使用 `VARBINARY(768)` 避免 InnoDB 3072-byte 复合索引上限，change 索引显式包含 `seq`；`MySQLInit` 传播连接/设置/AutoMigrate 错误，`cmd/main` 在启动其他组件前 fail-fast。
- **验证方式**：GORM schema parser contracts 与 MySQL dialector DryRun DDL contracts 覆盖七表注册、table options、主键、索引、复合外键 `NO ACTION`、JSON SQL NULL 承载和 association 写权限；`go test ./dao ./cmd`、`git diff --check` 与两轮独立只读审查通过。DryRun 不联网；未连接 MySQL、未启动服务、未执行真实建表。
- **部署边界**：D-012 中“真实 MySQL 验收延后”继续有效。部署准备阶段必须在隔离数据库运行 AutoMigrate，并用 `information_schema` 核验列、PK/FK、索引、engine/collation，提供清理/回退流程；开发期发现不兼容测试表时清理测试数据库，不添加旧格式兼容 ALTER。
- **回滚点**：当前真实数据库未执行建表；可在部署前回退 `e931ac1`。Todo v1 模型和数据未被修改，显式 runner 历史仍可从 Git 审计但不再是当前代码路径。
- **关联事实源**：[后端 Schedule v2 设计](schedule-v2-backend.md)、[总路线图](schedule-v2-calendar-roadmap.md)、D-011、D-012、D-014。

### D-016：Room3 先提交 strict mapper，再独立实施 transaction Store

- **状态**：strict mapper 微切片已以 `610a56d06` 独立提交；后续 noWeb internal transaction Store、durable generation Flow 与 bounded candidate query 已实现并通过验证，生产 Provider/Repository 尚未接入。
- **背景**：Room3 schema/DAO 已完成，但同时实现 mapper、relation graph DAO、transaction Store、generation Flow、候选查询和 Provider 切换会形成过大的审查面，并可能留下尚未具备完整事务 API 的半套生产接入。
- **约束与证据**：Room 物理列包含 timing tagged union、RRULE scalar+selector child、exception patch presence/三态、全天空时区 identity 与派生范围 key；数据库 graph 属于不可信输入，不能只依赖 Entity/DAO 写前校验。Settings 仍是生产路径，strict mapper 完成不等于 Store 或 Provider 已切换。
- **候选方案**：1. mapper 与完整 Store 一次性交付；2. 先交付 strict value codec/Entity graph mapper 和纯逻辑 contracts，再单独实现事务 Store；3. 直接在 Store 中内联 nullable 字段转换。
- **选择**：采用方案 2。新增 noWeb `ScheduleRoomValueCodec`、Entity graph aggregate/mappers 与 Desktop mapper contracts；本切片不新增 relation DAO、transaction API、Flow、候选 SQL、SyncState mapper 或 Provider/Repository 改动。
- **理由**：mapper 本身已包含可独立验证的高风险边界，先收敛可把“损坏 SQL graph 必须拒绝”与后续“多表原子提交/commit 后发布”分开审查；方案 3 会重复逻辑并重新引入 nullable 猜测。
- **关键合同**：四类 timing 使用 canonical Date/MinuteTimeDate 并重算/核对 minute 与 epoch key；Timed 采用 checked arithmetic 防止 packed-year 回绕；所有 Long→Int exact-range；RRULE selectors 稳定排序并拒绝重复/错账号 child；无损区分 patch null、显式全 INHERIT、CLEAR 与 REPLACE(emptyList)；全天 recurrence domain null 与 SQL 空时区键双向映射；outbox 保留入队 payload，并拒绝 state/attempt/dispatch metadata 矛盾。
- **验证方式**：定向与完整 Schedule `desktopTest`、Desktop/Common metadata/JS/Wasm 编译、Android Studio diagnostics 与 diff check 通过。首轮两个独立 reviewer 发现 outbox metadata、Timed duration 回绕、`hasPatch=false` 隐藏残留和 exact-range/category 测试缺口；返修后最终只读 Workflow 无剩余 finding。未运行 Android/iOS 设备测试。
- **影响范围**：`610a56d06` 新增三个 noWeb/Desktop mapper 文件且不修改 Entity/DAO/Database；后续独立切片在 noWeb 新增 relation graph DAO、transaction Store、durable generation Flow 与 bounded candidate query，仍未修改 Settings、Provider/Repository 或 Web graph。下一切片从 repository transaction 适配开始，网络仍不得进入 SQLite transaction。
- **回滚点**：尚未接入生产 Store、未迁移或双写 Settings，可整体回退 `610a56d06`，现有 schema 和 Settings 生产路径不受影响。
- **关联事实源**：[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[总路线图](schedule-v2-calendar-roadmap.md)、D-008、D-013。

### D-017：Room3 Store 使用 durable generation CAS 与冻结 device namespace

- **状态**：已采用并实施；Desktop contracts、跨端编译、依赖隔离与独立只读审查门禁均已通过。
- **背景**：schema 与 strict mapper 已完成，但生产切换前必须证明 relation graph、outbox、tombstone、sync metadata 和发布代次位于同一真实 SQLite transaction，且失败不能发布或改写已派发身份。
- **约束与证据**：Room3 Store 只存在于 noWeb；Settings 仍是生产路径。网络不得进入 transaction。`generation` 是账号级已提交写事务代次，不是资源 revision/cursor；`deviceId` 首次持久化后冻结，新入队 mutation 必须属于该 durable namespace，历史 outbox payload/identity 不从当前资源重建。
- **选择**：新增 noWeb internal `ScheduleRoomStore`/suspend `ScheduleTx`，使用 `withReadTransaction`/`withWriteTransaction`；首次 writer transaction 在 block 前原子初始化 sync-state，block 后以 `accountId + expectedGeneration` SQL CAS 推进一次 generation。DAO 不暴露整行 sync-state update，只允许 metadata 列更新和单调 CAS。relation graph 按 exception 子树、selector/reminder、parent、new child 顺序替换；outbox 同 resource RMW 位于同一 transaction。仅 `QUEUED && !hasEverBeenDispatched` 的旧项允许按 `ScheduleOutboxMerger` 保留既有 mutationId 并合并 payload；一旦可能派发，mutationId/payload 不可变；调用方直接传入已存在 mutationId 时仅允许完整相同记录幂等 no-op。
- **理由**：真实 SQLite transaction 能在异常、取消、FK、mapper 和 generation 溢出时整体回滚；受限 SQL 防止模块调用者降低 generation 或覆盖 durable deviceId；新 mutation 的 deviceId 一致性保护服务端幂等 namespace，同时不重建历史 outbox identity。
- **影响范围**：noWeb Room3 DAO/Entity/Store、schema v1 baseline、Desktop Store contracts 与存储/路线图文档。该切片未切 Provider/Repository；后续 D-018 已补齐 bounded candidate query。claim/dispatch、CalendarLink/baseline/conflict 与 Web remote-required 仍未实现。
- **验证方式**：Desktop contracts 覆盖 commit/rollback/cancel、generation Flow、重开恢复、账号隔离、relation graph 替换、sync metadata、deviceId mismatch、outbox 合并与 mutationId 冲突；完整 `desktopTest`、Desktop/Common/JS/Wasm 编译、IDE diagnostics 与独立只读审查作为提交门禁。
- **回滚点**：Schedule v2 尚未上线且 Settings 仍为生产路径；可通过本切片独立 commit 的后续 revert 回退，不迁移、不双写、不改写历史。
- **关联事实源**：[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[总路线图](schedule-v2-calendar-roadmap.md)、D-008、D-013、D-016。

### D-018：Room3 bounded candidate query 采用固定项精确预筛与 recurring 全量宽化

- **状态**：已采用并实施；Desktop contracts、跨端编译与独立只读审查门禁均已通过。
- **背景**：internal Store 已能读取完整账号 graph，但 UI/Repository 后续不能依赖全账号扫描；SQL 预筛又不能替代 common engine 的 RRULE、exception、effective timing、DST 与严格半开相交语义。
- **约束与证据**：候选可以过宽但不能漏项。查询窗口是本地 `MinuteTimeDate` 的 `[startInclusive, endExclusive)`；Timed 使用实际 duration，Deadline 占用一分钟，AllDay 使用 epoch-day 半开范围，非重复 Unscheduled 仅在显式 include 时纳入。moved-in/moved-out 和所有 recurrence identity 仍由完整 graph + common engine 裁决。
- **选择**：DAO 使用一个 account-scoped、`schedule_id ASC` 的单表查询。非重复 TIMED/DEADLINE/ALL_DAY 分别按 canonical minute/epoch-day key 预筛，UNSCHEDULED 受 include 开关；`recurrence_frequency IS NOT NULL` 的 parent 无条件纳入，不在 SQL 中解释 UNTIL/COUNT/selectors，也不 join/filter exception。Store 在同一 read transaction 中执行 orphan/category 校验、稳定 parent 校验并 materialize 全部 child/exception 后 strict map。
- **持久化边界**：因为候选 SQL 在 mapper 前消费派生 key，公开 DAO insert/update 必须先通过共享 `ScheduleRoomValueCodec.decodeTiming`，从 canonical timing 重算 parent 与 exception 的 start/end key、duration end 和 `movedStartMinuteKey`；错 key 在进入 SQLite 前 fail-closed。候选路径读取到的全部 category 也立即 strict map。
- **理由**：固定项索引能避免无必要的全表 graph materialize；recurring 全量宽化则消除 RRULE 稀疏性、长 duration、COUNT/UNTIL、AllDay replacement 与 moved exception 带来的 false-negative 风险。单 SELECT 不引入 UNION 去重、动态 `IN`、bind 上限或分页一致性问题。
- **影响范围**：noWeb Room3 DAO/Store/shared timing column projection、Desktop database/Store contracts 与状态文档。schema/version/migration、Settings、Provider/Repository/factory、Web、网络与 dispatch/claim 均未修改。
- **验证方式**：bundled SQLite contracts 覆盖 Timed/Deadline/AllDay 边界、Unscheduled include、recurring 宽化、排序、账号隔离、损坏 category 和 parent/exception 派生 key 写前拒绝；完整 `desktopTest`、Desktop/Common/JS/Wasm 编译、diff check、IDE diagnostics 与独立只读 Workflow 作为提交门禁。
- **回滚点**：尚未生产接入且 Settings 仍为事实源，可独立 revert 本切片；不会触发数据迁移、双写或远端副作用。
- **关联事实源**：[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[总路线图](schedule-v2-calendar-roadmap.md)、D-005、D-013、D-016、D-017。

### D-019：Repository 适配先冻结 common local-command reducer，再接入 Room transaction

- **状态**：已采用；Slice A/B/C/D 已完成，Settings 生产路径已改用 reducer，noWeb Room adapter 与账号绑定 repository/factory 已落地；生产 Provider 尚未切换。
- **背景**：Settings repository 已包含完整本地命令、outbox 合并、tombstone 与日历增量语义；若直接在 Room adapter 复制这些算法，会形成两个会漂移的语义源，同时把 Store transaction、dispatch 和 factory 切换混入同一审查面。
- **约束与证据**：`RequestSync` 继续走既有两次本地事务与中间网络，不进入 reducer；reducer 不依赖 Room/DAO/SQLite，也不以整库 `ScheduleLocalEnvelopeV2` 为合同。设备身份必须惰性读取，只有实际创建 mutation 时按 `mutationId → durableDeviceId → clock` 调用；无效命令与 no-op 不得消费标识或时钟。结构性系列变更必须输出原子的 `ReplaceScheduleGraph`，不能让未来 adapter 以“先更新 parent、后删除 child”的顺序违反 DAO 保护。
- **选择**：在 commonMain 新增 internal typed state/context/reduction 与 atomic operation plan；全部本地数据命令统一经 `ScheduleLocalCommandReducer` 执行。Settings repository 只负责在现有账号锁内投影 envelope 与 reducer state、落盘并发布；日历变更 ID 由事务初始/最终 schedule+exception graph 比较得出。Slice B 已补 writer-scoped strict-read、首次冻结 durable deviceId 与轻量 committed receipt；receipt 仅含 `accountId`、`committedGeneration`、`durableDeviceId`、`value`，不是发布快照。Slice C 的 noWeb internal adapter 在同一 writer transaction 内投影 strict snapshot、注入冻结 deviceId、运行 reducer 并按 FK 安全顺序重放 graph/outbox/tombstone；Store 仅推进一次 generation。
- **理由**：先证明行为等价与纯状态合同，可以在不切换生产 Store、不迁移、不双写的前提下收敛最危险的命令语义；atomic graph operation 让 Room adapter 直接复用已冻结的 relation replacement 边界。惰性 provider 与最终图 diff 保留旧 repository 的副作用顺序和日历发布语义。
- **影响范围**：common reducer、Settings-backed `LocalFirstScheduleRepository`、noWeb internal `ScheduleRoomLocalCommandAdapter`、账号绑定的 `RoomScheduleRepository`/factory 与 Desktop SQLite contracts。删除 parent 会先按稳定 exception resourceId 归约 live child，再归约 parent；仅当同资源最后一条 pending 已是 DELETE 时复用终态意图，历史 DELETE 后出现新 CREATE/PATCH 时仍追加最终 DELETE；墓碑依归约后同资源 pending 是否存在判定。Provider 生产切换、dispatch/claim、cursor/remote apply、Web remote-required、CalendarLink/baseline/conflict/origin 均未修改。
- **验证方式**：reducer contracts 覆盖 CRUD、exception、split/following、分类、validation、outbox/tombstone、operation replay、复杂 split 成功/整体失败、惰性 ID/clock 顺序与 identical graph no-change；Desktop 真 SQLite adapter/repository contracts 覆盖 CRUD/category/exception graph replay、parent child-first DELETE、已派发 mutation identity、RequestSync/no-op/invalid、rollback、immutable account、多 facade advanced generation、初始化 hook、取消恢复、损坏分类、pendingCount 与发布顺序。完整 Desktop/Common/Android/iOS/JS/Wasm 编译测试、IDE diagnostics、diff check 与独立只读审查作为提交门禁。
- **回滚点**：Settings 仍是生产事实源，Slice A reducer、Slice B receipt、Slice C internal adapter 与 Slice D repository/factory 均可按独立提交 revert；未创建或改写 Room 生产数据，未接入网络或生产 Provider。
- **关联事实源**：[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[当前数据流](schedule-v2-current-data-flow.md)、D-005、D-013、D-017、D-018。

### D-020：共享 mutation wire v1 使用严格 canonical envelope 与域分隔 hash

- **状态**：已采用；客户端 strict codec、共享 golden vector 与协议文档已完成，后端 Go strict codec 已以 `ba9f96c` 提交。迟到独立审查确认 lone surrogate 会造成 Kotlin/Go hash 漂移；客户端已以 `dacf4d8bd` 拒绝未配对 surrogate，后端对应修复仍在独立切片收敛，尚未提交。
- **背景**：客户端 outbox 已保存版本化 payload，后端七表也已预留 request hash 与 canonical JSON，但 mutation 外层字段 presence、分钟时间文本、可空性和 hash 字节此前尚未形成 Kotlin/Go 可逐字节复验的共同事实。
- **约束与证据**：客户端领域、Record、Room 与既有专题文档都使用 `yyyy-MM-dd HH:mm`；本地未被服务端接受的资源 revision 为 `0`，因此 PATCH/DELETE 不能收窄为正数；Schedule 领域 `description` 为非 null 字符串，后端物理 nullable 列不能反向扩大 wire 取值域；owner 只能来自认证 context。
- **候选方案**：1. 接受 `T`/空格等价时间并由各端自行编码；2. 以严格、唯一的 canonical envelope 和共享 golden bytes 冻结 v1；3. 直接 hash 客户端入队的原始 JSON 文本。
- **选择**：采用方案 2。`MinuteTimeDate` 唯一形式为 `yyyy-MM-dd HH:mm`，拒绝 `T`、秒、offset、zone 和非补零 alias；`baseRevision` 字段必须显式出现，CREATE 为 JSON `null`，PATCH/DELETE 为非负整数并允许 `0`；Schedule `description` 必需且非 null。canonical JSON 递归按 ASCII key 顺序编码；RRULE selector 集合拒绝重复并按数值升序规范化，其他数组保持业务顺序。Schedule/occurrence 同步时间戳使用 `Instant.toString()` 的唯一 UTC RFC 3339 文本并保持 `updatedAt >= createdAt`；DELETE 即使 payload 为 null 也独立验证 resource identity。request hash 输入固定为 UTF-8 `schedule-v2-mutation-hash-v1 + NUL + canonicalJson`，输出 64 字符小写 SHA-256，owner 不进入 body 或 hash。
- **理由**：唯一字节表示能让 mutation 幂等 receipt 跨 Kotlin/Go 稳定复验；严格 presence 避免省略/null/零值在 Go DTO 中坍缩；域与 NUL 分隔避免不同协议或前缀拼接共享同一摘要空间。原始 JSON 可能包含 key 顺序和转义 alias，不能作为跨端 request identity。
- **影响范围**：客户端 common mutation codec/contracts、后端 `schedulev2wire` codec、共享 golden vector、后端 mutation validator 与幂等 request hash。现有 outbox identity/payload 不从较新资源重建；生产 gateway、Room Provider/factory、后端路由/service/transaction repository 均不在本切片。
- **验证方式**：客户端 golden contract 对 canonical JSON 与 `badf5b0802595c0dcaa7ab27262db9c7c2b617c7d341ec0fd6d5057acb65622b` 逐字节断言，并覆盖 `T` alias、payload `schemaVersion` presence、baseRevision 矩阵、nested duplicate key、resource/payload mismatch、RRULE 集合规范化与领域不变量、DELETE identity、同步时间戳、occurrence Unscheduled timing patch 和 category 负 revision；后端 Go codec 已读取同一 vector，并覆盖 strict token、canonical JSON、hash 与 operation/resource/payload 矩阵。迟到复核确认 Go `encoding/json` 会把 `\uD800` 归一化为 U+FFFD，而 Kotlin/JVM lone surrogate 编码字节不同；客户端现已在原始 JSON scanner 中同时校验 key/value，拒绝 lone high、lone low、high+non-low，接受合法 pair、literal U+FFFD 与转义反斜杠。完整 Desktop tests、Android/iOS/JS/Wasm 编译与独立只读复审通过；后端 surrogate 修复提交前仍需 Go 单测、GoLand diagnostics 与独立复审。
- **回滚点**：Schedule v2 尚未上线，当前未派发或接受任何该版本 mutation；可在后端 service 接入前整体回退 codec、contracts、vector 与文档，不涉及数据库 migration 或用户数据转换。
- **关联事实源**：[后端 Schedule v2 设计](schedule-v2-backend.md)、[当前数据流](schedule-v2-current-data-flow.md)、D-005、D-014、D-019。

### D-021：Room repository facade 使用不可变账号绑定，不在实例内追踪动态账号

- **状态**：已采用并以 `8df4705b0` 实现；生产 Provider 原子发布尚未接入。
- **背景**：原候选方案尝试在同一 repository 内读取动态 account provider，并以 epoch/重复检查阻止旧账号结果发布；独立审查确认账号切换方不参与 repository mutex，最终检查与 `MutableStateFlow.value` 赋值之间仍存在 check-to-assignment TOCTOU。
- **约束与证据**：账号隔离不能依赖一次外部状态检查；同一 facade 的 Store 读写、公开 snapshot 与 calendar event 必须永久属于同一账号。当前 production Provider 仍使用 Settings，不需要在本切片提前建立账号订阅或 Session 框架。
- **候选方案**：1. 在同一实例增加 account epoch 与多次 `isCurrentSession()` 检查；2. facade 构造时冻结 `accountId`，账号切换时由 Provider 创建并原子替换新 facade；3. 当前切片直接重写 production Provider 和全平台 Session 生命周期。
- **选择**：采用方案 2。common `ScheduleRepositoryFactory.create(accountId)` 只创建不可变账号绑定实例且不执行 I/O；noWeb Room factory 复用同一 Store，每个 repository 只读写构造时的账号。方案 3 延后到生产切换门禁。
- **理由**：对象边界直接消除实例内动态分区 TOCTOU，方案最小且可独立验证；epoch 在没有与账号切换方共享线性化原语时不能证明安全，提前实现完整 Session/订阅则扩大当前切片。
- **影响范围**：common factory contract、noWeb Room repository/factory、初始化/命令序列化、提交后 strict re-read、snapshot/calendar event 与 Desktop contracts。Web、Settings production Provider、dispatch/claim 和远端同步未修改。
- **验证方式**：Desktop bundled-SQLite contracts 覆盖不同账号 facade 的 durable graph 隔离、同账号多 facade advanced generation、初始化 hook、execute-before-initialize、取消后重读、initial strict-read corruption 与普通领域异常分类、`RequestSync` pendingCount/无 writer transaction，以及 snapshot 先于 calendar event。完整跨平台编译、IDE diagnostics、diff check 与最终独立只读复审通过。
- **回滚点**：Settings 仍是生产事实源，未迁移、未双写；可独立 revert `8df4705b0`。生产接入时必须在一个应用级原子边界替换 facade，不能改回实例内动态账号键。
- **关联事实源**：[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[当前数据流](schedule-v2-current-data-flow.md)、[总路线图](schedule-v2-calendar-roadmap.md)、D-019。

### D-022：生产 Provider 接入 AccountSession 绑定 facade

- **状态**：已采用并以 `d0a321a717f8cd1c9847772e39e2f2889b4d2d91` 独立提交；仅同步客户端 Schedule Provider，不包含 Token 或日历 C 类 hardening。
- **背景**：Room3 账号绑定 facade、AccountSession/generation 与 scope 门禁已完成，但旧文档仍将生产 Provider 描述为直接使用 Settings，无法准确表达 Android 与非 Android 平台的当前分流。
- **选择**：生产 Provider 暴露稳定的 `AccountSwitchingScheduleRepository`，按权威 `AccountSession` 创建不可变 delegate；Android production factory 使用进程级 Room3，iOS、Desktop、Web 暂时使用 Settings-backed factory。相同学号的新 generation 重建 delegate，旧 snapshot/calendar event 通过 binding identity 隔离；初始化 pending/failure fail-closed，Room strict-read 初始化失败继续抛出，代理转发 calendar event 前先发布最新 snapshot。
- **后续演进（当前事实）**：上述 Desktop Settings-backed 分流仅记录 `d0a321a7` 提交时的历史快照；后续 Desktop production 已改由进程生命周期 owner 持有 Room3。当前分流为 Android/Desktop production 使用 Room3，iOS/Web production 仍使用 Settings-backed fallback。Web 的 common remote-required 纯合同已完成，但 production actual 与真实 transport 尚未接入。
- **理由**：把账号生命周期与持久化 delegate 的 identity 放进同一代理状态机，避免 collector 尚未调度、同学号重新登录或旧异步结果穿透新账号；保留当时尚未迁移平台的 Settings fallback，避免将 Web production remote-required wiring 或其他平台 SQL 误报为已完成。
- **影响范围**：AccountSession、`generation`、`accountCoroutineScopeFor`、Schedule repository factory/provider、Room facade 与当前数据流文档；Settings 未删除、未迁移、未双写。
- **验证方式**：AccountService scope、AccountSwitchingScheduleRepository、Room facade 初始化失败/重试/日历事件顺序测试已纳入该切片的既有验证；本次仅同步文档，不运行测试。
- **边界与未完成项**：Web remote-required production actual/真实 transport、dispatch/claim、远端入站与 CalendarLink/baseline/conflict 均不属于该提交；其中 Web common remote-required 纯合同已由后续切片完成，production wiring 仍未完成。Android enable/disable/clearAndDelete generation hardening 同样不属于 `d0a321a7`，但已由后续 D-023 / `914c5ba8` 独立完成。全局 Token/account lifecycle 也不属于 `d0a321a7`，但已由后续 D-024 / `c7771c5a` / `97c24e69` 独立完成；typed authenticated response 已闭环，HTTP error body、raw/stream/download 与 `RedrockApiWrapper` 仍是显式边界。安全 stash `safety/mixed-before-schedule-provider-A-20260719` 仅作为历史线索参考，不视为最终实现。
- **回滚点**：回退 `d0a321a7` 与承载本决策的文档提交即可恢复对应代码与事实描述；不涉及迁移、生产数据库或系统日历外部操作。

### D-023：Android 单向日历导出的 AccountSession/generation/owner 隔离

- **状态**：已采用并以 `914c5ba81ce682da693eb922fe8adb732fd67169` 独立提交。
- **背景**：Android 系统日历以学号派生稳定 scope；只比较学号会把“登出后同学号重新登录”误判为同一生命周期。旧 enable、disable、clear、权限回调或删除确认若迟到，可能停止新 coordinator、覆盖新开关意图或删除新 Provider 投影；同一 session 内旧关闭命令在 mutex 等待期间也可能落后于新 enable。
- **选择**：初始化 hook、设置页异步上下文与 Controller 命令均冻结完整 `AccountSession`。Android 只通过 `accountCoroutineScopeFor(session)` 原子取得 scope，并在 Provider 操作前复核 session identity。Controller 状态同时比较 command generation 与 owner Job/session；Coordinator 的 `stopAndRemove` 仅接受 expected owner Job。实际 Provider 删除不放入 `NonCancellable`，并在 IO 前再次复核同一 session。
- **理由**：`AccountSession.generation` 区分同学号新登录，owner Job 区分 coordinator 生命周期，而 command generation 区分同一 session 内的先后意图；三者分别覆盖跨登录、跨 scope 与同 scope 排队竞争，且无需改动 planner、worker、retry、schema 或权限模型。
- **影响范围**：Android 日历 Controller/Coordinator/设置页、平台初始化 hook、LocalFirst/Room hook seam、desktop Room contract 测试及当前数据流文档；本提交不涉及 Token/account lifecycle、`ApiWrapper`、Web、后端、双向日历或 iOS EventKit，其中 Token/account lifecycle 已由后续 D-024 独立完成。
- **验证方式**：新增相同 accountId 不同 generation 时 Room 初始化 hook 保持冻结 session identity 的纯 Kotlin 测试；`git diff --check`、`:cyxbs-components:account:desktopTest`、`:cyxbs-pages:schedule:desktopTest` 与 `:cyxbs-pages:schedule:compileAndroidMain` 均通过。
- **回滚点**：独立回退 `914c5ba8` 即可撤回 Android 命令门禁；不迁移数据库、不修改系统日历既有事件，也不触碰 Token 生命周期。
- **关联事实源**：[Android 单向日历导出架构](schedule-v2-calendar-export.md)、[当前数据流](schedule-v2-current-data-flow.md)、[总路线图](schedule-v2-calendar-roadmap.md)、D-022。

### D-024：全局 Token/account lifecycle 与 typed 认证响应使用 exact request lease

- **状态**：已采用；refresh/UserInfo 生命周期隔离以 `c7771c5ac2c7fe49fb96a32b1bb70c09dfc79462`（`:bug: fix(account): 隔离 token 刷新账号生命周期`）独立提交，普通请求认证副作用隔离以 `97c24e6973916c3632995289088c940cd65e02e2`（`:bug: fix(account): 隔离普通请求认证副作用`）独立提交。
- **背景**：网络请求发起后可能经历切号、同学号重新登录或同 session token refresh。若迟到响应在处理时读取全局当前账号，旧请求的 `20002`/`20003` 会使新 token 过期，`20004` 会登出新 session；已反序列化 `ApiWrapper` 的延迟 `data` getter 还会把访问时状态误当成请求归属。
- **约束与证据**：账号归属必须同时保留 exact `AccountSession` identity 与实际附加的源 `TokenBean` identity。学号不能区分同学号新 generation，token 字符串不能证明对象生命周期，响应时读取当前账号或依赖 ThreadLocal 都不能跨 suspend/redirect/converter 正确传播请求归属。
- **候选方案**：1. 保留全局副作用并在响应时读取当前账号；2. 仅传播 accountId 或 token 字符串；3. 在发起时冻结 opaque request lease，由账号模块私有实现保存 exact session/source-token identity，并在发布副作用时通过同一 publication guard 条件提交。
- **选择**：采用方案 3。refresh deferred 按发起时 `AccountSession`/源 `TokenBean` identity 复用和条件发布，新 token 与 refresh `20004` 副作用都只能提交给 exact lifecycle；UserInfo 请求以 owner/session 门禁隔离旧结果。普通 Ktor/Android Retrofit 请求传播 `TokenLifecycleLease`，网络层只读取 `token`，账号模块只接受其私有 lease 实现。Ktor 在 typed body 形成后的 response pipeline、Retrofit 在既有 converter 单次反序列化后处理 `IApiStatus`；Ktor Send 重入或复制 builder 时先移除旧 `Authorization`，再写入与最终 lease 一致的唯一 Bearer；`ApiWrapper.throwApiExceptionIfFail()` 与延迟 `data` getter 纯化为只抛 `ApiException`。
- **理由**：request lease 将“响应属于谁”固定在发起时，publication guard 将校验和 token 失效/登出放入同一原子边界；同时保持现有 String token API 兼容，不要求全量 API 重写，也不缓存、重放或二次反序列化 response body。
- **影响范围**：Account session/token/UserInfo 发布，Ktor bearer 注入与 typed response hook，Android Retrofit/OkHttp response body 委托与 converter 前置包装，以及 `ApiWrapper` 错误语义。当前 lifecycle 的 typed authenticated response 仍自动处理 `20002`/`20003`/`20004`；stale lease、未知/伪造 lease、无 token 登录接口与非 `IApiStatus` 响应均 fail-closed。
- **验证方式**：`AccountServiceScopeTest` 覆盖切号、同学号新 generation、同 session 新 `TokenBean`、当前/stale `20002`/`20003`/`20004`、节流和 wrapper 延迟访问；`TokenPluginTest` 覆盖 lease 传播、fail-closed 及 Send 重入时唯一且最新的 Authorization。Account/utils Desktop 测试、Android main 编译、IDE diagnostics 与 `git diff --check` 均通过；当前未引入 Ktor MockEngine 或 Retrofit MockWebServer 集成测试。
- **边界与未完成项**：闭环只覆盖成功进入 typed conversion 的 authenticated response。HTTP 4xx/5xx error body、raw response、stream/download 与 `RedrockApiWrapper` 尚未传播和消费 request lease；后续若纳入，必须单独设计、测试、审查并提交，不能把 typed authenticated response 重新列为未完成。
- **回滚点**：按相反顺序独立回退 `97c24e69` 与 `c7771c5a` 可撤回对应闭环；不涉及 Schedule schema、生产数据库或系统日历外部操作。不得只回退 identity/publication guard 而保留无条件全局副作用。
- **关联事实源**：[当前数据流](schedule-v2-current-data-flow.md)、[总路线图](schedule-v2-calendar-roadmap.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、D-022、D-023。

### D-025：common baseline 合同与最小后端 mutation 的交接边界

- **状态**：已采用；客户端 common typed contract 以 `1fa049cfcdadbbdac278ac348bb2b6a85d139caf` 提交，后端最小 mutation API 以 `9345ec47237a2d39dc198d562fe0c941541c73a1` 提交。
- **背景**：双向日历后续需要可恢复的 CalendarLink/共同基线，远端同步则需要幂等 mutation 与 CAS；本批次只具备可独立审查的共同合同和最小单资源写入，不具备 durable 三方同步闭环。
- **约束与证据**：客户端已定义 `CalendarLinkRecord`、Schedule/Calendar 独立字段快照、mapper 和严格 codec/test，但未修改 Room schema/version/migration，也未实现持久化、planner、Provider inbound、SyncAdapter、冲突状态机或 UI。后端 mutation endpoint 位于 TokenVerify 后，当前由静态路径 `/v2/schedule-mutations` 直接注册；Hertz 冒号参数的 exact path guard 仅继续保护 bootstrap 路由。raw body 必须先经 `DecodeStrict`/`RequestHash`，owner/device/mutation receipt、Schedule/category revision CAS 和 active category 校验在同一事务完成。
- **选择**：维持该批次为两条独立的基础能力。后端接受 Schedule 与 category 的单资源 mutation；occurrence mutation 统一稳定拒绝 `occurrence_exception_unsupported`，不推测、截断或近似写入 exception。receipt 写入后从 MySQL 回读 JSON 原始字节，首次返回与幂等 replay 均使用该稳定字节。
- **理由**：在 occurrence transaction、bootstrap/change feed 和客户端 gateway 未完成时 fail-closed 可避免半实现的 exception 破坏资源语义；MySQL JSON 回读避免存储归一化或二次编码造成幂等响应字节漂移。
- **影响范围**：后续必须实现 CalendarLink/baseline durable persistence、三方 planner/conflict、bootstrap/delta/change feed、客户端 production gateway 和 Room-native inbound apply；当前 create/update 仍只本地提交并生成 outbox，Android `RequestSync` 继续返回 `BackendNotDeployed` 且 `attempted=false`。
- **验证方式**：客户端 common contract 的提交范围已包含严格 codec/test；D-025 当时的执行环境未发现 `go`/`gofmt`，所以该批 targeted Go tests 未实际运行。后续 bootstrap v1 切片已通过绝对路径 SDK 完成独立 wire 与聚焦 service 测试，但不追溯替代 D-025 的 mutation 验证，也不构成真实 MySQL、完整 service package 或服务端端到端测试。
- **回滚点**：两个提交分别可独立回退；未修改已发布数据、Room migration、真实数据库或系统日历。后端既有未提交 `schedulev2wire/decode.go` surrogate 严格校验不属于 `9345ec4`，继续作为独立 changelist。
- **关联事实源**：[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[后端 Schedule v2 设计](schedule-v2-backend.md)、[当前数据流](schedule-v2-current-data-flow.md)、[总路线图](schedule-v2-calendar-roadmap.md)。

### D-026：CalendarLink 初始持久化与纯三方 planner 分离提交

- **状态**：已采用并分别以 `a318dfa4b`、`faebec222` 独立提交。
- **背景**：双向日历已具备 typed canonical baseline contract，但没有 durable 基线无法跨重启恢复，也不能只以 fingerprint 执行字段级合并；将持久化、planner、Provider inbound、命令执行和全平台接线混在同一切片会扩大数据一致性审查面。
- **约束与证据**：Schedule v2 尚未发布，因此 CalendarLink/baseline 可以直接更新 Room 初始 schema v1，不建立 1→2 migration。基线必须复用严格 `CalendarCanonicalBaselineCodec`；planner 必须以 `base / Schedule / Calendar` 逐字段比较，不能用设备时钟 LWW 或 fingerprint 代替字段真值表。
- **选择**：`a318dfa4b` 在 Room v1 新增 `calendar_link` 单表，以 `account_id`、`platform`、`projection_uri` 复合主键持久化整个 `CalendarLinkRecord` canonical payload；typed DAO 使用 `INSERT ON CONFLICT DO UPDATE`，并提供精确 find/delete 与按账号、平台、canonical URI 反查。`faebec222` 单独实现纯 common `CalendarThreeWayPlanner`，比较 title、description、timing、RRULE、reminders，返回 typed merged Schedule/Calendar snapshots、classification 与传播或冲突字段。
- **理由**：单表 canonical payload 是当前共同基线的最小原子聚合，可避免字段级半写入；它不禁止将来按查询需求增加投影索引字段。将 planner 保持为纯函数，可先验证 NoOp、单边传播、非重叠双边自动合并、双边同值收敛和同字段异值冲突，再由后续协调器承接副作用。
- **影响范围**：Room 初始 schema v1、`calendar_link` DAO 与 schema JSON；common 三方分类/合并能力及其合同测试。当前 `ScheduleRoomStore`、repository、Provider、同步 transaction、conflictId、`CalendarLinkState`、基线原子推进、Android Provider inbound/SyncAdapter、iOS 双向、Web remote-required、后端 bootstrap/delta/change feed 均未改变。
- **验证方式**：`a318dfa4b` 的 `CalendarLinkRoomDaoDesktopTest` 共 5 项，覆盖首次打开与重开、同键覆盖、账号/平台隔离、精确删除和身份拆裂拒绝；`faebec222` 的 `CalendarThreeWayPlannerTest` 共 9 项，覆盖逐字段单边传播、非重叠双边合并、不同双基线同值收敛、同字段异值冲突与快照不可变性。Room changelist 的 targeted Desktop DAO 测试、planner changelist 的 targeted common 测试、IDE diagnostics、diff check 与独立只读审查均通过；它们分别独立提交且没有运行时调用链接入，因此不能将这些证据表述为 Provider、SyncAdapter 或真实双向闭环验收。
- **回滚点**：两个提交可分别 revert。Schedule v2 尚未发布，撤回 `a318dfa4b` 不需要 migration 数据回滚；撤回 `faebec222` 只移除无副作用的纯 planner。两者均未触发系统日历、网络或后端外部操作。
- **关联事实源**：[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[总路线图](schedule-v2-calendar-roadmap.md)、[当前数据流](schedule-v2-current-data-flow.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-027：冲突证据与 Android 严格只读快照分离提交

- **状态**：已采用；common conflict evidence/open transition 以 `3586e1b1a` 独立提交，Android strict read-only managed-calendar snapshot adapter 以 `de31f7e20` 独立提交。
- **背景**：现有 `CalendarLink` durable baseline 与纯三方 planner 可以识别冲突，但没有可恢复的冲突证据，且 Android 旧的受管事件读取接口不能作为后续 inbound 边界的完整 typed canonical 输入。一次性补 persistence、协调器、命令、Provider inbound 与后端 bootstrap 会扩大跨事务和跨进程审查面。
- **选择**：先交付两条无运行时接线的基础能力。common `CalendarConflictRecord` 与 `CalendarConflictTransitions.open` 要求调用方提供 identity、双基线、当前双侧 canonical snapshot、冲突字段、observed revision/fingerprint 和发现时间，并对 LINKED/no-conflictId/base 一致/planner Conflict/合法 identity fail-closed；Android adapter 以 `CalendarAbsent`/`Present` 表达严格只读查询，缺失日历零写入，按同一 canonical fields 验证并计算 fingerprint，保留合法重复 projection row，旧 `queryManagedEvents()` 只映射新快照。
- **理由**：纯 conflict transition 先冻结创建冲突所需的完整证据和 link 最小状态变化，防止未来协调器按不完整输入写入；只读 snapshot 先消除“查询即创建”与宽松回读风险。两者均不把 adapter 误称为 inbound sync，也不以纯 planner/transition 伪装为双向闭环。
- **本批拒绝范围**：不新增 conflict codec、Room conflict persistence、link+conflict 原子 transaction、resolution、UI、outbound origin、boundary coordinator、`ScheduleCommand` 执行、反向删除、SyncAdapter/ContentObserver 或后端 bootstrap/delta/change feed。原因是这些能力依赖可恢复冲突记录和原子事务边界，不能在本批以进程内状态或单次 Provider 查询替代。
- **影响范围**：新增 common pure conflict evidence/open transition 与 Android read-only canonical snapshot；既有 create/update/`requestSync`、单向导出写入行为、`CalendarLink` persistence 和后端最小 mutation API 均不变。
- **验证方式**：common conflict targeted test、common metadata compile、IDE diagnostics 与独立审查通过；Android main/device-test 源码编译、IDE diagnostics、diff check 与独立审查通过。未运行 `connectedAndroidDeviceTest` 或其他连接设备测试，未执行真实 Calendar Provider 行为验收。
- **回滚点**：分别 revert `3586e1b1a` 或 `de31f7e20` 即可撤回对应基础能力；两条提交均未写入真实系统日历、未连接后端、未引入持久化冲突数据。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[Android 单向日历导出架构](schedule-v2-calendar-export.md)、[当前数据流](schedule-v2-current-data-flow.md)。

### D-028：冲突 canonical codec 独立冻结

- **状态**：已采用并以 `f2f9eecb9` 独立提交。
- **背景**：D-027 已冻结纯 conflict evidence/open transition，并在当时明确拒绝 conflict codec；该拒绝范围是当批历史决策，不因后续切片而改写。后续 durable conflict persistence 需要一个跨重启、可精确复验的冲突证据字节合同。
- **选择**：采用 schemaVersion=`1` 的 `CalendarConflictCodec`。编码固定 root、record 及四份 typed snapshot（base Schedule、base Calendar、current Schedule、current Calendar）的 key/field order；decode 对合法输入 exact re-encode，只有字节完全一致才接受。
- **严格边界**：拒绝 duplicate、unknown、missing 与乱序字段，以及非 canonical escape、number、enum 和 UTF-16 文本；在递归 duplicate scanner 前先以非递归 raw nesting preflight 限制 64 层，受控拒绝深层对象/数组和不平衡字符串结构，避免受攻击输入耗尽调用栈。`CalendarConflictRecord` 的直接构造边界和 `CalendarConflictTransitions.open` 也补强了 identity、文本、fingerprint 与 canonical projection 校验；它们分别校验账号与 projection identity，不要求 accountId 等于 projection scope。
- **理由**：固定的唯一表示可让后续 Room 持久化、重启恢复和跨端审查复验同一冲突事实；预检在递归扫描前限制原始输入深度，避免把防 duplicate 的解析路径变成可控栈耗尽入口。
- **影响范围**：common conflict codec、既有 baseline 严格 JSON 边界、`CalendarConflictRecord`/`open` 构造校验及其 contracts；未接 Room、repository、Provider、`requestSync`、`ScheduleCommand`、runtime、resolution 或 UI，也未实现 durable conflict table、恢复路径、link+conflict 原子事务或双向闭环。Android strict snapshot 仍等待 boundary coordinator。
- **验证方式**：两个最终 reviewer 均为 PASS；focused 28 项、完整 Desktop 319 项测试及 common metadata 编译通过，六个文件 IDE diagnostics 无 error。未运行 connected/device tests；一名 reviewer 额外运行 iOS Simulator 相关 focused suite，但该结果不构成平台运行时验收。
- **回滚点**：Schedule v2 尚未将 conflict codec 接入运行时或持久化表；回退 `f2f9eecb9` 可撤回该独立 common 合同，不涉及 Room migration、真实系统日历、后端或用户数据。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)、[当前数据流](schedule-v2-current-data-flow.md)。

### D-029：CalendarConflict 最小独立 Room3 persistence

- **状态**：已采用并以 `96b63b9ed` 独立提交。
- **背景**：D-028 已冻结唯一 canonical conflict evidence，但其后续 coordinator 需要跨重启恢复一整条已验证证据；此前只有纯 common codec，不能将单次内存中的冲突视为 durable state。
- **选择**：在未发布开发期的 Room 初始 schema v1 新增独立 `calendar_conflict` 表。表仅有 `account_id`、`conflict_id`、`platform`、`projection_uri`、`canonical_payload` 五个 `TEXT NOT NULL` 列；以 `(account_id, conflict_id)` 为复合主键，并以 `(account_id, platform, projection_uri)` 建立唯一索引。完整 evidence 只保存在 `CalendarConflictCodec` 的 canonical payload 中；SQL 的 account、conflict、platform、projection URI 四个 identity 与 decode 后 payload 必须逐项严格交叉校验。typed DAO 只提供 `upsert` 和精确 `find`，写入使用 `INSERT ON CONFLICT DO UPDATE`，不用 `REPLACE`。
- **理由**：以完整 canonical payload 作为唯一证据源，避免将四份 snapshot、冲突字段和观察元数据拆成可部分更新的第二协议；冗余 SQL identity 仅用于账号作用域的定位、唯一约束和存储损坏检测。非 REPLACE upsert 保留将来关联关系的更新语义，不把更新隐式变成 delete/insert。
- **明确拒绝范围**：本切片**不**为 `calendar_link` 添加 FK，也不实现 `NO ACTION` FK 语义。在 D-029 该切片时尚不存在 link 与 conflict 的跨表原子 transaction，提前加 FK 会强加尚未定义的写入与删除顺序；DAO 也不提供 delete、list 或 recovery scan。resolution/state machine、Store/repository/Provider/requestSync 接线、outbound origin、Provider inbound、命令执行、基线推进、UI、SyncAdapter 和双向闭环仍未实现。
- **影响范围**：Room 初始 schema v1、独立 conflict entity/DAO 和 exact recovery contract；既有 create/update/`requestSync` runtime、`CalendarLink` persistence、单向导出、Store 与 repository 均未改变。`resolution`、`state`、`reason`、`updated_at` 等属于未来 resolution/state machine 扩展，不是当前物理列；未发布开发期初始 schema v1 不新增 migration。
- **验证方式**：focused `CalendarConflictRoomDaoDesktopTest`、完整 Schedule `desktopTest`、common metadata 与 Desktop compile 均通过；五个相关文件的 IDE diagnostics 无 error。未运行 connected/device tests，以上不构成 Android 系统日历行为验收。
- **回滚点**：Schedule v2 尚未发布且该表只在初始 schema v1 中存在；回退 `96b63b9ed` 不涉及已发布数据库 migration、真实系统日历、后端或用户数据。
- **关联事实源**：[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[总路线图](schedule-v2-calendar-roadmap.md)、[当前数据流](schedule-v2-current-data-flow.md)。

### D-030：CalendarLink 与冲突 evidence 的一次性打开事务

- **状态**：已采用并以 `51c6ad39c` 独立提交。
- **背景**：D-029 已将 link 和完整 conflict evidence 分别持久化，但没有定义两表的同提交顺序；协调器若先写 link 或只写 evidence，进程中断、约束失败或伪造 `Opened` 都会留下不可恢复的不一致状态。
- **选择**：新增 `internal suspend fun ScheduleRoomDatabase.openCalendarConflictAtomically(opened: CalendarConflictTransitions.Opened): Unit`，以 Room3 `withWriteTransaction` 实现严格的一次性 open。它不修改 schema version、migration、FK、既有 Entity/DAO 或单表 persistence 合同。
- **严格边界**：事务外先交叉验证 `Opened` 的 CONFLICT state、account、platform、projection、conflictId、双基线及 `updatedAt == detectedAt`；再以 record 的四份 snapshot 重跑 `CalendarThreeWayPlanner.compare`，要求结果为真实 `Conflict`，且冲突字段集合精确相同。事务内精确重读 durable link，要求仍为 `LINKED` 且 `conflictId == null`，并只允许 state、conflictId、updatedAt 三项变化；同 account/conflictId 的 evidence 必须不存在。写序固定为 link 后 evidence，任一 DAO、codec、唯一约束或 SQLite 失败均回滚整对。
- **理由**：公开 data class 和独立 DAO 都不能替代 durable compare-and-open 边界；先重算 evidence、再冻结 durable link 可拒绝伪冲突、陈旧状态与重复证据，固定写序的真实 SQLite 回滚测试可证明中间 link 不会逃逸。
- **一次性与恢复边界**：这不是幂等 API，重放 open 不视为成功。取消或 commit 竞态下未收到 `Unit` 不能证明未提交；后续 bounded coordinator 必须按完整 identity 精确重读 link/evidence 判定结果。本切片未实现该 coordinator、recovery、idempotency、`ScheduleRoomStore`/repository/Provider/requestSync 接线、Android snapshot runtime、network、resolution/UI、delete/list/recovery scan、Provider inbound、命令执行或 outbound write-back/基线推进。
- **验证方式**：focused 18 tests 通过；完整 Schedule Desktop 332 tests，0 failures/errors/skipped；common metadata 编译成功；IDE diagnostics 无错误。未运行设备或 connected tests，以上不构成 Android 系统日历行为验收。
- **回滚点**：Schedule v2 尚未将该 primitive 接入运行时；回退 `51c6ad39c` 仅撤回受限 Room3 open 边界及 Desktop 合同测试，不涉及 schema migration、真实系统日历、后端或用户数据。
- **关联事实源**：[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[总路线图](schedule-v2-calendar-roadmap.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)、[当前数据流](schedule-v2-current-data-flow.md)。

### D-031：纯 common CalendarReconciliationCommandPlanner

- **状态**：已采用并以 `25b5245f9` 独立提交；当前实现仅完成无副作用的 common 规划边界。
- **背景**：D-030 已保证一次性打开冲突时 link 与 evidence 成对落盘，但仍缺少一个能在不触碰运行时或持久化的前提下，将已验证的当前 Schedule、Provider 观察和 durable link 转成后续执行意图的窄边界。
- **选择**：新增纯 common `CalendarReconciliationCommandPlanner`。它只校验输入并调用既有 `CalendarThreeWayPlanner`，返回封闭 typed intent：`NoOp`（`NONE`）、`PropagateToCalendar`（`TO_CALENDAR`）、`PropagateToSchedule`（`TO_SCHEDULE`）、`Merge`（`BIDIRECTIONAL`）或 `OpenConflict`。其中向 Schedule 的意图保留完整 Schedule source；planner 不反向 materialize `Schedule`。
- **守卫与 identity**：每个计划冻结完整 guards：account、platform、完整 projection、durable link/双基线、预期 Schedule revision、Provider fingerprint 与本次重发现的 current platform event ref。`eventIdentifier` 只是 link 中可空、可失效的定位缓存；已验证的当前 event ref 可以重发现事件，不能要求与缓存等值，也不是稳定业务 identity。
- **冲突与收敛边界**：只有 `LINKED + conflictId == null` 会三方比较。`CONFLICT` 会逐项校验既有 evidence 的 conflictId、account/platform/projection、双基线、当前 Schedule/Calendar canonical snapshot、Schedule revision 和 Provider fingerprint；当前 planner 不重算或比较 evidence 的 `conflictingFields` 与 `detectedAt`，因此这里不把 evidence 描述为全部字段完全一致。缺失、陈旧或跨投影 evidence 一律 fail-closed。历史双基线不同并不自动要求传播：双方各自保持基线、或单边已经使双方 canonical 内容对齐时，合法结果仍可为 `NONE`。
- **明确未完成**：planner 不创建 `ScheduleCommand.Update`、mutation、outbox 或 `requestSync`；不读写 Room、Provider、repository、network 或 UI；不推进 baseline、cursor、fingerprint、state 或 `updatedAt`；不生成 `conflictId`/`detectedAt`，不调用冲突原子事务。尚无 executor 或任何运行时接线。
- **下一依赖**：执行边界必须重新读取并重新验证 durable link、Schedule revision、Provider ref/fingerprint，随后将 `OpenConflict` 交给既有一次性原子 open，或执行 Provider/Schedule canonical 写入，并且**仅在对应动作成功后**受保护地推进 baseline；这一步尚未完成。其后才是 resolution/UI 与更广平台同步。
- **验证方式**：focused planner 13 tests、完整 Schedule Desktop 345 tests 与 common metadata 编译均成功；未运行设备测试。
- **回滚点**：该 planner 仍是 common 纯计算边界，未触及 runtime、数据库 schema、Provider 或用户日历；回退该实现不会回滚任何已执行的平台或 Schedule 写入。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)、[当前数据流](schedule-v2-current-data-flow.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)。

### D-032：已确认 CalendarLink 共同基线的受保护确认与原子推进

- **状态**：已采用并以 `5ea4d19e4f07aab3925369bdc5ab329d821f2db5` 实现；只完成执行动作之后的确认和 durable 推进原语，尚未接入 runtime coordinator。
- **背景**：D-031 只签发无副作用的公开 typed plan，不能信任可 `copy`/构造的 plan；而成功写入后的共同基线也不能由陈旧或部分 durable link 覆盖。
- **选择**：planner 仅以 file-private `PlannerIssuedCalendarReconciliationGuards` 签发公开只读 guards，冻结完整 account/platform/projection、link/双基线、双方 observed canonical fields、Schedule revision、Provider fingerprint 与本次重发现的 platform event ref。`CalendarReconciliationLinkTransitions.confirm()` 先验证 provenance，再由冻结事实重跑三方 planner，精确校验 classification、传播方向、target 与写后最终事实，才产生私有构造、无 unchecked factory/copy 的 `CalendarLinkAdvancement`。
- **允许与拒绝**：只允许确认 `RESOLVED` 的 `NoOp`、`PropagateToSchedule`、`PropagateToCalendar` 与 `Merge`；`ALREADY_CONFLICTING`、`NOT_ELIGIBLE` 及 `OpenConflict` 一律不得推进 baseline。`OpenConflict` 仍由未来执行边界重验后交给既有一次性 `openCalendarConflictAtomically()`，不走 baseline advancement。
- **持久化边界**：advancement 从 expected link 到 updated link 只允许变更 `eventIdentifier`、`baseScheduleRevision`、`baseSchedule`、`baseCalendar`、`lastProviderFingerprint`、`updatedAt` 六项，且 revision/时间不可倒退。`advanceCalendarLinkAtomically()` 在单个 Room `withWriteTransaction` 中按完整 account/platform/projection identity 重读 durable link，只有整条记录完全等于 expected link 才 upsert updated link；普通异常回滚。取消或调用方未收到 `Unit` 时提交状态未知，必须精确重读：等于 expected 为未提交，等于 updated 为已提交，其他状态重新规划。
- **审查收敛**：首次审查发现公开 plan/advancement 可被伪造；第二次审查发现字段自洽的伪造 guards 仍可绕过；随后以 planner-issued provenance、confirm 内 replan 与 opaque advancement 修复。最终独立审查结论为 `NO_FINDINGS`。
- **明确未完成**：截至 D-032 源码提交时尚未实现 Schedule canonical materializer；后续已由 D-033/`c4ad4e3d1` 补齐纯 candidate prerequisite。当时未实现的本地 Store/outbox/link 联合事务，后续已由 D-034/`d0f85e451` 补齐同一 writer scope 内的 durable composition primitive；该补齐不等于 runtime executor 已完成。Provider/Schedule 实际执行、`BIDIRECTIONAL` phase/recovery/compensation、runtime coordinator、Provider inbound wiring、SyncAdapter、UI 或跨平台实现仍未实现；没有真实双向同步闭环。
- **验证方式**：focused 25 tests、完整 Schedule `desktopTest` 357 tests、common metadata 编译、IDE errors 0 与 Git diff check 通过；未运行 connected/device tests，不能表述为 Provider 真机或模拟器验收。
- **回滚点**：该切片未调用 Provider、repository、Store command、network、mutation、outbox 或 `requestSync`，且未对真实系统日历执行操作；回退 `5ea4d19e4` 仅撤回纯确认与 Room exact-CAS primitive。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)、[当前数据流](schedule-v2-current-data-flow.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)。

### D-033：planner-guarded 的纯 common Schedule 候选物化

- **状态**：已采用并以 `c4ad4e3d1` 独立提交；只完成从 planner 已授权 canonical target 生成完整 Schedule candidate 的纯 common 边界，未接入 runtime。
- **背景**：D-031 只产生 `TO_SCHEDULE`/`BIDIRECTIONAL` 意图，D-032 只能在实际写入后的 confirmed facts 上确认并推进 baseline；二者之间仍缺少一个不会信任调用方 target、也不会丢失 Schedule 私有字段的反向映射边界。
- **选择**：新增 `CalendarScheduleMaterializer.materialize()`。它只接受 planner-issued guards、重新读取的完整 Schedule、occurrence exceptions、canonical target 与调用方显式提供的新增 DEVICE `ReminderId` 映射，输出完整 Schedule candidate；不返回 `ScheduleCommand.Update`。
- **授权与新鲜度边界**：materializer 在读取 guard facts 前先验证 provenance；只接受 `LINKED + conflictId == null`。它从 guards 冻结的四份 canonical 快照重跑 `CalendarThreeWayPlanner`，仅允许 `TO_SCHEDULE` 或 `BIDIRECTIONAL`，且 target 必须逐字段等于 `mergedSchedule`。`NoOp`、`TO_CALENDAR`、`Conflict`、`RETRYING`、`UNSUPPORTED`、`DETACHED`、任何伪造 provenance、当前 Schedule identity/revision/投影/observed fields 偏离、非 `LINKED` 或存在 `conflictId`、非 Schedule 写方向或 target 不匹配均拒绝；live Provider fingerprint/ref 的新鲜度不在 materializer 输入内，仍由未来 executor 重新读取并重验。随后以当前 source 和完整 exceptions 重投影，精确验证 revision、完整 projection identity、observed canonical fields 与 exception unsupported 边界；candidate 再投影必须保持同一 projection kind/full identity，并逐字段精确等于 target。
- **字段与可逆性边界**：只替换 title、description、timing、recurrence 和 DEVICE reminders；保留 id、revision、category、completion、createdAt、updatedAt 与全部 PUSH reminders。既有 DEVICE reminder ID 不按 minute 列表重建；仅新增 minute 由调用方显式给出新 ID，且必须精确覆盖、唯一并不与任何 source ID 冲突。RRULE 必须严格 canonical decode、通过 `ScheduleValidator` 并 exact re-encode；Timed/Deadline `UNTIL` 只有在可证明为目标 IANA 时区当地日期 `23:59:59` 且逐字重编码相等时接受，任何不可逆或领域不支持组合均拒绝。
- **明确未完成**：candidate 的 revision 与 `Schedule.updatedAt` 分配策略仍未决定，当前原样保留 metadata。materializer 不读取 Clock、Provider、Room、repository 或 network；不创建 mutation/outbox/receipt、不推进 link/baseline/conflict evidence。截至 D-033 源码提交时，Store/outbox/CalendarLink 复合 SQLite transaction 尚未实现；后续 D-034/`d0f85e451` 已补齐同一 Store writer scope 的本地 durable composition primitive，但 runtime executor 尚未完成。honestly named best-effort Provider ownership/ref preflight + canonical read-after-write（不是 Provider CAS）、runtime preflight/coordinator，以及 `BIDIRECTIONAL` durable phase/recovery/compensation 仍未实现。`OpenConflict` 不使用 materializer，仍走既有 conflict transition/Room transaction。
- **审查收敛**：独立审查先发现 target 授权漏洞、WEEKLY 测试假阳性与 `NOT_ELIGIBLE` link state 绕过；均返修后最终复审结论为 `NO_FINDINGS`。
- **验证方式**：focused materializer tests 通过；完整 Schedule Desktop 42 XML suites/365 tests，0 failures/errors/skipped；Gradle 535 tasks `BUILD SUCCESSFUL`；common、JS、iOS Simulator、Android、Desktop 与 Android device-test 源码编译通过，IDE errors 0。未运行 connected/device tests，不得宣称 Android Provider 设备行为已验证。
- **回滚点**：该切片只新增纯 common candidate 物化与其测试，不调用 Provider、repository、Store command、network 或真实系统日历；回退 `c4ad4e3d1` 不涉及 durable schema 或外部副作用。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)、[当前数据流](schedule-v2-current-data-flow.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)。

### D-034：Store writer scope 内组合 Schedule、outbox 与 CalendarLink 的 durable 原语

- **状态**：已采用并以 `d0f85e451` 独立提交；只完成 Store writer-scope durable composition primitive，未接入 runtime coordinator。
- **背景**：D-032 的 standalone `advanceCalendarLinkAtomically()` 只能独占一次 Room writer transaction，D-033 只能产生纯 Schedule candidate；若未来 executor 分别提交 graph/outbox 与 link，stale link、取消或 SQLite 失败可能留下不可恢复的半完成本地事实。
- **选择**：`CalendarLinkRoomDao` 新增 transaction-free `advanceCalendarLinkInCurrentTransaction()`。helper 自身绝不调用 `withWriteTransaction`：先拒绝非 `LINKED + null conflictId`，再次校验 D-032 的六字段允许变更及 revision/时间不回退，以 typed full identity `find` 触发 strict mapper，要求 durable 完整记录与 expected 全等后才 typed upsert。既有 `ScheduleRoomDatabase.advanceCalendarLinkAtomically()` 保持兼容，只开启一次 `withWriteTransaction` 后委托该 helper。`ScheduleTx` 注入 link DAO；`advanceCalendarLink()` 入口只显式 fail-closed 要求 `expectedLink.accountId == transaction accountId`，随后委托 helper，不暴露 raw DAO/entity，也不开 nested transaction。`updatedLink` 不能改变 identity 不是入口显式比较两端账号的结论，而是 opaque advancement 与 `requireOnlyAllowedLinkChanges()`、current-transaction helper 的防御性约束共同保证。
- **事务 ownership 与后续组合**：未来一个 `ScheduleRoomStore.transaction` 可在同一 writer scope 内依次 strict read、D-033 materialize、metadata assignment、replace graph、冻结/merge outbox、read-your-writes、D-032 confirm、advance link，最后由 Store 只推进一次 generation 并返回 receipt。`replaceSchedule()` 仍不是 revision CAS；未来 executor 必须在同一 writer scope 内 strict read 后 materialize/write，禁止在 transaction 外生成 candidate 后直接写入。payload 必须在 transaction 内从最终 candidate 冻结；已派发或 `DELIVERY_UNKNOWN` mutation 的不可改写规则不变。
- **回滚与未知提交边界**：真实 bundled SQLite contracts 已验证 graph/outbox/link/sync-state generation 在同一 commit：成功一起提交；stale link、commit 前 hook 的 `CancellationException` 与跨账号 advancement 均一起回滚；原 database wrapper 四项测试继续保留。无 receipt 不能证明未提交：取消与 SQLite commit 竞态仍为 unknown，恢复必须精确重读 generation/link/graph/outbox。本批取消测试只覆盖 commit 前 hook 的确定性回滚。
- **明确未完成**：这是 SQLite 本地原子性，不包含真实 Calendar Provider。诚实命名的 best-effort Provider ownership/ref preflight + canonical read-after-write（不是 Provider CAS）、metadata revision/`updatedAt` 策略、runtime preflight/coordinator、Provider inbound，以及 `BIDIRECTIONAL` durable phase/recovery/compensation 均未完成；不得据此将 `TO_SCHEDULE` runtime 或双向同步标记完成。
- **兼容与 schema 边界**：无 schema version、migration、entity、DAO SQL、resource enum 或公共产品 API 变化。
- **验证方式**：独立三路审查均为 `candidates=[]`，最终 `NO_FINDINGS`。focused 8/8；完整 Desktop 42 XML suites/369 tests，0 failures/errors/skipped；535 Gradle tasks `BUILD SUCCESSFUL`；common/Desktop/iOS Simulator/Android/JS 与 Android device-test 源码编译、IDE errors 0。未运行 connected/device tests，不能宣称 Android Provider 验收。
- **回滚点**：回退 `d0f85e451` 即撤回 Store writer-scope composition primitive 与其 Desktop contracts；未改变 schema、真实 Provider、网络或用户数据。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[当前数据流](schedule-v2-current-data-flow.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-035：planner 授权的 TO_SCHEDULE 单 SQLite 事务执行入口

- **状态**：已采用并以 `c668d69e6` 独立提交；仅新增 noWeb `ScheduleRoomLocalCommandAdapter.executeCalendarPropagationToSchedule()` 内部入口，未接入 production runtime/coordinator/repository/factory。
- **背景**：D-033 已能在纯 common 层物化 candidate，D-034 已能将 graph/outbox/confirm/link/generation 组合到一个 writer scope；仍缺少一个复用真实本地命令语义、而非手写 graph/outbox 的受保护 TO_SCHEDULE 实际提交边界。
- **选择**：入口只接受 planner-issued `PropagateToSchedule`。先在 transaction 外验证 provenance、非空 `accountId` 与 guards account 的精确一致，避免错误账号初始化 sync-state；随后在一个 Store writer transaction 内 strict durable graph read、exact revision guard、`Long.MAX_VALUE` 拒绝、无 occurrence exception graph guard、materialize、单次冻结 Clock、candidate `revision = expected + 1`/`updatedAt = commitAt`，并复用真实 `ScheduleCommand.Update` reducer、PATCH/baseRevision/outbox operation replay。
- **确认与原子性边界**：事务内执行 strict read-your-writes/reprojection、纯 `confirm()` 与 CalendarLink whole-record exact-CAS，最后仅推进一次 generation 并返回 receipt。原子性只覆盖 SQLite；该入口零 Provider I/O。TO_SCHEDULE 的 Provider observation 必须由未来 runtime 在入事务前冻结，提交后新 observation 属于下一轮 reconciliation，绝不宣称跨存储 snapshot/CAS/all-or-nothing。
- **字段与拒绝边界**：当前 Room normal Update 的 `ScheduleValidator` 默认 `pushSupported=false`，本切片不扩展 PUSH 持久化；materializer 可保留 PUSH 并不等于 adapter 支持写入。带 occurrence exceptions 的重复图整体 fail-closed 并回滚。`commitAt` 不得早于 durable Schedule `updatedAt` 或 expected link `updatedAt`。
- **取消与恢复**：事务体内或 commit 前可观测取消确定回滚；取消与最终 SQLite commit 竞态时结果未知。调用方必须重读 durable generation、CalendarLink，并按需核对 graph/outbox，不能盲目重放。
- **验证方式**：真实 planner/reducer/materializer/confirm/SQLite 覆盖成功路径与 stale revision、stale link、account mismatch、overflow、exceptions、两类时钟倒退、commit 前取消的整体 rollback；focused 50/50，完整 Desktop 42 suites、378 tests、0 failures/errors/skipped，535 tasks（55 executed/480 up-to-date）`BUILD SUCCESSFUL`，common/Desktop/JS/iOS Simulator/Android/Android device-test sources 编译通过。三路独立审查仅发现 KDoc 将取消误写为确定回滚，已修正并 targeted re-review `NO_FINDINGS`。未运行设备测试，不构成 Android Provider 行为验收。
- **回滚点**：回退 `c668d69e6` 即撤回该内部 SQLite 入口与 Desktop contract；不改变 schema、生产 runtime、Provider、网络或用户日历。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[当前数据流](schedule-v2-current-data-flow.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-036：冻结 Android CalendarLink discovery 合同

- **状态**：已采用并以 `a1e65656c` 独立提交；只完成 pure common identity/discovery contract，未接入 production 输入、reader 或 runtime。
- **背景**：D-035 已具备仅 SQLite 的 `TO_SCHEDULE` 本地执行 leg，但生产协调器仍缺少可审计的受管 Calendar 与 durable link 重发现边界。若直接以 event ref、link state 或部分 projection 身份配对，可能把错误日历、重复行或不可信 Provider 数据带入后续 planner。
- **选择**：新增纯 common `AndroidManagedCalendarIdentifierCodec` 与 `CalendarLinkDiscovery`。codec 的唯一 canonical 文本为 `android-calendar-row:v1:<positive canonical decimal>`；`decodeOrNull()` 面对任意不可信文本只返回 `null`、绝不抛出。classifier 输入冻结的 trusted account/platform/links 和 `CalendarAbsent` 或 `Present(identifier, observations)`，只返回完整 `Ready(matches, linksMissingExternalEvent, observationsMissingLink)` 或单一 typed `Blocked`。
- **可信与不可信边界**：trusted caller 的 account/platform/link 不一致抛 `IllegalArgumentException`；不可信 observation 的 platform mismatch、blank event ref 或 blank fingerprint 以固定批次优先级返回 typed `Blocked`，不返回部分结果。invalid/inconsistent/mismatched calendar identifier、durable/provider duplicate full projection，以及 Calendar absent with durable links 同样 fail-closed。
- **配对与副作用边界**：仅按完整 `CalendarProjectionId` 配对，三个 `Ready` 列表均按 canonical projection URI 排序；stale/null eventIdentifier 和 fingerprint change 不阻断，且不按 link state 筛选，后续适格性仍由 command planner 判断。该 API 零 I/O/副作用：不读写 Room、Provider 或 network，不创建/更新 link，不执行 planner、`TO_SCHEDULE`、`TO_CALENDAR` 或 `BIDIRECTIONAL`，不授权写操作，也不声明跨存储原子快照。它只定义 Android identity，不定义 iOS EventKit。
- **当前接线缺口与下一门禁（历史，snapshot-only 前置已由 D-037 完成）**：D-036 时 `AndroidManagedCalendarSnapshot.Present` 尚未携带 Calendar row ID，gateway 已读取的 row ID 会被丢弃，测试构造的 identifier 不能冒充 production 输入。`e63cf0c65` 已完成该 row identity 的 snapshot-only 传递；后续 D-038/`b615468b1` 已提供 production 单向导出协调器消费的 session/scope/owner-scoped reader，D-039/`a9406a307` 已提供严格 account/platform 的 CalendarLink DAO list，D-041/`187b0a9d7` 已提供固定 Room 后 Provider 的 account-bound 只读 discovery facade。D-041 非原子且没有 production caller、retry、repair、bootstrap、planner/coordinator execution 或设备运行验证；不实现 Provider inbound/write-back、`BIDIRECTIONAL` recovery、SyncAdapter、pull/apply、retention、Web remote-required、iOS EventKit runtime 或 conflict UI。不得把 D-036 的 `Blocked` 直接塞入既有单向 export `coordinator.start`；诚实命名的 best-effort Provider ownership/ref preflight + canonical read-after-write（不是 Provider CAS）及生产 runtime 接线仍待从新 HEAD 重新 Discover，具体下一切片不得预设。
- **Provider 并发边界**：Android CalendarProvider 的 Events ownership pre-read 与 `applyBatch` 之间不是 CAS；fingerprint 横跨 Events/Reminders，不能宣称 expected-fingerprint Provider CAS。后续最多是诚实命名的 best-effort preflight + canonical read-after-write，并仍需恢复语义。
- **验证方式**：focused 25/25；完整 Desktop 43 suites、403 tests、0 failures/errors/skipped；510 Gradle tasks（54 executed/456 up-to-date）`BUILD SUCCESSFUL`；common/Desktop/JS/iOS Simulator/Android/Android device-test sources 编译通过。未运行设备测试，不能称为 Provider 行为验收。独立审查仅发现完整 `CalendarProjectionId` 对抗测试不足；已补同 `ScheduleId` 不同 scope/kind/recurrence identity 的匹配、判重与排序测试，targeted re-review 为 `NO_FINDINGS`。
- **与 D-035 的关系**：D-035 的 `TO_SCHEDULE` local leg 仍已完成但未接 runtime；`TO_CALENDAR` Provider effect、`BIDIRECTIONAL` recovery 仍未完成。D-036 不改变该分向执行边界。
- **回滚点**：回退 `a1e65656c` 仅撤回纯 common codec/discovery contract 与测试；不改变 schema、production runtime、Provider、network 或用户日历。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[当前数据流](schedule-v2-current-data-flow.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-037：传递受管 Calendar row identity 的 snapshot-only 前置

- **状态**：已采用并以 `e63cf0c65` 独立提交；只完成 Android 严格只读 snapshot 的真实 row identity 传递，未接入 production discovery 或 runtime。
- **背景**：D-036 的 codec/discovery 已冻结 Android calendar identifier 合同，但当时 `AndroidManagedCalendarSnapshot.Present` 丢弃 gateway 同次读取到的 Calendar row ID，纯 common 测试 identifier 不能成为 production 输入。
- **选择**：`Present` 现在携带 `calendarIdentifier`。`queryManagedCalendarSnapshot()` 从同一次 `registry.findCurrentManagedCalendar(accountId)` 得到真实 row ID，并只经既有 `AndroidManagedCalendarIdentifierCodec.encode()` 编码一次；`scheduleIds` 为空的早返回和正常事件查询均复用该 identifier。
- **理由**：把 durable link 所需的 Calendar row identity 与严格只读 observation 绑定在同一已确认存在的 row 上，同时不扩展为跨存储快照或写路径。
- **影响范围**：`CalendarAbsent`、`queryManagedEvents()`、现有 source→Calendar 单向导出 planner/apply、Provider 写入和状态/retry 均保持不变。Android device-test 仅新增真实 row identity 的源码合同；它已编译，但没有运行 connected/device tests，不能视为 Provider 行为验收。
- **未实现与下一门禁（D-037 时的历史边界）**：本切片没有接入 `CalendarLinkDiscovery.classify`、Room、DAO、reader、repository、`AccountSession`/owner Job、coordinator、bootstrap/recovery，或 `TO_SCHEDULE`/`TO_CALENDAR`/`BIDIRECTIONAL`。后续 D-038/`b615468b1` 已完成 production-consumed session/scope/owner-scoped reader，D-039/`a9406a307` 已完成 account/platform 严格 DAO list，D-041/`187b0a9d7` 已完成固定 Room 后 Provider 的 account-bound 只读 discovery facade。D-041 没有 production caller、retry、repair、bootstrap、planner/coordinator execution 或设备运行验证，且不实现 Provider inbound/write-back、`BIDIRECTIONAL` recovery、SyncAdapter、pull/apply、retention、Web remote-required、iOS EventKit runtime 或 conflict UI。不得直接把 D-036 `Blocked` 塞入现有单向 export `coordinator.start`；诚实命名的 best-effort Provider ownership/ref preflight + canonical read-after-write（不是 Provider CAS）及生产 runtime 接线仍须从新 HEAD Discover，具体下一切片不得预设。
- **验证方式**：Android main 与 Android device-test sources 编译 `BUILD SUCCESSFUL`；强制重跑 `desktopTest` 为 43 suites、403 tests、0 failures/errors/skipped，363 tasks 全部执行并 `BUILD SUCCESSFUL`；独立三路审查与 verifier 均为 `NO_FINDINGS`。`:cyxbs-pages:schedule:check` 首次因 Gradle configuration cache 无法序列化 `jsBrowserTest` 失败；以 `--no-configuration-cache` 重跑跨 Desktop/iOS Simulator/JS/Wasm 编译测试链后，仅因仓库级 `kotlinStoreYarnLock` 要求升级 lock 而失败，工作树未产生 lockfile 修改。本切片不升级 Yarn lock，故完整 check 环境/仓库门禁未完全通过。
- **回滚点**：回退 `e63cf0c65` 仅撤回 snapshot identity 与 device-test 源码合同；不改变 schema、production runtime、Provider 写入、network 或用户日历。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[当前数据流](schedule-v2-current-data-flow.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-038：收紧单向导出 Coordinator 的账号生命周期门禁

- **状态**：已采用并以 `b615468b1` 独立提交；仅完成 production 单向导出协调器消费的 `AccountSessionScopedReadOnlyReader` 与其生命周期门禁，不扩展为 CalendarLink discovery runtime。
- **选择**：Provider 只读查询结果只在完整 `AccountSession`、CoroutineScope 与 owner Job 仍为权威时交付；逐项同步写入前后继续复核同一生命周期。stale worker cancellation 会结束完整 lifecycle 并清理已取出及排队的手动 completion；状态按当前账号 binding fail-closed 投影。
- **边界**：外部 ContentResolver 调用仍只是 best-effort，绝不是跨系统 CAS。该切片不实现 CalendarLink DAO list、discovery、bootstrap、双向 runtime 或 Provider inbound。
- **验证方式**：focused `desktopTest`、`compileAndroidMain`、IDE diagnostics 与 `git diff --check` 均通过；未运行设备测试，不能宣称 Android Provider 行为验收。
- **回滚点**：回退 `b615468b1` 即撤回该单向导出生命周期门禁及其测试；不改变 CalendarLink durable schema、双向 runtime、Provider CAS 或网络协议。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[当前数据流](schedule-v2-current-data-flow.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-039：提供 CalendarLink 的严格 account/platform 列表

- **状态**：已采用并以 `a9406a307` 独立提交；只完成 `CalendarLinkRoomDao.list()` 的 durable 严格读取，不引入 runtime、分页或 Provider 侧行为。
- **选择**：入口拒绝空白 accountId，以 typed platform 和 account 双重范围查询全部状态记录，按 canonical projection URI 升序返回。每一行均经 strict canonical mapper；任一 malformed、非 canonical 或 split-brain 行都会使整个列表 fail-closed，不静默跳过。
- **边界**：该 DAO list 不读取 Provider、不执行 discovery、bootstrap、repair、planner、传播或写入；它本身不构成 production runtime。
- **验证方式**：bundled SQLite Desktop contracts 覆盖空结果、范围隔离、稳定排序与损坏行整体失败；focused `desktopTest`、Room/KSP 编译、Android Studio diagnostics 与 `git diff --check` 均通过，未运行设备测试。
- **回滚点**：回退 `a9406a307` 即撤回该严格列表与 Desktop contracts；不改变 schema、Provider、网络或用户日历。
- **关联事实源**：[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[当前数据流](schedule-v2-current-data-flow.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-040：持久化 mutation 的 immutable change snapshot

- **状态**：已采用并以 backend `05b7224` 独立提交；只为已接受的 Schedule 与 category mutation 在同一事务持久化资源、owner-scoped 唯一 change 与 DELETE tombstone。
- **选择**：`schedule_v2_changes.resource_data` 是 `NOT NULL LONGBLOB`，保存 canonical UPSERT 的 exact immutable bytes 或私有 internal DELETE snapshot bytes；`snapshot_hash` 严格是实际存储字节的 SHA-256，禁止 MySQL JSON 归一化或重序列化改变摘要输入。事务内复用 sequence、revision 与 UTC 时间；change、tombstone 或最终 receipt 任一写入失败都会回滚资源、sequence 与全部证据。
- **边界**：拒绝、replay、identity reuse 与 unsupported occurrence 不新增 change evidence。私有 DELETE snapshot 不建立 public bootstrap/change-feed wire contract；bootstrap、delta/change-feed 的 pull/apply、retention 和公开 wire protocol 均未实现。
- **验证方式**：uncached DAO/model/service focused tests、GoLand diagnostics、`gofmt -d` 与 `git diff --check` 均通过；未连接真实数据库或启动服务。
- **回滚点**：回退 backend `05b7224` 即撤回 mutation change/tombstone evidence；不部署服务、不发布 public change-feed contract。
- **关联事实源**：[后端设计](schedule-v2-backend.md)、[总路线图](schedule-v2-calendar-roadmap.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-041：组合 Android CalendarLink 的只读 discovery facade

- **状态**：已采用并以 `187b0a9d7` 独立提交；**截至 D-041**只完成 account-bound `AndroidCalendarLinkDiscoveryFacade`，当时没有 production caller。其后 W15/D-045 已接线 bounded `TO_SCHEDULE` 启动链，W16 在 finalization 后增加已有唯一 `LINKED` 的 `TO_CALENDAR` Update/`RESOLVED` NoOp，W17 另在全批 eligibility 下受控恢复 missing link；当前 production 范围以[执行手册第 12.1 节](schedule-v2-dynamic-workflow-runbook.md#121-w16w17-finalized-长期-worker-的-calendarlink-生产事实)为准。
- **选择**：构造时校验 durable link 的账号边界，固定先以 D-039 严格列表读取 Room 的 Android links，再检查协程取消并读取 D-037 受管 Calendar Provider snapshot，最后交给 D-036 classifier。读取顺序是 Room 后 Provider，任一读取或分类异常原样传播；结果只有完整 `Ready` 或 fail-closed `Blocked`。
- **原子性与边界**：该 facade 只读且 Room/Provider 非原子；不写入、不修复、不重试、不 bootstrap，不执行 planner、`TO_SCHEDULE` runtime、`TO_CALENDAR` write-back、`BIDIRECTIONAL` recovery 或 coordinator start。它也不实现 Provider inbound、SyncAdapter、Web remote-required、iOS EventKit runtime、retention、pull/apply 或 conflict UI，且没有设备运行验证。
- **验证方式**：Android main 与 Android device-test 源码编译、IDE diagnostics 与 `git diff --check` 均通过；instrumented contracts 仅作为源码加入，未运行 connected/device tests。
- **回滚点**：回退 `187b0a9d7` 即撤回该只读 facade 与 instrumentation 测试源码；不改变 Room schema、Provider 写入、生产 runtime、网络或用户日历。
- **关联事实源**：[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[当前数据流](schedule-v2-current-data-flow.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-042：冻结 bootstrap wire 与 Settings-envelope apply 的未接线恢复事实

- **状态**：已采用；`eb2322571` 与 `57085e1fd` 分别独立提交，记录 D1/D2 的已实现原语，不扩大为同步运行时批准。
- **选择**：D1 仅确认严格 `schemaVersion=1` bootstrap DTO/wire codec，严格区分 success/error 并校验 `category_v2`/`schedule_v2`；D2 仅确认 `ScheduleRemoteBootstrap`、`ScheduleSnapshotMerger.mergeBootstrap()` 与 `ScheduleBootstrapApplyPlanner`，对 Settings envelope 执行权威替换、pending/tombstone 保护闭包、最终关系图校验、exact `AccountSession` 引用与 generation 门禁，并以一次 `Store.update` 提交图和 opaque cursor。
- **边界**：codec/planner 只有测试引用；没有 production transport consumer，未接入 Room durable inbound 或 `RequestSync`。不得借 D1/D2 批准 durable inbound、change feed、Web migration、occurrence transport、CalendarLink runtime coordinator、token/account 工作或设备验证。
- **回滚点**：分别回退 `eb2322571` 或 `57085e1fd`；不改变网络路由、Room schema、生产同步或平台日历。
- **关联事实源**：[当前数据流](schedule-v2-current-data-flow.md)、[创建、更新与同步流程](schedule-v2-create-update-sync-flow.md)、[总路线图](schedule-v2-calendar-roadmap.md)。

### D-043：确认 Settings dispatcher 与后端 bootstrap 的受限能力

- **状态**：已采用；`0f341b58d` 与 backend `499fec3` 独立提交，均不代表 production sync 已上线。
- **选择**：Settings fallback dispatcher 每轮按持久化顺序至多冻结 2 条 `QUEUED`、`DELIVERY_UNKNOWN` 或遗留 `IN_FLIGHT`，同账号完整周期互斥、单 mutation 严格串行；逐条处理 `Accepted`、`Rejected`、`DeliveryUnknown`，foreign receipt 转 `DELIVERY_UNKNOWN`，匹配 `Accepted` 仅在 `DELETE` 时清同 resourceType/resourceId tombstone，任何 receipt 不推进 `syncCursor`。backend bootstrap v1 service 默认 `Enabled=false`、单次完整响应，受 `MaxResources` 与最终 canonical body `MaxEncodedBytes` 双硬上限保护，并使用 HMAC 认证、owner-bound、fixed-high-water opaque cursor；后续已增加受原始路径守卫与 TokenVerify 保护的公开 handler。
- **边界**：production gateway 仍 unavailable；Room `RequestSync` 仍 strict reread 后返回 `BackendNotDeployed`/`attempted=false`。backend bootstrap 的公开路由与认证边界已存在，但 runtime 默认关闭且没有 production caller；真实 MySQL、delta/change-feed、客户端接线、部署和设备验证均未批准。
- **回滚点**：分别回退 `0f341b58d` 或 backend `499fec3`；不改动公开 API、数据库部署或设备状态。
- **关联事实源**：[后端设计](schedule-v2-backend.md)、[当前数据流](schedule-v2-current-data-flow.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-044：保留 D3/D4 的受限批准范围

- **状态**：已采用；仅记录已获批准的边界，不生成新的实现授权。
- **选择**：D3 只能进行显式 one-shot、read-only preview；D4 只能进行 privacy/lifecycle-only 工作。
- **边界**：D3/D4 不批准 EventKit、durable inbound、Web migration、occurrence transport、CalendarLink runtime coordinator、token/account 工作或设备验证；也不解除 production gateway、公开 bootstrap route、change feed、部署或任何平台写入门禁。
- **回滚点**：不产生代码或平台状态；后续超出上述范围必须另行决策。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-045：W15 受限生产 TO_SCHEDULE 一次性启动桥接

- **状态**：已采用且仍是当前生产启动链；W16 仅增强 finalization 后的长期 worker，不 替代 本决策。
- **选择**：只由显式 enable 或 durable `enabled=true` 的 repository-initialized resume 发起；稳定代理经 exact session 的内部 hook registration 取得同一 Room delegate 的最小 reconciliation access，不做公开代理强转。启动仍先执行 `bootstrapMissingLinks`，再 inbound-first 处理 planner-issued `PropagateToSchedule`/resolved `NoOp`；多 match 使用 prefix-commit。之后构造 Full plan 并保留 Delete-free legacy 首轮 Create/Update/NoOp 导出：任意含 `CalendarExportAction.Delete` 的计划会在 Provider callback 前被整批拒绝，`legacyDeleteFact` 仅可为后续本地 `DETACHED` 作为证据，绝不删除单个 Provider event。post-apply bootstrap 后，startup/final `NoOp` fence 要求每个 fenced projection 唯一 `NoOp`，通过后才 finalization 并激活长期 worker。
- **授权与一次性语义**：显式 enable 在成功前 durable false，resume 必须 durable true；每次检查 exact session/scope/owner、generation/revocation/desired。activation 后再次 read-back bootstrap 新 Create 事件并要求完整 discovery 无 missing link；最终持久化 true 在 controller lock 内重验 active owner/session/scope。pending 只为同一并发启动共享，Ready 不缓存为重放凭据；同 epoch 失败 sticky block，新显式 enable 才能重试。disable/clear 会撤销并取消未完成启动。
- **边界**：不注册 Calendar observer、不轮询、不自动 rearm 或重试 inbound。仅安全 Android CalendarLink bootstrap/new link 已接入；W15 启动链不执行 `TO_CALENDAR`，`BIDIRECTIONAL`、冲突、SyncAdapter、持续 Provider inbound、远端 bootstrap/delta/backend 请求均不在本批准范围。W16 另行限定为 finalization 后长期 worker 的既有唯一 LINKED `TO_CALENDAR` Update/`RESOLVED` NoOp，不替代本启动链；Room `RequestSync` 继续 `BackendNotDeployed`。启动前置 startup fence 失败或 activation 前撤销时零 Provider 写入；已通过启动前置 fence 后才进入既有单向 Schedule→Calendar worker。设备测试仅保留源码，未运行。但 W15 的 legacy 首轮 apply 发生在 post-apply bootstrap 与 final resolved-NoOp verification 之前，因此该 post-apply/final verification 失败可能保留已经应用的 legacy Provider prefix，此时 durable enabled 仍为 false，activation 与长期 worker 均中止，后续 recovery 必须重新 discovery，不能假定 rollback。
- **回滚点**：回退本次 production runtime/controller/coordinator 接线与对应测试；不涉及 Room schema、backend、网络部署或用户日历清理。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[当前数据流](schedule-v2-current-data-flow.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### W16：finalized 长期 worker 的 CalendarLink 出站确认

- **状态**：已采用；仅在 D-045 W15 启动 bootstrap、inbound-first、legacy 首轮导出、post-apply bootstrap 与 final NoOp fence 成功 finalization 后生效。
- **背景**：D-045 接线后需要把“可启动一次”与“可推进 CalendarLink 的事实集合”分开，避免将 discovery、纯 planner 或 SQLite leg 误报为 Create/Delete、连续 Provider inbound 或完整双向同步。
- **选择**：W16 生产范围仅允许已有且唯一 `LINKED` link 上的 planner-issued `TO_CALENDAR` outbound `Update`，以及 recovery 中 planner-resolved `NoOp` 推进 CalendarLink；missing-link Create 是当时未实现的边界，后续受限 W17 另行决策。Delete、连续 Provider inbound、`BIDIRECTIONAL`/Merge、冲突、SyncAdapter、backend/remote 与设备 acceptance 均保持未实现。
- **精确边界**：长期 worker 固定 Room strict read → Provider strict snapshot/read → fresh pre-write strict Provider read → Provider Update → strict read-back → Room whole-record exact-CAS → final strict Provider reread。fresh pre-write 必须逐值匹配 planner 冻结 observation，read-back/final reread 必须携带同一 calendarIdentifier。final reread 若不同于 CAS-confirmed receipt，保留 truthful last-common baseline，返回 typed fresh-replan failure，不 rollback Provider/CalendarLink 且不发布 Completed。每个阻塞读写与 Room transaction 前后复核授权；Room/Provider 无共同事务，final reread 后仍有不可消除的残余外部写入窗口。
- **理由**：将本地可证明的 durable 组合与跨存储不可原子边界明确分离，避免外部 Provider 变化被旧基线吞掉，也避免把缺失 link 或不支持方向静默降级为 Create/Delete/NoOp。
- **影响范围**：实际新增/修改 common `ScheduleCalendarOutboundLinkRuntime` 与 `ScheduleCalendarReconciliationAccess`、Android `ScheduleCalendarExportInitializer`、`AndroidManagedCalendarRegistry.kt`、`AndroidScheduleCalendarGateway.kt`、controller/coordinator、noWeb Room repository/adapter，以及 common runtime、Room Desktop focused tests 和 `AndroidCalendarProviderInstrumentedTest.kt` 的受控 LOCAL fixed-row/membership 漂移 contract；完整的逐文件 production/test inventory 与同步回滚清单以[执行手册第 12.1 节](schedule-v2-dynamic-workflow-runbook.md#121-w16w17-finalized-长期-worker-的-calendarlink-生产事实)为唯一事实源。不新增 schema、协议、Provider API、backend caller 或用户日历操作。finalized coordinator 保留 strict Provider snapshot 的 calendar row identity，在任一 Provider action 前以完整 Room link 批次重用 `CalendarLinkDiscovery` 校验；Android 的 registry lookup 固定使用已确认的 calendar row，非创建且不替换日历；只允许唯一 `LINKED` Update 或 `RESOLVED` NoOp，Create/Delete/Unsupported、missing/duplicate/non-LINKED link 和 calendar identity 漂移均阻断整轮。
- **验证方式**：运行 `ScheduleCalendarOutboundLinkRuntimeTest`、Room repository/CalendarLink Desktop contracts、bundled SQLite/Desktop focused tests、Android main source 编译、`:cyxbs-pages:schedule:compileAndroidDeviceTest` 与 `git diff --check`；`AndroidCalendarProviderInstrumentedTest` 的 device-test source 已编译，**未执行任何 connected/device test**，也未操作真实 Calendar Provider、backend/network、MySQL 或真实用户日历。common runtime tests 直接调用 production batch dispatcher，覆盖唯一 LINKED Update/NoOp 的派发，以及 missing/duplicate/non-LINKED/conflict link、Create/Delete/Unsupported、absent/malformed/mismatched identity、不可信/重复 observation 与 event-ref 漂移的零 callback 阻断；同时覆盖 RESOLVED NoOp 的 CAS 前 strict reread、CAS 后 final reread、CAS 前/后外部字段漂移及 calendar row replacement，确认末次漂移保留 truthful last-common baseline 但返回 typed replan failure。
- **回滚点**：回退 W16 production 行为必须完整使用[执行手册第 12.1 节](schedule-v2-dynamic-workflow-runbook.md#121-w16w17-finalized-长期-worker-的-calendarlink-生产事实)的逐文件清单，并重跑其中的 focused tests、Android main 编译及 `git diff --check`；registry 的固定行、非创建 lookup，以及 ownership/membership Provider Update 必须与 coordinator/runtime 一起回退，不能只撤销上层接线，否则会留下已改变的 Provider 行为边界；只回退四份文档不能关闭已接线的 finalized outbound gate。该回滚不涉及 schema、Provider 数据清理、网络或外部用户数据。
- **关联事实源**：[当前创建、更新与同步数据流](schedule-v2-current-data-flow.md)、[总路线图](schedule-v2-calendar-roadmap.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)、D-045。

### W17：finalized worker 的受控 missing-link orphan adoption / Create

- **状态**：已采用；只在 D-045 已成功 finalization 的长期 worker 内，作为 W16 既有 `LINKED` Update/`RESOLVED` NoOp dispatcher 外的受限恢复。
- **背景**：W16 明确拒绝 missing link，避免将没有 durable baseline 的 Provider 写误报为已确认；但严格 discovery 仍可能发现 exact canonical orphan，或 planner 已签发一个完全缺失投影的 Create。把它们直接塞入 W16 dispatcher 会破坏其“已有唯一 `LINKED`”前提，也会在跨 Provider/Room 故障后产生重复 Create 风险。
- **选择**：先对完整 Room link、完整 Provider scope、local snapshot、calendar identity 与全部 action 做 whole-batch preflight。任何另一 action/link 为 retrying/conflict、`TO_SCHEDULE`、Delete、Unsupported、missing-event 或 identity-invalid 时，整批零 Create。通过后只选择一个 winner：已有 exact orphan 走零 Provider 写的 adoption；否则仅 planner-issued Create 允许执行 whole-scope preflight → single fixed-row noncreating Provider Create → strict canonical read-back（含 returned event ID）→ bootstrap planner → create-if-absent exact durable winner → final whole-scope reread → complete replan。
- **理由**：完整 batch gate 防止用局部 target 观察掩盖其他不安全事实；fixed-row 非创建 adapter 防止 Calendar 删除/同名替换时落入新 row；strict receipt、exact durable winner 与 final reread 将可观察漂移 fail-closed。认领和 Create 成功后都必须废弃旧计划，避免把已变更的跨存储事实继续作为 W16 confirmation 的输入。
- **跨存储与恢复边界**：Provider 与 Room 没有共同事务。Provider Create 后若 blocked、结果 unknown 或 Room create-if-absent 失败，事件保留为 orphan：不做 compensating delete、不在本次自动 retry；同一逻辑 worker batch 的 shared ledger 禁止第二次 Create。后续新的 worker 请求仅能在全新 discovery/bootstrap 证明 exact matching orphan 后 adoption，不能假定 rollback 或复用旧计划。
- **影响范围**：新增 common `ScheduleCalendarMissingLinkCreateRuntime.kt` 及其 `ScheduleCalendarMissingLinkCreateRuntimeTest.kt`；修改 Android `ScheduleCalendarExportCoordinator.kt`、`AndroidScheduleCalendarGateway.kt` 与 `AndroidCalendarProviderInstrumentedTest.kt`。W16 原有 production/test 文件仍为同一不可拆分的回滚边界，完整 inventory 以[执行手册第 12.1 节](schedule-v2-dynamic-workflow-runbook.md#121-w16w17-finalized-长期-worker-的-calendarlink-生产事实)为准。
- **验证方式**：已批准 production review；当前 focused evidence 覆盖 `ScheduleCalendarMissingLinkCreateRuntimeTest` 的 callback 顺序、whole-batch 阻断、fixed-row Create receipt、post-Create no-second-Create ledger 与 final reread，连同既有 W16/Room focused contracts、Android main/device-test source 编译和 `git diff --check`。`AndroidCalendarProviderInstrumentedTest` 的 fixed-row Create 是 compile-only source contract，未运行 connected/device test；未操作真实 Provider、网络、MySQL 或用户日历。
- **回滚点**：必须与 W16 inventory 同步回退上述五个 W17 文件及第 12.1 节列出的全部 W16 文件，再重跑对应 focused tests、Android source compilation 与 `git diff --check`。不得仅回退 coordinator 或文档；不得以删除 orphan 作为回滚补偿。W15 startup、Room schema、backend/remote caller 与连续 inbound 均不在本决策范围。
- **关联事实源**：[当前数据流](schedule-v2-current-data-flow.md)、[总路线图](schedule-v2-calendar-roadmap.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)、D-045、W16。

### W18：删除生命周期只作为显式脱链门禁

- **状态**：已采用并以 `539446207` 独立提交；纯 common 判定，不是 Android 生产 detachment caller。
- **背景**：受管 Provider 事件的缺失、日历替换或外部删除不能推断为 Schedule 删除，更不能借此删除事件、重建事件或改写 CalendarLink。
- **选择**：只有调用方给出显式 destructive authorization，且完整批次同时证明本地 Schedule 已删除、存在 `LINKED` 无冲突 link、当前受管 Provider event 与同一 scope 的冻结证据一致时，才签发无副作用的删除生命周期 proof；缺失/替换/重复/过期 Provider 事实、非 `LINKED`、缺授权或任一批次不一致均 typed fail-closed。
- **理由**：将“业务 Schedule 已删除”的 durable 事实与“Provider 当前观察”分开，避免把 Provider 可见性变化升级为跨端删除授权。
- **边界**：W18 不执行 Provider Delete、不写 Room、不恢复历史 tombstone、不创建 transition，也不提供连续 inbound、`BIDIRECTIONAL`、SyncAdapter、冲突解决或设备验证。
- **验证方式**：纯 common decision-table focused tests 已通过；未运行 connected/device test，未访问真实 Provider、日历、网络或外部数据库。
- **回滚点**：回退 `539446207` 只撤回纯门禁与合同测试；不影响 Provider、Room schema 或用户日历。
- **关联事实源**：[总路线图](schedule-v2-calendar-roadmap.md)、[当前数据流](schedule-v2-current-data-flow.md)、W19、W20b。

### W19：冻结不透明且不可执行的脱链转换

- **状态**：已采用并以 `93401a2b1`、`455af2828` 独立提交；仍没有 Android production detachment wiring/caller。
- **选择**：仅可从同一份已通过 W18 的完整证据生成 opaque `CalendarLinkDetachmentTransition`。它逐 projection 保留 expected `LINKED` record、唯一 terminal `DETACHED` record 与冻结 lifecycle identity；后者精确绑定 Schedule revision、**原始** `deletedAt` 文本和 canonical `deleteMutationId`。terminal 只改变 state、清空 event identifier，并以不回退的 `updatedAt` 终止；账号、projection、calendar row、baseline、cursor、outbound operation 与 Provider fingerprint 全部保留。
- **理由**：transition 以私有不可变快照保留并行列表的 canonical 对齐，拒绝 raw records、部分 projection key 或事后集合篡改，从而不给调用方拼接局部写入权威。
- **边界**：W19 本身不读写 Room/Provider/network，不删除 Provider event，不恢复历史 tombstone，不执行或授权任何同步动作；Store 必须在写前重读全部 durable 事实。它不是账户 session、scope、owner/controller 或 Provider authorization。
- **验证方式**：focused common contracts 与 Android main compilation 已通过；未运行设备测试或访问真实日历/网络/数据库。
- **回滚点**：回退上述 commits 仅撤回纯合同；不能把未接线 transition 误称为已完成生产 detach。
- **关联事实源**：W18、W20b、[执行手册第 12 节](schedule-v2-dynamic-workflow-runbook.md#12-当前-d-045-生产范围与后续工作门禁)。

### W20b：Store-owned Room whole-batch DETACHED exact-CAS

- **状态**：已采用并以集成提交 W20b=`d14b45356` 独立提交；任务分支 source commit 映射为 `bcebe10a1` → `d14b45356`。只提供 Store-owned bundled-SQLite durable primitive，未接线 Android production caller。
- **选择**：入口只接受 W19 opaque transition 与 durable generation token。在首个 upsert 前，单个 Room writer transaction 严格读取完整 Android target link 子集、Schedule graph、精确 tombstone lifecycle、sync-state 与无关 links；只在 `ALL_EXPECTED` 且 generation 精确匹配时整批写入 `DETACHED` 并将 generation 精确推进一次。`ALL_TERMINAL` 仅在更高 generation 下返回零写入 `AlreadyTerminal`；expected/terminal 混合、缺失/多余/改变的第三状态、陈旧 generation、Schedule recreated 和 tombstone revision/raw `deletedAt`/`deleteMutationId` 不匹配均 reason-only `Blocked`，绝不 partial repair。
- **原子性与未知提交边界**：同一 SQLite transaction 覆盖全批 link、generation 与事务内复读；后续行失败、generation CAS 失败或 CAS 前取消均整体回滚。取消与 SQLite commit 竞态时结果是 unknown commit，调用方只能完整 durable reread，**不得自动 retry**。SQLite 原子性不等于 `AccountSession`/scope/owner/controller gate，也不等于 Provider authorization 或跨 Room/Provider atomicity。
- **验证方式**：12 项独立临时 bundled SQLite contract 覆盖提交、重开、无关 link、ALL_TERMINAL、mixed/third/stale/recreated/lifecycle、行/CAS 失败和取消回滚；连同 transition/advance/generation focused regressions 共 42 项通过。未运行 connected/device test，未访问真实 Provider、用户日历、网络或外部数据库。
- **回滚点**：回退 Store transaction、其 Desktop contracts 与 transition 依赖；不做 Provider 补偿删除或自动重试。
- **关联事实源**：W18、W19、W20r、[当前数据流](schedule-v2-current-data-flow.md)。

### W20r：durable DETACHED 的运行时终态回归

- **状态**：已采用并以集成提交 W20r=`cf26de3c8` 提交测试合同；任务分支 source commit 映射为 `14c0e9b8e` → `cf26de3c8`。没有新增 production/Provider/Room wiring。
- **选择**：durable `DETACHED` 即使 Provider 仍可观察到旧 event，也必须令 W16/W17 全批 fail-closed：不得 Update、recreate、adopt 或把无 observation 的 durable link 降格为 missing-link Create 候选。本地 `DETACHED` **刻意不删除 Provider event**。
- **理由**：终态本地脱链只撤销未来受管投影关系，不能把已存在的外部事件解释为可重新接管的授权。
- **边界**：不恢复 tombstone、不执行 inbound、`BIDIRECTIONAL`、SyncAdapter、冲突 resolution 或 Provider 清理；没有 production detachment caller。W21a exact Room repository detachment boundary 已集成（task branch source `95f11cc8b` → integrated `61cf8b778`），但没有 Android finalized production caller/runtime registration/exact-session wrapper/lifecycle capability source，仍不构成当前 Android production detachment wiring；W21b 仍待后续 Discover。
- **验证方式**：focused common runtime regressions 覆盖 Provider 有/无旧 observation 的 zero callback/typed block；未运行设备测试或真实 Provider。
- **回滚点**：回退 regression tests 不会改变已落地 Store primitive；生产 caller 仍不存在。
- **关联事实源**：W16、W17、W20b、[总路线图](schedule-v2-calendar-roadmap.md)。

### W21/W22：将普通删除接线为本地 `DETACHED` 的 finalized controller router

- **状态**：已采用并完成 production caller；本节是对 W18–W20r 当时“尚无 production caller”历史快照的 HEAD supersession，不改写那些原始决策的边界。
- **选择**：Room 仅从 exact retained local-deletion proof 发布 `replay=0` 候选。Controller 只有在同一 direct Room façade 已注册 issuer、独立 opaque authorization、fresh evidence source 与 detachment access，并已达到 finalized Ready 时才安装 router；router 将 exact `AccountSession`/generation、scope、owner、export scope、issuer 与 controller generation 闭合。disable、clear、替代 enable、注册替换、账号切换或 owner 结束均先撤销 binding，再取消 collector。
- **执行边界**：runtime 独立一次性消费 proof/authorization，读取完整 fresh Room/Provider evidence，要求整批 W18/W19 eligibility 后，最多调用一次 W20b Room whole-batch detachment commit。它没有 Provider gateway、coordinator 或 callback authority；`legacyDeleteFact` 仅是证据。成功只在一个 SQLite commit 中写本地 `DETACHED`，普通删除不删 Provider；`DETACHED` 不得 Update、adopt、recreate 或成为 Create 候选。
- **失败边界**：`Blocked`、`AlreadyTerminal`、异常、取消与 unknown commit 都是该候选终态，禁止 replay、retry、Provider 删除、补偿删除、recreate 或 adoption。
- **验证方式**：host/common focused contracts 与 Android source/device-test source 编译覆盖门禁和 no-retry 边界；未运行 connected/device test、ADB、真实 Provider/用户日历、网络或外部数据库。
- **回滚点**：必须同步回退 Room candidate/issuer、controller registration/router、authorization/evidence/detachment capability 与对应 contracts；不得只回退上层 router 后留下可消费候选或 capability。
- **关联事实源**：[当前数据流第 18.2 节](schedule-v2-current-data-flow.md#4-系统日历边界active但不属于后端同步合同)、[执行手册第 12.2 节](schedule-v2-dynamic-workflow-runbook.md#122-w18w22-普通删除的本地-detached-生产路径)、[总路线图第 8.2 节](schedule-v2-calendar-roadmap.md#7-历史-wave-索引)。

### S24：finalized 后只开放用户显式的 bounded inbound refresh

- **状态**：已采用并完成 production wiring；finalized 用户现在可直接执行“从系统日历刷新”，无需为读取新的 Provider 变化重新启用开关。本节只扩展当前能力，不改写 D-045/W16/W17 的历史启动/出站边界。
- **背景**：W15 仅在 enable/resume 做一次 inbound，W16/W17 finalized worker 只持续出站。用户在系统日历修改受管事件后，需要一个不依赖未来 observer/SyncAdapter、可审计且可立即停止的显式入口。
- **选择**：设置页仅在当前 exact session、权限齐全、durable enabled 且 controller 已 finalized 时展示可点击动作。Controller 冻结 start token、completed、registration identity 与同一 direct Room finalized sibling，但不预造 causal token；Coordinator 通过 active production causal lifecycle 冻结 session/scope/owner/start generation 与 finalized `LifecycleSession`，随后在任何 Room callback 前为每次操作签发新的 opaque token，并将独立 `InboundRefresh` 放入现有单 Channel/worker。token 只作进程内 routing metadata，不携带 accountId、不持久化、不查询 repository、不授予 Room/Provider authority；worker 的队列顺序也不参与因果证明。
- **整批能力边界**：在首个 Room mutation/confirmation 前，common preflight 必须读取 Ready snapshot、完整 links 与 whole-scope Provider discovery，证明所有本地投影与唯一完整 `LINKED` match 一一对应，且 planner 结果只含 `PropagateToSchedule` 或本地/Provider canonical fields 精确相等的 `RESOLVED` NoOp。`DETACHED`、冲突、重试、不支持、missing/duplicate/ambiguous/incomplete/malformed、非法投影、非 Ready、`TO_CALENDAR`、Merge、OpenConflict、divergent NoOp 或 legacy Delete 任一存在即整批零 callback；不得挑选安全子集。
- **因果握手与执行边界**：inbound 只读 Provider，只能写/确认 Room；不 bootstrap、不调用 Provider create/update/delete/clear、missing-link recovery、legacy apply、`requestSync`、远端 gateway 或本地删除 router。runtime 在每项 callback 前后 fresh rediscovery 并依赖 exact-CAS，每个 projection 最多一次。每个已知成功的 `TO_SCHEDULE` transaction 都经 direct Room façade 的 final source-bound writer 执行；stamp、causal commit、receipt 与已验证 observation 的具体实现均由 Room writer 私有创建，并让真实 `SchedulesCommitted` 共享同一个 token/stamp causal object。已删除通用 writer、通用 committed-write constructor/factory、可继承 issuer、protected receipt factory 与 module-level `issue`；上层只能把 opaque receipt 交回冻结的 exact Room access 验证，不能制造可被该 source 接受的 metadata pair。隔离 router 单测允许测试域 opaque 实现，但它不构成 Room durable issuance 证据。collector 对 active token 只 hold tagged event；无标签及任一 nonmatch（包括同 ID）保持普通自动 work。完整 Ready 后 direct sibling 验证 receipt source、exact token 与 stamp uniqueness，gate 只抑制 exact pairs。禁止 ID equality、时间戳、arrival/event order、time window、queue emptiness、SharedFlow replay/buffer timing、generation proximity 或 first-match 作为 causal proof。Room/Provider 无共同事务，preflight/final reread 不是 Provider CAS。
- **follower 与自动队列**：只有完整 Ready、全部 receipts 验证且 fresh authorization 仍有效时，production worker 才创建恰好一条携带手动 completion 的 follower Full。该 follower 固定 no-merge/no-drain/no-retry，并恰好一次执行既有 finalized outbound replan；自动事件在 follower 前、中、后均保留自己的 queue entry，继续使用普通 debounce/merge 与 Provider 瞬时失败 retry，不能被 follower completion 或 ledger 污染。collector、router、worker、fence 与 handoff 已收敛为 coordinator 实际委托的同一 production causal lifecycle，controller/host test 不再另建旁路 queue/gate。
- **失败边界**：partial block、失败、请求级取消或授权丢失均只终止本请求；router 同步 release 未被 source-bound receipt 证明为自身回声的 held/nonmatch event，让它们进入普通自动路径。SQLite commit 未知没有 validated receipt，不推断、不 replay、retry 或建立 fallback。每个已验证 known-success commit 在 callback 临界区立即登记 exact fallback；handoff 只有在旧 channel `trySend` 成功取得普通队列所有权后才按 exact stamp 删除，授权后、交付前关闭 channel 时当前及其余 fallback 均原样保留。真实 lifecycle Job 取消/替换时，替代 lifecycle 把全部 fallback 作为普通 Incremental 接回，并对每个尚未交付的 delayed exact publication 仅按 opaque stamp 抑制一次，重复交付恢复普通自动语义。旧 channel 已消费或已取出的其它普通 Incremental/Full 也进入同账号同 scope 的 lifecycle-safe handoff，替代 lifecycle 以自己的 gate 接回，不能依赖 `SharedFlow replay=1`；旧 completion、授权闭包、InboundRefresh 与 follower 不交接。不创建 synthetic follower，无 Room retry、replay、补偿或 safe-subset。stop/revoke 取消 in-flight 与 pending completion。同账号旧 generation、迟到 UI callback、replacement、disable/clear/stop、registration 替换或 owner 结束都在下一边界失效。
- **保留边界**：W15 enable/resume prefix-commit 与 post-apply fence 不变；W16/W17 出站/missing-link 规则不变；普通 Schedule 删除仍只写本地 `DETACHED` 且不删 Provider，legacy Delete 仍全批 fail-closed。此 S24 决策原本不包含 observer；后续 S25 已补 process-lifetime 的 signal-only observer，S26a 再补 Room-only durable conflict open，但 conflict resolution/UI 与 S28 可靠后台仍未完成；不宣称连续/完整双向、远端收敛、iOS 或设备/OEM acceptance。
- **验证方式**：纯 common whole-scope preflight/finalized runtime 覆盖精确 action/projection/observation identity、全部拒绝状态、mixed batch 零 issuance、每投影一次与 unknown-commit no replay；Desktop 真实 SQLite repository 测试证明 source-bound receipt 与真实 Room event 共享 exact token/stamp。新增 Android host focused test 使用临时 bundled JVM SQLite 与 concrete `RoomScheduleRepository`，从 `ScheduleCalendarExportCoordinator.refreshFromCalendar` 进入 production identity、whole-scope preflight、runtime 与 production causal lifecycle，消费 real Room source-bound receipt/`SchedulesCommitted` publication，并串起 causal gate、follower/自动队列和真实 lifecycle Job replacement；覆盖两个 stamp 在 collector 交付前已知成功后取消、同 ID 无标签 real Room race、取消后无 follower、替代 lifecycle 从 handoff 接回全部 known-success fallback、每个 delayed exact publication 只抑制一次及其重复交付恢复普通语义；另一个直接驱动同一 production worker 的确定性门禁在 fallback acquisition 与 `trySend` 之间关闭旧 channel，证明失败交付不会移除任何 known-success 条目，replacement 只接回普通自动 work，旧 completion/follower 不复活。controller freeze/dispatch 另由窄 host 委派合同覆盖。另执行 Android host/desktop focused tests、common/noWeb/Android 编译、diff/allowlist/doc-link/stale-claim check 与 Kotlin IDE diagnostics；IDE 多 worktree 索引若给出跨文件伪 unresolved，以同一 worktree Gradle 编译结果为准。未运行 connected/device/ADB/真实 Provider、网络或用户数据库。
- **回滚点**：同步回退 S24 preflight/runtime mode、Coordinator `InboundRefresh`、Controller/UI action、两份 common tests与本次四份文档；不得只删 UI 后遗留可调用的内部请求入口，也不得以 Provider 删除作为补偿。
- **关联事实源**：[当前数据流第 18.1.1 节](schedule-v2-current-data-flow.md#4-系统日历边界active但不属于后端同步合同)、[有限双向能力矩阵](schedule-v2-calendar-bidirectional.md#32-有限入站是历史事实不是新方向)、[总路线图第 8.3 节](schedule-v2-calendar-roadmap.md#7-历史-wave-索引)。

### S25：finalized lifecycle 内的 Calendar Provider change pulse

- **状态**：已采用并完成 production/host 接线；只覆盖进程存活期间的 exact finalized lifecycle，不是可靠后台同步。
- **背景**：S24 已提供用户显式的有界 Calendar→Schedule 刷新，但用户在系统日历修改受管事件后仍需手动点击；同时不能把全局 Events 通知误用为按日历行过滤、因果证明或新的 reconciliation 实现。
- **选择**：由 coordinator 在 controller 的 durable enabled/post-apply 收尾后、completion fence 打开前注册 `CalendarContract.Events.CONTENT_URI` 的单一 `ContentObserver`。registrar 开始安装 observer 后即把 callback 保留到当前 lifecycle 的 `CONFLATED` relay，但 completion fence/authorization recheck 前不 drain；relay 每次 drain 使用已冻结的 exact session/scope/owner/start-generation 与 direct Room sibling，经现有 S24 `InboundRefresh` causal lifecycle、whole-scope preflight/runtime 与 Ready-only isolated Full follower 执行。注册失败或 post-register recheck 失败在 Ready 前失败关闭；显式 stop、disable、replacement 与 prepared-start abort 先摘除 exact identity，使 completion hook 成为 no-op，再关闭 relay/channel/worker。owner completion，或 causal session 在仍 active 的 SupervisorJob owner 下独立失败时，由 app-level/host cleanup scope 在失败 session 之外条件比较 provider entry、exact lifecycle session/pulse 与 session/scope/owner/start-generation，随后 best-effort unregister、关闭 requests、cancel-and-join worker 并移除 exact registry/status ownership；unregister 抛错只 suppressed 到原 session failure，不能阻断收敛。
- **理由**：复用唯一 source-bound causal token/receipt、single worker、preflight 和 follower 实现，避免 callback 线程读写 Provider/Room 或引入按 URI、event ID、时间、到达顺序、队列空闲、SharedFlow timing、generation 邻近/first-match 的错误因果推断。`CONFLATED` 仅降低通知压力；运行时新增 callback 保留一个后继 bounded pulse。
- **边界**：Events 通知不能可靠限制为受管 Calendar row，所有 I/O 仍需现有 exact managed scope 与 whole-scope preflight。自身 outbound 触发的通知不作启发式抑制，照常进入 finalized inbound；收敛观察产生 exact NoOp，isolated follower 不再写 Provider，因此不形成写回环。自动 pulse 不 retry、replay 或补偿失败/unknown SQLite commit；手动 S24 completion 语义保持独立。普通删除仍为 Room-only `DETACHED`，不取得 Provider/coordinator/callback authority，legacy Delete 仍在 Provider callback 前 fail-closed。
- **验证方式**：Android host fake registrar 覆盖 relay 的 register/unregister、Ready 前 callback 保留且零 dispatch、stale callback、burst coalescing、执行期后继脉冲、部分注册失败自清理与 throwing-unregister；注入同一 fake 到 production coordinator host seam，覆盖 finalized activation 的一次注册、注册期间 callback 后 authorization 撤销（含 throwing-unregister）仍 fail-closed、active observer callback 在阻塞 outbound 后只通过同一 worker 排队并等待 follower 的 production `Completed`，以及 concrete Room repository、whole-scope preflight/runtime 后的 exact NoOp 单 follower 无 Provider 写回环；owner completion 与仍 active SupervisorJob owner 下的独立 causal-session failure 都会 unregister、使旧 callback inert 并移除 exact registry/status ownership，throwing unregister 仅 suppressed 到原 session failure。controller production cleanup regression 另证明 finalization action 已成功但 post-action 授权撤销时，abort 与 `stopAndRemoveIfSame` 都执行，pending 保留原失败且 exact ownership 仍移除。stop/replacement/迟到 callback 也必须在旧 lifecycle inert 后收敛。既有 S24 concrete Room causal worker regression 继续覆盖 Ready-only follower、同 ID 真变化、两个 delayed exact publication、取消、replacement handoff 和单 worker serialization。已在本 lane 强制执行 `:cyxbs-pages:schedule:testAndroidHostTest` 的 `ScheduleCalendarManagedProviderChangePulseTest`、`ScheduleCalendarExportCoordinatorCausalWorkerTest` 与 `ScheduleCalendarExportControllerDelegationTest`；仅为 host compile/test，不运行设备、ADB、真实 Provider、网络或用户数据库。
- **剩余限制**：没有 polling、SyncAdapter、可靠后台、进程死亡/重启重投递或 device/OEM Provider acceptance；这些属于 S28 及后续平台验证，不能宣称连续完整双向。
- **回滚点**：同步回退 observer registrar/relay、coordinator finalized registration/cleanup、host tests 与五份 current-state 文档（含[日历导出架构](schedule-v2-calendar-export.md)）；不得以删除 Provider event 或补偿写入作为回滚。
- **关联事实源**：[当前数据流第 18.1.1 节](schedule-v2-current-data-flow.md#4-系统日历边界active但不属于后端同步合同)、[有限双向能力矩阵](schedule-v2-calendar-bidirectional.md#32-有限入站是历史事实不是新方向)、[总路线图第 8.3 节](schedule-v2-calendar-roadmap.md#7-历史-wave-索引)。

### S26a：planner-issued 冲突只开放 Room durable terminal open

- **状态**：已采用并完成窄 production slice；不包含 resolution、UI、可靠 replay 或后台投递。
- **选择**：新增纯 common whole-scope conflict preflight 与账号绑定 `ScheduleCalendarConflictOpenAccess`。finalized 入站在原 S24 preflight 前、出站在 W16 callback 前拦截；发现新冲突时只签发 canonical ordered 不可拆分批次。W15 保留 prefix-commit，只允许在已知成功前缀后打开首条冲突并返回 typed terminal outcome。
- **Room 边界**：adapter 在进入 writer transaction 前注入全部唯一 conflictId，并只读取一次时钟冻结 detectedAt；Store 在单个 transaction 内重验 live Schedule projection/revision、whole durable link 与 planner conflict，先写完整 link 批次、再写完整 evidence 批次，并只推进一次 generation。已存在 `CONFLICT`、stale/third-state、ID 冲突或任一 SQLite 失败整体失败关闭。
- **副作用边界**：durable open 不写 Provider、Schedule graph、outbox，不发布 `SchedulesCommitted`、causal receipt 或 follower。取消或未返回 receipt 时不得推断 SQLite outcome、retry、replay、补偿或重开；后续只能 fresh 读取完整 durable facts。
- **剩余限制**：conflict resolution state machine/UI、merge、可靠后台/进程死亡重投递、SyncAdapter 与设备/OEM 验收仍未完成。
- **验证方式**：纯 common preflight/W15 typed outcome、Desktop bundled SQLite adapter/repository 与 Android host causal worker focused tests；未运行 connected/device/ADB、真实 Provider、网络或用户数据库。

### S26b：durable 冲突证据成对只读观察

- **状态**：已采用；只增加内部账号绑定的只读观察边界，不启动 resolution 或可靠恢复。
- **选择**：新增 internal `ScheduleCalendarConflictReadAccess` 与不可变 `ScheduleCalendarConflictEvidencePair`。Room repository 从其构造时冻结的账号派生读取范围；调用方只能按 typed platform 取得完整 `CONFLICT` link/evidence 对，不能传入账号或取得 DAO、Store、数据库、entity、Provider 或写入能力。
- **Room 边界**：`CalendarConflictRoomDao` 新增 typed canonical account/platform `list`；Store 在**同一个** `withReadTransaction` 内读取完整 link/evidence 列表。它要求 evidence→恰好一个 `CONFLICT` link、每个 `CONFLICT` link→恰好一个 evidence，并校验账号、平台、projection/canonical URI、non-null conflictId、双基线及 `updatedAt == detectedAt`；随后用纯 common transition 精确重算 persisted conflict。orphan、duplicate、split-brain、non-CONFLICT 配对或 malformed 行都使整次读取失败关闭，绝不返回部分结果；非 `CONFLICT` link 可共存并被排除，空 scope 返回空列表。
- **副作用与可靠性边界**：该读取不进入 writer mutex，不初始化或推进 generation，不发布 snapshot/Flow/event，不触碰 Schedule graph、outbox、tombstone、Provider、causal receipt/follower、retry 或 replay。它只观察 durable evidence，**不是** conflict resolution、用户决策应用、可靠重放、后台投递或跨 Room/Provider 原子恢复。
- **验证方式**：Desktop host-only bundled SQLite focused contracts 覆盖 typed list account/platform scope、完整 pair、普通 LINKED 共存排除、evidence/link orphan、non-CONFLICT mismatch 与 split-brain fail-closed，以及 repository frozen-account 的零 publication/generation 观察；未访问真实数据库、Provider、用户日历、网络或设备。

### S26c：显式冲突决策的纯预检合同

- **状态**：已采用为 common 纯数据校验；没有 production caller、Room writer、Provider callback、UI 或 resolution state machine。
- **选择**：`ScheduleCalendarConflictResolutionPreflight` 只接受显式 `accountId/conflictId/platform/projectionId + SCHEDULE|CALENDAR` intent、S26b exact durable pair、同账号 Ready `ScheduleSnapshot` 与完整 discovery。intent 的 `accountId` 必须为 canonical nonblank 文本，并逐值等于 pair、snapshot 与 discovery 的账号；它逐值重算冲突、双基线、`updatedAt == detectedAt`、当前本地 projection/revision，并要求唯一 `CONFLICT` match 的 event ref、canonical Calendar fields 与 fingerprint 均仍等于 evidence。
- **失败边界**：iOS/unsupported、malformed、账号不匹配、partial/duplicate、删除/替换事件、第三状态、snapshot account/revision/canonical 漂移或任一 stale 事实只返回 reason-only `Blocked`；不挑选安全子集、不猜测 winner，也不以 projection identity 忽略 event ref 变化。
- **副作用与可靠性边界**：`Ready` 只回传可伪造且会过期的 intent 数据，不是 capability、plan、token、receipt 或写入授权。后续 writer 必须独立 fresh-read/revalidate；本切片不清除/重开 conflict、不改 Room/Provider/Schedule/outbox/tombstone/generation/Flow/event、不创建 ID 或时钟、不 retry/replay，也不涉及 S28 可靠后台。
- **验证方式**：common focused contracts 覆盖两种明确 choice，以及 identity/bases/timestamp/evidence、Ready frozen account、local revision/canonical、unsupported/third-state、complete unique discovery 与 event ref/Calendar/fingerprint 的全部 fail-closed 分支；未运行 device/ADB，未访问真实 Provider、用户日历、数据库、网络或后端。

### S26d：严格的冲突 logical choice payload

- **状态**：已采用为 common 纯逻辑合同；不接入任何执行路径。
- **选择**：新增封闭 `CalendarConflictChoice` 与 canonical account-bound `CalendarConflictChoiceIntent`；S26c 直接复用该 intent。`CalendarConflictChoiceRecord` 仅保存明确 `SCHEDULE`/`CALENDAR` choice 和完整 expected link/evidence pair；schema v1 codec 把既有 `CalendarCanonicalBaselineCodec` 与 `CalendarConflictCodec` 的 canonical 输出作为 JSON string 嵌入，不复制内部字段格式。
- **严格边界**：外层与两个嵌入 payload 都拒绝 unknown、missing、duplicate、重排、alias、非 canonical 文本与错误版本。logical transition 在没有既有 record 时只返回 `ToRecord`；只有 link、evidence 和 choice 全部逐值相等才返回 `AlreadyRecorded`；反向 choice、identity/pair 不匹配、非 Android 或任一 canonical URI、双基线、时间、冲突重算/字段不一致均 reason-only `Blocked`。
- **副作用边界**：payload 不含时间戳、ID、generation、receipt 或可执行计划；不会默认选择 winner、自动 merge 或应用 choice。调用方仍必须独立重新读取并验证全部事实。
- **验证方式**：focused common contracts 覆盖 deterministic canonical encoding、嵌入 codec 严格解码、完整 pair 幂等、反向 choice、mismatched evidence，以及 S26c 共享 intent 的直接复用。

### S26e：durable choice intent 的独立 Room CAS

- **状态**：已采用；只持久化 S26d logical choice intent，未接线 choice 应用、resolution/UI、后台投递或生产调用方。
- **选择**：Room schema v2 新增独立 `calendar_conflict_choice(account_id, conflict_id, choice_payload)`，复合主键固定 account-bound conflict identity。`choice_payload` 仅保存严格 `CalendarConflictChoiceCodec` 输出；mapper 必须逐项交叉验证 SQL account/conflict、payload intent 与 payload 内 exact Android `CONFLICT` link/evidence pair。DAO 只暴露 `INSERT OR IGNORE` 和 scoped strict `find`，没有 update/delete/upsert/list。唯一 1→2 migration 只建表，并注册到 Android/Desktop/iOS builder；不回填、不改写旧表或 Settings。
- **CAS 边界**：独立 Store writer transaction 在任何 DAO I/O 前验证 bound canonical Android intent；随后同一 transaction fresh-read exact current link/evidence pair 与 existing choice，调用 S26d transition。只有 `ToRecord` 才 insert-ignore，之后 strict reread 后再决策；同完整 winner 返回零写 `AlreadyRecorded`，反向 choice 或 identity/pair mismatch 返回零覆盖 `Blocked`。独立 account-bound repository writer/read sibling 分离 broad repository authority；read 必须在同一只读 transaction 先 fresh-read 当前 link/evidence 并逐值匹配请求 pair，才 fresh inspect；旧 pair 已缺失、终态或替换时 fail-closed，`null` 只表示当前 active pair 未见 row，unknown commit 不能被自动 retry/replay/compensate。
- **副作用边界**：不 apply choice，不 resolve/clear/reopen conflict，不改 link/evidence/baselines/Schedule/outbox/tombstone/sync state/generation，不创建 ID、时间、receipt 或 token，也不发布 snapshot/Flow/event、不调用 Provider。该 append-only intent table 不是 resolution state machine 或第二份 pair 真相。
- **验证方式**：Desktop bundled SQLite focused contracts 覆盖 v1→v2 仅建表且旧行保留、DAO strict mapper/insert-ignore、独立 writer CAS 的初写/幂等/反向及 mismatch 零覆盖、retained choice 面对已替换 link/evidence pair 的 fail-closed inspect、无 generation 副作用和 repository frozen-account sibling 的零 publication；未访问真实数据库、Provider、用户日历、网络或设备。

### S26f：durable choice 的纯应用计划

- **状态**：已采用；只新增 common zero-I/O application planner，不接线 Room、Provider、Schedule、UI 或运行时 executor。
- **选择**：planner 只接受 nullable durable `CalendarConflictChoiceRecord`、当前 exact conflict pair、Ready `ScheduleSnapshot` 与完整 discovery；唯一 choice 来自 durable record。它复用 S26c freshness preflight，阻断 missing、malformed、stale 或 mismatched facts，并仅返回 reason-only `Blocked`、完整五字段的 `ScheduleWins` 或完整五字段的 `CalendarWins`。
- **严格边界**：ready plan 冻结 durable record、current pair、Schedule revision、calendar identifier、event ref、Provider canonical fields 与 fingerprint；它只是未来 expected-facts 比较数据，不是 writer token、capability、authorization、receipt、retry/replay/recovery 机制或执行证明。
- **后续门禁**：ScheduleWins 与 CalendarWins executor 必须分离实现，并在各自写入边界 fresh-read/revalidate 全部事实；不自动合并或局部复制字段，不清除/解决 conflict。UI、可靠后台、进程死亡重投递与设备/Provider 验收继续阻断。
- **验证方式**：focused common contracts 覆盖 no-choice、两侧选择、五字段整体复制、snapshot/discovery stale block、durable record/pair mismatch、无 partial merge 与 expected facts 冻结；不访问真实 Provider、数据库、网络、设备或用户日历。

### S26g：choice 应用计划的纯等值重验证

- **状态**：已采用；只新增 common zero-I/O、capability-free revalidation，不接线 executor、Room/Provider/运行时、UI 或可靠投递。
- **选择**：`ScheduleCalendarConflictChoiceApplicationValidator` 以非空白 bound account 作为纯数据路由约束；它接收完整 requested intent、caller-supplied S26f candidate、fresh nullable durable read result、current exact pair、Ready snapshot 与 complete discovery。requested intent 必须与 bound account 相等；只在 fresh record 非空时，才要求其 intent 在身份与 choice 字段逐值相等。
- **严格边界**：validator 只重新调用既有 S26f planner，绝不复制或放宽 S26c/S26f 语义。fresh read 缺失 choice 必须原样进入 S26f 的 `NoDurableChoice`，再统一映射为 reason-only `Blocked`；只有重新计算得到同一 sealed winner branch，且 target 与完整 expected facts（durable record、pair、revision、calendar/event identifier、Provider fields/fingerprint）均与 candidate data-equal 时，才回传新算 canonical plan；candidate/new `Blocked`、任一事实或分支变化均 reason-only `Blocked`。
- **副作用与可靠性边界**：成功数据仍可伪造、会过期，不是 token、命令 capability、authorization、receipt、执行证明、live handle、retry/recovery 或 writer authority；choice 不执行，不清除/解决 conflict。
- **验证方式**：common host-only focused contracts 覆盖两个 winner、target/expected-facts 所有字段、账号/intent/choice 失配、candidate/branch 阻断，以及 fresh snapshot/discovery/pair 由既有 S26c/S26f fail-closed；未访问真实 Provider、数据库、网络、设备或用户日历。

### S26h：冲突 choice 的纯终态收敛提案

- **状态**：已采用；本节记录的 S26h 仍只新增 common zero-I/O terminal-transition contract，本身不接线 writer、Provider/Schedule executor、runtime、UI、可靠投递或物理清理策略。S206-02 已在其外侧单独实现 production-uncalled 的 ScheduleWins Room-only terminalizer。
- **选择**：`CalendarConflictResolutionTransition` 提供两个强类型入口：只接受 S26g `RevalidatedScheduleWins` 的 ScheduleWins 入口，以及只接受 `RevalidatedCalendarWins` 的 CalendarWins 入口；不提供按 choice 枚举分支的通用 executor。每个入口接收 fresh durable choice、fresh exact active link/evidence pair、调用方提供的完整写后确认事实与 `resolvedAt`，只返回 reason-only `Blocked` 或 capability-free terminal proposal。
- **严格边界**：转换重新复用 S26d 的 `requireSupportedExactConflictPair`；fresh choice/pair 必须与 typed S26g plan 内嵌 expected facts 完全相等，choice 必须与入口方向一致，且 `resolvedAt >= conflict link.updatedAt`。ScheduleWins 保留选中的 Schedule 五字段和 revision，并以完整 Schedule target 收敛 Calendar；CalendarWins 保留选中的 Provider fields/event ref/fingerprint，并要求 confirmed Schedule revision 严格超过 expected revision，但不决定 operation ID 或具体递增策略。双方均拒绝 partial merge。
- **终态与保留边界**：proposal 只提出 `LINKED + conflictId == null` 的完整收敛 link，更新双基线、confirmed revision、event ref、fingerprint 与时间；账号、平台、projection、calendar identifier、remote cursor、last outbound operation ID 及其他 metadata 必须不变。proposal 同时携带 exact active evidence record；作为 S26h 的历史纯合同，它不决定 evidence 是 delete、archive、FK 还是 GC，也不决定 durable choice 的保留期限。S206-02 已在独立 Room transaction 中采用 exact-delete active evidence + retained append-only choice，但仅实现 ScheduleWins 且 production-uncalled。
- **能力与执行边界**：proposal 的构造限制仅减少随手拼装，不构成 provenance/security。proposal 不是 token、receipt、command、authorization 或 writer input；历史纯 S26h/S26i 合同不选择物理策略。S206-02 的 production-uncalled ScheduleWins Room-only terminalizer 已固定 exact-delete active evidence + retained choice；#237 又在单个 Store transaction 内提交 CalendarWins Schedule graph/PATCH outbox，并复用 S26h exact-CAS/delete 收敛 link/evidence、保留 choice。#238 construction-bound 窄 capability 已实现，但只暴露既有 Room operation，且不泄漏 CalendarWins receipt/内部 evidence；#239/S206-07 已在该 surface 外侧实现 exact-session 手动 CalendarWins executor；本节当时仍未实现的 Provider effect 与 #240 ScheduleWins execution/recovery 已由 D-049 完成，#241 协调边界由 D-050 完成；runtime/UI、可靠投递、自动 retry/replay/compensation 与 #206 闭环仍未实现；没有新增 `RESOLVED`、`APPLYING`、`PENDING` 或 `RETRYING` link state。
- **验证方式**：common host-only focused contracts 覆盖两条 winner、五字段整体收敛、expected plan facts、choice/pair stale/replace/malformed、confirmation/revision/ref/fingerprint/time fail-closed，以及 exact evidence retirement 与 choice 保持；未访问真实 Provider、数据库、网络、设备或用户日历。

### S26i：已选择冲突的纯 no-write 当前收敛分类

- **状态**：已采用；只新增 common zero-I/O classifier，不接线 runtime、Room terminalizer、Provider/Schedule executor、UI、回调、重试、恢复或 durable state。
- **选择**：`ScheduleCalendarConflictNoWriteConvergencePreflight` 只接受 fresh durable choice、fresh exact active `CONFLICT` pair、Ready snapshot 与 complete discovery，并只返回 reason-only `Blocked` 或带普通 current facts 的 typed `ScheduleWins`/`CalendarWins`。它不重新选择 winner、不给第三值或字段 merge 留入口。
- **严格边界**：先逐值验证 strict pair 与 durable choice/pair equality。ScheduleWins 仅当当前 Schedule 五字段仍等于 evidence 的 Schedule target 且 revision 未变，并且唯一当前 observation 已完整复制该 target 时成立；Provider ref/fingerprint 可以只是新的当前观察。CalendarWins 仅当 Provider 五字段、原 event ref 与原 fingerprint 均未漂移，且当前 Schedule 已完整复制 Calendar target 并严格超过 evidence revision 时成立。missing/partial/duplicate/replaced discovery、错误账号/平台/projection/choice、第三值和局部合并均 fail-closed。
- **终态与保留边界**：S26i 不产生 `TerminalProposal`，不能清除或结算 link/evidence/choice/baseline，不能替代或放宽 S26c/S26f/S26g/S26h 的历史 evidence expected-fact guards。历史纯 S26h/S26i 合同不选择物理保留策略；S206-02 已固定 ScheduleWins Room-only exact-delete + retained choice，#237 已另行实现 CalendarWins Schedule/PATCH/outbox 与 terminal link/evidence/choice 的单 Store transaction 提交。#238 construction-bound 窄 capability 已实现，但自身不执行 Provider effect 或 runtime；#239/S206-07 与 #240/S206-08 已在该 capability 外侧分别提供手动 CalendarWins 与 ScheduleWins executor，#241/S206-09 再以严格 choice-record/dispatch 协调二者；runtime/UI、可靠投递、自动 retry/replay/compensation 与 #206 闭环仍未实现。
- **验证方式**：common host-only focused contracts 覆盖双方有效收敛、五字段整体 target、账号/choice/pair mismatch、revision/ref/fingerprint drift、missing/partial/ambiguous/replaced discovery、第三值与无 terminal/execution surface；未访问真实 Provider、数据库、网络、设备或用户日历。

### S206-01：终态 expectation、unknown outcome 与 ScheduleWins recovery 纯合同

- **状态**：已采用；仅新增 pure common contracts 与 focused common tests，当前没有任何 production/runtime caller。
- **结果词汇**：执行结果只允许 `Completed`、exact `AlreadyTerminal`、stable reason-only `Blocked`、`ProviderEffectUnknown` 与 `RoomCommitUnknown`。这些值不携带 repository、durable row、Provider ref/fingerprint、`TerminalProposal`、winner capability、retry permission 或 writer authority；unknown 只能触发显式精确重读，不能自动重放。
- **terminal expectation/inspection**：`ScheduleCalendarConflictTerminalInspection` 只从 retained historical exact pair/choice、fresh winner-specific current facts 与注入时间推导唯一 terminal link。允许变化仅限双基线、Schedule revision、Provider ref/fingerprint、`state/conflictId` 与 `updatedAt`；所有无关 `CalendarLink` metadata 保留。fresh durable state 只有两种完整解释：原 exact active pair + unchanged choice，或 exact `LINKED + conflictId == null` terminal link + active evidence absence + unchanged retained choice；stale/replaced/malformed/partial/third state 全部 reason-only 阻断。它不接受或返回 S26h `TerminalProposal` 作为 authority。
- **ScheduleWins recovery**：`ScheduleCalendarConflictScheduleWinsRecoveryPreflight` 直接消费 historical exact pair/retained choice、Ready snapshot 与 complete discovery，不消费 S26i output。只有 durable `SCHEDULE` choice 仍锚定 exact pair、selected Schedule revision/完整投影未变，且 complete discovery 每条 match 的 W48 v2 managed-calendar identity 都精确保持、ref/fingerprint 均非空白时才继续；Provider 等于 evidence old fields + old fingerprint 只返回 `StillActive`，完整复制 selected Schedule target 且 fingerprint 已变才返回 ordinary `Converged` facts。五字段 partial、第三值、identity/ref/fingerprint malformed 或不一致均阻断。
- **持久化实现与未实现项**：S206-02 已实现 **exact-delete active evidence + retained choice** 的 ScheduleWins Room-only terminalizer；S206-01 本身仍不改 Room/DAO/schema/migration，也没有 terminal commit、retry loop、Provider/Schedule executor、Provider/Room atomicity、publication、UI 或 completion claim。历史 S26h/S26i/W45 文字继续按各自当时范围理解；只有 S206-02 的 production-uncalled Room foundation 已接线，不能倒写为完整 #206 解决闭环。
- **验证方式**：focused desktop common tests 覆盖双方 terminal expectation、metadata preservation、evidence absence/choice retention、stale/replaced/malformed/partial/third state、timestamp/revision regression、ScheduleWins-only recovery、W48 identity/ref/fingerprint、五字段整体 convergence、old-state versus target-state 与无 authority-bearing execution surface；未访问真实 Provider、Room、网络、设备或用户数据。

### S206-02：ScheduleWins 精确 Room-only terminalizer foundation

- **状态**：已采用并实现；仅提供 production-uncalled 的 noWeb Room writer foundation，不接 Provider/runtime/recovery/UI，也不声明 #206 完成。
- **输入与重验证**：唯一入口只接受账号 scope、typed S26g `RevalidatedScheduleWins`、ordinary confirmed post-effect Provider facts 与 `resolvedAt`；不接受 `TerminalProposal`、raw DAO/entity、winner enum 或 caller-built terminal link。独立 `withWriteTransaction` fresh-read exact Android link、exact evidence 与 append-only choice，逐值匹配 winner expected facts 后重新运行 S26h，proposal 只作为本事务内普通校验输出。
- **精确写入**：`CalendarLinkRoomDao` 以完整账号/平台/canonical projection identity + expected canonical payload 做 CAS，并写完整 S26h terminal payload；`CalendarConflictRoomDao` 以完整账号/conflict/platform/projection identity + canonical payload 物理 exact-delete active evidence。两步都必须恰好影响一行；choice 不更新、不删除。link 成功后任一 delete/read-back/unresolved mismatch 通过私有回滚信号撤销整个 transaction。
- **提交后事务内核验**：terminal link 必须逐值等于 expected `LINKED + conflictId == null`，exact evidence 必须缺失，raw retained choice 必须完全不变，既有 unresolved reader 必须成功且不再返回 retired pair；无关 rows 保留。
- **副作用与 unknown 边界**：不新增 schema/migration/FK/link state，不使用 generic Store transaction，不初始化 sync state、不推进 generation、不写 Schedule/outbox/tombstone、不发布 snapshot/Flow/event。mapper、trigger、普通异常、取消与 commit 后丢失返回值原样传播并保持 unknown；禁止自动 retry/replay/compensation，不签发 receipt。
- **验证方式**：bundled SQLite focused Desktop contracts 覆盖成功与允许 metadata 保留、evidence 删除/choice 保留/unresolved 空缺、无关 rows、missing/stale/replaced pair、opposite/malformed choice、changed confirmation、时间回退、CAS/delete row-count failure，以及 evidence-delete trigger 在 link update 后拒绝时的整体回滚。另在 link exact-CAS 后、evidence retirement 前的确定性挂起缝取消，断言 `CancellationException` 原样传播、原 link/evidence/choice 完整回滚且无 sync-state/generation/Schedule/outbox/publication 副作用。未访问真实 Provider、设备、生产数据库或用户数据。

### S206-03：exact terminal inspection 与显式 ScheduleWins recovery foundation

- **状态**：已采用并实现；仅新增 production-uncalled 的 noWeb Room exact inspector、一次性显式 recovery helper 与窄 Store façade，不接 runtime、UI、Provider gateway 或 CalendarWins execution。
- **AlreadyTerminal 唯一来源**：separate read-only inspector 在同一 Room read transaction 内 strict-read 当前账号/Android 平台完整 `CalendarLink` 与 conflict-evidence 集合、retained append-only choice 及所需 Schedule facts，并复用 `validateCalendarConflictEvidencePairs` 验证完整 unresolved 集合，之后才按 conflictId 从 choice 恢复 historical exact pair。既有 `inspectCalendarConflictChoice` 继续先要求当前 exact active link/evidence，terminalization 后不会因 retained choice 而放宽。exact active pair + unchanged choice 返回 `Active`；`AlreadyTerminal` 还必须从 fresh Room Schedule 与调用方 ordinary discovery 证明 winner-specific complete convergence，再由 S206-01 推导 current link 的完整 expected terminal payload，并同时要求 `LINKED + null conflictId`、active evidence absence、unchanged choice，且历史 projection/conflictId 不在已验证 unresolved 集合。missing/changed choice、orphan evidence、任一配对/账号/平台/identity mismatch、同 projection 不同 conflictId、replacement evidence、link-only partial、third state、wrong projection/metadata、stale convergence、mapper/读取异常都只能 stable reason-only indeterminate。
- **显式恢复顺序**：一次用户调用先运行 exact inspector；已 terminal 完全零写。只有 exact active 才在新的 Room read transaction fresh-read pair/choice/current Schedule，并把 caller-supplied fresh ordinary discovery 交给 `ScheduleCalendarConflictScheduleWinsRecoveryPreflight`。`StillActive` 保持 active 且零写；partial/third/opposite/W48 replacement/revision drift/stale pair 均阻断；只有 `Converged` 才构造与历史 S26g expected facts 等值的 typed ScheduleWins wrapper，并最多一次调用 S206-02 exact CAS/delete。该路径没有 Provider mutation 或 gateway reachability。
- **副作用与 unknown**：不新增 schema/migration/FK/link state，不改 choice，不经过 generic Store writer，不初始化或推进 sync generation，不写 Schedule/outbox/tombstone，不发布 snapshot/Flow/event。S206-02 首次写 SQL 后的全部 controlled mismatch 继续由私有 rollback signal 撤销；delete trigger/普通异常保守映射 `RoomCommitUnknown`，取消原样传播，lost return 只能由后续显式 exact inspector 确认，禁止 loop、worker、receipt、phase table、自动 retry/replay/compensation。
- **未实现项**：#237 已由 S206-04 在 production-uncalled 的单个 Store transaction 内提交 CalendarWins Schedule graph/PATCH outbox，并终态化 link/evidence、保留 choice；它不改变本节的 Room-only ScheduleWins recovery 边界。#238 construction-bound 窄 capability 已集成；#239/S206-07 与 #240/S206-08 已在其外侧分别实现 exact-session 手动 CalendarWins 与 ScheduleWins executor，#241/S206-09 再以严格 choice-record/dispatch 协调二者；仍没有 production caller、session runtime/UI、可靠后台、跨存储 atomicity 或 #206 completion claim。
- **验证方式**：bundled SQLite Desktop contracts 覆盖 ScheduleWins/CalendarWins exact terminal、exact active、partial/orphan/missing/changed choice、wrong projection/metadata、stale Schedule/Provider convergence、unrelated rows与 mapper failure；recovery 覆盖 converged 单次提交、already-terminal no-rewrite、old-state no-action、third/opposite/W48 replacement/revision drift/stale pair、delete trigger rollback、lost return unknown 与 cancellation no replay。全部只使用临时数据库和 ordinary discovery，未访问真实 Provider、设备、生产数据库或用户数据。

### S206-04：CalendarWins candidate 与 Room 原子提交基础

- **状态**：已采用并实现；#236 提供 production-uncalled 的 pure common materializer，#237 在其外侧新增 production-uncalled 的 noWeb Room writer。两者均未接 production caller、Provider gateway、session capability、runtime 或 UI。
- **输入与 fresh 重验证**：writer 只接受 account scope、S26g `RevalidatedCalendarWins`、ID generators 与 clock；入口先校验账号和 Android 平台绑定，再在唯一 `ScheduleRoomStore.transaction` 内 fresh strict-read 当前 Schedule/exception graph、exact active link/evidence 与 retained `CALENDAR` choice，逐值匹配调用方 winner 后重新运行 materializer。caller 不能提交 `TerminalProposal`、raw entity 或预制 committed state。
- **正常 Schedule 写入**：同一 transaction 固定一个 `commitAt`，显式阻断 revision overflow，以 `revision + 1` 构造正常 `ScheduleCommand.Update`；只接受 reducer 产生的一个完整 graph replace 与一个 outbox merge。PATCH payload/base revision、categories、exceptions、tombstones、其他资源 mutation 与 reminder identity 继续遵循正常 local-command reducer/Store 语义。
- **事务内终态收敛与 receipt**：writer strict read-back committed graph、canonical Schedule fields 与完整 outbox，再以这些当前事实重跑 S26h；随后 exact-CAS terminal link 为 `LINKED + conflictId == null`，物理 exact-delete active evidence、保留 append-only choice，并确认 unresolved pair 不再出现。成功 receipt/evidence 中的 graph、outbox、terminal link、choice、generation 与 device facts 全部来自同一 Store writer scope。
- **回滚、unknown 与跨存储边界**：link CAS、evidence delete、strict read-back 或 unresolved mismatch 都通过私有受控信号整体回滚，包含 trigger 旁写；取消、SQLite/mapper/普通异常和 lost-return commit 原样传播，不自动 retry/replay/compensate。事务不访问 Provider，Room 与 Provider 不共享 transaction、CAS、provenance 或 cross-store atomicity。
- **后续边界**：#238 窄 production capability 已集成，但它不执行 Provider effect、不产生 runtime/可靠投递，也不泄漏 internal receipt/evidence。#239/S206-07 与 #240/S206-08 已分别实现 exact-session 的手动 CalendarWins/ScheduleWins executor，#241/S206-09 已实现严格 choice-record/dispatch 协调器；runtime/UI、可靠后台、真实 Provider 验收与 #206 completion 仍未实现。不得把 #237 Room foundation、#238 capability 或 #239–#241 手动链解释为完整 conflict resolution 闭环。
- **验证方式**：pure common contracts 继续覆盖 candidate expected-facts；bundled SQLite Desktop contracts 覆盖正常 PATCH metadata/outbox/strict readback、stale graph/link、exact-CAS trigger 旁写整体回滚、Schedule strict-read rollback、取消与普通异常单次传播。测试只使用临时数据库并清理 DB/WAL/SHM；未访问真实 Provider、设备、生产数据库或用户数据。

### W40：拒绝任意 Room reader 与任意 Provider reader 的拼接

- **状态**：已采用。
- **背景**：后续冲突观察可能同时需要 durable Room facts 与 Android Provider snapshot；但 S26i、OBS-RO-1 和 W40 的职责、authority 与生命周期均不同。
- **候选方案**：将一个任意 Room reader 与一个任意 Provider reader 由调用方组合；或只发放 session-bound、Android-only 的窄 Provider snapshot reader。
- **选择**：拒绝前者。W40 只发放 private-construction 的 `AndroidAccountSessionManagedCalendarSnapshotReader`：issuer 接收 `Context`、`IAccountService`、exact `AccountSession`、账号 `CoroutineScope` 与冻结 `CalendarExportScope`，仅从 session 派生 accountId、保留 application context 并内部构造 W39 acquirer，签发时零 I/O。reader 只提供 `read(expectedSession)`，以 `===` 和 account lifecycle gate fail-closed；同一 gate 会传入 W39 的每个 Provider/Cursor 读取边界，ordinary snapshot 复制也在外层 post-copy 复核之前完成。
- **理由**：任意 reader 拼接会让调用方自行选择不同 account/session/scope、不同读取时刻与不受约束的 authority，既不能建立两端事实的共同 provenance，也不能把独立 Room/Provider 操作伪装成跨 store atomicity。窄 W40 capability 先限制 Provider 侧的 session、scope、I/O 时点和输出形状，但不声称解决这些跨存储证明问题。
- **影响范围（W40 当时）**：W40 在该切片完成时是 Android-only、未接线、未消费的读能力；不是 OBS-RO-2、facade 或 S26i runtime，不引入 Room aggregate、gateway/registry、reconciliation、callback/Flow/controller/coordinator、写入、terminalization、retry/replay、publication 或 command/token/receipt/proposal/choice artifacts。当前 W40 仅由 W45b/#242 设置页的固定 one-shot 只读序列消费；W40/W45b 仍没有独立后台或自动 runtime wiring。
- **验证方式**：Android JVM host contract 直接调用 production issuer，覆盖签发零权限/Provider I/O、session accountId 派生、仅保留 application context 的 transitive 闭包、foreign/同账号新 generation 在 fake 权限/Provider 操作前拒绝、scope freeze、Provider query 后 lifecycle revoke 的后续 I/O 截止、ordinary copied output 与 reflection capability shape；未访问真实 Provider、用户日历、数据库、网络或设备。
- **未证明事项**：Room/Provider 联合 provenance 与跨 store atomicity 仍未证明；任何未来组合必须另设统一 authority、freshness/provenance contract 及明确的非原子边界。

### W41：Room OBS-RO-1 以 exact session 作为唯一直接签发前置

- **状态**：已采用；仅实现 noWeb `RoomScheduleRepository` 内部 direct issuer 与临时 bundled SQLite host contract，不接线任何 runtime。
- **背景**：OBS-RO-1 既有 view 虽然在构造时冻结账号，但其 internal access property 可被同模块任意路径直接取得，不能表达调用方必须仍持有同一 `AccountSession` 原引用的前置。
- **选择**：将 access property 改为 private，新增非 suspend 的 `issueCalendarConflictNoWriteObservationReadAccess(expectedSession)`；仅在 `expectedSession === boundSession` 时返回既有 narrow view。foreign session、同账号新 generation 与结构相等副本一律先抛 `CancellationException`，再不发生任何 Store/DAO/Room 读写、publication 或 generation 操作；view 仅从 exact boundSession 在内部派生唯一 account partition key。
- **理由**：引用身份同时拒绝同账号生命周期替换和可伪造的结构相等 session，又不向 common access interface 增加 probe、callback、token 或 wrapper。私有 view 可确保 Room-only authority 只能从该 issuer 取得。
- **影响范围（W41 当时）**：只影响当时 Room-only、未接线的 OBS-RO-1 前置与 desktop host contract；不新增 Android binding、Room/Provider aggregate、shared provenance、binding、atomicity、currentness、authorization 或 S26i runtime，也不改变当时 W40 未接线/未消费状态。当前 W41 的窄 view 与 W40/W42 都只由 W45b 固定只读序列消费；三者均未获得独立 caller/runtime wiring，W45b 由 #242 设置页显式 one-shot 调用。
- **验证方式**：临时 bundled SQLite host test 覆盖 original session 成功、foreign/同账号新 generation/结构相等副本在访问前取消、签发不改变 fixture facts、publication 或 generation，以及返回 view 保留原有窄读取且不获得广泛 authority；未访问真实 Provider、用户日历、生产数据库、网络或设备。
- **回滚点**：回退本 issuer、对应 host contract 与文档即可；没有 schema、migration、Provider 或外部状态变更。

### W42：Android snapshot 到 discovery 的无授权纯映射

- **状态（W42 当时）**：已采用；该切片仅新增当时未消费的 Android 顶层同步纯映射与 JVM host 合同。当前 W42 仅由 W45b/#242 设置页的固定 one-shot 只读序列消费，仍不是独立 runtime entry；W45b 没有后台或自动 caller。
- **选择**：`AndroidManagedCalendarSnapshot.toCalendarLinkDiscoverySnapshot()` 只把调用方提供的普通 `AndroidManagedCalendarSnapshot`（其形状可以与 W39 输出一致）按原事件顺序映射为普通 `CalendarLinkDiscoverySnapshot`，为 observation 和 `CanonicalCalendarFields`（含 reminder list）分配新值；W39 仍是 production Provider acquisition 的唯一实现。
- **严格边界**：该映射不签发或调用 W39/W40/W45a issue/read，不访问 W41/Room，也不自行调用 discovery 或 S26i classifier；当时没有 runtime/caller。它不形成 source provenance、currentness、共享 scope、跨存储 atomicity 或 authorization；输出只是可能陈旧的 ordinary data。W40 当时仍无 caller，W45a 不消费 W42；后续 W45b 只在自己的固定只读序列中消费 mapper 输出，W40 除该链仍 otherwise uncalled，W42 仍不是 runtime entry point。
- **排除范围**：不新增 controller、coordinator、initializer、callback、Flow、retry、replay、holder/facade/wrapper 或生产 wiring；lane-02 不在本切片范围。
- **验证方式**：Android host-only contract 只使用内存值，覆盖 absent、Present 字段/顺序、canonical/reminder 深复制及 JVM 单 receiver/无 Continuation、callback、保留字段 shape；未访问 Context、权限、Provider、Room/数据库、设备、用户日历或网络。

### W45a：production Room acquisition 的当前边界

- **状态**：已采用；本节补充 W41 历史 direct issuer 的当前 production-factory 使用边界，不改写 W41 原始决策。
- **选择**：Android-internal `issueProductionScheduleCalendarConflictNoWriteObservationReadAccess(expectedSession)` 仅以 dedicated、lazy `RoomScheduleRepositoryFactory` 取得 temporary broad `RoomScheduleRepository`，随后立即调用既有 W41 exact-session issuer，返回其窄 no-write view；broad repository 不逃逸、不缓存。
- **理由**：factory 只共享进程 Room database resource 与稳定 Android device ID，不宣称与既有 façade 共享 transaction、provenance 或 currentness；通过 W41 立即降格，避免把 broad repository 扩展为观察或执行 authority。
- **副作用与能力边界**：remote gateway unavailable、initialized hook no-op。首次签发会按 lazy 语义初始化 dedicated factory/database owner，并构建 Room database resource；但不调用 `RoomScheduleRepository.initialize()`、initialized hook 或 export initialization，不注册 export hook，不读写 Store/DAO、不发布 snapshot/event、不推进 generation、不读 Provider、不执行 W42/discovery/S26i、也不启动工作。签发既不暴露、签发或消费 authorization，返回的窄 view 不具有 authorization authority。Room/Provider 观察仍非原子，不能建立共享 provenance、currentness、scope 或 authorization。
- **接线边界（W45a 历史范围）**：当时 IDE references 无 caller；故 W45a acquisition 本身没有 coordinator、runtime、consumer、UI、controller、initializer 或后台 wiring，也没有 observation flow。W40 当时仍无 caller；W42 不 issue/read/consume W45a。此历史记录不预先宣称 W45b 的实现状态；其后续已实现的只读协调器及仍未接线边界见 W45b 记录。
- **验证方式**：以 exact-session issuance 与 factory 边界的 host contracts、IDE reference 查询及静态检查为限；未访问 Provider、日历、网络或数据库。
- **回滚点**：同步回退 production issuer、dedicated factory 边界与本 current-boundary 记录；不触发 Store/Provider 外部状态变更。

### W45b：以固定窄读取顺序实现手动 conflict observation coordinator

- **状态**：已采用；`AndroidManualScheduleCalendarConflictObservationCoordinator` 已实现并有 Android JVM host-only contracts。本记录的“没有 production caller”是 W45b 当时历史边界，当前设置页 wiring 已由 D-053 supersede。
- **背景**：W41/W45a 只取得 exact-session 的 Room observation capability，W40/W39 只提供 session-bound Provider snapshot，W42 只做 snapshot-to-discovery 纯映射；仍需要一个不会扩大 authority 的方式，把这些现有只读事实按可审计顺序交给 S26i。
- **选择**：coordinator 只在 exact `AccountSession`、scope 与 owner lifecycle gate 均当前时构造并观察。固定顺序为 active conflict pairs；空集即时返回；Room current snapshot；Android links；恰好一次 W40 的 W39-backed Provider snapshot；W42 mapper 加 `CalendarLinkDiscovery`；对每个 pair 作 final durable-choice inspect；S26i no-write classification。每个 suspend 边界前后复核 cancellation 与 lifecycle，漂移/撤销以 `CancellationException` fail-closed，且不得开始下一项读取。
- **结果与能力边界**：只返回不可变 ordinary per-conflict outcome：choice absent、observed Schedule target、observed Calendar target 或 reason-only blocked。结果不持有 target fields、snapshot、DAO/Cursor、reader、gate 或可变集合；不提供写入、冲突 terminalization/resolution、snapshot/event publication、retry/replay、callback/Flow、background task、UI/controller/initializer/worker/SyncAdapter、authorization 或行动 capability。Room 与 Provider 仍无 shared transaction、provenance/currentness proof 或跨存储 atomicity。
- **不接线选择（历史）**：W45b 落地时 actual reference scan 只找到 coordinator 的 defining source 和 host test；当时没有 UI、controller、initializer、worker、SyncAdapter、background runtime、callback/Flow registration 或 production observation-flow caller。D-053 只 supersede 当前 UI wiring，不把本结果升级为后台 conflict runtime、bidirectional sync 或 runtime authorization。
- **权限门禁（历史与当前）**：W39 的 snapshot reader 始终同时要求 `READ_CALENDAR` 与 `WRITE_CALENDAR`。W45b 当时因此未接线；D-053 后续只在两项权限均已授予时由设置页显式调用，并未拆分或降低权限合同。
- **验证方式**：host-only fakes 覆盖 mandated order、empty fast return、exact-session/lifecycle cancellation、Provider failure 不 retry、不可变/窄 result surface 与 Schedule/Calendar/absent/blocked outcome mapping。未运行 device、真实 Calendar Provider、真实 Room database 或其他真实外部行为。
- **回滚点**：回退 coordinator、其 host contract 与本记录即可恢复 W45a-only acquisition 边界；不触发 Store/Provider 外部状态变更。
- **关联事实源**：[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[当前创建、更新与同步数据流](schedule-v2-current-data-flow.md)、[总路线图](schedule-v2-calendar-roadmap.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### W48：以 CAL_SYNC1 UUID 固化 Android Calendar row 世代身份

- **状态**：已采用并由 `4007b5416` 集成；本节明确 supersede D-036/W47 的 row-only v1 历史边界，不将 v2 倒写为当时已经存在的能力。
- **背景**：Xiaomi 可重用被删除的 `CalendarContract.Calendars._ID`。历史 `android-calendar-row:v1:<id>` 只能定位数字 row ID，不能证明当前 row 是先前受管物理 row 的同一 incarnation。
- **选择**：当前严格身份为 `android-calendar-row:v2:<positive-row-id>:<canonical-lowercase-uuid>`。每次创建 managed LOCAL Calendar 时生成 per-creation UUID，并仅在 sync-adapter-owned `CAL_SYNC1` 持久化；`ACCOUNT_NAME`、`ACCOUNT_TYPE_LOCAL` 和名称仍只用于定位候选，不能替代 incarnation 证明。
- **失败关闭与迁移边界**：v1 durable identifier、tokenless/malformed row、缺 marker、uppercase/noncanonical UUID 全部 fail-closed。禁止 read-time backfill、silent durable-link rewrite 和 automatic migration；如需恢复旧/损坏关联，只能另行授权明确的 relink/reset 流程。
- **执行边界**：strict snapshot 在 Event/Reminder 读取完成后复验完整 identity；finalized Create 在 `beforeInsert` 后复验，finalized Update 比较完整 identity；cleanup 对每个枚举行复验，并使用一个 token-conditioned Provider batch 与 `expectedCount`，使 token 漂移时事件删除整批回滚。它们是 best-effort preflight/read-back gates，不是 Provider CAS，也不提供跨存储原子性。
- **W48a 范围**：只修正 Xiaomi item-URI + selection incompatibility 与 Deadline projection-kind precedence 的测试 fixtures；production Update shapes 与 timing inference 保持不变。
- **验证方式**：task branch 与 integration branch 的完整 Android host/desktop suites 及 `compileAndroidDeviceTest` 已通过。当前 loop 未执行新的 device test，12-case Xiaomi Provider suite 仍 deferred；因此不声称真实 runtime Provider acceptance。
- **不变项**：iOS/EventKit 语义，以及 Room schema/DAO 行为均未修改。
- **回滚点**：回滚完整 W48 identity implementation 与其依赖的测试/文档，不能只降级 codec 而保留使用 token identity 的 Provider gates；回滚不触发 relink、Room migration、Provider 清理或任何用户日历操作。
- **关联事实源**：[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[总路线图](schedule-v2-calendar-roadmap.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-046：S205-02 EventKit full-access gateway 以 production-uncalled 方式落地

- **状态**：已采用并集成；以下 production-uncalled、未接设置页/repository 初始化/worker 的描述仅是 S205-02 当时历史边界，后续 #280 设置层与 D-051/#281 runtime 已 supersede 当前 wiring 状态。
- **背景**：在 S205-02 当时，iOS 一期需要在不接线设置页或导出 worker 的前提下，先建立能查询、恢复与精确 reconcile 的 EventKit 平台边界；write-only 无法满足完整扫描、identifier 失效恢复和写后对账。
- **选择**：实现单一真实 `EKEventStore` bridge 的 full-access gateway，构造无副作用，只有显式 `requestFullAccess()` 申请 iOS 17+ full access 或 iOS 15/16 legacy event access。gateway 只接受 selected source identifier 与 canonical scope；以最多 1460 天的 target/recovery 窗口扫描，首个受管 calendar 与 event 以单次 commit 提交，随后 fresh authority reread；任何 source/calendar/event 歧义、撤权、资源消失或无法分类状态均 fail-closed。
- **理由**：避免回退默认日历或按标题接管，并防止 EventKit 单 predicate 四年上限和模糊 commit 终态导致重复 calendar 或错误成功。
- **影响范围**：iOS gateway/bridge、权限用途说明、fake contracts 与专题 EventKit 文档；本切片当时不接线 repository 初始化、设置页、production export worker、通知/后台、occurrence exception 或双向同步。后续 #280 与 D-051/#281 只 supersede 当前 wiring 状态，不倒写本切片当时的范围。
- **验证方式**：fake contracts 覆盖显式权限、selected-source/canonical scope、1460 天窗口、原子首次提交、fresh reread 与 fail-closed；`iosSimulatorArm64Test` 和 metadata 编译通过。它们不验证真实系统权限、iCloud/source、真实 EventKit store 或真机行为。
- **回滚点**：回退 S205-02 的 gateway/bridge、Info.plist、fake contracts 与专题文档；没有 production caller 或真实用户日历副作用。
- **关联事实源**：[iOS EventKit 导出设计](schedule-v2-calendar-ios-eventkit.md)、[总路线图](schedule-v2-calendar-roadmap.md)。

### D-048：#239 以双轮只读重验证收口 CalendarWins 手动执行

- **状态**：已采用并集成；production-uncalled、construction-bound/exact-session 的手动 CalendarWins executor，不构成 #206 completion。
- **选择**：每次执行严格两轮完整只读事实序列：exact pair → current Schedule snapshot → 完整 Android `CalendarLink` → W40 Provider read-only snapshot → 完整 discovery → pair-bound choice。首轮仅接受 CalendarWins S26f candidate；第二轮必须得到 `RevalidatedCalendarWins`，其后至多调用一次既有 #237 Room command。
- **副作用与 unknown 边界**：不写 Provider、不 record choice、不执行 ScheduleWins、terminal inspection/recovery 或 post-command 自动检查。command 开始后的普通异常/lost-return 收窄映射为 `RoomCommitUnknown`；`CancellationException` 原样传播；没有 retry、replay 或 compensation。Room 与 Provider 不共享 transaction/CAS。
- **当时未实现项**：本决策落地时没有 production caller、runtime/UI 或可靠投递；后续 executor/coordination 由 D-049/D-050 补齐，当前设置页 caller 由 D-053 supersede。

### D-050：#241 以 strict durable-record 回绑后单方向 dispatch

- **状态**：已采用并集成；production-uncalled，不构成 UI/runtime 或 #206 completion。
- **选择**：协调器只接受 opaque conflictId 与显式 side，从 fresh exact unique pair 派生 canonical choice intent，恰好一次调用 append-only choice writer。只有 writer 返回 record 的 choice、intent、expected link 与 expected evidence 全量回绑本次 pair，才调用 #239 CalendarWins 或 #240 ScheduleWins 中一个 executor。
- **unknown 与非原子边界**：choice writer 调用开始后的普通异常/lost-return 映射 coordinator-local `ChoiceCommitUnknown`，禁止自动 inspect、recover、retry、replay 或 compensation。choice record 与 executor effect 是两个独立步骤，不建立 Room/Provider 共享 transaction、CAS、provenance 或 combined currentness。
- **验证与后续**：Android host 14 个 coordinator contracts 与 #239/#240 共 22 个回归通过；未运行设备或真实 Provider。本记录落地时 #242 UI 尚 pending，当前已由 D-053 supersede；#243 隔离 Provider 验收、#244/#206 completion 仍 pending。

### D-049：#240 以写后完整重读收口 ScheduleWins Provider effect

- **状态**：已采用并集成；production-uncalled。
- **选择**：exact-session executor 执行两轮完整 facts 重验证，仅一次 W48 managed-event update；成功后重新读取当前 Schedule snapshot、完整 Android links、W40 Provider snapshot 与 discovery，只有 ScheduleWins recovery preflight `Converged` 才调用一次 Room terminalizer。
- **unknown 边界**：Provider 调用开始后的普通异常/lost-return 为 `ProviderEffectUnknown`，Room command 开始后的普通异常/lost-return 为 `RoomCommitUnknown`；取消原样传播。任何 unknown 都不自动 retry/replay/inspect/recover/compensate，显式 inspect/recover 仍是独立用户动作。Provider 与 Room 不共享 transaction/CAS。
- **验证与后续**：Android host 15 个 ScheduleWins 与 7 个 CalendarWins 回归通过，Android/common 编译通过；未访问真实 Provider/用户数据。本记录落地时 production caller/UI 尚未完成，当前设置页 caller 由 D-053 supersede；可靠投递与 #243/#244 仍未完成。

### D-047：#238 以 construction-bound exact-session capability 收口 Room 冲突操作

- **状态**：已采用并集成。
- **背景**：#237 已能在单一 Store transaction 提交 CalendarWins 的 Schedule/PATCH/outbox 与 terminal link/evidence/choice，S206-02/03 也已有 ScheduleWins Room terminalizer、inspection/recovery；直接向协调层暴露 repository、Store、DAO 或 transaction receipt 会扩大账号、重放和 Provider authority。
- **选择**：新增内部 `ScheduleCalendarConflictResolutionAccess`，只能从已完成正常 production Room factory `create` 的同一 factory、为 exact `AccountSession` 原引用签发。access 只从冻结 session 派生账号分区，收敛 ordinary snapshot、Android exact active pair、**无参数** `readAndroidCalendarLinks()`（只固定读取该账号 `CalendarPlatform.ANDROID` 的完整 `CalendarLink`，调用方不能选择 platform）、choice read/write、S206-03 terminal inspection/显式 ScheduleWins recovery、既有 ScheduleWins Room command 与 #237 CalendarWins Room command；完整 links 仅用于 #239 构造完整 `CalendarLinkDiscoveryResult`，不能以单 pair 替代，也不泄漏 Store/DAO/repository；CalendarWins receipt 与内部 evidence 被抹除。
- **理由**：construction-bound identity 在 entry、factory 与 Room repository 三层拒绝未构造、外来账号、同账号旧 generation 与结构相等副本，且失败发生在 Store/DAO/Room I/O 前；窄表面避免调用方获得 broad persistence、Provider 或重放 authority，同时复用既有严格 transaction，不复制其 CAS、receipt 或 unknown-outcome 规则。
- **cold 与副作用边界**：cold issuer 不构造 dedicated factory，不读 `BaseApp.getAndroidID()` 或 Settings Provider，不初始化 database、不注册 export、不调 initialized hook，也不启动 dispatcher/background work。access 不暴露 `RoomScheduleRepository`、Store、DB、DAO、Provider、remote gateway、Flow/publication、controller、token 或 raw authority；Provider/Room 仍不共享 transaction/CAS，unknown、取消和 lost-return 不自动 retry/replay/compensate。
- **后续边界**：capability 自身仍无 production caller，也不直接执行 Provider effect、UI/runtime 或可靠投递；#239/S206-07 与 #240/S206-08 已在该 capability 外侧分别提供 exact-session 手动 CalendarWins/ScheduleWins executor，#241/S206-09 再以严格 choice-record/dispatch 协调二者，但 #206 闭环仍未完成。
- **验证方式**：Desktop 6 个 bundled-SQLite contracts 覆盖 exact-session issuance、窄 surface、完整 Android links 的冻结账号/固定平台读取、choice/inspection/recovery 与两条既有 Room command；Android host 1 个 cold-issuer contract 覆盖未注册 factory 时不触碰 Android ID、database、Room、export 或后台工作。仅创建并清理测试临时 SQLite；本次 integration Desktop 6 tests 与 `compileKotlinDesktop` 已通过，未访问设备、真实 Provider 或用户数据。
- **回滚点**：同步回退 production registry/issuer、noWeb access 与 repository/factory issuer、两个 focused contract 以及六份共享 Schedule 真值文档；不得只移除上层 issuer 而保留可绕过的 broad authority。
- **关联事实源**：[当前数据流](schedule-v2-current-data-flow.md)、[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[总路线图](schedule-v2-calendar-roadmap.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-051：#281 以 post-initialize handoff 接入 process-resident iOS 单向 EventKit runtime

- **历史范围**：本段记录 #281 integration 时的存储分流；当前 iOS repository 状态由 D-054 supersede。
- **状态**：已采用并由 `048db97514fc1f5a2c085c11c12d2bae8379429c` 集成。
- **选择**：initializer 在 `LocalFirstScheduleRepository.initializeMutex` 内仅注册 direct repository、exact `AccountSession`/account scope/owner Job 并返回 opaque one-shot handoff；同一次 `initialize()` 正常退出 mutex 后同步 release，stale/replaced/repeated handoff 均为 no-op。单 serialized Full actor + conflated generation 统一处理 `Initialized` 与同账号 `SchedulesCommitted`；`RemoteCommitted` 不触发。
- **门禁**：durable enabled、selected source、FULL_ACCESS、same-account Ready/Recovered、exact session/scope/owner/generation 在 preference/snapshot/EventKit/cache 每个 suspension/effect boundary 前后复核。source/disable 在首次 durable write 前先 invalidate generation 并打开 `explicitIntentPending`；terminal uncertainty 只允许新 session 或后续显式 user intent 恢复。确定性 invalid source/calendar/identity 按 enabled=false → clear calendarIdentifier → clear full ledger 顺序 fail-closed。
- **原子与恢复**：bridge 使用 `PRE_COMMIT → COMMIT_ENTERED → POST_COMMIT_READBACK`；只有 commit-entered unknown 为 `ATOMIC_COMMIT_OUTCOME_UNKNOWN`，普通 pre-commit `AMBIGUOUS` 不得获得 proof。recovery eligibility 仅由 confirmed atomic commit 或 commit-entered unknown 建立，绑定当前 process/store universe/scope/source/projection/fingerprint、gateway issuer 与 proof 对象身份，不可序列化且进程死亡丢失。locator 两阶段持久化后必须 fresh settings exact reread 并 same-gateway acknowledgement；ack 与 retirement 是同一 eligibility 的互斥一次性消费者，携 proof 的 Update/Delete 先 retirement，NoOp 第二次 fresh lookup 后 ack。EventKit 与 cache 不共享 transaction，unknown/cache/ack/retirement failure 不自动 retry/replay。
- **范围**：仅 outbound Schedule → EventKit；无 notification/background/manual-sync/inbound/bidirectional/conflict/occurrence exception。iOS repository 仍为 Settings-backed fallback，#281 不代表 Room/SQL 迁移或 common CalendarLink 三方同步。
- **验证方式**：integration 报告 710 个 in-memory/fake iOS tests，以及 metadata/iOS/common/Android/noMobile/Desktop compilation；pure iOS foundation tests 驱动 production atomic stage machine，fake runtime 覆盖 snapshot/preference/store/cache late completion。未构造 `EKEventStore`、未访问用户日历。#282 的真实 full-access、source/iCloud、真实 store commit/readback、atomic recovery、专用测试日历和真机验收仍待完成。
- **回滚点**：`1b0fb60826df3594a7d56fe487b967892990ded2` 为 source commit，`048db97514fc1f5a2c085c11c12d2bae8379429c` 为 integration commit；回滚须覆盖完整 15-path runtime/gateway/bridge/settings/initializer/tests/专题文档边界，不执行 EventKit 或用户日历清理。
- **关联事实源**：[iOS EventKit 导出设计](schedule-v2-calendar-ios-eventkit.md)、[总路线图](schedule-v2-calendar-roadmap.md)、[当前数据流](schedule-v2-current-data-flow.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)。

### D-052：Room 初始化采用 `operationMutex` 外的 typed one-shot handoff 与全局单调 registration

- **状态**：已采用；source commit `c57f3fadf91282e3e596c148300f4b2b00356e0f`，integration commit `b063adad8b26b804e79005650bd644ecafa70f39`。
- **背景**：Android production Room 初始化需要在 snapshot Ready 后恢复系统日历 runtime，但 Settings、权限、Provider、账号服务与 controller 都不得在 `RoomScheduleRepository.operationMutex` 内执行；`yield()`、异步 `launch()` 或普通布尔快照也不能证明锁已释放、replacement 未发生或最新 durable intent 仍属于旧 owner。
- **选择**：`initialize()` 在 `operationMutex` 内只完成 strict read、Ready/Initialized 发布并同步 reserve `ScheduleRepositoryInitializationHandoff`；`withLock` 正常返回后才同步 `releaseAfterInitializationMutex()`。registration 以 exact `AccountSession` 对象身份保存槽位并使用跨 registry 实例全局单调顺序；publication 只包住纯 controller 内存事务，锁序固定为 `registryLock → controller stateLock`，success/reject/throw 都只清理 exact slot。
- **replacement 与 Settings**：publication 后重新捕获 exact session/scope/owner。same-owner registration replacement 只推进一次 start generation，使旧 finalized coordinator 不得被新 binding 恢复。每账号 Settings effect lane 使用 successor revision、串行 effect lock 与 exact true receipt；新 publication 原子继承最新 target，旧 true 只有仍为 latest 时才条件发布 false，较新 disable/enable/adoption 永远优先。外层/内层重入 flush 不得回退 `appliedRevision`。
- **失败与原子边界**：handoff 为 one-shot；stale、replacement、重复 release、preflight reject、publication throw、生命周期漂移或未知 effect 均不自动 retry/replay。release 及 suspend test seam 均在 Room 锁外。Room、Settings 与 Provider 不共享 transaction/CAS，本决策不增加跨存储补偿或重放 authority。
- **验证方式**：integration 运行 Android registry 17、controller delegation 5、Desktop Room 33、iOS runtime 37，共 92 项，`failures=0/errors=0/skipped=0`；Android、Desktop、noMobile 与 iOS 编译均通过。Desktop contract 以独立 JVM thread 同步重新进入 `execute()`，直接证明 handoff release 不持有 operationMutex。未访问真实 Provider、EventKit 或用户数据。
- **回滚点**：同步回退七路径 handoff/registry/controller/factory/test 改动与本记录；不得只移除 post-lock release 而保留可在 Room 锁内触发外部 effect 的 registration。
- **关联事实源**：[Android 单向日历导出架构](schedule-v2-calendar-export.md)、[当前数据流](schedule-v2-current-data-flow.md)、[总路线图](schedule-v2-calendar-roadmap.md)、[动态工作流执行要求](schedule-v2-dynamic-workflow-runbook.md)。

### D-053：#242 以 exact-session、一次观察和二次确认接入 Android 手动冲突终结 UI

- **状态**：已采用；source commit `f96f90a3283049fe8ba3a9c356ae6099e8e28e78`，integration commit `533b8ddcbd00e3f2b238cec9113f9f36122423c9`。本记录 supersede W45b、D-048/D-049/D-050 中“当前无 caller/UI”的 wiring 状态，不倒写那些切片落地时的历史范围。
- **背景**：W45b 已能按固定窄顺序观察冲突，#239/#240/#241 已能在 exact facts 重验证后单向执行，但普通 UI 不能持有 pair、snapshot、intent、evidence、Provider reader 或 replay authority，也不能把 unknown outcome 当作可恢复成功。
- **选择**：设置页是当前唯一 production caller。入口冻结 exact `AccountSession`，并要求 `READ_CALENDAR`、`WRITE_CALENDAR` 均已授予；用户每次显式执行一次 W45b 观察。UI 只保存 opaque `conflictId` 与 choice-absent、observed Schedule/Calendar target 或 reason-only blocked。只有 choice absent 可选择 `SCHEDULE`/`CALENDAR`；二次确认后 `ScheduleCalendarConflictResolutionRequest` 被一次性消费，并至多调用一次 #241。
- **状态与终态边界**：session 原引用变化立即清空 loading、items、feedback、confirmation、request 与 delayed observation。`Blocked`、取消、普通异常、`ChoiceCommitUnknown`、`ProviderEffectUnknown` 与 `RoomCommitUnknown` 均终结本次操作并清空旧列表/confirmation/request；用户再次尝试必须重新观察。UI 不执行 inspect、recovery、retry、replay、compensation，也不从旧 observation 合成 choice、Provider payload 或 durable facts。
- **不变项**：不新增或修改 Room schema、choice/evidence/link state、phase、receipt 或恢复队列；append-only choice、exact pair、ScheduleWins/CalendarWins executor、Room/Provider 非原子与 unknown 禁止自动恢复保持不变。#243 隔离真实 Provider 验收、#244、#206 completion、可靠后台/进程死亡投递与自动/完整 conflict state machine 仍 pending。
- **验证方式**：integration 运行设置状态 6、W45b observation coordinator 13、#241 resolution coordinator 14，共 33 项，`failures=0/errors=0/skipped=0`；Android 与 common 编译通过，checkout clean。未运行设备测试、真实 Calendar Provider 或用户数据。
- **回滚点**：同步回退设置页状态机、UI wiring、focused state contracts 与本记录；回滚不删除 durable choice/evidence，不触发 Provider/Room 清理或补偿。
- **关联事实源**：[双向日历同步设计](schedule-v2-calendar-bidirectional.md)、[当前数据流](schedule-v2-current-data-flow.md)、[Android 单向日历导出架构](schedule-v2-calendar-export.md)、[分端本地数据架构升级](schedule-v2-platform-storage-upgrade.md)、[总路线图](schedule-v2-calendar-roadmap.md)。

### D-054：#306 iOS production Room3 owner 与 post-mutex EventKit registration

- **状态**：已采用；source commit `9da50596c`，integration commit `db0e85f82`。
- **选择**：iOS production factory 复用进程级 `IosScheduleRoomDatabaseOwner` 的固定 Home Room3 文件与独立 Preferences stable UUID namespace；每个 AccountSession/generation 仅创建新 facade，不 rebuild/close process DB。identity 首次写入失败直接传播，未引入 Settings fallback、legacy reader、迁移或双写。
- **同步与日历边界**：factory 保留 Room 默认 unavailable remote gateway，`RequestSync` 返回 `BackendNotDeployed(attempted=false)` 且不发送/claim/记录远端证据。iOS initializer 提取同步 exact-session registration，Room hook 返回既有 opaque handoff；start/reconcile 只在 `operationMutex` 释放后执行。Room、Settings cache 与 EventKit 不共享 transaction/CAS；partial/unknown/cancellation/lost-return 不自动 retry、replay、compensate 或作跨库证明。
- **范围与验证**：仅 iOS owner/factory/initializer、隔离临时路径与内存 identity-store tests 及所属文档；不改 EventKit bridge/权限/真实日历、Room schema/DAO/迁移、远端激活或 Settings 迁移。真实 EventKit/#282 验收仍未进行。

## 后续记录模板

### D-XXX：标题

- **状态**：提议 / 已采用 / 已撤销
- **背景**：
- **约束与证据**：
- **候选方案**：
- **选择**：
- **理由**：
- **影响范围**：
- **验证方式**：
- **回滚点**：
- **关联事实源**：
