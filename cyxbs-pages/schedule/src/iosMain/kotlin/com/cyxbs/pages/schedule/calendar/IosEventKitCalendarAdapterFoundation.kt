package com.cyxbs.pages.schedule.calendar

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toMinuteTimeDate
import com.cyxbs.pages.schedule.domain.calendar.CalendarCanonicalBaselineMapper
import com.cyxbs.pages.schedule.domain.calendar.CalendarEventProjection
import com.cyxbs.pages.schedule.domain.calendar.CalendarExportScope
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionFingerprint
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionId
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionKind
import com.cyxbs.pages.schedule.domain.calendar.CalendarProjectionUriCodec
import com.cyxbs.pages.schedule.domain.calendar.CalendarRecurrenceCanonicalizer
import com.cyxbs.pages.schedule.domain.calendar.CalendarTiming
import com.cyxbs.pages.schedule.domain.calendar.CanonicalCalendarFields
import com.cyxbs.pages.schedule.domain.calendar.ManagedCalendarEvent
import com.cyxbs.pages.schedule.domain.calendar.PlatformCalendarEventRef
import com.cyxbs.pages.schedule.domain.time.LocalDateTimeResolution
import com.cyxbs.pages.schedule.domain.time.ScheduleDstResolver
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * EventKit 日期在纯映射层中的无 Foundation 表示。
 *
 * 后续 gateway 负责在 `NSDate` 与本类型之间精确转换；本切片不构造 `EKEventStore`、不申请权限，也不访问
 * 系统日历。保留秒和纳秒可让 mapper 明确拒绝超出 Schedule 分钟领域的值，而不是静默截断。
 */
data class IosEventKitRawMoment(
  val epochSeconds: Long,
  val nanoseconds: Int,
)

/**
 * EventKit 原始提醒。
 *
 * `relativeOffsetSeconds` 名称只描述 `NSTimeInterval` 的物理单位，不表示已量化为整秒；该 `Double`
 * 必须原样保留 `EKAlarm.relativeOffset` 的值/位模式。`null` 表示绝对提醒或无法证明为相对提醒。
 * foundation 不能先把 `TimeInterval` 量化为整秒、纳秒或任何 `hasFraction` 标记：
 * `-60.0000000005s` 与分钟边界相邻的 Double 都可能在量化后伪装成 `-1min`，从而改变提醒时刻，
 * 因此只有在 mapper 验证 finite、整秒、整分钟和范围后，才允许生成整数秒的写入 payload。
 */
data class IosEventKitRawAlarm(
  val relativeOffsetSeconds: Double?,
)

/**
 * 未来 EventKit bridge 读取后交给纯 mapper 的事件快照。
 *
 * `externalUri` 必须来自 `EKEvent.url` 的绝对文本，`eventIdentifier` 仅是可失效的 opaque cache。bridge 必须如实
 * 标记 recurrence exception；不能以主事件字段伪装独立 occurrence。
 */
data class IosEventKitRawEvent(
  val eventIdentifier: String?,
  val externalUri: String?,
  val title: String?,
  val notes: String?,
  val start: IosEventKitRawMoment,
  val endExclusive: IosEventKitRawMoment,
  val timeZoneId: String?,
  val allDay: Boolean,
  val recurrenceRules: List<String>,
  val alarms: List<IosEventKitRawAlarm>,
  val hasOccurrenceException: Boolean,
)

/** 仅供后续 EventKit 写桥消费的纯写入 payload；不携带 `EKEvent` 或任何系统句柄。 */
data class IosEventKitWritePayload(
  val externalUri: String,
  val title: String,
  val notes: String,
  val timing: IosEventKitWriteTiming,
  val recurrenceRule: String?,
  val alarms: List<IosEventKitRelativeAlarm>,
)

