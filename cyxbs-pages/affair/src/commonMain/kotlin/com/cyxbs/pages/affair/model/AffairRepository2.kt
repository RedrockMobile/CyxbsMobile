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
import com.cyxbs.pages.affair.api.AffairModel
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.bean.AffairWhatTime
import com.cyxbs.pages.affair.bean.GetAffairBean
import com.cyxbs.pages.affair.net.AffairApiService2
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.updateAndGet

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/19
 */
object AffairRepository2 {

  private const val SETTING_KEY_AFFAIR = "setting_key_affair"

  private val affairFlow = MutableStateFlow<List<AffairEntity>?>(null)
  private val affairItemModelFlow = MutableStateFlow<AffairModelImpl?>(null)

  init {
    IAccountService::class.impl()
      .stuNumFlow
      .mapLatest { stuNum ->
        if (stuNum != null) {
          val cacheAffair = loadCacheAffair() ?: emptyList()
          affairFlow.value = cacheAffair
          affairItemModelFlow.value = AffairModelImpl(
            stuNum = stuNum,
            affairList = combineAffair(
              origin = cacheAffair,
              add = LocalAddAffairRepository.getFlow().value,
              update = LocalUpdateAffairRepository.getFlow().value,
              delete = LocalDeleteAffairRepository.getFlow().value
            ),
            addAction = {
              addAffair(stuNum, it, allowLocal = true, needShowException = true)
            },
            updateAction = {
              updateAffair(stuNum, it, allowLocal = true, needShowException = true)
            },
            deleteAction = {
              deleteAffair(stuNum, it, allowLocal = true, needShowException = true)
            },
          )
          requestAffair(stuNum)
        } else {
          affairFlow.value = null
          affairItemModelFlow.value = null
        }
      }.launchIn(appCoroutineScope)
  }

  fun getAffairModelStateFlow(): StateFlow<AffairModel?> {
    return affairItemModelFlow
  }

  /**
   * 请求课程
   */
  private suspend fun requestAffair(
    stuNum: String, // 当前登陆人的学号，仅用于检测请求返回时学号是否发生改变，防止出现请求返回时已退出登陆或已切换账号
  ): Result<List<AffairEntity>> {
    val nowStuNum = IAccountService::class.impl().stuNum
    if (stuNum != nowStuNum) {
      return Result.failure(StuNumNotMatchException(stuNum, nowStuNum))
    }
    // 先上传所有本地临时事务才能拉取的远端事务
    uploadLocalAffair(stuNum).onFailure {
      return Result.failure(it)
    }
    return runCatchingCoroutine {
      AffairApiService2::class.impl().getAffair()
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it
    }.mapCatching {
      // 检查请求前后的学号一致性
      checkStuNum(stuNum)
      it
    }.mapCatching { bean ->
      bean.data.mapNotNull { it.toAffair() }
    }.onSuccess { entities ->
      affairFlow.updateAndGet {
        if (it != null) entities else null
      }?.also {
        saveAffair(it)
      }
    }.onSuccess { entities ->
      val newAffair = combineAffair(
        origin = entities,
        add = LocalAddAffairRepository.getFlow().value,
        update = LocalUpdateAffairRepository.getFlow().value,
        delete = LocalDeleteAffairRepository.getFlow().value
      )
      // 同步远端事务到本地
      affairItemModelFlow.value?.syncAffair(newAffair)
    }
  }

