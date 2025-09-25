package com.cyxbs.pages.affair.model.a

import com.cyxbs.components.config.sp.AccountSettings
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.model.b.AffairIdModelEditorImpl
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/14
 */
class AffairIdModelImpl(
  stuNum: String,
  override val localId: String,
) : AffairIdModel {

  private val settings = AccountSettings.get(stuNum)

  override val enable = MutableStateFlow(true)
  override val remindTime = MutableStateFlow(settings.getInt(KEY_REMINDTIME, 0))
  override val title = MutableStateFlow(settings.getString(KEY_TITLE, ""))
  override val content = MutableStateFlow(settings.getString(KEY_CONTENT, ""))
  override val whatTimeList = MutableStateFlow(

  )

  private val affairIdModelEditorFlow = MutableStateFlow<AffairIdModelEditor?>(null)

  override fun createEditor(): AffairIdModelEditor? {
    val editor = AffairIdModelEditorImpl(idModel = this) { update() }
    if (affairIdModelEditorFlow.compareAndSet(null, editor)) {
      // 确保线程安全
      return editor
    }
    return null
  }

  override suspend fun createEditorSuspend(): AffairIdModelEditor {
    while (true) {
      val editor = createEditor()
      if (editor != null) {
        return editor
      }
      affairIdModelEditorFlow.first { it == null } // 挂起直到下一次为 null 时
    }
  }

  private suspend fun update(): Result<AffairIdModelEditor.EditResult> {

  }
}