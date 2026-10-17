## Agent skills

### Issue tracker

Issues and specs are tracked in GitHub Issues for `RedrockMobile/CyxbsMobile`. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the default five-role triage vocabulary. See `docs/agents/triage-labels.md`.

### Logging

新增或修改日志时统一使用 `CyxbsLog`；实现、接入和边界见 `cyxbs-components/log/README.md`，持久化等级与隐私依据见 `cyxbs-components/log/docs/research/mobile-log-persistence-policy.md`。

- 使用 `CyxbsLog.v/d/i/w/e/a(tag, message, throwable)`，消息直接传 `String`，异常可省略；`showLocation` 控制 `文件名:行号 -> ` 前缀，只有 `e` 默认开启，其他入口默认关闭，业务侧只传消息正文。不直接调用 Kermit、`println` 或自行写日志文件。
- 等级语义：`Verbose/Debug` 只记录开发细节；`Info` 记录启动、前后台、导航、状态迁移和请求结果等低频诊断轨迹；`Warn` 记录可恢复异常、重试或降级；`Error` 记录操作失败并传入原始 `Throwable`；`Assert` 仅用于违反程序不变量的状态。
- TAG 由调用方定义，同一文件/类保持一致，跨文件业务链路按需共享 TAG；禁止把用户信息、动态 URL、请求 ID 等拼进 TAG。需要关联并发事件时，只在消息中使用非敏感关联 ID。
- 禁止记录姓名、学号、手机号、邮箱等个人信息，以及 token、Authorization、Cookie、密码、验证码、完整请求/响应 body、完整 query/header、动态 URL 和其他业务明文。文件层脱敏只是兜底，不能代替调用方最小化；网络日志只允许 method、归一化 route、status、耗时和非敏感关联 ID。
- 消息只包含诊断文本，不要重复拼接异常堆栈。日志内部的异常格式化和输出失败必须与业务控制流隔离，也不得用“已经打日志”代替错误处理。
- 业务模块不得自行创建 `FileLogConfig`、拼接日志目录或操作文件生命周期；文件日志的隐私同意、用户开关、主进程限制、flush、导出和清理由 `AppLogManager` 统一管理。
- 修改脱敏、JSONL 协议、队列、轮转、恢复、导出或清理语义时，必须补对应边界/故障回归测试，并至少运行 `:cyxbs-components:log:desktopTest`。

### Domain docs

single-context: one root `CONTEXT.md` shared by the repository, with architectural decisions in `docs/adr/`. See `docs/agents/domain.md`.
