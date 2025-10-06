package com.cyxbs.pages.affair.repos

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.extensions.showExceptionDialog
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.network.ApiException
import com.cyxbs.pages.affair.api.AffairGroupModel
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.bean.AffairWhatTime
import com.cyxbs.pages.affair.bean.GetAffairBean
import com.cyxbs.pages.affair.model.SyncAffairUtils
import com.cyxbs.pages.affair.model.impl.AffairGroupModelImpl
import com.cyxbs.pages.affair.net.AffairApiService2
import io.ktor.client.plugins.ClientRequestException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/19
 */
object AffairRepository2 {

  private const val SETTING_KEY_AFFAIR = "setting_key_affair"

  private val affairFlowMap = HashMap<String, MutableStateFlow<PersistentList<AffairEntity>>>()
  private val affairFlowMapSynchronized = SynchronizedObject()

  private val affairItemModelMap = HashMap<String, AffairGroupModelImpl>()
  private val affairItemModelFlow = MutableStateFlow<AffairGroupModel?>(null)

  init {
    IAccountService::class.impl()
      .stuNumFlow
      .mapLatest { stuNum ->
        val groupModel = if (stuNum != null) {
          affairItemModelMap.getOrPut(stuNum) {
            AffairGroupModelImpl(stuNum, getAffairStateFlow(stuNum).value)
          }
        } else null
        affairItemModelFlow.value = groupModel
        if (stuNum != null) {
          requestAffair(stuNum)
        }
      }
  }

  private inline fun editAffairList(
    stuNum: String,
    action: (PersistentList<AffairEntity>) -> PersistentList<AffairEntity>
  ): PersistentList<AffairEntity> {
    val stateFlow = getAffairStateFlowInternal(stuNum)
    return stateFlow.updateAndGet {
      action.invoke(it)
    }
  }

  private fun getAffairStateFlowInternal(stuNum: String): MutableStateFlow<PersistentList<AffairEntity>> {
    return affairFlowMap[stuNum] ?: synchronized(affairFlowMapSynchronized) {
      affairFlowMap.getOrPut(stuNum) {
        MutableStateFlow(loadCacheAffair(stuNum).toPersistentList())
      }
    }
  }

  fun getAffairStateFlow(stuNum: String): StateFlow<ImmutableList<AffairEntity>> {
    return getAffairStateFlowInternal(stuNum)
  }

  fun getAffairModelStateFlow(): StateFlow<AffairGroupModel?> {
    return affairItemModelFlow
  }

  /**
   * 请求课程
   */
  @OptIn(ExperimentalUuidApi::class)
  suspend fun requestAffair(
    stuNum: String, // 当前登陆人的学号，仅用于检测请求返回时学号是否发生改变，防止出现请求返回时已退出登陆或已切换账号
  ): Result<List<AffairEntity>> {
    // 先上传所有本地临时事务才能拉取远端事务
    uploadLocalAffair(stuNum).onFailure {
      return Result.failure(it)
    }
    return runCatchingCoroutine {
      AffairApiService2::class.impl().getAffair()
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it
    }.mapCatching { bean ->
      bean.data.mapNotNull { it.toAffair() }
    }.onSuccess { newAffairList ->
      editAffairList(stuNum) { oldAffairList ->
        newAffairList.map { new ->
          new.copy(
            localId = oldAffairList.find { it.remoteId == new.remoteId }?.localId
              ?: Uuid.random().toString()
          )
        }.toPersistentList()
      }.also {
        saveAffair(stuNum, it)
      }
    }.onSuccess {
      val groupModel = affairItemModelMap[stuNum]
      if (groupModel != null) {
        SyncAffairUtils.syncAffair(it, groupModel)
      }
    }
  }