/** EventKit 写入的时间形状；全天以日期半开区间表示，绝不以固定 24 小时替代。 */
sealed interface IosEventKitWriteTiming {
  /** 定时/Deadline 的 instant 半开区间，以及供 EventKit 解释墙上时间的 IANA 时区。 */
  data class Timed(
    val start: IosEventKitRawMoment,
    val endExclusive: IosEventKitRawMoment,
    val timeZoneId: String,
  ) : IosEventKitWriteTiming

  /** 全天事件的日期半开区间；`endExclusiveDate` 不是最后一天的 23:59。 */
  data class AllDay(
    val startDate: Date,
    val endExclusiveDate: Date,
  ) : IosEventKitWriteTiming
}

/** 相对开始时间的 EventKit 提醒；负数代表开始前，零分钟提醒保持为零。 */
data class IosEventKitRelativeAlarm(
  val relativeOffsetSeconds: Long,
)

/** 已完整验证并可供 common planner 使用的 EventKit 托管事件快照。 */
data class IosEventKitManagedEventSnapshot(
  val managedEvent: ManagedCalendarEvent,
  val canonicalFields: CanonicalCalendarFields,
)

/** 纯 mapper 的 fail-closed 错误类型；调用方必须将其当作 Unsupported/诊断，而不能继续写入。 */
enum class IosEventKitMappingError {
  INVALID_CANONICAL_URI,
  SCOPE_MISMATCH,
  UNSUPPORTED_OCCURRENCE_EXCEPTION,
  MISSING_EVENT_IDENTIFIER,
  INVALID_EVENT_IDENTIFIER,
  INVALID_TITLE,
  INVALID_DESCRIPTION,
  INVALID_TIME_ZONE,
  NON_MINUTE_PRECISION,
  IRREVERSIBLE_WALL_TIME,
  INVALID_DATE_INTERVAL,
  UNSUPPORTED_RECURRENCE,
  UNSUPPORTED_ALARM,
  INVALID_CANONICAL_FIELDS,
  INVALID_PROJECTION_SHAPE,
  INVALID_PROJECTION_FINGERPRINT,
}

/** 严格映射结果；与 `null` 区分，避免 future gateway 把不支持事件误认为不存在。 */
sealed interface IosEventKitMappingResult<out T> {
  data class Mapped<T>(val value: T) : IosEventKitMappingResult<T>

  data class Unsupported(val error: IosEventKitMappingError) : IosEventKitMappingResult<Nothing>
}

/**
 * iOS EventKit 的纯合同 mapper。
 *
 * 本对象只在 common 投影、严格 raw snapshot 和可 fake payload 之间转换。canonical v2 URI 是唯一业务身份，
 * `eventIdentifier` 只在输出 [ManagedCalendarEvent] 时作为短期缓存；这里没有 `EKEventStore`、权限、日历选择、
 * source 枚举或任何创建/修改/删除平台资源的副作用。
 */
