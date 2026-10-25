package com.cyxbs.pages.schedule.data.migration

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimeDate
import com.cyxbs.pages.schedule.domain.model.IsoWeekDay
import com.cyxbs.pages.schedule.domain.model.RecurrenceEnd
import com.cyxbs.pages.schedule.domain.model.RecurrenceFrequency
import com.cyxbs.pages.schedule.domain.model.ScheduleKind
import com.cyxbs.pages.schedule.domain.model.ScheduleTiming
import com.cyxbs.pages.schedule.domain.model.ScheduleTodoState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** 旧数据迁移的纯映射回归测试，不访问网络、Room、Settings 或系统日历。 */
class LegacyScheduleMapperTest {

  /** 迁移 UUID 必须在重试时稳定、隔离不同旧资源，并带正确的 v5 version 与 RFC variant。 */
  @Test
  fun deterministicUuid_isStableUuidV5AndSeparatesResources() {
    val first = LegacyScheduleMapper.deterministicScheduleUuid("20210000|todo|1")
    val retry = LegacyScheduleMapper.deterministicScheduleUuid("20210000|todo|1")
    val anotherResource = LegacyScheduleMapper.deterministicScheduleUuid("20210000|todo|2")
    val anotherSource = LegacyScheduleMapper.deterministicScheduleUuid("20210000|affair|1")

    assertEquals(first, retry)
    assertNotEquals(first, anotherResource)
    assertNotEquals(first, anotherSource)
    assertEquals('5', first[14])
    assertTrue(first[19] in "89ab")
  }

  /** 旧固定日期格式允许一位月日时分，但空值、格式错误和非法日期都必须安全返回 null。 */
  @Test
  fun legacyDateTimeParser_acceptsSupportedFormatAndRejectsInvalidValues() {
    assertEquals(
      MinuteTimeDate(2026, 3, 8, 4, 5),
      LegacyScheduleMapper.parseLegacyDateTime(" 2026年3月8日4:5 "),
    )
    assertNull(LegacyScheduleMapper.parseLegacyDateTime(null))
    assertNull(LegacyScheduleMapper.parseLegacyDateTime("2026-03-08 04:05"))
    assertNull(LegacyScheduleMapper.parseLegacyDateTime("2026年2月30日04:05"))
  }

  /** week=[0] 的旧事务迁成整学期周重复事务，并保留描述、提醒和课表归属。 */
  @Test
  fun allWeekTransaction_mapsWeeklyAffair() {
    val item = LegacyScheduleMapper.mapTransactions(
      accountId = ACCOUNT_ID,
      transactions = listOf(
        legacyTransaction(
          time = 15,
          date = listOf(legacyTransactionTime(day = 2, week = listOf(0))),
        )
      ),
      firstMonday = FIRST_MONDAY,
      maxWeek = 20,
    ).single()

    val timing = assertIs<ScheduleTiming.Timed>(item.schedule.timing)
    assertEquals(MinuteTimeDate(2026, 3, 4, 8, 0), timing.start)
    assertEquals(100, timing.durationMinutes)
    assertEquals(ScheduleKind.AFFAIR, item.schedule.kind)
    assertNull(item.schedule.todoState)
    assertTrue(item.schedule.linkedToCourse)
    assertEquals("旧事务内容", item.schedule.description)
    assertEquals(15, item.schedule.reminder?.offsetMinutes)
    assertEquals(RecurrenceFrequency.WEEKLY, item.schedule.recurrence?.frequency)
    assertEquals(setOf(IsoWeekDay.WEDNESDAY), item.schedule.recurrence?.byWeekDays)
    assertEquals(RecurrenceEnd.Count(20), item.schedule.recurrence?.end)
    assertEquals(Date(2026, 3, 4), item.schedule.recurrenceAnchorDate)
  }

