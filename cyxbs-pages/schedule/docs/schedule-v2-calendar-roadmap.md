# Schedule v2 客户端路线图

> 当前执行入口以 [Codex 交接](schedule-v2-codex-handoff.md) 和 [四表存储设计](schedule-v2-platform-storage-upgrade.md) 为准。旧 Wave、cursor/outbox、semantic command 与双向 Calendar 方案只存在于明确标为 ARCHIVE 的历史文档中，不得继续派生任务。

## 已完成

- typed Category、Schedule、OccurrenceOverride wire 与严格 codec；
- 完整资源 `version`、AtomicField、confirmed/upsert/delete 与 atomic batch；
- remote/pending 双快照、localRevision R→U 保护；
- Schedule 日常 POST/PUT/DELETE 与完整 Sync；
- 四张 Room 表和 typed JSON converter；
- Android、Desktop、iOS production Room/Ktor 接线；
- Web READ_ONLY unavailable 边界；
- Android/iOS 仅保留 Schedule 到受管系统日历的单向导出；
- 删除 cursor、outbox、receipt、checkpoint、candidate、旧数据库图和 Calendar 双向冲突链。

## 集成收尾

1. 运行 Desktop 聚焦测试和完整 test compile；
2. 运行 Android host、iOS metadata/test、JS/Wasm compile；
3. 核对四表 schema export 与旧符号扫描；
4. 将客户端候选合入 `guoxiangrui/feature/schedule`；
5. 核对提交说明、工作树和交接文档。

## 后续独立验收

代码合入不等于上线。真实后端 HTTP、账号切换、断网 pending、R→U、Android Calendar Provider、iOS EventKit、生产数据库和多设备行为需要在部署与测试账号准备完成后另行授权验收。

当前明确不恢复 MONTHLY/YEARLY、occurrence timing/category override、Web 离线编辑、旧库推断迁移、自动重试框架或 Calendar 入站冲突合并。
