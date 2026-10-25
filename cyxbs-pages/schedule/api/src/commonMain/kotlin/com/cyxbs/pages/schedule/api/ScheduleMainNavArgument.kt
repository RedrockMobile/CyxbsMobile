package com.cyxbs.pages.schedule.api

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.appNavBackStack
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

private val SCHEDULE_UUID_CANONICAL = Regex(
  "^[0-9a-f]{8}-[0-9a-f]{4}-[57][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
)

/**
 * 日程在导航层、领域层和服务端共用的稳定 UUID 标识。
 *
 * 普通新增使用 UUID v7；旧清单和旧事务迁移使用输入稳定的 UUID v5。两者都只接受小写规范文本，
 * 避免同一标识因大小写或连字符形式不同而在导航、Room 和同步协议之间产生歧义。
 */
@Serializable
@JvmInline
value class ScheduleId private constructor(val value: String) {
  override fun toString(): String = value

  companion object {
    /**
     * 从字符串创建日程标识。
     *
     * @param value 待校验的 UUID v5 或 v7 文本；必须是规范小写形式。
     * @return 与输入文本对应的 [ScheduleId]。
     * @throws IllegalArgumentException 输入不符合规范 UUID v5/v7 时抛出。
     */
    operator fun invoke(value: String): ScheduleId {
      require(SCHEDULE_UUID_CANONICAL.matches(value)) { "ScheduleId must be a canonical UUIDv5 or UUIDv7" }
      return ScheduleId(value)
    }

    /**
     * 尝试解析规范 UUID v5/v7 文本，适合处理深链或其他不可信输入。
     *
     * @return 解析成功时返回标识，否则返回 `null`，不会抛出格式异常。
     */
    fun parseOrNull(value: String): ScheduleId? =
      if (SCHEDULE_UUID_CANONICAL.matches(value)) ScheduleId(value) else null
  }
}

/**
 * 重复日程中某次发生的稳定身份。
 *
 * 身份保留规则展开前的本地墙上时间、时区和全天属性，而不是仅保存转换后的瞬时时间；这样在 DST
 * 切换或时区规则更新后，编辑、完成和删除命令仍能定位用户最初看到的同一次发生。
 */
@Serializable
data class RecurrenceId(
  val originalDateTime: MinuteTimeDate,
  val timeZoneId: String?,
  val allDay: Boolean,
)

/**
 * 日程主页面的导航契约。
 *
 * [scheduleId] 为空时仅打开主页面；非空时定位指定日程，[recurrenceId] 可进一步定位重复系列中的一次发生。
 * 该契约有意不兼容旧版 `Long` 标识和仅含日期的深链：调用方必须传递与仓库命令一致的 UUID 及完整
 * 重复身份，避免 DST、跨时区或同日多次发生时误操作其他实例。
 *
 * @param scheduleId 要定位的日程；为 `null` 时仅进入主页面。
 * @param recurrenceId 要定位的重复发生身份；非空时 [scheduleId] 也必须非空。
 * @throws IllegalArgumentException 仅提供 [recurrenceId]、未提供其所属 [scheduleId] 时抛出。
 */
@Serializable
data class ScheduleMainNavArgument(
  val scheduleId: ScheduleId? = null,
  val recurrenceId: RecurrenceId? = null,
) : AppNavArgument {
  init {
    require(recurrenceId == null || scheduleId != null) {
      "recurrenceId requires a scheduleId"
    }
  }

  /**
   * 打开日程主页；重复打开同一个定位目标时不堆叠页面，而是通知现有页面重新定位并播放高亮。
   *
   * Android 的系统日历可能在应用已停留于目标日程时再次发送完全相同的 deeplink。通用导航栈会
   * 拒绝与栈顶完全相等的参数，因此这里把该场景转为一次页面内事件；其他参数仍按标准导航入栈。
   */
  override fun navigate() {
    if (appNavBackStack.lastOrNull() == this) {
      ScheduleMainNavigationRequests.dispatch(this)
    } else {
      super.navigate()
    }
  }
}

/**
 * 日程主页对重复定位请求的进程内事件通道。
 *
 * 通道只承载已经通过导航解码与类型校验的参数，不负责持久化；缓冲用于覆盖 Intent 到达与页面协程
 * 恢复之间的短暂时序差，当前可见的日程主页消费后即移除。
 */
object ScheduleMainNavigationRequests {
  private val channel = Channel<ScheduleMainNavArgument>(capacity = Channel.BUFFERED)

  /** 重复定位请求流；日程主页应仅处理与自身参数相等的请求。 */
  val requests: Flow<ScheduleMainNavArgument> = channel.receiveAsFlow()

  /** 导航入口在主线程提交已校验请求；缓冲关闭等异常不应影响现有页面。 */
  internal fun dispatch(argument: ScheduleMainNavArgument) {
    channel.trySend(argument)
  }
}