  /** 指定周事务按有效周数拆成独立日程，重复周数去重且越界周数忽略。 */
  @Test
  fun selectedWeekTransaction_splitsDistinctValidWeeks() {
    val items = LegacyScheduleMapper.mapTransactions(
      accountId = ACCOUNT_ID,
      transactions = listOf(
        legacyTransaction(
          time = 0,
          date = listOf(legacyTransactionTime(day = 0, week = listOf(1, 3, 3, 21, -1))),
        )
      ),
      firstMonday = FIRST_MONDAY,
      maxWeek = 20,
    )

    assertEquals(2, items.size)
    assertEquals(
      listOf(Date(2026, 3, 2), Date(2026, 3, 16)),
      items.map { assertIs<ScheduleTiming.Timed>(it.schedule.timing).start.date },
    )
    assertTrue(items.all { it.schedule.recurrence == null })
    assertTrue(items.all { it.schedule.reminder == null })
    assertNotEquals(items[0].schedule.id, items[1].schedule.id)
  }

  /** 同一旧事务的多个时间位置必须独立迁移，不能只保留第一处或复用同一个 ID。 */
  @Test
  fun transactionWithMultipleTimePositions_mapsEveryPosition() {
    val items = LegacyScheduleMapper.mapTransactions(
      accountId = ACCOUNT_ID,
      transactions = listOf(
        legacyTransaction(
          date = listOf(
            legacyTransactionTime(beginLesson = 1, day = 0, period = 1, week = listOf(1)),
            legacyTransactionTime(beginLesson = 5, day = 4, period = 2, week = listOf(2)),
          ),
        )
      ),
      firstMonday = FIRST_MONDAY,
      maxWeek = 20,
    )

    assertEquals(2, items.size)
    assertEquals(MinuteTimeDate(2026, 3, 2, 8, 0), timedStartOf(items[0]))
    assertEquals(MinuteTimeDate(2026, 3, 13, 14, 0), timedStartOf(items[1]))
    assertNotEquals(items[0].schedule.id, items[1].schedule.id)
  }

  /** 中午、傍晚是旧事务协议的特殊 beginLesson，迁移后必须保持旧课表行对应的完整时间段。 */
  @Test
  fun transactionAtLegacyBreakRows_preservesLunchAndEveningTiming() {
    val items = LegacyScheduleMapper.mapTransactions(
      accountId = ACCOUNT_ID,
      transactions = listOf(
        legacyTransaction(
          date = listOf(
            legacyTransactionTime(beginLesson = -1, day = 0, period = 1, week = listOf(1)),
            legacyTransactionTime(beginLesson = -2, day = 1, period = 1, week = listOf(1)),
          ),
        )
      ),
      firstMonday = FIRST_MONDAY,
      maxWeek = 20,
    )

    val lunch = assertIs<ScheduleTiming.Timed>(items[0].schedule.timing)
    val evening = assertIs<ScheduleTiming.Timed>(items[1].schedule.timing)
    assertEquals(MinuteTimeDate(2026, 3, 2, 11, 55), lunch.start)
    assertEquals(125, lunch.durationMinutes)
    assertEquals(MinuteTimeDate(2026, 3, 3, 17, 55), evening.start)
    assertEquals(65, evening.durationMinutes)
  }

  /** 非正 period 与跨出旧课表末行的时间位置都属于旧脏数据，只跳过自身。 */
  @Test
  fun transactionWithInvalidPeriod_isSkippedWithoutAffectingValidPosition() {
    val items = LegacyScheduleMapper.mapTransactions(
      accountId = ACCOUNT_ID,
      transactions = listOf(
        legacyTransaction(
          date = listOf(
            legacyTransactionTime(beginLesson = 1, period = 0, week = listOf(1)),
            legacyTransactionTime(beginLesson = 12, period = 2, week = listOf(1)),
            legacyTransactionTime(beginLesson = 3, period = 1, week = listOf(1)),
          ),
        )
      ),
      firstMonday = FIRST_MONDAY,
      maxWeek = 20,
    )

    assertEquals(1, items.size)
    assertEquals(MinuteTimeDate(2026, 3, 2, 10, 15), timedStartOf(items.single()))
  }

