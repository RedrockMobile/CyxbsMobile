package com.cyxbs.pages.affair.repos

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.extensions.showExceptionDialog
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.network.ApiException
import com.cyxbs.pages.affair.api.AffairGroupModel
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.bean.AffairWhatTime
import com.cyxbs.pages.affair.bean.AffairWhenBean
import com.cyxbs.pages.affair.model.SyncAffairUtils
import com.cyxbs.pages.affair.model.impl.AffairGroupModelImpl
import com.cyxbs.pages.affair.net.AddAffairRequest
import com.cyxbs.pages.affair.net.AffairApiService2
import com.cyxbs.pages.affair.net.UpdateAffairRequest
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
      it.data
    }.mapCatching { beanList ->
      editAffairList(stuNum) { oldAffairList ->
        beanList.map { bean ->
          AffairEntity(
            remoteId = bean.remoteId,
            localId = oldAffairList.find { it.remoteId == bean.remoteId }?.localId
              ?: Uuid.random().toString(),
            remindTime = bean.remindTime,
            title = bean.title,
            content = bean.content,
            whatTime = bean.whenList.map { whenBean ->
              whenBean.toAffairWhatTime()
            }
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
    request: AddAffairRequest,
    allowLocal: Boolean,
    needShowException: Boolean,
  ): Result<AffairEntity> {
    return runCatchingCoroutine {
      AffairApiService2::class.impl().addAffair(request)
    }.mapCatching {
      it.throwApiExceptionIfFail()
      @OptIn(ExperimentalUuidApi::class)
      request.toAffairEntity(localId = localId, remoteId = it.id)
    }.recoverCatching {
      // 上传失败
      if (it is ClientRequestException || it is ApiException) {
        if (needShowException) showExceptionDialog(
          RuntimeException("添加失败, request = $request", it)
        )
      } else if (allowLocal) {
        // 添加进本地临时数据中
        @OptIn(ExperimentalUuidApi::class)
        val newAffair = request.toAffairEntity(localId = localId, remoteId = 0) // 本地临时事务 remoteId 为 0
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
    if (affair.remoteId == 0 && affair.localId.isNotEmpty()) {
      // localId 等于 0 且 localId 不为空 则说明是本地临时事务
      LocalAddAffairRepository.update(stuNum, affair)
      return Result.success(Unit)
    }
    return runCatchingCoroutine {
      AffairApiService2::class.impl().updateAffair(
        request = UpdateAffairRequest(
          remoteId = affair.remoteId,
          remindTime = affair.remindTime,
          title = affair.title,
          content = affair.content,
          whenList = affair.whatTime.map {
            AffairWhenBean(
              start = it.timePair.first.minuteOfDay,
              end = it.timePair.second.minuteOfDay,
              date = it.date
            )
          }
        )
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
            request = AddAffairRequest(
              remindTime = affair.remoteId,
              title = affair.title,
              content = affair.content,
              whenList = affair.whatTime.map {
                AffairWhenBean(
                  start = it.timePair.first.minuteOfDay,
                  end = it.timePair.second.minuteOfDay,
                  date = it.date,
                )
              }
            ),
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


  private fun AddAffairRequest.toAffairEntity(
    localId: String,
    remoteId: Int,
  ): AffairEntity {
    return AffairEntity(
      remoteId = remoteId,
      localId = localId,
      remindTime = remindTime,
      title = title,
      content = content,
      whatTime = whenList.map { whenBean ->
        whenBean.toAffairWhatTime()
      }
    )
  }

  private fun AffairWhenBean.toAffairWhatTime(): AffairWhatTime {
    return AffairWhatTime(
      timePair = MinuteTimePair(
        first = MinuteTime(
          hour = start / 60,
          minute = start % 60,
        ),
        second = MinuteTime(
          hour = end / 60,
          minute = end % 60,
        )
      ),
      date = date,
    )
  }
}