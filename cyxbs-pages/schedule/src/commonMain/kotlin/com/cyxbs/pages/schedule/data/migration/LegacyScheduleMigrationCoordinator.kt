package com.cyxbs.pages.schedule.data.migration

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.pages.course.api.CourseUtils
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.repository.ScheduleCommand
import com.cyxbs.pages.schedule.domain.repository.ScheduleRepository
import com.cyxbs.pages.schedule.ui.todo.main.loadScheduleTodoPinnedIds
import com.cyxbs.pages.schedule.ui.todo.main.saveScheduleTodoPinnedIds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Android/iOS 旧事务与旧清单的一次性账号迁移协调器。
 *
 * 每次账号仓库初始化时最多尝试一次。旧接口、Room 命令或账号门禁任一失败都不会写完成版本，下次启动或重新
 * 登录会用确定性 ID 安全重试。
 */
internal object LegacyScheduleMigrationCoordinator {
  /**
   * 当前账号已成功完成的旧数据迁移方案版本；记录的是版本号，不是迁移次数。
   *
   * Key 缺失时按 0 处理，表示从未完成迁移；保存为 2 表示已执行“失败 pending 保留”修复后的补偿迁移。
   * 将来需要补迁其他旧数据时，递增 [CURRENT_MIGRATION_VERSION] 即可让旧版本账号再次执行新的迁移方案。
   */
  private const val MIGRATION_VERSION_KEY = "schedule_legacy_migration_version"

  /**
   * 本次客户端要求账号至少完成的迁移方案版本。
   *
   * 版本 2 用于补回早期服务端拒绝后被客户端错误清掉的确定性 ID；已存在 ID 会由 [persistItems] 幂等跳过。
   */
  private const val CURRENT_MIGRATION_VERSION = 2

  /**
   * 旧服务迁移入口的硬截止时间。
   *
   * 新版发布约两年后，无论本地是否存在迁移版本都不再访问旧接口；代码可以继续保留为休眠兼容逻辑，避免后续
   * 还要专门安排一次删除版本。使用 UTC 绝对时刻，避免设备时区变化使迁移窗口产生歧义。
   */
  internal val MIGRATION_EXPIRES_AT: Instant = Instant.parse("2028-09-01T00:00:00Z")

  /** 截止时刻本身已经视为过期，调用方必须在旧接口请求前检查。 */
  internal fun isMigrationWindowOpen(now: Instant): Boolean = now < MIGRATION_EXPIRES_AT

  /**
   * 为已初始化的账号专属 [repository] 启动迁移。
   *
   * 此方法只在账号 scope 中发起一次异步尝试，不阻塞 repository 初始化 handoff。首次离线或旧服务暂时失败时不
   * 写完成版本，由下次账号仓库初始化重新尝试，不为一次性迁移常驻网络监听。
   */
  fun start(repository: ScheduleRepository, session: AccountSession) {
    if (!isMigrationWindowOpen(Clock.System.now())) return
    val accountId = session.accountId ?: return
    val settings = AccountSettings.get(accountId)
    if (settings.getInt(MIGRATION_VERSION_KEY, 0) >= CURRENT_MIGRATION_VERSION) return

    val accountService = IAccountService::class.impl()
    if (accountService.session.value !== session) return
    session.accountCoroutineScope.launch {
      runCatching { migrateOnce(repository, session, settings) }
    }
  }