  /** 空标题、非法星期、非法节次和空周数均不生成半成品事务。 */
  @Test
  fun invalidTransactionRecords_areSkippedWithoutBlockingOthers() {
    val items = LegacyScheduleMapper.mapTransactions(
      accountId = ACCOUNT_ID,
      transactions = listOf(
        legacyTransaction(title = "   "),
        legacyTransaction(date = listOf(legacyTransactionTime(day = 7, week = listOf(1)))),
        legacyTransaction(date = listOf(legacyTransactionTime(beginLesson = 99, week = listOf(1)))),
        legacyTransaction(date = listOf(legacyTransactionTime(week = emptyList()))),
        legacyTransaction(remoteId = 5, title = "有效事务"),
      ),
      firstMonday = FIRST_MONDAY,
      maxWeek = 20,
    )

    assertEquals(1, items.size)
    assertEquals("有效事务", items.single().schedule.title)
  }

  /** 旧清单通知时刻等于截止时刻时，0 必须保留为准时提醒，不能当作未设置。 */
  @Test
  fun todoWithSameNotifyAndDeadline_mapsExactReminder() {
    val dateTime = "2026年3月8日14:30"
    val item = mapTodos(
      legacyTodo(
        endTime = dateTime,
        remindMode = LegacyTodoRemindModeDto(notifyDateTime = dateTime),
      )
    ).single()

    assertIs<ScheduleTiming.Deadline>(item.schedule.timing)
    assertEquals(0, item.schedule.reminder?.offsetMinutes)
  }

  /** 截止/通知的常用组合分别映射为无提醒、准时、提前提醒或丢弃晚于截止的非法提醒。 */
  @Test
  fun nonRepeatingTodo_mapsDeadlineAndReminderMatrix() {
    val due = "2026年3月8日14:30"
    val notify = "2026年3月8日14:00"
    val lateNotify = "2026年3月8日15:00"
    val items = mapTodos(
      legacyTodo(todoId = 1, endTime = due),
      legacyTodo(
        todoId = 2,
        remindMode = LegacyTodoRemindModeDto(notifyDateTime = notify),
      ),
      legacyTodo(
        todoId = 3,
        endTime = due,
        remindMode = LegacyTodoRemindModeDto(notifyDateTime = notify),
      ),
      legacyTodo(
        todoId = 4,
        endTime = due,
        remindMode = LegacyTodoRemindModeDto(notifyDateTime = lateNotify),
      ),
      legacyTodo(todoId = 5, endTime = "非法时间"),
    )

    assertEquals(LegacyScheduleMapper.parseLegacyDateTime(due), deadlineOf(items[0]))
    assertTrue(items[0].schedule.reminder == null)
    assertEquals(LegacyScheduleMapper.parseLegacyDateTime(notify), deadlineOf(items[1]))
    assertEquals(0, items[1].schedule.reminder?.offsetMinutes)
    assertEquals(LegacyScheduleMapper.parseLegacyDateTime(due), deadlineOf(items[2]))
    assertEquals(30, items[2].schedule.reminder?.offsetMinutes)
    assertEquals(LegacyScheduleMapper.parseLegacyDateTime(due), deadlineOf(items[3]))
    assertTrue(items[3].schedule.reminder == null)
    assertEquals(ScheduleTiming.Unscheduled, items[4].schedule.timing)
    assertTrue(items[4].schedule.reminder == null)
  }

  /** 没有截止和通知的旧清单必须保持未排期，且不能产生提醒、重复或课表关联。 */
  @Test
  fun todoWithoutTime_mapsUnscheduledWithoutDerivedFields() {
    val item = mapTodos(legacyTodo()).single()

    assertEquals(ScheduleTiming.Unscheduled, item.schedule.timing)
    assertTrue(item.schedule.reminder == null)
    assertNull(item.schedule.recurrence)
    assertFalse(item.schedule.linkedToCourse)
  }

