package com.cyxbs.pages.schedule.ui.edit

/**
 * 重复日程执行编辑或删除时由用户选择的作用范围。
 *
 * [THIS_ONLY] 仅写入当前 [com.cyxbs.pages.schedule.domain.model.RecurrenceId] 对应的实例例外；
 * [THIS_AND_FOLLOWING] 从当前原始实例锚点截断或拆分系列；[ALL] 直接修改或删除整个系列。
 * 非重复日程应由调用方固定使用 [ALL]，其余范围都要求存在稳定的实例标识。
 */
enum class EditScope { THIS_ONLY, THIS_AND_FOLLOWING, ALL }
