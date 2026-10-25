package com.cyxbs.pages.schedule.domain.time

import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.components.config.time.toLocalDateTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * 将日程领域中的本地墙上时间解析为 IANA 时区中的确定瞬时。
 *
 * 不把 offset 写回领域或 wire：offset 是某一瞬时的秒级派生事实，政治时区规则变化后可重新计算。解析结果
 * 显式标记正常、gap 修正、overlap 消歧与无法由分钟领域表达的 gap 调整，调用方不得依赖平台默认策略。
 */
object ScheduleDstResolver {
  /**
   * 解析 [local] 在 [timeZoneId] 中对应的瞬时。
   *
   * gap 按 `SHIFT_FORWARD_BY_GAP` 保留其在跳过区间内的相对位置；overlap 选择候选中较早的 instant。
   * requested/wire 仍是分钟墙上时间，effective local、instant 与 offset 都是秒级派生事实。
   */
  fun resolve(local: MinuteTimeDate, timeZoneId: String): LocalDateTimeResolution {
    val timeZone = try {
      TimeZone.of(timeZoneId)
    } catch (_: IllegalArgumentException) {
      return LocalDateTimeResolution.InvalidTimeZone(timeZoneId)
    }
    val requested = local.toLocalDateTime()
    val candidateOffsets = nearbyOffsets(requested, timeZone)
    val validCandidates = candidateOffsets.map { offset -> requested.toInstant(offset) to offset }
      .filter { (instant, _) -> instant.toLocalDateTime(timeZone) == requested }
      .sortedBy { (instant, _) -> instant }

    return when (validCandidates.size) {
      1 -> validCandidates.single().let { (instant, offset) ->
        LocalDateTimeResolution.Exact(local, requested, instant, offset)
      }
      2 -> validCandidates.first().let { (instant, offset) ->
        LocalDateTimeResolution.OverlapResolved(local, requested, instant, offset)
      }
      0 -> resolveGap(requested, local, timeZoneId, timeZone, candidateOffsets)
      else -> LocalDateTimeResolution.TransitionNotResolved(local, timeZoneId)
    }
  }

  /** common API 不公开 transition 枚举，先探测附近 offset，再以严格 round-trip 证明候选。 */
  private fun nearbyOffsets(requested: LocalDateTime, timeZone: TimeZone): List<UtcOffset> {
    val anchor = requested.toInstant(TimeZone.UTC)
    return listOf(
      timeZone.offsetAt(anchor - 48.hours),
      timeZone.offsetAt(anchor),
      timeZone.offsetAt(anchor + 48.hours),
    ).distinct()
  }

  /**
   * 仅从确实夹住请求时间的相邻 offset 对定位 gap，绝不按全局最小 offset 猜测 transition。
   * gap 调整量不是整分钟时返回机器可读失败，避免将秒静默写回 MinuteTimeDate/wire。
   */
  private fun resolveGap(
    requested: LocalDateTime,
    local: MinuteTimeDate,
    timeZoneId: String,
    timeZone: TimeZone,
    candidateOffsets: List<UtcOffset>,
  ): LocalDateTimeResolution {
    val pair = candidateOffsets.flatMap { offsetBefore ->
      candidateOffsets.mapNotNull { offsetAfter ->
        val delta = offsetAfter.totalSeconds - offsetBefore.totalSeconds
        if (delta <= 0) return@mapNotNull null
        val forward = requested.toInstant(offsetBefore).toLocalDateTime(timeZone)
        val backward = requested.toInstant(offsetAfter).toLocalDateTime(timeZone)
        if (forward > requested && backward < requested &&
          forward.utcDistanceSecondsFrom(requested) == delta.toLong() &&
          requested.utcDistanceSecondsFrom(backward) == delta.toLong()
        ) GapOffsetPair(offsetBefore, offsetAfter, forward, delta) else null
      }
    }.minByOrNull { it.adjustmentSeconds }
      ?: return LocalDateTimeResolution.TransitionNotResolved(local, timeZoneId)

    if (pair.adjustmentSeconds % 60 != 0) {
      return LocalDateTimeResolution.GapAdjustmentNotMinuteAligned(
        requestedLocal = local,
        timeZoneId = timeZoneId,
        adjustmentSeconds = pair.adjustmentSeconds,
        offsetBefore = pair.offsetBefore,
        offsetAfter = pair.offsetAfter,
      )
    }
    val instant = requested.toInstant(pair.offsetBefore)
    return LocalDateTimeResolution.GapShifted(
      requestedLocal = local,
      effectiveLocal = pair.effectiveLocal,
      instant = instant,
      offset = timeZone.offsetAt(instant),
      adjustmentSeconds = pair.adjustmentSeconds,
    )
  }

  private fun LocalDateTime.utcDistanceSecondsFrom(other: LocalDateTime): Long =
    (toInstant(TimeZone.UTC) - other.toInstant(TimeZone.UTC)).inWholeSeconds

  private data class GapOffsetPair(
    val offsetBefore: UtcOffset,
    val offsetAfter: UtcOffset,
    val effectiveLocal: LocalDateTime,
    val adjustmentSeconds: Int,
  )
}

/** 本地墙上时间的确定性解析结果；offset 只是派生事实，不写入领域或 wire。 */
sealed interface LocalDateTimeResolution {
  /** 成功解析结果的公共视图；effective local 允许保留历史秒级时区事实。 */
  sealed interface Resolved : LocalDateTimeResolution {
    val requestedLocal: MinuteTimeDate
    val effectiveLocal: LocalDateTime
    val instant: Instant
    val offset: UtcOffset
  }

  /** IANA 标识不可解析。 */
  data class InvalidTimeZone(val timeZoneId: String) : LocalDateTimeResolution

  /** 请求墙上时间唯一对应一个 instant；历史 offset 即使含秒也属于可无损成功结果。 */
  data class Exact(
    override val requestedLocal: MinuteTimeDate,
    override val effectiveLocal: LocalDateTime,
    override val instant: Instant,
    override val offset: UtcOffset,
  ) : Resolved

  /** gap 按缺口长度向前平移后的成功结果；[adjustmentSeconds] 已保证为整分钟。 */
  data class GapShifted(
    override val requestedLocal: MinuteTimeDate,
    override val effectiveLocal: LocalDateTime,
    override val instant: Instant,
    override val offset: UtcOffset,
    val adjustmentSeconds: Int,
  ) : Resolved

  /** overlap 中按 EARLIER_INSTANT 选择的成功结果。 */
  data class OverlapResolved(
    override val requestedLocal: MinuteTimeDate,
    override val effectiveLocal: LocalDateTime,
    override val instant: Instant,
    override val offset: UtcOffset,
  ) : Resolved

  /** gap 调整量含秒，无法满足冻结的分钟领域边界；Exact/Overlap 的秒级 offset 不走此分支。 */
  data class GapAdjustmentNotMinuteAligned(
    val requestedLocal: MinuteTimeDate,
    val timeZoneId: String,
    val adjustmentSeconds: Int,
    val offsetBefore: UtcOffset,
    val offsetAfter: UtcOffset,
  ) : LocalDateTimeResolution

  /** 附近 offset 无法证明合法候选或 gap transition。 */
  data class TransitionNotResolved(
    val requestedLocal: MinuteTimeDate,
    val timeZoneId: String,
  ) : LocalDateTimeResolution
}