  /** 非重复完成态可以迁移；重复清单则必须保持待完成，不能把一次完成扩散到整个系列。 */
  @Test
  fun todoCompletion_mapsByRecurrenceSemantics() {
    val items = mapTodos(
      legacyTodo(todoId = 1, isDone = 1, endTime = "2026年3月8日14:30"),
      legacyTodo(
        todoId = 2,
        isDone = 1,
        endTime = "2026年3月31日14:30",
        remindMode = LegacyTodoRemindModeDto(
          repeatMode = LegacyTodoRemindModeDto.DAILY,
          notifyDateTime = "2026年3月8日14:30",
        ),
      ),
    )

    assertEquals(ScheduleTodoState.COMPLETED, items[0].schedule.todoState)
    assertEquals(ScheduleTodoState.PENDING, items[1].schedule.todoState)
  }

  /** 旧三类分组名称和端上置顶状态必须随清单一起映射，但分类 ID 由协调器统一解析。 */
  @Test
  fun todoCategoryAndPin_mapToMigrationMetadata() {
    val items = mapTodos(
      legacyTodo(todoId = 1, type = "study", isPinned = 1),
      legacyTodo(todoId = 2, type = "生活"),
      legacyTodo(todoId = 3, type = "unknown"),
      legacyTodo(todoId = 4, type = "other"),
      legacyTodo(todoId = 5, type = ""),
    )

    assertEquals(listOf("学习", "生活", "其他", "其他", "其他"), items.map { it.categoryName })
    assertTrue(items[0].pinned)
    assertFalse(items[1].pinned)
    assertTrue(items.all { it.schedule.categoryId == null })
  }

  /** 日重复使用下一次通知作为锚点、结束日作为 Until，并用 0 表达每次准时提醒。 */
  @Test
  fun dailyTodo_mapsSupportedRecurrenceAndExactReminder() {
    val item = mapTodos(
      legacyTodo(
        isDone = 1,
        endTime = "2026年3月31日14:30",
        remindMode = LegacyTodoRemindModeDto(
          repeatMode = LegacyTodoRemindModeDto.DAILY,
          notifyDateTime = "2026年3月8日14:30",
        ),
      )
    ).single()

    assertEquals(MinuteTimeDate(2026, 3, 8, 14, 30), deadlineOf(item))
    assertEquals(RecurrenceFrequency.DAILY, item.schedule.recurrence?.frequency)
    assertEquals(RecurrenceEnd.Until(Date(2026, 3, 31)), item.schedule.recurrence?.end)
    assertEquals(Date(2026, 3, 8), item.schedule.recurrenceAnchorDate)
    assertEquals(0, item.schedule.reminder?.offsetMinutes)
    assertEquals(ScheduleTodoState.PENDING, item.schedule.todoState)
  }

  /** 周重复缺少 notify 时从迁移当天向后寻找最近选择日，并沿用结束时间的时分。 */
  @Test
  fun weeklyTodoWithoutNotify_findsNextSelectedWeekday() {
    val item = mapTodos(
      legacyTodo(
        endTime = "2026年3月31日14:30",
        remindMode = LegacyTodoRemindModeDto(
          repeatMode = LegacyTodoRemindModeDto.WEEKLY,
          // 旧 Calendar 常量：1=周日，2=周一，4=周三。
          week = listOf(1, 2, 4),
        ),
      )
    ).single()

    assertEquals(MinuteTimeDate(2026, 3, 1, 14, 30), deadlineOf(item))
    assertEquals(RecurrenceFrequency.WEEKLY, item.schedule.recurrence?.frequency)
    assertEquals(
      setOf(IsoWeekDay.SUNDAY, IsoWeekDay.MONDAY, IsoWeekDay.WEDNESDAY),
      item.schedule.recurrence?.byWeekDays,
    )
    assertTrue(item.schedule.reminder == null)
  }

