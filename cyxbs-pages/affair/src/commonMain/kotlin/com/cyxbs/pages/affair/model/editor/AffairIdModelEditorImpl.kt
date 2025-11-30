package com.cyxbs.pages.affair.model.editor

import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.affair.model.impl.AffairIdModelImpl
import com.cyxbs.pages.affair.model.impl.AffairWhatTimeModelImpl
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
class AffairIdModelEditorImpl(
  override val idModel: AffairIdModelImpl,
  val commitAction: suspend AffairIdModelEditorImpl.(Boolean) -> Result<AffairIdModelEditor.EditResult>,
) : AffairIdModelEditor {
  override var remindTime = idModel.remindTime.value
  override var title = idModel.title.value
  override var content = idModel.content.value

  override val whatTimeDate: MutableMap<AffairWhatTimeModelEditorImpl, MutableList<AffairDateModelEditorImpl>> =
    idModel.whatTimeDate.value.entries.associateTo(LinkedHashMap()) {
      val whatTimeModelEditor = AffairWhatTimeModelEditorImpl(
        idModelEditor = this,
        whatTimeModel = it.key,
      )
      val dateEditorList = it.value.mapTo(mutableListOf()) { dateModel ->
        AffairDateModelEditorImpl(
          idModelEditor = this,
          whatTimeEditor = whatTimeModelEditor,
          dateModel = dateModel,
        )
      }
      whatTimeModelEditor to dateEditorList
    }

  override val incrementAddList = mutableListOf<AffairWhatTimeModelEditorImpl>()
  override val incrementRemoveList = mutableListOf<AffairWhatTimeModelEditorImpl>()

  @Volatile
  private var enableModify: Boolean = true

  val whatTimeDateLock = SynchronizedObject()

  // 上锁编辑 whatTimeDate
  inline fun editorWhatTimeDate(
    action: (MutableMap<AffairWhatTimeModelEditorImpl, MutableList<AffairDateModelEditorImpl>>) -> Unit
  ) {
    synchronized(whatTimeDateLock) {
      if (!enableModify()) return
      action.invoke(whatTimeDate)
    }
  }


  private var isPostRunnable = false

  // 通知下游编辑态数据更新
  fun sendValueByEditorStateFlow() {
    if (isPostRunnable) return
    isPostRunnable = true
    appCoroutineScope.launch(Dispatchers.Main) {
      isPostRunnable = false
      // 延后一个 runnable 执行，防止同一堆栈中多次调用导致下游多次消费
      idModel.whatTimeDate.valueByEditorStateFlow.tryEmit(
        this@AffairIdModelEditorImpl to whatTimeDate.entries.associateByTo(
          destination = LinkedHashMap(),
          keySelector = { entry ->
            entry.key.whatTimeModel
          },
          valueTransform = { entry ->
            entry.value.map { it.dateModel }.toPersistentList()
          }
        ).toImmutableMap()
      )
    }
  }

  override fun setRemindTime(remindTime: Int): String? {
    if (!enableModify()) return "提交修改后不可再修改"
    if (remindTime < 0) return "remindTime 不能小于 0"
    this.remindTime = remindTime
    return null
  }

  override fun setTitle(title: String): String? {
    if (!enableModify()) return "提交修改后不可再修改"
    if (title.isBlank()) return "title 不能为空"
    this.title = title
    return null
  }

  override fun setContent(content: String): String? {
    if (!enableModify()) return "提交修改后不可再修改"
    this.content = content
    return null
  }

  override fun add(timePair: MinuteTimePair): AffairWhatTimeModelEditor? {
    if (!enableModify()) return null
    editorWhatTimeDate { whatTimeDate ->
      whatTimeDate.keys.firstOrNull { it.timePair == timePair }?.let { return null } // 已有的话则直接返回 null
      val whatTimeModel = AffairWhatTimeModelImpl(
        idModel = idModel,
        timePair = timePair,
      )
      val whatTimeModelEditor = AffairWhatTimeModelEditorImpl(this, whatTimeModel)
      whatTimeDate[whatTimeModelEditor] = mutableListOf()
      incrementAddList.add(whatTimeModelEditor)
      sendValueByEditorStateFlow()
      return whatTimeModelEditor
    }
    return null
  }

  override fun remove(whatTime: AffairWhatTimeModelEditor): Boolean {
    if (!enableModify()) return false
    if (whatTime !is AffairWhatTimeModelEditorImpl) return false
    if (whatTime.idModelEditor !== this) return false
    editorWhatTimeDate { whatTimeDate ->
      if (whatTimeDate.contains(whatTime)) {
        whatTime.clear()
        whatTime.whatTimeModel.enable.valueByEditorStateFlow.tryEmit(whatTime to false)
        whatTimeDate.remove(whatTime)
        if (!incrementAddList.remove(whatTime)) {
          incrementRemoveList.add(whatTime)
        }
        sendValueByEditorStateFlow()
        return true
      }
    }
    return false
  }

  override fun clear() {
    if (!enableModify()) return
    editorWhatTimeDate { whatTimeDate ->
      whatTimeDate.keys.forEach {
        it.clear()
        it.whatTimeModel.enable.valueByEditorStateFlow.tryEmit(it to false)
        incrementRemoveList.add(it)
      }
      incrementRemoveList.removeAll(incrementAddList)
      incrementAddList.clear()
      whatTimeDate.clear()
      sendValueByEditorStateFlow()
    }
  }

  fun reset() {
    if (!enableModify) return
    setRemindTime(idModel.remindTime.value)
    setTitle(idModel.title.value)
    setContent(idModel.content.value)
    editorWhatTimeDate { whatTimeDate ->
      incrementAddList.forEach {
        it.reset()
        it.whatTimeModel.enable.valueByEditorStateFlow.tryEmit(it to false)
        whatTimeDate.remove(it)
      }
      incrementAddList.clear()
      incrementRemoveList.forEach {
        whatTimeDate[it] = mutableListOf()
        it.whatTimeModel.enable.valueByEditorStateFlow.tryEmit(it to true)
      }
      incrementRemoveList.clear()
      whatTimeDate.keys.forEach {
        it.reset()
      }
      sendValueByEditorStateFlow()
    }
  }

  override fun enableModify(): Boolean {
    return enableModify
  }

  private val commitMutex = Mutex()

  override suspend fun commit(needUpload: Boolean): Result<AffairIdModelEditor.EditResult> {
    if (!enableModify()) return Result.failure(IllegalStateException("提交修改后不可再修改"))
    return commitMutex.withLock {
      commitAction.invoke(this, needUpload).onSuccess {
        enableModify = false // 提交成功后不可再修改
      }
    }
  }
}