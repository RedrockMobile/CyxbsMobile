package com.cyxbs.pages.schedule.domain.repository

import com.cyxbs.pages.schedule.domain.model.CategoryId
import com.cyxbs.pages.schedule.domain.model.RecurrenceId
import com.cyxbs.pages.schedule.domain.model.RecurrenceRule
import com.cyxbs.pages.schedule.domain.model.Schedule
import com.cyxbs.pages.schedule.domain.model.ScheduleCategory
import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.model.ScheduleOccurrenceException
import com.cyxbs.pages.schedule.domain.model.ScheduleReminder
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** 当前账号身份；本地分区、账号锁与所有远端操作都必须以它隔离。 */
fun interface ScheduleAccountProvider {
  /** 返回当前账号的稳定分区键；空值与游客身份的约定由实现层统一处理。 */
  fun accountId(): String
}

/** 仓库对调用方公开的已确认状态；具体实现可来自本地持久化或经验证的远端完整图。 */
data class ScheduleSnapshot(
  val schedules: List<Schedule> = emptyList(),
  val exceptions: List<ScheduleOccurrenceException> = emptyList(),
  val categories: List<ScheduleCategory> = emptyList(),
  val status: ScheduleRepositoryStatus = ScheduleRepositoryStatus.Loading,
  /** 生成此快照的账号；`null` 只用于尚未初始化的进程初始态。 */
  val accountId: String? = null,
)

/**
 * 仓库向调用方公开的健康与可用性状态。
 *
 * 各实现可由本地持久化或远端合并结果提供快照；状态只描述当前已确认事实是否可读和后续操作的健康度，不承诺
 * 特定存储实现。
 */
sealed interface ScheduleRepositoryStatus {
  /** 当前实现尚未完成可信快照初始化。 */
  data object Loading : ScheduleRepositoryStatus

  /**
   * 当前已确认状态可读且最近同步未失败。
   *
   * [pendingCount] 表示本地尚未被远端确认的操作数；[hasPendingDeletes] 只表示其中存在等待提交的 DELETE，不能解释为
   * 本地或远端保存了 tombstone 资源。
   */
  data class Ready(val pendingCount: Int, val hasPendingDeletes: Boolean) : ScheduleRepositoryStatus

  /** 曾遇到可恢复故障，当前已回退到可读的已确认状态；[cause] 供诊断而非决定数据可见性。 */
  data class Recovered(val pendingCount: Int, val cause: Throwable) : ScheduleRepositoryStatus

  /** 已确认事实仍可读，但当前远端操作不可用、被拒绝或尚未恢复。 */
  data class Unavailable(val pendingCount: Int, val error: ScheduleRemoteError) :
    ScheduleRepositoryStatus

  /** 当前权威数据源损坏，不能安全构造可信快照。 */
  data class Corrupted(val cause: Throwable) : ScheduleRepositoryStatus
}

/** “此次及后续”拆分后应用于新系列的可编辑字段；未提供部分继承旧系列。 */
data class ScheduleSeriesChanges(
  val title: String,
  val description: String,
  val categoryId: CategoryId?,
  val timing: ScheduleTiming,
  val recurrence: RecurrenceRule?,
  val reminders: List<ScheduleReminder>,
)

