package com.cyxbs.pages.affair.model.b

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.affair.model.a.AffairDateModelImpl
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
class AffairDateModelEditorImpl(
  override val idModelEditor: AffairIdModelEditor,
  override val whatTime: MutableStateFlow<AffairWhatTimeModelEditorImpl?>,
  val dateModel: AffairDateModelImpl,
) : AffairDateModelEditor {
  override val date = MutableStateFlow(dateModel.date.value)

  override fun setDate(date: Date): String? {
    if (!idModelEditor.enableModify()) return "提交修改后不可再修改"
    val whatTime = whatTime.value
    if (whatTime != null && whatTime.dateList.value.any { it.date.value == date }) {
      return "该日期已存在"
    }
    this.date.value = date
    return null
  }

  override fun replace(whatTime: AffairWhatTimeModelEditor): Boolean {
    if (!idModelEditor.enableModify()) return false
    if (whatTime !is AffairWhatTimeModelEditorImpl) return false
    return whatTime.replace(this)
  }
}