object IosEventKitCalendarAdapterFoundation {
  /**
   * 将 common 投影映射为未来 EventKit bridge 可写的纯 payload。
   *
   * 解析墙上时间时复用统一 DST resolver：gap 无法随 EventKit instant 无歧义读回，立即拒绝；overlap 使用
   * common 规定的 EARLIER_INSTANT。无法回写为完整分钟 instant 的历史时区结果同样拒绝。Deadline 固定为一分钟，
   * 全天保留日期半开区间。
   */
  fun toWritePayload(
    projection: CalendarEventProjection,
  ): IosEventKitMappingResult<IosEventKitWritePayload> {
    if (CalendarProjectionUriCodec.decodeOrNull(projection.externalUri) != projection.id) {
      return unsupported(IosEventKitMappingError.INVALID_CANONICAL_URI)
    }
    if (projection.id.kind == CalendarProjectionKind.OCCURRENCE_EXCEPTION) {
      return unsupported(IosEventKitMappingError.UNSUPPORTED_OCCURRENCE_EXCEPTION)
    }
    if (!hasSupportedProjectionShape(
        kind = projection.id.kind,
        timing = projection.timing,
        recurrenceRule = projection.recurrenceRule,
      )
    ) {
      return unsupported(IosEventKitMappingError.INVALID_PROJECTION_SHAPE)
    }
    val canonicalFields = runCatching {
      CalendarCanonicalBaselineMapper.toCalendarFields(projection)
    }.getOrNull() ?: return unsupported(IosEventKitMappingError.INVALID_CANONICAL_FIELDS)
    val expectedFingerprint = CalendarProjectionFingerprint.compute(
      externalUri = projection.externalUri,
      title = projection.title,
      description = projection.description,
      timing = projection.timing,
      recurrenceRule = projection.recurrenceRule,
      reminderMinutes = projection.deviceReminderMinutes,
    )
    if (projection.fingerprint != expectedFingerprint) {
      return unsupported(IosEventKitMappingError.INVALID_PROJECTION_FINGERPRINT)
    }
    val timing = projection.timing.toWriteTimingOrError() ?: return unsupported(
      if (projection.timing is CalendarTiming.AllDay) {
        IosEventKitMappingError.INVALID_DATE_INTERVAL
      } else {
        IosEventKitMappingError.IRREVERSIBLE_WALL_TIME
      },
    )
    val alarms = projection.deviceReminderMinutes.map { minutes ->
      // EventKit 相对提醒以“距开始的秒数”表达；Schedule 的正分钟偏移表示开始前提醒。
      IosEventKitRelativeAlarm(relativeOffsetSeconds = -minutes.toLong() * SECONDS_PER_MINUTE)
    }
    return IosEventKitMappingResult.Mapped(
      IosEventKitWritePayload(
        externalUri = projection.externalUri,
        title = canonicalFields.title,
        notes = canonicalFields.description,
        timing = timing,
        recurrenceRule = canonicalFields.recurrenceRule,
        alarms = alarms,
      ),
    )
  }

  /**
   * 验证 canonical URI kind 与 timing/RRULE 的唯一可逆形状。
   *
   * EventKit 只能保存通用事件字段，无法在 read path 推断被伪装的 Deadline 或单次事件。写入和读取统一在
   * 此处 fail-closed，防止形状不一致的 URI 获得另一个业务语义。
   */
  private fun hasSupportedProjectionShape(
    kind: CalendarProjectionKind,
    timing: CalendarTiming,
    recurrenceRule: String?,
  ): Boolean = when (kind) {
    CalendarProjectionKind.DEADLINE -> timing is CalendarTiming.Deadline && recurrenceRule == null
    CalendarProjectionKind.SERIES_MASTER -> timing !is CalendarTiming.Deadline && recurrenceRule != null
    CalendarProjectionKind.SINGLE -> timing !is CalendarTiming.Deadline && recurrenceRule == null
    CalendarProjectionKind.OCCURRENCE_EXCEPTION -> false
  }