  /** 缺少 notify 时若当天目标时分已过，日/周重复都必须选择下一次而不是迁出过期锚点。 */
  @Test
  fun recurrenceWithoutNotify_skipsPastTimeOnCurrentDay() {
    val now = MinuteTimeDate(2026, 3, 1, 15, 0)
    val items = LegacyScheduleMapper.mapTodos(
      accountId = ACCOUNT_ID,
      todos = listOf(
        legacyTodo(
          todoId = 1,
          endTime = "2026年3月31日14:30",
          remindMode = LegacyTodoRemindModeDto(repeatMode = LegacyTodoRemindModeDto.DAILY),
        ),
        legacyTodo(
          todoId = 2,
          endTime = "2026年3月31日14:30",
          remindMode = LegacyTodoRemindModeDto(
            repeatMode = LegacyTodoRemindModeDto.WEEKLY,
            week = listOf(1),
          ),
        ),
      ),
      now = now,
      nowEpochMillis = NOW_EPOCH_MILLIS,
    )

    assertEquals(MinuteTimeDate(2026, 3, 2, 14, 30), deadlineOf(items[0]))
    assertEquals(MinuteTimeDate(2026, 3, 8, 14, 30), deadlineOf(items[1]))
  }

  /** 当天目标时刻恰好等于迁移时刻时仍以当前 occurrence 为锚点，不能误跳到下一周期。 */
  @Test
  fun recurrenceWithoutNotify_keepsOccurrenceAtCurrentMinute() {
    val item = LegacyScheduleMapper.mapTodos(
      accountId = ACCOUNT_ID,
      todos = listOf(
        legacyTodo(
          endTime = "2026年3月31日12:00",
          remindMode = LegacyTodoRemindModeDto(repeatMode = LegacyTodoRemindModeDto.DAILY),
        )
      ),
      now = NOW,
      nowEpochMillis = NOW_EPOCH_MILLIS,
    ).single()

    assertEquals(NOW, deadlineOf(item))
  }

  /** 无 notify、无 end 的日重复以未来最近零点为锚点，并保持永不结束且不虚构提醒。 */
  @Test
  fun dailyWithoutNotifyOrEnd_anchorsAtNextMidnight() {
    val item = mapTodos(
      legacyTodo(
        remindMode = LegacyTodoRemindModeDto(repeatMode = LegacyTodoRemindModeDto.DAILY),
      )
    ).single()

    assertEquals(MinuteTimeDate(2026, 3, 2, 0, 0), deadlineOf(item))
    assertEquals(RecurrenceEnd.Never, item.schedule.recurrence?.end)
    assertTrue(item.schedule.reminder == null)
  }

  /** 非法周选择器不猜测重复规则；有截止时降为一次性，无合法时间时保持未排期。 */
  @Test
  fun invalidWeeklyTodo_fallsBackWithoutUnuploadableRecurrence() {
    val items = mapTodos(
      legacyTodo(
        todoId = 1,
        endTime = "2026年3月31日14:30",
        remindMode = LegacyTodoRemindModeDto(
          repeatMode = LegacyTodoRemindModeDto.WEEKLY,
          week = emptyList(),
        ),
      ),
      legacyTodo(
        todoId = 2,
        remindMode = LegacyTodoRemindModeDto(
          repeatMode = LegacyTodoRemindModeDto.WEEKLY,
          week = emptyList(),
        ),
      ),
    )

    assertEquals(MinuteTimeDate(2026, 3, 31, 14, 30), deadlineOf(items[0]))
    assertNull(items[0].schedule.recurrence)
    assertTrue(items[0].schedule.reminder == null)
    assertEquals(ScheduleTiming.Unscheduled, items[1].schedule.timing)
    assertNull(items[1].schedule.recurrence)
  }

  /** 月重复必须保留旧 day 选择器和下一次通知锚点。 */
  @Test
  fun monthlyTodo_preservesRuleAndNextDeadline() {
    val notify = "2026年3月8日14:30"
    val item = mapTodos(
      legacyTodo(
        endTime = "2026年12月31日14:30",
        remindMode = LegacyTodoRemindModeDto(
          repeatMode = LegacyTodoRemindModeDto.MONTHLY,
          day = listOf(8),
          notifyDateTime = notify,
        ),
      )
    ).single()

    assertEquals(LegacyScheduleMapper.parseLegacyDateTime(notify), deadlineOf(item))
    assertEquals(RecurrenceFrequency.MONTHLY, item.schedule.recurrence?.frequency)
    assertEquals(setOf(8), item.schedule.recurrence?.byMonthDays)
    assertEquals(0, item.schedule.reminder?.offsetMinutes)
  }

