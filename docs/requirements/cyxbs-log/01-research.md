# LOCAL-LOG-01：日志模块与本地保存调研

Part of: [LOCAL-LOG-MAP](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/map.md>)

- 类型/标签：research / wayfinder:research
- 状态：completed
- Blocked by：无
- 完成日期：2026-10-13

## 问题

新增独立 log 是否优于 utils？能复用哪些 KMP 开源能力？自有 CyxbsLog、用户本地保存、分享与清理必须补哪些边界？

## 结论与产物

- [选型与源码](</Users/generals/AndroidProject/CyxbsMobile/docs/research/kmp-logging-research.md>)：Kermit 平台输出可复用，kermit-io 缺少可靠管理协议。
- [模块/协议设计](</Users/generals/AndroidProject/CyxbsMobile/docs/research/cyxbs-log-module-design.md>)：独立模块、单向兼容桥、自有文件协议、策略和初始化边界。
- [平台保存](</Users/generals/AndroidProject/CyxbsMobile/docs/research/cyxbs-log-local-storage-platforms.md>)：私有目录、备份排除、锁屏保护、主动分享与浏览器限制。

## 验收记录

源码和平台资料已形成可追溯建议；所有候选参数与未验证项明确标注。未执行编译/基准，因此没有平台集成或性能通过结论。完成的是调研子项，不是整个日志需求。

## 进度

调研完成；需要验证的内容移交 [02](</Users/generals/AndroidProject/CyxbsMobile/docs/requirements/cyxbs-log/02-prototype.md>) 与地图 Fog。
