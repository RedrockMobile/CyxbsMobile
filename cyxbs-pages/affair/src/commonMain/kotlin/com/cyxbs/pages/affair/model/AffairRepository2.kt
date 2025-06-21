package com.cyxbs.pages.affair.model

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.extensions.showExceptionDialog
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.network.ApiException
import com.cyxbs.pages.affair.api.IAffairService2
import com.cyxbs.pages.affair.bean.GetAffairBean
import com.cyxbs.pages.affair.net.AffairApiService2
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/19
 */
object AffairRepository2 : IAffairService2 {

  private const val SETTING_KEY_AFFAIR = "setting_key_affair"

  private val affairFlow = MutableStateFlow<List<IAffairService2.Affair>?>(null)

  init {
    IAccountService::class.impl()
      .stuNumFlow
      .onEach {
        affairFlow.value = if (it != null) loadCacheAffair() else null
      }.launchIn(appCoroutineScope)
  }

  /**
   * 观察登陆人的事务
   * @param needRequest 订阅时是否需要请求新的数据，默认为 false，主页课表会自动请求
   */
  override fun observeAffair(
    needRequest: Boolean,
  ): Flow<List<IAffairService2.Affair>?> {
    return combine(
      affairFlow,
      LocalAddAffairRepository.getFlow(),
      LocalUpdateAffairRepository.getFlow(),
      LocalDeleteAffairRepository.getFlow(),
    ) { origin, add, update, delete ->
      combineAffair(origin, add, update, delete)
    }.onStart {
      if (needRequest) {
        IAccountService::class.impl()
          .accountCoroutineScope
          .launch { requestAffair() }
      }
    }
  }

  /**
   * 获取课程缓存
   */
  override fun getCacheAffair(): List<IAffairService2.Affair>? {
    return combineAffair(
      origin = affairFlow.value,
      add = LocalAddAffairRepository.getFlow().value,
      update = LocalUpdateAffairRepository.getFlow().value,
      delete = LocalDeleteAffairRepository.getFlow().value,
    )
  }

  /**
   * 请求课程
   */
  private suspend fun requestAffair(): Result<List<IAffairService2.Affair>> {
    // 先上传所有本地临时事务才能拉取的远端事务
    uploadLocalAffair().onFailure {
      return Result.failure(it)
    }
    return runCatchingCoroutine {
      AffairApiService2::class.impl().getAffair()
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it
    }.mapCatching { bean ->
      bean.data.mapNotNull { it.toAffair() }
    }.onSuccess {
      affairFlow.tryEmit(it)
      saveAffair()
    }
  }

  override fun addAffair(affair: IAffairService2.Affair) {
    IAccountService::class.impl()
      .accountCoroutineScope
      .launch {
        addAffair(affair, allowLocal = true, needShowException = true)
      }
  }

  /**
   * 内部独用方法，请使用 [IAffairService2.addAffair] 代替
   */
  private suspend fun addAffair(
    affair: IAffairService2.Affair,
    allowLocal: Boolean,
    needShowException: Boolean,
  ): Result<Any> {
    return runCatchingCoroutine {
      AffairApiService2::class.impl().addAffair(
        time = affair.remindTime,
        title = affair.title,
        content = affair.content,
        dateJson = affair.whatTime.toDateJson(),
      )
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it
    }.onSuccess { bean ->
      // 上传成功
      affairFlow.update {
        listOf(affair.copy(id = bean.id)) + it.orEmpty()
      }
      saveAffair()
    }.onFailure {
      // 上传失败
      if (it is ClientRequestException) {
        if (needShowException) {
          showExceptionDialog(RuntimeException("添加失败, http 400, update affair = $affair", it))
        }
      } else if (it is ApiException) {
        if (needShowException) {
          showExceptionDialog(RuntimeException("添加失败, update affair = $affair", it))
        }
      } else if (allowLocal) {
        // 添加进本地临时数据中
        LocalAddAffairRepository.add(affair)
      }
    }
  }

  override fun updateAffair(affair: IAffairService2.Affair) {
    IAccountService::class.impl()
      .accountCoroutineScope.launch {
        updateAffair(affair, allowLocal = true, needShowException = true)
      }
  }

  /**
   * 内部独用方法，请使用 [IAffairService2.updateAffair] 代替
   */
  private suspend fun updateAffair(
    affair: IAffairService2.Affair,
    allowLocal: Boolean,
    needShowException: Boolean,
  ): Result<Any> {
    if (affair.id < 0) {
      // id 小于 0 说明是本地临时事务
      LocalAddAffairRepository.update(affair)
      return Result.success(Unit)
    } else {
      return runCatchingCoroutine {
        AffairApiService2::class.impl().updateAffair(
          remoteId = affair.id,
          time = affair.remindTime,
          title = affair.title,
          content = affair.content,
          dateJson = affair.whatTime.toDateJson(),
        )
      }.mapCatching {
        it.throwApiExceptionIfFail()
        it
      }.onSuccess {
        // 上传成功
        affairFlow.update { list ->
          if (list == null) {
            // 正常情况下不会为空，除非更新事务的接口返回太慢了
            null
          } else {
            val index = list.indexOfFirst { it.id == affair.id }
            if (index >= 0) {
              list.toMutableList().apply { set(index, affair) }
            } else {
              // index < 0 说明事务不存在，正常情况也不会出现
              list
            }
          }
        }
        saveAffair()
      }.onFailure {
        // 上传失败
        if (it is ClientRequestException) {
          if (needShowException) {
            showExceptionDialog(RuntimeException("更新失败, http 400, update affair = $affair", it))
          }
        } else if (it is ApiException) {
          if (needShowException) {
            showExceptionDialog(RuntimeException("更新失败, update affair = $affair", it))
          }
        } else if (allowLocal) {
          // 添加进本地临时数据中
          LocalUpdateAffairRepository.update(affair)
        }
      }
    }
  }