  /**
   * 从 future EventKit bridge 的纯 raw snapshot 重建 common 托管事件。
   *
   * 只接受当前 [scope] 的 canonical v2 URI。秒/纳秒、非 UTC 全天边界、无法按统一 DST 策略逆解析的重叠时间、
   * 非受限 RRULE、绝对或非整分钟提醒及 occurrence exception 均返回 [IosEventKitMappingResult.Unsupported]。
   */
  fun toManagedEvent(
    raw: IosEventKitRawEvent,
    scope: CalendarExportScope,
  ): IosEventKitMappingResult<IosEventKitManagedEventSnapshot> {
    if (raw.hasOccurrenceException) return unsupported(IosEventKitMappingError.UNSUPPORTED_OCCURRENCE_EXCEPTION)
    val externalUri =
      raw.externalUri ?: return unsupported(IosEventKitMappingError.INVALID_CANONICAL_URI)
    val id = CalendarProjectionUriCodec.decodeOrNull(externalUri)
      ?: return unsupported(IosEventKitMappingError.INVALID_CANONICAL_URI)
    if (id.scope != scope) return unsupported(IosEventKitMappingError.SCOPE_MISMATCH)
    if (id.kind == CalendarProjectionKind.OCCURRENCE_EXCEPTION) {
      return unsupported(IosEventKitMappingError.UNSUPPORTED_OCCURRENCE_EXCEPTION)
    }
    val identifier =
      raw.eventIdentifier ?: return unsupported(IosEventKitMappingError.MISSING_EVENT_IDENTIFIER)
    if (identifier.isBlank() || identifier.length > PlatformCalendarEventRef.MAX_LENGTH) {
      return unsupported(IosEventKitMappingError.INVALID_EVENT_IDENTIFIER)
    }
    val title = raw.title?.takeIf { it.isNotBlank() && it == it.trim() }
      ?: return unsupported(IosEventKitMappingError.INVALID_TITLE)
    // EventKit 的 nil notes 与空文本具有相同的“没有描述”业务语义；其余文本不得由 mapper trim 或修复。
    val notes = raw.notes.orEmpty()
    if (notes != notes.trim()) return unsupported(IosEventKitMappingError.INVALID_DESCRIPTION)
    val recurrence = when (raw.recurrenceRules.size) {
      0 -> null
      1 -> CalendarRecurrenceCanonicalizer.canonicalizeOrNull(
        raw.recurrenceRules.single(),
        raw.allDay
      )
        ?: return unsupported(IosEventKitMappingError.UNSUPPORTED_RECURRENCE)

      else -> return unsupported(IosEventKitMappingError.UNSUPPORTED_RECURRENCE)
    }
    val reminders = raw.alarms.toReminderMinutesOrError()
      ?: return unsupported(IosEventKitMappingError.UNSUPPORTED_ALARM)
    val timing = raw.toCalendarTimingOrError(id) ?: return unsupported(
      raw.precisionOrTimeError(),
    )
    if (!hasSupportedProjectionShape(id.kind, timing, recurrence)) {
      return unsupported(IosEventKitMappingError.INVALID_PROJECTION_SHAPE)
    }
    val fields = runCatching {
      CanonicalCalendarFields(
        title = title,
        description = notes,
        timing = timing,
        recurrenceRule = recurrence,
        deviceReminderMinutes = reminders,
      )
    }.getOrNull() ?: return unsupported(IosEventKitMappingError.INVALID_CANONICAL_FIELDS)
    val fingerprint = CalendarProjectionFingerprint.compute(
      externalUri = externalUri,
      title = fields.title,
      description = fields.description,
      timing = fields.timing,
      recurrenceRule = fields.recurrenceRule,
      reminderMinutes = fields.deviceReminderMinutes,
    )
    return IosEventKitMappingResult.Mapped(
      IosEventKitManagedEventSnapshot(
        managedEvent = ManagedCalendarEvent(
          id = id,
          fingerprint = fingerprint,
          platformEventRef = PlatformCalendarEventRef(identifier),
        ),
        canonicalFields = fields,
      ),
    )
  }

  /** 将三类 common 时间写成 EventKit 未来 bridge 可精确消费的形状。 */
  private fun CalendarTiming.toWriteTimingOrError(): IosEventKitWriteTiming? = when (this) {
    is CalendarTiming.Timed -> {
      val start = resolveMinuteMomentOrNull(this.start, this.timeZoneId) ?: return null
      IosEventKitWriteTiming.Timed(
        start = start,
        endExclusive = start.plusWholeMinutesOrNull(this.durationMinutes) ?: return null,
        timeZoneId = this.timeZoneId,
      )
    }

    is CalendarTiming.Deadline -> {
      val start = resolveMinuteMomentOrNull(this.due, this.timeZoneId) ?: return null
      IosEventKitWriteTiming.Timed(
        start = start,
        endExclusive = start.plusWholeMinutesOrNull(1) ?: return null,
        timeZoneId = this.timeZoneId,
      )
    }

    is CalendarTiming.AllDay -> IosEventKitWriteTiming.AllDay(
      startDate = this.startDate,
      endExclusiveDate = this.startDate.plusDays(this.durationDays),
    )
  }

