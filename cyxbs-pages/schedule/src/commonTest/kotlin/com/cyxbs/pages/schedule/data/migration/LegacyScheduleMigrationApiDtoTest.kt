package com.cyxbs.pages.schedule.data.migration

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 旧接口 DTO 的历史 JSON 兼容测试，确保字段缺失与服务端冗余字段不会阻断整批迁移。 */
class LegacyScheduleMigrationApiDtoTest {

  /** 事务响应允许服务端继续携带学号、学期等迁移不使用的旧字段。 */
  @Test
  fun transactionDto_decodesRequiredFieldsAndIgnoresLegacyExtras() {
    val transactions = json.decodeFromString<List<LegacyTransactionDto>>(
      """
      [
        {
          "id": 17,
          "title": "接口验收",
          "content": "旧事务描述",
          "time": 10,
          "term": "2025-2026-2",
          "stuNum": "20210000",
          "date": [
            {
              "begin_lesson": 5,
              "day": 2,
              "period": 2,
              "week": [1, 3, 5],
              "unused": true
            }
          ]
        }
      ]
      """.trimIndent()
    )

    val transaction = transactions.single()
    assertEquals(17, transaction.remoteId)
    assertEquals("接口验收", transaction.title)
    assertEquals(10, transaction.time)
    assertEquals(listOf(1, 3, 5), transaction.date.single().week)
  }

  /** 缺少旧事务可选字段时使用空描述、无提醒和空时间位置，不因默认值产生虚构数据。 */
  @Test
  fun transactionDto_missingOptionalFieldsUsesSafeDefaults() {
    val transaction = json.decodeFromString<LegacyTransactionDto>(
      """{"id":1,"title":"只有必填字段"}"""
    )

    assertEquals("", transaction.content)
    assertEquals(0, transaction.time)
    assertTrue(transaction.date.isEmpty())
  }

  /** 旧清单完整响应应保留完成态、重复选择器、分组和置顶字段，并忽略未使用的派生状态。 */
  @Test
  fun todoDto_decodesMigrationFieldsAndIgnoresDerivedExtras() {
    val response = json.decodeFromString<LegacyTodoListDto>(
      """
      {
        "sync_time": 1700000000,
        "changed_todo_array": [
          {
            "todo_id": 9,
            "title": "准备答辩",
            "detail": "整理材料",
            "is_done": 1,
            "last_modify_time": 1700000000000,
            "type": "study",
            "end_time": "2026年3月8日14:30",
            "is_pinned": 1,
            "is_over": 1,
            "remind_mode": {
              "repeat_mode": 2,
              "notify_datetime": "2026年3月8日14:20",
              "week": [2, 4]
            }
          }
        ]
      }
      """.trimIndent()
    )

    val todo = response.todos.orEmpty().single()
    assertEquals(9, todo.todoId)
    assertEquals(1, todo.isDone)
    assertEquals("study", todo.type)
    assertEquals(1, todo.isPinned)
    assertEquals(LegacyTodoRemindModeDto.WEEKLY, todo.remindMode.repeatMode)
    assertEquals(listOf(2, 4), todo.remindMode.week)
  }

  /** 空增量数组和缺失数组都必须变成可安全 orEmpty 的结果，允许账号没有任何旧清单。 */
  @Test
  fun todoListDto_supportsEmptyAndMissingTodoArrays() {
    val empty = json.decodeFromString<LegacyTodoListDto>(
      """{"sync_time":1,"changed_todo_array":[]}"""
    )
    val missing = json.decodeFromString<LegacyTodoListDto>(
      """{"sync_time":2}"""
    )

    assertTrue(empty.todos.orEmpty().isEmpty())
    assertNull(missing.todos)
  }

  /** 旧服务无数据时可能显式返回 null，必须与缺失数组一样安全解释为空列表。 */
  @Test
  fun todoListDto_supportsExplicitNullTodoArray() {
    val response = json.decodeFromString<LegacyTodoListDto>(
      """{"sync_time":3,"changed_todo_array":null}"""
    )

    assertTrue(response.todos.orEmpty().isEmpty())
  }

  /** 清单缺少可选字段时保持无时间、未完成、其他分组和无重复的旧协议默认语义。 */
  @Test
  fun todoDto_missingOptionalFieldsUsesSafeDefaults() {
    val todo = json.decodeFromString<LegacyTodoDto>(
      """{"todo_id":1,"title":"稍后处理"}"""
    )

    assertEquals("", todo.detail)
    assertEquals(0, todo.isDone)
    assertEquals("other", todo.type)
    assertNull(todo.endTime)
    assertEquals(0, todo.isPinned)
    assertEquals(LegacyTodoRemindModeDto.NONE, todo.remindMode.repeatMode)
    assertTrue(todo.remindMode.date.isEmpty())
    assertTrue(todo.remindMode.week.isEmpty())
    assertTrue(todo.remindMode.day.isEmpty())
    assertNull(todo.remindMode.notifyDateTime)
  }

  private companion object {
    val json = Json {
      ignoreUnknownKeys = true
      explicitNulls = false
    }
  }
}