  /**
   * 内部独用方法，外界请使用 [AffairModel] 编辑事务
   */
  private suspend fun addAffair(
    stuNum: String, // 当前登陆人的学号，仅用于检测请求返回时学号是否发生改变，防止出现请求返回时已退出登陆或已切换账号
    affair: AffairEntity,
    allowLocal: Boolean,
    needShowException: Boolean,
  ): Result<AffairEntity> {
    val nowStuNum = IAccountService::class.impl().stuNum
    if (stuNum != nowStuNum) {
      return Result.failure(StuNumNotMatchException(stuNum, nowStuNum))
    }
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
    }.mapCatching {
      // 检查请求前后的学号一致性
      checkStuNum(stuNum)
      affair.copy(id = it.id)
    }.onSuccess { newAffair ->
      // 上传成功
      affairFlow.updateAndGet {
        if (it == null) null else listOf(newAffair) + it
      }?.also {
        saveAffair(it)
      }
    }.recoverCatching {
      // 上传失败
      if (it is ClientRequestException) {
        if (needShowException) showExceptionDialog(
          RuntimeException("添加失败, http 400, update affair = $affair", it)
        )
        throw it
      } else if (it is ApiException) {
        if (needShowException) showExceptionDialog(
          RuntimeException("添加失败, update affair = $affair", it)
        )
        throw it
      } else if (allowLocal && it !is StuNumNotMatchException) {
        // 添加进本地临时数据中
        LocalAddAffairRepository.add(affair)
      } else {
        if (needShowException) showExceptionDialog(it)
        throw it
      }
    }
  }

  /**
   * 内部独用方法，外界请使用 [AffairModel] 编辑事务
   */
  private suspend fun updateAffair(
    stuNum: String, // 当前登陆人的学号，仅用于检测请求返回时学号是否发生改变，防止出现请求返回时已退出登陆或已切换账号
    affair: AffairEntity,
    allowLocal: Boolean,
    needShowException: Boolean,
  ): Result<Any> {
    val nowStuNum = IAccountService::class.impl().stuNum
    if (stuNum != nowStuNum) {
      return Result.failure(StuNumNotMatchException(stuNum, nowStuNum))
    }
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
      }.mapCatching {
        // 检查请求前后的学号一致性
        checkStuNum(stuNum)
      }.onSuccess {
        // 上传成功
        affairFlow.updateAndGet { list ->
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
        }?.also {
          saveAffair(it)
        }
      }.recoverCatching {
        // 上传失败
        if (it is ClientRequestException) {
          if (needShowException) showExceptionDialog(
            RuntimeException("更新失败, http 400, update affair = $affair", it)
          )
          throw it
        } else if (it is ApiException) {
          if (needShowException) showExceptionDialog(
            RuntimeException("更新失败, update affair = $affair", it)
          )
          throw it
        } else if (allowLocal && it !is StuNumNotMatchException) {
          // 添加进本地临时数据中
          LocalUpdateAffairRepository.update(affair)
        } else {
          if (needShowException) showExceptionDialog(it)
          throw it
        }
      }
    }
  }

  /**
   * 内部独用方法，外界请使用 [AffairModel] 编辑事务
   */
  private suspend fun deleteAffair(
    stuNum: String, // 当前登陆人的学号，仅用于检测请求返回时学号是否发生改变，防止出现请求返回时已退出登陆或已切换账号
    id: Int,
    allowLocal: Boolean,
    needShowException: Boolean,
  ): Result<Any> {
    val nowStuNum = IAccountService::class.impl().stuNum
    if (stuNum != nowStuNum) {
      return Result.failure(StuNumNotMatchException(stuNum, nowStuNum))
    }
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
      }.mapCatching {
        // 检查请求前后的学号一致性
        checkStuNum(stuNum)
      }.onSuccess {
        affairFlow.updateAndGet { list ->
          list?.filter { it.id != id }
        }?.also {
          saveAffair(it)
        }
      }.recoverCatching { throwable ->
        // 上传失败
        if (throwable is ClientRequestException) {
          if (needShowException) showExceptionDialog(
            RuntimeException(
              "删除失败, http 400, " +
                "delete affair = ${affairFlow.value?.find { it.id == id } ?: id}", throwable))
          throw throwable
        } else if (throwable is ApiException) {
          if (needShowException) showExceptionDialog(
            RuntimeException(
              "删除失败, " +
                "delete affair = ${affairFlow.value?.find { it.id == id } ?: id}", throwable))
          throw throwable
        } else if (allowLocal && throwable !is StuNumNotMatchException) {
          // 添加进本地临时数据中
          LocalDeleteAffairRepository.delete(id)
        } else {
          if (needShowException) showExceptionDialog(throwable)
          throw throwable
        }
      }
    }
  }

  private suspend fun uploadLocalAffair(
    stuNum: String, // 当前登陆人的学号，仅用于检测请求返回时学号是否发生改变，防止出现请求返回时已退出登陆或已切换账号
  ): Result<Unit> {
    return runCatchingCoroutine {
      coroutineScope {
        fun checkException(e: Throwable) {
          if (e is ClientRequestException) {
            // 如果是 400 异常，则认为是客户端参数问题，我们丢弃掉这次请求
          } else throw e
        }
        // 这里无需异步上传，同步即可
        LocalDeleteAffairRepository.getFlow().value.forEach { id ->
          deleteAffair(stuNum, id, allowLocal = false, needShowException = false).recoverCatching {
            checkException(it)
          }.onSuccess {
            LocalDeleteAffairRepository.delete(id)
          }.getOrThrow()
        }
        LocalUpdateAffairRepository.getFlow().value.forEach { (_, affair) ->
          updateAffair(
            stuNum,
            affair,
            allowLocal = false,
            needShowException = false
          ).recoverCatching {
            checkException(it)
          }.onSuccess {
            LocalUpdateAffairRepository.delete(affair.id)
          }.getOrThrow()
        }
        LocalAddAffairRepository.getFlow().value.forEach { (_, affair) ->
          addAffair(stuNum, affair, allowLocal = false, needShowException = false).recoverCatching {
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
    origin: List<AffairEntity>,
    add: Map<Int, AffairEntity>,
    update: Map<Int, AffairEntity>,
    delete: Set<Int>,
  ): List<AffairEntity> {
    val list = add.values.toMutableList()
    origin.forEach {
      if (!delete.contains(it.id)) {
        list.add(update[it.id] ?: it)
      }
    }
    return list
  }

  // 加载磁盘中的事务
  private fun loadCacheAffair(): List<AffairEntity>? {
    // 读取磁盘
    val accountSettings = AccountSettings.now
    return accountSettings.getStringOrNull(SETTING_KEY_AFFAIR)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<List<AffairEntity>>(json)
      }.onFailure {
        accountSettings.remove(SETTING_KEY_AFFAIR)
        if (isDebug()) toast("加载磁盘中的事务异常, ${it.message}")
      }.getOrNull()
    }
  }

  // 保存进磁盘
  private fun saveAffair(list: List<AffairEntity>) {
    AccountSettings.now.putString(
      SETTING_KEY_AFFAIR,
      defaultJson.encodeToString<List<AffairEntity>>(list)
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
  private fun List<AffairWhatTime>.toDateJson(): String {
    return map { whatTime ->
      GetAffairBean.AffairDateBean(
        day = -1, // Compose 新课表标识
        beginLesson = whatTime.timePair.first.hour * 100 + whatTime.timePair.first.minute,
        period = whatTime.timePair.first.minutesUntil(whatTime.timePair.second),
        week = whatTime.date.map {
          it.year * 10000 + it.monthNumber * 100 + it.dayOfMonth
        }
      )
    }.let {
      defaultJson.encodeToString(it)
    }
  }

  private fun checkStuNum(oldStuNum: String?) {
    val newStuNum = IAccountService::class.impl().stuNum
    if (newStuNum != oldStuNum) {
      throw StuNumNotMatchException(oldStuNum, newStuNum)
    }
  }

  private class StuNumNotMatchException(
    oldStuNum: String?,
    newStuNum: String?
  ) : IllegalStateException("旧学号 $oldStuNum 与新学号 $newStuNum 不匹配")
}