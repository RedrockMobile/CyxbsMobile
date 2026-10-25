package com.cyxbs.pages.schedule.domain.model

import kotlin.jvm.JvmInline

typealias ScheduleId = com.cyxbs.pages.schedule.api.ScheduleId

/** 稳定的分类标识；领域层只保证非空，不解释其持久化编码。 */
@JvmInline
value class CategoryId(val value: String) {
  init { require(value.isNotBlank()) { "CategoryId must not be blank" } }
  override fun toString(): String = value
}