  /**
   * 按 common DST 决议把分钟墙上时间转为无秒/nano 的 EventKit moment。
   *
   * gap 的原始墙上时间无法仅凭 EventKit instant 在读回时恢复，因此拒绝写入；overlap 保留 common 规定的
   * EARLIER_INSTANT，避免把平台的隐式选择当作跨端协议。
   */
  private fun resolveMinuteMomentOrNull(
    local: MinuteTimeDate,
    timeZoneId: String,
  ): IosEventKitRawMoment? {
    val resolved = ScheduleDstResolver.resolve(local, timeZoneId)
    if (resolved !is LocalDateTimeResolution.Exact &&
      resolved !is LocalDateTimeResolution.OverlapResolved
    ) return null
    val instant = resolved.instant
    if (instant.epochSeconds % SECONDS_PER_MINUTE != 0L || instant.nanosecondsOfSecond != 0) return null
    return IosEventKitRawMoment(instant.epochSeconds, 0)
  }

  /** 只生成完整分钟的结束 instant，防止将毫秒意外写入未来 EventKit bridge。 */
  private fun IosEventKitRawMoment.plusWholeMinutesOrNull(minutes: Int): IosEventKitRawMoment? {
    if (minutes <= 0 || nanoseconds != 0) return null
    val instant = toInstantOrNull() ?: return null
    val end = runCatching { instant + minutes.toLong().minutes }.getOrNull() ?: return null
    if (end.epochSeconds % SECONDS_PER_MINUTE != 0L || end.nanosecondsOfSecond != 0) return null
    return IosEventKitRawMoment(end.epochSeconds, 0)
  }

  /** 读取 Timed/Deadline 时先验证整分钟和统一 DST reverse mapping。 */
  private fun IosEventKitRawEvent.toCalendarTimingOrError(
    id: CalendarProjectionId,
  ): CalendarTiming? = if (allDay) {
    toAllDayTimingOrNull()
  } else {
    toTimedTimingOrNull(id.kind)
  }

  /** 全天只接受 UTC/无时区的两个 UTC 午夜，并以日期半开区间恢复。 */
  private fun IosEventKitRawEvent.toAllDayTimingOrNull(): CalendarTiming.AllDay? {
    if (timeZoneId != null && timeZoneId != TimeZone.UTC.id) return null
    val startInstant = start.toInstantOrNull() ?: return null
    val endInstant = endExclusive.toInstantOrNull() ?: return null
    if (start.nanoseconds != 0 || endExclusive.nanoseconds != 0) return null
    val startLocal = startInstant.toLocalDateTime(TimeZone.UTC)
    val endLocal = endInstant.toLocalDateTime(TimeZone.UTC)
    if (!startLocal.isMidnight() || !endLocal.isMidnight()) return null
    val days = (endInstant - startInstant).inWholeDays
    if (days <= 0 || startInstant + days.days != endInstant || days > Int.MAX_VALUE) return null
    return CalendarTiming.AllDay(
      startDate = Date(startLocal.year, startLocal.month.number, startLocal.day),
      durationDays = days.toInt(),
    )
  }

  /**
   * 定时事件必须可逆回统一 resolver 选定的分钟墙上时间；Deadline 额外冻结为一分钟。
   *
   * 先在 instant 轴拒绝非整分钟的 epoch 秒与纳秒，再转换到 local time。IANA 历史时区可能有秒级
   * offset：若先看 local 的分钟外观再反推，会把该秒级精度静默丢失，故此处必须 fail-closed。
   */
  private fun IosEventKitRawEvent.toTimedTimingOrNull(
    kind: CalendarProjectionKind,
  ): CalendarTiming? {
    if (start.epochSeconds % SECONDS_PER_MINUTE != 0L ||
      endExclusive.epochSeconds % SECONDS_PER_MINUTE != 0L ||
      start.nanoseconds != 0 ||
      endExclusive.nanoseconds != 0
    ) return null
    val zoneId = timeZoneId ?: return null
    val zone = runCatching { TimeZone.of(zoneId) }.getOrNull() ?: return null
    val startInstant = start.toInstantOrNull() ?: return null
    val endInstant = endExclusive.toInstantOrNull() ?: return null
    val startLocal = startInstant.toLocalDateTime(zone)
    if (startLocal.second != 0 || startLocal.nanosecond != 0) return null
    val startMinute = startLocal.toMinuteTimeDate()
    val resolved =
      ScheduleDstResolver.resolve(startMinute, zoneId) as? LocalDateTimeResolution.Resolved
        ?: return null
    if (resolved.instant != startInstant) return null
    val duration = (endInstant - startInstant).inWholeMinutes
    if (duration <= 0 || duration > Int.MAX_VALUE || startInstant + duration.minutes != endInstant) return null
    return if (kind == CalendarProjectionKind.DEADLINE) {
      if (duration != 1L) null else CalendarTiming.Deadline(startMinute, zoneId)
    } else {
      CalendarTiming.Timed(startMinute, duration.toInt(), zoneId)
    }
  }

