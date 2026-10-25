package com.cyxbs.pages.schedule.data.migration

import com.cyxbs.components.account.api.AccountSession
import com.cyxbs.components.utils.network.ApiWrapper
import com.cyxbs.components.utils.network.plugin.EXPECTED_ACCOUNT_SESSION_ATTRIBUTE_NAME
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Headers
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Tag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 迁移期读取旧事务与旧清单的 Ktorfit 接口。
 *
 * 只保留上线迁移实际需要的只读接口；请求携带初始化时冻结的 [AccountSession]，切号后由 TokenPlugin 在发包前
 * fail-closed。旧写接口不会被 Schedule 调用。
 */
internal interface LegacyScheduleMigrationApiService {

  /** 读取旧事务服务当前学期的有效 Transaction；未上线的 TimeTransaction 不在该响应内。 */
  @POST("magipoke-reminder/Person/getTransaction")
  @Headers("App-Version:74")
  suspend fun getTransactions(
    @Tag(EXPECTED_ACCOUNT_SESSION_ATTRIBUTE_NAME) session: AccountSession,
  ): ApiWrapper<List<LegacyTransactionDto>>

  /** 读取旧清单服务当前仍有效的完整清单。 */
  @GET("magipoke-todo/list")
  suspend fun getTodos(
    @Tag(EXPECTED_ACCOUNT_SESSION_ATTRIBUTE_NAME) session: AccountSession,
  ): ApiWrapper<LegacyTodoListDto>
}

/** 旧事务的最小迁移 DTO；响应中的 term、stuNum 等历史字段由序列化器忽略。 */
@Serializable
internal data class LegacyTransactionDto(
  @SerialName("id") val remoteId: Int,
  val title: String,
  val content: String = "",
  val time: Int = 0,
  val date: List<LegacyTransactionTimeDto> = emptyList(),
)

/** 旧事务的一处课表时间位置。 */
@Serializable
internal data class LegacyTransactionTimeDto(
  @SerialName("begin_lesson") val beginLesson: Int,
  val day: Int,
  val period: Int,
  val week: List<Int> = emptyList(),
)

/** 旧清单完整读取响应中的 data。 */
@Serializable
internal data class LegacyTodoListDto(
  @SerialName("changed_todo_array") val todos: List<LegacyTodoDto>? = null,
  @SerialName("sync_time") val syncTime: Long = 0,
)

/** 旧清单的最小迁移 DTO。 */
@Serializable
internal data class LegacyTodoDto(
  @SerialName("todo_id") val todoId: Long,
  val title: String,
  val detail: String = "",
  @SerialName("is_done") val isDone: Int = 0,
  @SerialName("remind_mode") val remindMode: LegacyTodoRemindModeDto = LegacyTodoRemindModeDto(),
  @SerialName("last_modify_time") val lastModifyTime: Long = 0,
  val type: String = "other",
  @SerialName("end_time") val endTime: String? = null,
  @SerialName("is_pinned") val isPinned: Int = 0,
)

/** 旧清单重复与下一次通知配置；数组缺失时按空集合处理，避免单条历史脏数据让整个响应解码失败。 */
@Serializable
internal data class LegacyTodoRemindModeDto(
  @SerialName("repeat_mode") val repeatMode: Int = NONE,
  val date: List<String> = emptyList(),
  val week: List<Int> = emptyList(),
  val day: List<Int> = emptyList(),
  @SerialName("notify_datetime") val notifyDateTime: String? = null,
) {
  companion object {
    const val NONE = 0
    const val DAILY = 1
    const val WEEKLY = 2
    const val MONTHLY = 3
    const val YEARLY = 4
  }
}
