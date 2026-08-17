# Schedule v2 Calendar 导出加固归档

> **状态：ARCHIVE-ONLY。** 本文件原先记录的入站观察、冲突处理、`CalendarLink`、`DETACHED` 与双向加固方案已经删除，不再是当前实现要求。

当前单向导出只保留以下安全边界：

- exact session、账号 scope、用户开关和权限必须在 Provider 边界前复核；
- 同一账号投影 worker 串行写入，替换与停止不会留下重复 worker；
- 只操作应用创建且身份完整匹配的受管 Calendar/事件；
- Provider 内容不生成 Schedule mutation、remote snapshot 或 tombstone；
- 普通关闭不删事件，只有用户二次确认才清理整份受管 Calendar。

当前实现说明见 [Android 单向日历导出](schedule-v2-calendar-export.md)。
