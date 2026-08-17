# Schedule v2 Calendar 双向方案归档

> **状态：ARCHIVE-ONLY。** 旧 Calendar Provider 入站、三方合并、冲突选择、`CalendarLink`/`DETACHED` 和手动冲突执行代码已经从当前客户端删除，不得据此恢复兼容层。

## 当前结论

当前客户端只保留用户显式开启的单向受管投影：

```text
Schedule repository snapshot / calendarChanges
  -> Calendar projection
  -> managed Calendar/EventKit adapter
```

系统日历不是 Schedule v2 的事实源，不生成远端 mutation、remote snapshot 或 tombstone。账号切换、权限变化和用户关闭导出只控制投影 worker 的生命周期。

## 历史范围

本文件原先记录过 Android 进程内 Provider 入站、观察器、冲突处理与 detachment 方案。该方案没有进入当前 typed 协议，也没有可靠后台或跨设备一致性保证，现已随客户端协议重做一并删除。

当前实现与边界见：

- [Schedule v2 交接说明](schedule-v2-codex-handoff.md)
- [Android 单向日历导出](schedule-v2-calendar-export.md)
- [四表本地存储设计](schedule-v2-platform-storage-upgrade.md)
