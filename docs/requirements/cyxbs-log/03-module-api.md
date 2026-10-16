# LOCAL-LOG-03：独立 log 模块与 CyxbsLog 门面

Part of: [LOCAL-LOG-MAP](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/map.md>)

- 类型/标签：task / wayfinder:task、needs-triage
- 状态：in_progress，门面与管理协议已实现；结构化匿名上下文待设计
- Blocked by：[02](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/02-prototype.md>)

## 需求

新增 `:cyxbs-components:log`，包名 `com.cyxbs.components.log`。业务依赖自有 API，门面隐藏 Kermit/文件系统/队列；平台输出适配可替换。核心不依赖 utils/config/account/页面，不额外引入 kmp.compose。

## 范围

- commonMain：等级、惰性消息、受控字段、匿名上下文、配置、健康与管理结果。
- 输出目的地分别过滤，message 仅在有输出接受时求值一次；快照不可变上下文。
- 热路径不采调用栈定位、不逐条协程、不执行文件 I/O；限制消息、字段与异常对象保留。
- 定义未初始化、重复 configure、关闭后调用、策略 epoch、故障与取消行为。
- Kermit 类型只在内部适配出现；公开 Swift 互操作能力以 PoC 为准。

## 验收

- 各选定目标编译通过且依赖图无环；Web 不解析 Native 文件依赖。
- 关闭等级不调用 lambda；双输出只求值一次；上下文不串请求/账号会话。
- 普通 writer 失败不破坏业务或阻止其他输出；管理取消不会被吞。
- API 文档说明过滤、接收与丢弃，未承诺未实现的持久性。

## 进度

用户于 2026-10-13 授权先引入第三方库并搭建最小框架，先行实施本项的基础部分。

已新增 `:cyxbs-components:log`，仅显式依赖 Kermit 2.2.0，使用 `manager.lib`；提供 `LogLevel`、`LogConfig`、`CyxbsLog`，支持等级过滤、惰性消息、原子配置快照及平台控制台输出。Kermit 类型不出现在公开 API 中。

当前已支持控制台/文件分别过滤、消息单次求值、消息与 writer 异常隔离、同步文件采集策略门，以及结构化管理结果和健康统计；Kermit 类型仍未暴露。普通日志热路径不执行文件 I/O，关闭等级不求值 lambda。尚未提供自动链路/账号会话上下文，跨请求关联仍由调用方使用非敏感稳定 TAG 或消息字段表达，因此本任务保持 in_progress。

构建验证覆盖 `compileAndroidMain`、`compileKotlinDesktop`、`compileKotlinJs`、`compileKotlinWasmJs`、`compileKotlinIosArm64`、`compileKotlinIosSimulatorArm64`。此结果验证依赖解析与源码编译，不代表 Native 框架链接、Swift 调用、真机输出或性能验证已完成。

依据：[设计报告](</Users/generals/AndroidProject/CyxbsMobile/docs/research/cyxbs-log-module-design.md>)。
