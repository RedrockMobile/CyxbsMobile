package com.cyxbs.pages.course.model

import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.pages.course.api.ILinkService2
import com.cyxbs.pages.course.bean.LinkStuBean
import com.cyxbs.pages.course.network.CourseApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/22
 */
object LinkLessonRepository {

  private const val SETTING_KEY_LINK_STU = "link_stu"
  private val EMPTY_LINK_STU = ILinkService2.LinkStu("", "", "", "")

  val state: StateFlow<ILinkService2.LinkStu> get() = _state
  private val _state = MutableStateFlow(EMPTY_LINK_STU)

  fun changeLinkStu(linkStuNum: String) {
    appCoroutineScope.launch {
      runCatchingCoroutine {
        CourseApiService::class.impl().changeLinkStudent(linkStuNum)
      }.mapCatching {
        it.throwApiExceptionIfFail()
        it.data
      }.onSuccess {
        AccountSettings.get(it.selfNum).putString(
          SETTING_KEY_LINK_STU,
          defaultJson.encodeToString(it)
        )
      }.map {
        ILinkService2.LinkStu(
          selfNum = it.selfNum,
          linkNum = it.linkNum,
          linkMajor = it.major,
          linkName = it.name,
        )
      }.onSuccess {
        _state.emit(it)
      }
    }
  }

  init {
    IAccountService::class.impl().stuNumFlow.map {
      AccountSettings.get(it)
    }.flatMapLatest { settings ->
      if (settings.stuNum == null) {
        flowOf<LinkStuBean?>(null)
      } else flow {
        emit(getCacheLinkStu(settings))
        requestLinkStu().onSuccess {
          emit(it)
        }
        // todo 测试，待删除
//        toast("已 mock 关联人")
//        emit(
//          LinkStuBean(
//            selfNum = settings.stuNum!!,
//            linkNum = "2023214565",
//            major = "软件工程",
//            name = "测试",
//          )
//        )
      }
    }.map {
      if (it == null) EMPTY_LINK_STU else {
        ILinkService2.LinkStu(
          selfNum = it.selfNum,
          linkNum = it.linkNum,
          linkMajor = it.major,
          linkName = it.name,
        )
      }
    }.onEach {
      _state.emit(it)
    }.launchIn(appCoroutineScope)
  }

  private fun getCacheLinkStu(settings: AccountSettings): LinkStuBean? {
    return settings.getStringOrNull(SETTING_KEY_LINK_STU)?.let { cache ->
      runCatching {
        defaultJson.decodeFromString<LinkStuBean>(cache)
      }.onFailure {
        settings.remove(SETTING_KEY_LINK_STU)
      }.getOrNull()
    }
  }

  private suspend fun requestLinkStu(): Result<LinkStuBean> {
    return runCatchingCoroutine {
      CourseApiService::class.impl().getLinkStudent()
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it.data
    }.onSuccess {
      AccountSettings.get(it.selfNum).putString(
        SETTING_KEY_LINK_STU,
        defaultJson.encodeToString(it)
      )
    }
  }
}