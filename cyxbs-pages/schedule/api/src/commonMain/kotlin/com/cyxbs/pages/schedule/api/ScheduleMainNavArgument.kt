package com.cyxbs.pages.schedule.api

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.navigation.AppNavArgument
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

private val UUID_V7_CANONICAL = Regex(
  "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
)

/**
 * 日程在导航层与领域层共用的 UUIDv7 标识。
 *
 * 构造时只接受小写、带连字符且版本位与变体位正确的规范 UUIDv7，避免同一标识因文本形式不同而在
 * 导航参数、持久化键和同步命令之间产生歧义。
 */
@Serializable
@JvmInline
value class ScheduleId private constructor(val value: String) {
  override fun toString(): String = value

  companion object {
    /**
     * 从字符串创建日程标识。
     *
     * @param value 待校验的 UUIDv7 文本；必须是规范小写形式。
     * @return 与输入文本对应的 [ScheduleId]。
     * @throws IllegalArgumentException 输入不符合规范 UUIDv7 时抛出。
     */
    operator fun invoke(value: String): ScheduleId {
      require(UUID_V7_CANONICAL.matches(value)) { "ScheduleId must be a canonical UUIDv7" }
      return ScheduleId(value)
    }

    /**
     * 尝试解析规范 UUIDv7 文本，适合处理深链或其他不可信输入。
     *
     * @return 解析成功时返回标识，否则返回 `null`，不会抛出格式异常。
     */
    fun parseOrNull(value: String): ScheduleId? =
      if (UUID_V7_CANONICAL.matches(value)) ScheduleId(value) else null
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
 * 该契约有意不兼容旧版 `Long` 标识和仅含日期的深链：调用方必须传递与仓库命令一致的 UUIDv7 及完整
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
}