  override fun deleteAffair(id: Int) {
    IAccountService::class.impl()
      .accountCoroutineScope.launch {
        deleteAffair(id, allowLocal = true, needShowException = true)
      }
  }

  /**
   * 内部独用方法，请使用 [IAffairService2.deleteAffair] 代替
   */
  private suspend fun deleteAffair(
    id: Int,
    allowLocal: Boolean,
    needShowException: Boolean,
  ): Result<Any> {
    if (id < 0) {
      // id 小于 0 说明是本地临时事务
      LocalAddAffairRepository.delete(id)
      return Result.success(Unit)
    } else {
      return runCatchingCoroutine {
        AffairApiService2::class.impl().deleteAffair(id)
      }.mapCatching {
        it.throwApiExceptionIfFail()
        it
      }.onSuccess {
        affairFlow.update { list ->
          list?.filter { it.id != id }
        }
        saveAffair()
      }.onFailure { throwable ->
        // 上传失败
        if (throwable is ClientRequestException) {
          if (needShowException) {
            showExceptionDialog(RuntimeException("删除失败, http 400, " +
                "delete affair = ${affairFlow.value?.find { it.id == id } ?: id}", throwable))
          }
        } else if (throwable is ApiException) {
          if (needShowException) {
            showExceptionDialog(RuntimeException("删除失败, " +
                "delete affair = ${affairFlow.value?.find { it.id == id } ?: id}", throwable))
          }
        } else if (allowLocal) {
          // 添加进本地临时数据中
          LocalDeleteAffairRepository.delete(id)
        }
      }
    }
  }

  private suspend fun uploadLocalAffair(): Result<Unit> {
    return runCatchingCoroutine {
      coroutineScope {
        fun checkException(e: Throwable) {
          if (e is ClientRequestException) {
            // 如果是 400 异常，则认为是客户端参数问题，我们丢弃掉这次请求
          } else throw e
        }
        // 这里无需异步上传，同步即可
        LocalDeleteAffairRepository.getFlow().value.forEach { id ->
          deleteAffair(id, allowLocal = false, needShowException = false).recoverCatching {
            checkException(it)
          }.onSuccess {
            LocalDeleteAffairRepository.delete(id)
          }.getOrNull()
        }
        LocalUpdateAffairRepository.getFlow().value.forEach { (_, affair) ->
          updateAffair(affair, allowLocal = false, needShowException = false).recoverCatching {
            checkException(it)
          }.onSuccess {
            LocalUpdateAffairRepository.delete(affair.id)
          }.getOrThrow()
        }
        LocalAddAffairRepository.getFlow().value.forEach { (_, affair) ->
          addAffair(affair, allowLocal = false, needShowException = false).recoverCatching {
            checkException(it)
          }.onSuccess {
            LocalAddAffairRepository.delete(affair.id)
          }.getOrThrow()
        }
      }
    }
  }

  // 根据原始事务数据与本地临时添加、更新、删除操作计算而得出最终该显示的事务数据
  private fun combineAffair(
    origin: List<IAffairService2.Affair>?,
    add: Map<Int, IAffairService2.Affair>,
    update: Map<Int, IAffairService2.Affair>,
    delete: Set<Int>,
  ): List<IAffairService2.Affair>? {
    if (origin == null) return null
    val list = add.values.toMutableList()
    origin.forEach {
      if (!delete.contains(it.id)) {
        list.add(update[it.id] ?: it)
      }
    }
    return list
  }

  // 加载磁盘中的事务
  private fun loadCacheAffair(): List<IAffairService2.Affair>? {
    // 读取磁盘
    val accountSettings = AccountSettings.now
    return accountSettings.getStringOrNull(SETTING_KEY_AFFAIR)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<List<IAffairService2.Affair>>(json)
      }.onFailure {
        accountSettings.remove(SETTING_KEY_AFFAIR)
        if (isDebug()) toast("加载磁盘中的事务异常, ${it.message}")
      }.getOrNull()
    }
  }

  // 保存进磁盘
  private fun saveAffair() {
    AccountSettings.now.putString(
      SETTING_KEY_AFFAIR,
      defaultJson.encodeToString<List<IAffairService2.Affair>>(affairFlow.value ?: emptyList())
    )
  }

  /**
   * Compose 版本课表在旧版课表接口上实现了随意时间段的新事务
   *
   * 新版本事务按一下规则进行解析:
   * - 如果 day < 0，
   *   - 则表示该事务是任意时间段的事务
   *   - 则 begin_lesson 字段表示 事务开始时间，格式为 小时数 * 100 + 分钟数
   *   - 则 period 字段表示 事务持续时间，单位分钟
   *   - 则 week 字段表示该事务在哪几天，格式为 年份 * 10000 + 月份 * 100 + 日
   */
  private fun List<IAffairService2.AffairWhatTime>.toDateJson(): String {
    return map { whatTime ->
      GetAffairBean.AffairDateBean(
        day = -1, // Compose 新课表标识
        beginLesson = whatTime.start.hour * 100 + whatTime.start.minute,
        period = whatTime.start.minutesUntil(whatTime.end),
        week = whatTime.date.map {
          it.year * 10000 + it.monthNumber * 100 + it.dayOfMonth
        }
      )
    }.let {
      defaultJson.encodeToString(it)
    }
  }
}