/** 显式领域命令；用类型区分整系列与单次操作，防止调用方误把 occurrence 语义应用到整个系列。 */
sealed interface ScheduleCommand {
  /** 创建完整日程。 */
  data class Create(val schedule: Schedule) : ScheduleCommand
  /** 使用调用方提供的完整日程更新本地状态。 */
  data class Update(val schedule: Schedule) : ScheduleCommand
  /** 按稳定 identity 删除整条日程。 */
  data class Delete(val scheduleId: ScheduleId) : ScheduleCommand
  /** 设置非重复日程完成状态；重复日程必须改写单次例外。 */
  data class CompleteNonRepeating(val scheduleId: ScheduleId, val completed: Boolean) : ScheduleCommand
  /** 新增或替换由 recurrence identity 唯一定位的单次例外。 */
  data class UpsertOccurrenceException(val exception: ScheduleOccurrenceException) : ScheduleCommand
  /** 删除单次例外，使该次发生恢复继承系列。 */
  data class DeleteOccurrenceException(val scheduleId: ScheduleId, val recurrenceId: RecurrenceId) : ScheduleCommand
  /**
   * 从 [recurrenceId] 起拆出新系列的预留命令。
   *
   * 当前 local reducer 明确返回 `UNSUPPORTED`；仓库不得静默降级为其他命令，也不设计补偿流程。
   */
  data class SplitSeries(
    val scheduleId: ScheduleId,
    val recurrenceId: RecurrenceId,
    val followingChanges: ScheduleSeriesChanges? = null,
  ) : ScheduleCommand
  /**
   * 从指定原始发生起删除后半系列的预留命令。
   *
   * 当前 local reducer 明确返回 `UNSUPPORTED`，不会改写为逐条删除或生成补偿操作。
   */
  data class DeleteThisAndFollowing(val scheduleId: ScheduleId, val recurrenceId: RecurrenceId) : ScheduleCommand
  /** 创建分类。 */
  data class CreateCategory(val category: ScheduleCategory) : ScheduleCommand
  /** 更新分类。 */
  data class UpdateCategory(val category: ScheduleCategory) : ScheduleCommand
  /**
   * 在所选分类尚不存在时，将分类 CREATE 与日程 CREATE/PATCH 作为同一次本地原子修改保存。
   *
   * [schedule] 必须引用 [category]；该命令只服务于固定默认分类的惰性创建，不承担通用分类管理。
   */
  data class SaveScheduleWithNewCategory(
    val category: ScheduleCategory,
    val schedule: Schedule,
  ) : ScheduleCommand
  /**
   * 删除未被日程引用的分类。
   *
   * 仓库会把该命令编码为仅包含 Category DELETE 的聚合批次并复用日常 DELETE 接口；当前暂不提供 UI 入口。
   * 仍被日程引用时本地直接拒绝，避免向服务端提交必然无法形成合法最终图的请求。
   */
  data class DeleteCategory(val categoryId: CategoryId) : ScheduleCommand
  /** 首次进入、网络恢复或用户主动对账时，提交 typed confirmed 与 pending 并合并服务端完整响应。 */
  data object RequestSync : ScheduleCommand
}

/** 一次远端尝试结果；[attempted] 用于区分后端缺席与实际投递失败。 */
sealed interface ScheduleSyncResult {
  /** 本地命令已提交，且在 [attempted] 为真时远端尝试也成功。 */
  data class Success(val attempted: Boolean = true) : ScheduleSyncResult
  /** 本地命令仍可已提交；[attempted] 表示是否真正向已部署后端发起请求。 */
  data class Failure(val error: ScheduleRemoteError, val attempted: Boolean) : ScheduleSyncResult
}

/** 第一阶段错误模型；领域契约不直接暴露 HTTP 或 Ktor 异常，调用方可稳定处理离线与冲突语义。 */
sealed interface ScheduleRemoteError {
  /** 当前平台没有提供 Schedule 远端能力；例如 Web 暂时只提供只读 façade。 */
  data object BackendNotDeployed : ScheduleRemoteError
  /** 远端资源不存在，通常意味着删除或版本冲突后的对账。 */
  data object NotFound : ScheduleRemoteError
  /** 服务端返回非成功状态；领域层仅保留稳定状态码。 */
  data class Server(val statusCode: Int) : ScheduleRemoteError
  /**
   * 服务端返回 HTTP 200 + REJECTED 时的稳定业务拒绝事实。
   *
   * [reason] 与最终 wire `ResultReason` 同构；具体资源 current/tombstone 等合并事实由 typed response data 表达。
   */
  data class MutationRejected(
    val reason: ScheduleMutationBusinessRejectionReason,
  ) : ScheduleRemoteError
  /** 请求超过同步时限；是否保留重试状态由具体仓库能力决定。 */
  data object Timeout : ScheduleRemoteError
  /** 远端响应或完整资源图不满足冻结合同，调用方必须 fail-closed。 */
  data class InvalidResponse(val cause: Throwable) : ScheduleRemoteError
  /** 未归类异常；[cause] 仅供诊断，不应穿透为 UI 协议。 */
  data class Unexpected(val cause: Throwable) : ScheduleRemoteError
}

/**
 * 后端 Schedule v2 `ResultReason` 的封闭稳定集合。
 *
 * 枚举名与 typed wire 枚举逐项同构；未知值必须在 codec 边界拒绝，不能映射成泛化的业务原因。
 */
enum class ScheduleMutationBusinessRejectionReason {
  INVALID_REQUEST,
  RESOURCE_NOT_FOUND,
  RESOURCE_DELETED,
  CATEGORY_NOT_FOUND,
  RESOURCE_CHANGED,
  FINAL_GRAPH_INVALID,
  UNSUPPORTED_RECURRENCE,
}

/**
 * 一次 finalized 手动入站操作的进程内不透明路由令牌。
 *
 * 令牌只由 Android coordinator 在单次请求绑定 active LifecycleSession 后签发，并仅用于把 Room writer 回声与该
 * 生命周期绑定；它不携带 accountId、不参与持久化、不能查询 repository，也不授予任何 Schedule 或 Provider 写入权限。
 */