  /** 旧月重复的 29/30/31 选择器必须完整保留，合法日期跳过语义由当前重复引擎统一处理。 */
  @Test
  fun monthlyTodo_preservesLateMonthDaySelectors() {
    val item = mapTodos(
      legacyTodo(
        endTime = "2027年12月31日14:30",
        remindMode = LegacyTodoRemindModeDto(
          repeatMode = LegacyTodoRemindModeDto.MONTHLY,
          day = listOf(29, 30, 31),
          notifyDateTime = "2026年3月29日14:30",
        ),
      ),
    ).single()

    assertEquals(RecurrenceFrequency.MONTHLY, item.schedule.recurrence?.frequency)
    assertEquals(setOf(29, 30, 31), item.schedule.recurrence?.byMonthDays)
  }

  /** 月/年重复缺少通知时间时按选择器寻找下一次发生，且不能凭空产生提醒。 */
  @Test
  fun monthlyAndYearlyWithoutNotify_findNextOccurrenceWithoutReminder() {
    val items = mapTodos(
      legacyTodo(
        todoId = 1,
        endTime = "2026年12月31日14:30",
        remindMode = LegacyTodoRemindModeDto(
          repeatMode = LegacyTodoRemindModeDto.MONTHLY,
          day = listOf(8),
        ),
      ),
      legacyTodo(
        todoId = 2,
        endTime = "2026年12月31日15:30",
        remindMode = LegacyTodoRemindModeDto(
          repeatMode = LegacyTodoRemindModeDto.YEARLY,
          date = listOf("3.8"),
        ),
      ),
    )

    assertEquals(MinuteTimeDate(2026, 3, 8, 14, 30), deadlineOf(items[0]))
    assertEquals(RecurrenceFrequency.MONTHLY, items[0].schedule.recurrence?.frequency)
    assertEquals(MinuteTimeDate(2026, 3, 8, 15, 30), deadlineOf(items[1]))
    assertEquals(RecurrenceFrequency.YEARLY, items[1].schedule.recurrence?.frequency)
    assertTrue(items.all { it.schedule.reminder == null })
  }

  /** 旧年重复日期若不能由 BYMONTH × BYMONTHDAY 精确表达，必须降级而不能扩出用户没选的日期。 */
  @Test
  fun yearlyCrossProductThatWouldBroadenDates_fallsBackToOneOffDeadline() {
    val item = mapTodos(legacyTodo(
      endTime = "2026年12月31日15:30",
      remindMode = LegacyTodoRemindModeDto(
        repeatMode = LegacyTodoRemindModeDto.YEARLY,
        date = listOf("3.8", "4.9"),
      ),
    )).single()

    assertEquals(MinuteTimeDate(2026, 12, 31, 15, 30), deadlineOf(item))
    assertNull(item.schedule.recurrence)
  }

  /** 重复结束点不晚于下一次通知时，系列无有效发生范围，应降为下一次单次截止。 */
  @Test
  fun recurrenceEndingAtAnchor_fallsBackToOneOffExactReminder() {
    val anchor = "2026年3月8日14:30"
    val item = mapTodos(
      legacyTodo(
        endTime = anchor,
        remindMode = LegacyTodoRemindModeDto(
          repeatMode = LegacyTodoRemindModeDto.DAILY,
          notifyDateTime = anchor,
        ),
      )
    ).single()

    assertEquals(MinuteTimeDate(2026, 3, 8, 14, 30), deadlineOf(item))
    assertNull(item.schedule.recurrence)
    assertEquals(0, item.schedule.reminder?.offsetMinutes)
  }

