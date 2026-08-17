package com.cyxbs.pages.schedule.domain.model

import kotlin.jvm.JvmInline

typealias ScheduleId = com.cyxbs.pages.schedule.api.ScheduleId

private val UUID_V7_CANONICAL = Regex(
  "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
)

/**
 * 一次变更的幂等标识，采用严格规范的 UUIDv7 文本。
 *
 * 服务端可据此识别重试是否属于同一次操作；因此文本形式也必须唯一，不能接受大小写或连字符变体。
 */
@JvmInline
value class MutationId private constructor(val value: String) {
  override fun toString(): String = value

  companion object {
    /**
     * 校验规范 UUIDv7 后创建标识。
     *
     * @throws IllegalArgumentException [value] 不是规范 UUIDv7 时抛出。
     */
    operator fun invoke(value: String): MutationId {
      require(UUID_V7_CANONICAL.matches(value)) { "MutationId must be a canonical UUIDv7" }
      return MutationId(value)
    }

    /** 尝试解析 [value]；格式不规范时返回 `null`，不抛出格式异常。 */
    fun parseOrNull(value: String): MutationId? =
      if (UUID_V7_CANONICAL.matches(value)) MutationId(value) else null
  }
}

/** 稳定的分类标识；领域层只保证非空，不解释其持久化编码。 */
@JvmInline
value class CategoryId(val value: String) {
  init { require(value.isNotBlank()) { "CategoryId must not be blank" } }
  override fun toString(): String = value
}

/** 一条日程内部提醒的稳定标识，用于更新时区分多个提醒。 */
@JvmInline
value class ReminderId(val value: String) {
  init { require(value.isNotBlank()) { "ReminderId must not be blank" } }
  override fun toString(): String = value
}