  /**
   * 内部独用方法，外界请使用 [AffairGroupModel] 编辑事务
   */
  @OptIn(ExperimentalUuidApi::class)
  suspend fun addAffair(
    stuNum: String, // 当前登陆人的学号，仅用于检测请求返回时学号是否发生改变，防止出现请求返回时已退出登陆或已切换账号
    localId: String = Uuid.random().toString(), // 生成本地唯一 localId
    title: String,
    content: String,
    remindTime: Int,
    whatTime: List<AffairWhatTime>,
    allowLocal: Boolean,
    needShowException: Boolean,
  ): Result<AffairEntity> {
    return runCatchingCoroutine {
      AffairApiService2::class.impl().addAffair(
        time = remindTime,
        title = title,
        content = content,
        dateJson = whatTime.toDateJson(),
      )
    }.mapCatching {
      it.throwApiExceptionIfFail()
      @OptIn(ExperimentalUuidApi::class)
      AffairEntity(
        remoteId = it.id,
        localId = localId,
        remindTime = remindTime,
        title = title,
        content = content,
        whatTime = whatTime,
      )
    }.recoverCatching {
      // 上传失败
      if (it is ClientRequestException || it is ApiException) {
        if (needShowException) showExceptionDialog(
          RuntimeException("添加失败, update whatTime = $whatTime", it)
        )
      } else if (allowLocal) {
        // 添加进本地临时数据中
        @OptIn(ExperimentalUuidApi::class)
        val newAffair = AffairEntity(
          remoteId = 0, // 本地临时事务 remoteId 为 0
          localId = localId,
          remindTime = remindTime,
          title = title,
          content = content,
          whatTime = whatTime,
        )
        LocalAddAffairRepository.add(stuNum, newAffair)
        return@recoverCatching newAffair
      } else {
        if (needShowException) showExceptionDialog(it)
      }
      throw it
    }.onSuccess { newAffair ->
      editAffairList(stuNum) { list ->
        val index = list.indexOfFirst { it.localId == newAffair.localId }
        if (index > 0) {
          // 已经存在，此时应该是本地临时事务的上传
          list.set(index, newAffair)
        } else {
          list.add(newAffair)
        }
      }.also {
        saveAffair(stuNum, it)
      }
    }
  }

  /**
   * 内部独用方法，外界请使用 [AffairGroupModel] 编辑事务
   */
  suspend fun updateAffair(
    stuNum: String, // 当前登陆人的学号，仅用于检测请求返回时学号是否发生改变，防止出现请求返回时已退出登陆或已切换账号
    affair: AffairEntity,
    allowLocal: Boolean,
    needShowException: Boolean,
  ): Result<Any> {
    if (affair.remoteId <= 0 && affair.localId.isNotEmpty()) {
      // localId 小于等于 0 且 localId 不为空 则说明是本地临时事务
      LocalAddAffairRepository.update(stuNum, affair)
      return Result.success(Unit)
    }
    return runCatchingCoroutine {
      AffairApiService2::class.impl().updateAffair(
        remoteId = affair.remoteId,
        time = affair.remindTime,
        title = affair.title,
        content = affair.content,
        dateJson = affair.whatTime.toDateJson(),
      )
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it
    }.recoverCatching {
      // 上传失败
      if (it is ClientRequestException || it is ApiException) {
        if (needShowException) showExceptionDialog(
          RuntimeException("更新失败, update affair = $affair", it)
        )
      } else if (allowLocal) {
        // 添加进本地临时数据中
        LocalUpdateAffairRepository.add(stuNum, affair)
        return@recoverCatching Unit
      } else {
        if (needShowException) showExceptionDialog(it)
      }
      throw it
    }.onSuccess {
      editAffairList(stuNum) { list ->
        val index = list.indexOfFirst { it.localId == affair.localId }
        list.set(index, affair)
      }.also {
        saveAffair(stuNum, it)
      }
    }
  }

  /**
   * 内部独用方法，外界请使用 [AffairGroupModel] 编辑事务
   */
  suspend fun deleteAffair(
    stuNum: String, // 当前登陆人的学号，仅用于检测请求返回时学号是否发生改变，防止出现请求返回时已退出登陆或已切换账号
    localId: String,
    allowLocal: Boolean,
    needShowException: Boolean,
  ): Result<Any> {
    val affair = findAffairByLocalId(stuNum, localId) ?: return Result.failure(
      IllegalArgumentException("未找到对应 localId 的事务, localId = $localId")
    )
    if (affair.remoteId <= 0 && affair.localId.isNotEmpty()) {
      // localId 小于等于 0 且 localId 不为空 则说明是本地临时事务
      LocalAddAffairRepository.remove(stuNum, localId)
      return Result.success(Unit)
    }
    return runCatchingCoroutine {
      AffairApiService2::class.impl().deleteAffair(affair.remoteId)
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it
    }.recoverCatching {
      // 上传失败
      if (it is ClientRequestException || it is ApiException) {
        if (needShowException) showExceptionDialog(
          RuntimeException("删除失败, delete affair = $affair", it)
        )
      } else if (allowLocal) {
        // 添加进本地临时数据中
        LocalDeleteAffairRepository.add(stuNum, localId)
        return@recoverCatching Unit
      } else {
        if (needShowException) showExceptionDialog(it)
      }
      throw it
    }.onSuccess {
      editAffairList(stuNum) {
        val index = it.indexOfFirst { it.localId == localId }
        it.removeAt(index)
      }.also {
        saveAffair(stuNum, it)
      }
    }
  }