class ScheduleCalendarInboundOperationToken private constructor() {
  companion object {
    /** 为一次新的手动操作创建不可复用对象 identity；仅模块内 coordinator/host 合同可签发。 */
    internal fun issue(): ScheduleCalendarInboundOperationToken = ScheduleCalendarInboundOperationToken()
  }
}

/** Repository 已完成持久化提交后发出的日历投影事件。 */
sealed interface ScheduleCalendarChange {
  /** 事件所属账号；消费者必须与快照账号同时校验，禁止跨账号重放。 */
  val accountId: String

  /** 指定账号的本地快照首次可用；调用方必须执行全量对账。 */
  data class Initialized(override val accountId: String) : ScheduleCalendarChange

  /** 本地事务已提交的日程变化；删除仍保留 ID，便于清理 Provider 投影。 */
  data class SchedulesCommitted(
    override val accountId: String,
    val scheduleIds: Set<ScheduleId>,
  ) : ScheduleCalendarChange

  /** 远端完整响应合并并持久化完成后的事实事件；没有远端提交时不得发出。 */
  data class RemoteCommitted(override val accountId: String, val scheduleIds: Set<ScheduleId>?) : ScheduleCalendarChange
}

/**
 * 当前账号 delegate 是否允许接受本地编辑命令。
 *
 * 正常账号仓库固定为 [LOCAL_FIRST]；没有当前账号 delegate 时为 [READ_ONLY]。同步健康状态不会把 local-first 动态切换
 * 成只读，账号代理只能转发当前精确 delegate 的声明。
 */
enum class ScheduleRepositoryMutationMode {
  /** 本地事务先提交业务事实；远端暂不可用时保留 typed pending，等待后续同步。 */
  LOCAL_FIRST,

  /** 当前没有精确账号 delegate，例如登出、游客或账号切换空窗；编辑命令必须关闭。 */
  READ_ONLY,
}

/**
 * 判断当前 delegate 是否允许下发编辑命令。
 *
 * [LOCAL_FIRST] 始终可写，[READ_ONLY] 始终不可写；`RequestSync` 是独立的对账命令，不使用此编辑门禁。
 */
fun ScheduleRepositoryMutationMode.canSubmitScheduleMutation(): Boolean =
  this == ScheduleRepositoryMutationMode.LOCAL_FIRST

/** Schedule v2 的稳定仓库边界，供主页面、Feed 与课表等消费者共享。 */
interface ScheduleRepository {
  val snapshot: StateFlow<ScheduleSnapshot>

  /**
   * 当前 delegate 的编辑能力。
   *
   * 默认值适用于正常 local-first 实现；账号 façade 在没有当前 delegate 时必须显式公开 [ScheduleRepositoryMutationMode.READ_ONLY]。
   */
  val mutationMode: ScheduleRepositoryMutationMode
    get() = ScheduleRepositoryMutationMode.LOCAL_FIRST

  /** 持久化成功且最新 [snapshot] 已发布后的变化流；默认空流兼容测试替身与只读实现。 */
  val calendarChanges: Flow<ScheduleCalendarChange> get() = emptyFlow()

  /**
   * 读取当前账号的本地状态并建立首个可信快照。
   *
   * 首次远端对账可由实现随后触发；本地读取失败必须抛出，不能发布伪造的空 `Ready`。
   */
  suspend fun initialize()

  /**
   * 应用一个命令。
   *
   * 编辑命令须原子提交本地业务事实与至多一个 typed pending；[ScheduleCommand.RequestSync] 提交当前 confirmed+pending
   * 并应用服务端完整响应。账号 façade 负责拒绝没有当前 delegate 的调用。
   */
  suspend fun execute(command: ScheduleCommand): ScheduleSyncResult?

  /**
   * 串行执行一组属于同一用户动作的命令。
   *
   * 每条命令前调用 [shouldContinue]；返回 [ScheduleSyncResult.Failure] 后立即停止。默认实现按顺序调用 [execute]；
   * 账号 façade 会把整组命令冻结到调用开始时的同一 delegate，避免切号后把剩余命令发给新账号。
   */
  suspend fun executeSerially(
    commands: List<ScheduleCommand>,
    shouldContinue: () -> Boolean,
  ): List<ScheduleSyncResult?> {
    val results = mutableListOf<ScheduleSyncResult?>()
    for (command in commands) {
      if (!shouldContinue()) break
      val result = execute(command)
      results += result
      if (result is ScheduleSyncResult.Failure) break
    }
    return results
  }
}
