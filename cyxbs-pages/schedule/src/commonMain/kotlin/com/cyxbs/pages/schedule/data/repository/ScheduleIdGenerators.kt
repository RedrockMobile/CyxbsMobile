package com.cyxbs.pages.schedule.data.repository

import com.cyxbs.pages.schedule.domain.model.ScheduleId
import com.cyxbs.pages.schedule.domain.uuid.UuidV7Generator

/**
 * 生成 Schedule v2 的日程标识。
 *
 * 接口只负责为新日程或拆分后的新系列提供唯一 ID，并允许测试注入确定序列；同步版本、原子批次与本地 pending
 * 元数据均由各自边界负责，不在这里生成。
 */
interface ScheduleIdGenerators {
  /** 生成日程或拆分后新系列的唯一标识。 */
  suspend fun scheduleId(): ScheduleId
}

/**
 * 基于 UUID v7 的默认日程标识生成器。
 *
 * 构造时可注入共享的单调 [UuidV7Generator]；默认构造则创建独立生成器。每次调用只推进一次 UUID 序列。
 */
class UuidV7ScheduleIdGenerators(
  private val generator: UuidV7Generator = UuidV7Generator(),
) : ScheduleIdGenerators {
  override suspend fun scheduleId(): ScheduleId = ScheduleId(generator.nextString())
}
