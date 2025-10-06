package com.cyxbs.pages.affair.model.impl

import com.cyxbs.components.config.time.Date
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.AffairWhatTimeModel
import com.cyxbs.pages.affair.model.editor.AffairDateModelEditorImpl
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * .
 *
 * @author 985892345
 * @date 2025/9/23
 */
class AffairDateModelImpl(
  override val idModel: AffairIdModel,
  whatTimeModel: AffairWhatTimeModelImpl,
  date: Date,
) : AffairDateModel {
  override val enable: EditorStateFlowImpl<AffairDateModelEditor, Boolean> =
    EditorStateFlowImpl(
      valueFlow = MutableStateFlow(true)
    )
  override val whatTime: EditorStateFlowImpl<AffairDateModelEditor, AffairWhatTimeModel> =
    EditorStateFlowImpl(
      valueFlow = MutableStateFlow(whatTimeModel),
    )
  override val date: EditorStateFlowImpl<AffairDateModelEditor, Date> =
    EditorStateFlowImpl(
      valueFlow = MutableStateFlow(date),
    )

  fun update(editor: AffairDateModelEditorImpl) {
    editor.whatTimeEditor?.let {
      whatTime.valueStateFlow.value = it.whatTimeModel
    }
    date.valueStateFlow.value = editor.date
  }
}