  /**
   * 只接受 EventKit 的相对开始提醒，且其原始 Double 可无损还原为 Schedule 非负分钟。
   *
   * bridge 必须原样传入 `TimeInterval`，此处先验证 finite、整秒、整分钟和 Schedule 的分钟范围，之后才
   * 转为整数。不能先 round、truncate 或依据调用方给出的 `hasFraction` 标记判断：亚纳秒尾数和分钟边界
   * 的相邻 Double 一旦量化，都会被错误地伪装成另一条 DEVICE reminder。相同分钟的两条 alarm 也不能在
   * common 模型中无损表示。
   */
  private fun List<IosEventKitRawAlarm>.toReminderMinutesOrError(): List<Int>? {
    val minutes = map { alarm ->
      val offset = alarm.relativeOffsetSeconds ?: return null
      if (!offset.isFinite() || offset > 0.0) return null
      if (offset % 1.0 != 0.0 || offset % SECONDS_PER_MINUTE.toDouble() != 0.0) return null
      if (offset < -MAX_DEVICE_REMINDER_SECONDS) return null

      // 仅在原始 Double 已证明为范围内的整分钟后才转换，避免提前量化小数尾数。
      val seconds = offset.toLong()
      (-seconds / SECONDS_PER_MINUTE).toInt()
    }
    return minutes.sorted().takeIf { it.zipWithNext().all { (previous, next) -> previous < next } }
  }

  /** 将 precision、时区与 DST 失败分成对 UI/诊断稳定的 typed 结果。 */
  private fun IosEventKitRawEvent.precisionOrTimeError(): IosEventKitMappingError = when {
    start.nanoseconds != 0 || endExclusive.nanoseconds != 0 ||
        start.epochSeconds % SECONDS_PER_MINUTE != 0L ||
        endExclusive.epochSeconds % SECONDS_PER_MINUTE != 0L -> IosEventKitMappingError.NON_MINUTE_PRECISION

    !allDay && timeZoneId?.let { runCatching { TimeZone.of(it) }.isSuccess } != true ->
      IosEventKitMappingError.INVALID_TIME_ZONE

    else -> IosEventKitMappingError.IRREVERSIBLE_WALL_TIME
  }

  private fun IosEventKitRawMoment.toInstantOrNull(): Instant? {
    if (nanoseconds !in 0..999_999_999) return null
    return runCatching { Instant.fromEpochSeconds(epochSeconds, nanoseconds.toLong()) }.getOrNull()
  }

  private fun kotlinx.datetime.LocalDateTime.isMidnight(): Boolean =
    hour == 0 && minute == 0 && second == 0 && nanosecond == 0

  private fun <T> unsupported(error: IosEventKitMappingError): IosEventKitMappingResult<T> =
    IosEventKitMappingResult.Unsupported(error)


  /** Schedule DEVICE reminder 可无损表示的最大原始相对秒数。 */
  private val MAX_DEVICE_REMINDER_SECONDS = Int.MAX_VALUE.toDouble() * SECONDS_PER_MINUTE
  private const val SECONDS_PER_MINUTE = 60L
}