  /**
   * 执行一轮完整迁移并在本地最终图确认后写版本。
   *
   * ScheduleRepository 是 local-first：远端 Failure 仍可能表示 Room 与 pending 已提交，因此这里只以最终本地快照
   * 是否包含全部目标 ID 判断成功，不把暂时的远端不可用误当成本地迁移失败。
   */
  private suspend fun migrateOnce(
    repository: ScheduleRepository,
    session: AccountSession,
    settings: AccountSettings,
  ): Boolean {
    // start() 到账号协程真正执行之间可能跨过截止时刻，发包前必须再次门禁。
    if (!isMigrationWindowOpen(Clock.System.now())) return false
    val accountId = session.accountId ?: return false
    if (settings.getInt(MIGRATION_VERSION_KEY, 0) >= CURRENT_MIGRATION_VERSION) return true
    if (repository.snapshot.value.accountId != accountId) return false

    val (transactionResponse, todoResponse) = coroutineScope {
      val api = LegacyScheduleMigrationApiService::class.impl()
      val transactionRequest = async {
        api.getTransactions(session).also { it.throwApiExceptionIfFail() }
      }
      val todoRequest = async {
        api.getTodos(session).also { it.throwApiExceptionIfFail() }
      }
      transactionRequest.await() to todoRequest.await()
    }
    val transactions = transactionResponse.data
    val todos = todoResponse.data.todos.orEmpty()

    val firstMonday = if (transactions.isEmpty()) {
      null
    } else {
      // 这不是重试或常驻监听：同一次迁移只等待课表仓库发布必需的学期锚点，账号 scope 结束会自动取消。
      SchoolCalendar.getFirstMonDay() ?: SchoolCalendar.observeFirstMonDay().first()
    }
    // 等待学期锚点期间可能跨过两年截止时间，过期后不再继续执行迁移写入。
    if (!isMigrationWindowOpen(Clock.System.now())) return false

    val now = Clock.System.now()
    val items = buildList {
      if (firstMonday != null) {
        addAll(
          LegacyScheduleMapper.mapTransactions(
            accountId = accountId,
            transactions = transactions,
            firstMonday = firstMonday,
            maxWeek = CourseUtils.maxWeek,
          )
        )
      }
      addAll(
        LegacyScheduleMapper.mapTodos(
          accountId = accountId,
          todos = todos,
          now = MinuteTimeDate.now(),
          nowEpochMillis = now.toEpochMilliseconds(),
        )
      )
    }

    persistItems(repository, accountId, items)
    val expectedIds = items.mapTo(mutableSetOf()) { it.schedule.id }
    val finalSnapshot = repository.snapshot.value
    if (finalSnapshot.accountId != accountId) return false
    if (!finalSnapshot.schedules.mapTo(mutableSetOf()) { it.id }.containsAll(expectedIds)) return false

    saveScheduleTodoPinnedIds(
      settings,
      mergeMigratedPinnedIds(
        existing = loadScheduleTodoPinnedIds(settings),
        migrated = items.filter(LegacyScheduleMigrationItem::pinned).map { it.schedule.id },
      ),
    )
    settings.putInt(MIGRATION_VERSION_KEY, CURRENT_MIGRATION_VERSION)
    return true
  }

  /**
   * 将旧清单置顶顺序追加到当前账号已有顺序，并按首次出现去重。
   *
   * 已有端上顺序优先，迁移项保持旧接口返回顺序；重复执行迁移不会覆盖用户现有置顶，也不会插入重复 ID。
   */
  internal fun mergeMigratedPinnedIds(
    existing: List<ScheduleId>,
    migrated: List<ScheduleId>,
  ): List<ScheduleId> =
    (existing.asSequence() + migrated.asSequence()).distinct().toList()

  /**
   * 逐条保存映射结果。
   *
   * 默认分类优先复用同 identity 或同名的已同步资源；缺失时仅随第一条引用日程通过原子命令创建，避免页面初始化
   * 产生空分类写入。已存在的确定性日程 ID 直接跳过。
   */
  internal suspend fun persistItems(
    repository: ScheduleRepository,
    accountId: String,
    items: List<LegacyScheduleMigrationItem>,
  ) {
    val knownScheduleIds = repository.snapshot.value.schedules.mapTo(mutableSetOf()) { it.id }
    val categories = repository.snapshot.value.categories.toMutableList()

    for (item in items) {
      if (repository.snapshot.value.accountId != accountId) return
      if (!knownScheduleIds.add(item.schedule.id)) continue

      val category = item.categoryName?.let { name ->
        categories.firstOrNull { existing ->
          existing.id == LegacyDefaultCategoryIds[name] ||
            existing.name.trim().equals(name, ignoreCase = true)
        } ?: ScheduleCategory(
          id = requireNotNull(LegacyDefaultCategoryIds[name]),
          revision = 0,
          name = name,
          color = null,
          sortOrder = LegacyDefaultCategoryIds.keys.indexOf(name),
        )
      }
      val schedule = item.schedule.copy(categoryId = category?.id)
      val categoryExists = category == null || categories.any { it.id == category.id }
      val command = if (category != null && !categoryExists) {
        ScheduleCommand.SaveScheduleWithNewCategory(category, schedule)
      } else {
        ScheduleCommand.Create(schedule)
      }
      repository.execute(command)

      val current = repository.snapshot.value
      check(current.accountId == accountId && current.schedules.any { it.id == schedule.id }) {
        "legacy schedule was not committed locally"
      }
      if (category != null && !categoryExists) {
        check(current.categories.any { it.id == category.id }) {
          "legacy schedule category was not committed locally"
        }
        categories += category
      }
    }
  }
}