  /** 秒、毫秒和无效旧时间戳分别规范成毫秒或迁移时刻，保证 UUID 时间部分与资源时间一致。 */
  @Test
  fun todoTimestamp_normalizesSecondsMillisAndInvalidFallback() {
    val fallback = 1_800_000_000_000L
    val items = LegacyScheduleMapper.mapTodos(
      accountId = ACCOUNT_ID,
      todos = listOf(
        legacyTodo(todoId = 1, lastModifyTime = 1_700_000_000L),
        legacyTodo(todoId = 2, lastModifyTime = 1_700_000_000_000L),
        legacyTodo(todoId = 3, lastModifyTime = 0),
      ),
      now = NOW,
      nowEpochMillis = fallback,
    )

    assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), items[0].schedule.createdAt)
    assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), items[1].schedule.createdAt)
    assertEquals(Instant.fromEpochMilliseconds(fallback), items[2].schedule.createdAt)
    assertTrue(items.all { it.schedule.createdAt == it.schedule.updatedAt })
  }

  /** 空标题属于无法展示的旧脏数据，跳过时不能影响同批其他有效清单。 */
  @Test
  fun blankTodoTitle_isSkippedIndependently() {
    val items = mapTodos(
      legacyTodo(todoId = 1, title = "  "),
      legacyTodo(todoId = 2, title = "有效清单"),
    )

    assertEquals(1, items.size)
    assertEquals("有效清单", items.single().schedule.title)
  }

  /** 执行一组使用固定时钟的旧清单映射，避免测试结果受当前日期影响。 */
  private fun mapTodos(vararg todos: LegacyTodoDto): List<LegacyScheduleMigrationItem> =
    LegacyScheduleMapper.mapTodos(
      accountId = ACCOUNT_ID,
      todos = todos.toList(),
      now = NOW,
      nowEpochMillis = NOW_EPOCH_MILLIS,
    )

  /** 断言迁移结果是截止时间并返回截止点，减少各用例重复的类型转换。 */
  private fun deadlineOf(item: LegacyScheduleMigrationItem): MinuteTimeDate =
    assertIs<ScheduleTiming.Deadline>(item.schedule.timing).due

  /** 断言迁移结果是时间段并返回开始点。 */
  private fun timedStartOf(item: LegacyScheduleMigrationItem): MinuteTimeDate =
    assertIs<ScheduleTiming.Timed>(item.schedule.timing).start

  /** 创建一条字段最少的有效旧事务。 */
  private fun legacyTransaction(
    remoteId: Int = 1,
    title: String = "旧事务",
    time: Int = 0,
    date: List<LegacyTransactionTimeDto> = listOf(legacyTransactionTime()),
  ): LegacyTransactionDto = LegacyTransactionDto(
    remoteId = remoteId,
    title = title,
    content = "旧事务内容",
    time = time,
    date = date,
  )

  /** 创建旧事务的一处合法课表位置。 */
  private fun legacyTransactionTime(
    beginLesson: Int = 1,
    day: Int = 0,
    period: Int = 2,
    week: List<Int> = listOf(1),
  ): LegacyTransactionTimeDto = LegacyTransactionTimeDto(
    beginLesson = beginLesson,
    day = day,
    period = period,
    week = week,
  )

  /** 创建一条字段最少的有效旧清单，可按场景覆盖迁移关心的全部字段。 */
  private fun legacyTodo(
    todoId: Long = 1,
    title: String = "旧清单",
    isDone: Int = 0,
    remindMode: LegacyTodoRemindModeDto = LegacyTodoRemindModeDto(),
    lastModifyTime: Long = 1_700_000_000_000L,
    type: String = "other",
    endTime: String? = null,
    isPinned: Int = 0,
  ): LegacyTodoDto = LegacyTodoDto(
    todoId = todoId,
    title = title,
    remindMode = remindMode,
    isDone = isDone,
    lastModifyTime = lastModifyTime,
    type = type,
    endTime = endTime,
    isPinned = isPinned,
  )

  private companion object {
    const val ACCOUNT_ID = "20210000"
    val FIRST_MONDAY = Date(2026, 3, 2)
    val NOW = MinuteTimeDate(2026, 3, 1, 12, 0)
    const val NOW_EPOCH_MILLIS = 1_700_000_000_000L
  }
}