  // 同一时间内只有有一个协程触发本地临时事务的上传
  private val uploadLocalAffairMutex = Mutex()

  private suspend fun uploadLocalAffair(
    stuNum: String, // 当前登陆人的学号，仅用于检测请求返回时学号是否发生改变，防止出现请求返回时已退出登陆或已切换账号
  ): Result<Unit> {
    return uploadLocalAffairMutex.withLock {
      runCatchingCoroutine {
        fun checkException(e: Throwable) {
          if (e is ClientRequestException || e is ApiException) {
            // 如果是 400 或者 ApiException，则认为是客户端参数问题，我们丢弃掉这次请求
          } else throw e
        }
        // 这里无需异步上传，同步即可
        LocalDeleteAffairRepository.get(stuNum).forEach { localId ->
          deleteAffair(
            stuNum = stuNum,
            localId = localId,
            allowLocal = false,
            needShowException = false
          ).recoverCatching {
            checkException(it)
          }.onSuccess {
            LocalDeleteAffairRepository.remove(stuNum, localId)
          }.getOrThrow()
        }
        LocalUpdateAffairRepository.get(stuNum).forEach { (remoteId, affair) ->
          updateAffair(
            stuNum = stuNum,
            affair = affair,
            allowLocal = false,
            needShowException = false
          ).recoverCatching {
            checkException(it)
          }.onSuccess {
            LocalUpdateAffairRepository.remove(stuNum, remoteId)
          }.getOrThrow()
        }
        LocalAddAffairRepository.get(stuNum).forEach { (localId, affair) ->
          addAffair(
            stuNum = stuNum,
            localId = affair.localId,
            remindTime = affair.remindTime,
            title = affair.title,
            content = affair.content,
            whatTime = affair.whatTime,
            allowLocal = false,
            needShowException = false
          ).onSuccess {
            LocalAddAffairRepository.remove(stuNum, localId)
          }.onSuccess { newAffair ->
            // 更新 idModel 中的 remoteId
            affairItemModelMap[stuNum]?.itemList?.value?.find {
              it.localId == localId
            }?.let {
              it.remoteId.value = newAffair.remoteId
              it.entity = newAffair
            }
          }.recoverCatching {
            checkException(it)
          }.getOrThrow()
        }
      }
    }
  }

  // 加载磁盘中的事务
  private fun loadCacheAffair(stuNum: String): List<AffairEntity> {
    // 读取磁盘
    val accountSettings = AccountSettings.get(stuNum)
    return accountSettings.getStringOrNull(SETTING_KEY_AFFAIR)?.let { json ->
      runCatching {
        defaultJson.decodeFromString<List<AffairEntity>>(json)
      }.onFailure {
        accountSettings.remove(SETTING_KEY_AFFAIR)
        if (isDebug()) {
          toast("加载磁盘中的事务异常, ${it.message}")
          showExceptionDialog(it)
        }
      }.getOrNull()
    } ?: emptyList()
  }

  // 保存进磁盘
  private fun saveAffair(stuNum: String, list: List<AffairEntity>) {
    AccountSettings.get(stuNum).putString(
      SETTING_KEY_AFFAIR,
      defaultJson.encodeToString<List<AffairEntity>>(list)
    )
  }

  private fun findAffairByLocalId(stuNum: String, localId: String): AffairEntity? {
    return getAffairStateFlow(stuNum).value.find {
      it.localId == localId
    }
  }

  private fun findAffairByRemoteId(stuNum: String, remoteId: Int): AffairEntity? {
    return getAffairStateFlow(stuNum).value.find {
      it.remoteId == remoteId
    }
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
}