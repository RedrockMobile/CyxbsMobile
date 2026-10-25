# Schedule 当前交接状态

> 更新时间：2026-09-06。本文只记录当前实现，不保留已经废弃的历史方案。

## 已完成实现

- 清单、课表事务统一使用 Schedule 领域模型；`kind` 区分 TODO/AFFAIR，`todoState` 表达是否属于清单及完成态，`linkedToCourse` 表达用户是否要求课表投影。
- 客户端采用 Room remote/pending 双快照，日常增删改本地优先；失败保留 pending，并支持失败记录定位和修复。
- 首次进入、存在 pending 的网络恢复和手动操作通过 `/schedule/sync` 完整对账。
- Category 与 OccurrenceAdjustment 使用客户端 UUID v7 本地 ID和服务端自增 ID；Schedule 使用客户端 UUID，普通新增为 v7，旧数据迁移为确定性 v5。
- 接口使用逐资源 `confirmed/upserts/deletes` 与 `confirmedResults/discoveredResults/upsertResults/deleteResults`，支持同请求部分成功。
- 重复规则支持日、周、月、年；单次调整使用不可变原始日期槽和字段级 AtomicField/FieldPatch。
- 分类、日程和单次调整使用物理删除；还原单次调整即删除调整资源。
- Android/iOS 旧清单与旧 Transaction 共享 commonMain 映射并按账号执行一次性迁移。
- Android/iOS 系统日历保持单向投影；时间点使用零时长事件，提醒支持准时和提前分钟。
- 支持导出标准 ICS 文件；Android、iOS、Desktop 与 Web 按各自平台能力保存或分享，重复规则和单次调整沿用同一套日历投影模型。
- 课表按 AllDay、Timed、Deadline 三层投影，事务与清单分别渲染，只有当前登录账号主页课表允许创建和展示。
- 清单页支持列表/时间轴切换、分类筛选和管理、批量操作、同步失败修复入口；分类管理可进入“全部”或指定分类的全量日程页，并按“待办事项/普通日程”分别查看和批量处理；首页 Feed 展示临期/超期提示与清单卡片。
- 分类全量页中的普通日程按有效实例分为“未过期/已过期”：前者取今天及之后第一次实例升序展示并保留 24 小时临期提示，后者取最后一次历史实例倒序展示且不追加超期标签。
- 课表时间轴为被压缩的 Item 保留统一最小高度；点击后可展开对应时间轴，详情弹窗会跟随内容高度避让当前 Item，并限制 Item 不越过课表顶部安全间距。
- 通知中心收到的没课约行程和活动详情不再写旧事务/旧清单接口：前者按单组学期节次创建课表事务，后者按活动开始时间创建清单；外部来源使用稳定 Schedule ID 去重，并沿用本地优先、失败后续同步的仓库链路。

## 当前协议事实源

- [Schedule 接口协议](schedule-protocol.md)
- [重复日程与单次调整](schedule-recurrence.md)
- [Schedule 存储与旧数据迁移](schedule-storage-and-migration.md)
- [Schedule 系统日历投影](schedule-calendar-export.md)
- [Schedule Android 验收清单](schedule-android-e2e-test-runbook.md)

## 验证状态

新数据结构完成后已执行的聚焦验证：

```text
# 客户端仓库
./gradlew :cyxbs-pages:schedule:desktopTest :cyxbs-pages:course:view:compileKotlinDesktop :cyxbs-pages:course:compileKotlinDesktop :cyxbs-pages:discover:compileKotlinDesktop --quiet
./gradlew :cyxbs-pages:schedule:compileAndroidMain :cyxbs-pages:course:view:compileAndroidMain :cyxbs-pages:course:compileAndroidMain --quiet
./gradlew :cyxbs-pages:schedule:compileKotlinIosSimulatorArm64 --quiet
./gradlew :cyxbs-pages:schedule:desktopTest :cyxbs-pages:notification:desktopTest :cyxbs-pages:notification:compileAndroidMain :cyxbs-pages:ufield:compileAndroidMain
./gradlew :cyxbs-pages:notification:compileKotlinIosSimulatorArm64 :cyxbs-pages:schedule:compileKotlinIosSimulatorArm64

# magipoke-todo 后端仓库
go test ./schedule ./schedulewire ./dao
go test ./service -run '^Test(Schedule|PublicSchedule)' -count=1
```

Android 真机基线已按验收清单完成 241 项验证，阻塞失败、跳过和待完成均为 0；后续又完成了 Android Calendar Provider 与 iOS EventKit 的重复单次调整映射、ICS 导出、课表折叠时间轴及清单时间轴多全天标题的聚焦回归。2026-09-06 新增的分类全量页、普通日程分区和区域全选共 14 项也已通过真机与聚焦测试，对应展示数据已在人工确认后清理，详见验收清单中的增量验收记录。

分类全量页、普通日程分区及配套验收文档均已提交，当前功能分支也已通过 merge 吸收当时最新的 `develop`。后续发起或更新 PR 前仍需检查远端 `develop` 是否产生新提交，并重新执行受影响模块的编译与聚焦测试。

## 维护边界

- 不重新引入 batch、receipt、cursor、逐条 outbox、远端墓碑、摘要键或幂等请求表。
- 不为少见的多设备竞争增加额外状态机；字段级 modifiedAt、资源 version 和后续 Sync 已覆盖当前产品需要。
- `UNSCHEDULED` 只服务旧清单迁移，新 UI 不创建。
- 账号隔离必须冻结 AccountSession；不能根据请求体接受 owner 或学号。
- 日历适配器中的 occurrence exception 是平台术语，不应重新暴露成 Schedule 业务模型。
