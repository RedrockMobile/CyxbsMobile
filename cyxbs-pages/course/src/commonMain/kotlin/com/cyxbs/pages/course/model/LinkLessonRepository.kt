package com.cyxbs.pages.course.model

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.components.config.sp.accountSettingsFlow
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.components.utils.extensions.logg
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.network.Network
import com.cyxbs.pages.course.api.ILinkService2
import com.cyxbs.pages.course.bean.LinkStuBean
import com.cyxbs.pages.course.network.createCourseApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

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
        Network.createCourseApiService().changeLinkStudent(linkStuNum)
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
    accountSettingsFlow.flatMapLatest { settings ->
      flow {
        emit(getCacheLinkStu(settings))
        requestLinkStu().onSuccess {
          emit(it)
        }
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
      Network.createCourseApiService().getLinkStudent()
    }.mapCatching {
      it.throwApiExceptionIfFail()
      it.data
    }.onSuccess {
      AccountSettings.get(it.selfNum).putString(
        SETTING_KEY_LINK_STU,
        defaultJson.encodeToString(it)
      )
    }.onFailure {
      logg(it.stackTraceToString())
    }
  }
}