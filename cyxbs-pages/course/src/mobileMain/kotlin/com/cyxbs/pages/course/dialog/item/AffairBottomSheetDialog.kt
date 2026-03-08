package com.cyxbs.pages.course.dialog.item

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.AffairIdModelEditor.EditResult
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.course.dialog.CourseItemBottomSheetDialogState
import com.cyxbs.pages.course.dialog.item.AffairBottomSheetDialogState.CurrentForm
import com.cyxbs.pages.course.dialog.item.affair.AffairEditCompose
import com.cyxbs.pages.course.dialog.item.affair.AffairShowCompose

/**
 * .
 *
 * @author 985892345
 * @date 2025/5/25
 */

class AffairBottomSheetDialogState(
  currentForm: CurrentForm,
) {

  val currentFormState = mutableStateOf(currentForm)

  sealed interface CurrentForm {
    val date: Date

    val whatTime: MinuteTimePair

    val title: String

    val content: String

    data class Show(private val model: AffairDateModel) : CurrentForm {
      override val date: Date
        get() = model.date.value
      override val whatTime: MinuteTimePair
        get() = model.whatTime.value.timePair.value
      override val title: String
        get() = model.idModel.title.value
      override val content: String
        get() = model.idModel.content.value

      fun createEdit(): Edit? {
        val editor = model.idModel.tryCreateEditor()
        if (editor != null) {
          val dateModelEditor = editor.findDateModelEditor(model)
          if (dateModelEditor != null) {
            return Edit(dateModelEditor)
          } else {
            toast("出现异常，当前 model 无法找到对应 editor")
          }
        } else {
          toast("出现异常，当前 model 无法创建 editor")
        }
        return null
      }
    }

    data class Edit(
      var editor: AffairDateModelEditor,
      val isCreateAffair: Boolean = false, // 是否是创建新事务
      val commitCallback: (Result<EditResult>) -> Unit = { result ->
        result.onSuccess {
          when (it) {
            EditResult.Deleted -> toast("事务已被删除")
            EditResult.Success -> toast("修改成功")
          }
        }
      },
    ) : CurrentForm {
      val isInEditTime = mutableStateOf(false)
      val isHourMinuteValid = mutableStateOf(true)
      override val date: Date
        get() = editor.date
      override val whatTime: MinuteTimePair
        get() = editor.whatTimeEditor!!.timePair
      override val title: String
        get() = editor.idModelEditor.title
      override val content: String
        get() = editor.idModelEditor.content

      // 编辑状态
      val editState = mutableStateOf(EditState.EditBasic)

      // 编辑时间段
      val editTimePair = mutableStateOf<AffairWhatTimeModelEditor?>(null)

      // 原始数据
      private val originModel: AffairDateModel = editor.dateModel

      // 点击保存时的检查
      val clickSaveCheck = mutableListOf<() -> String?>()

      // 点击上一步时的检查
      val clickPrevCheck = mutableListOf<() -> String?>()

      // 点击其他时间时的检查
      val clickSwitchTimeCheck = mutableListOf<() -> String?>()

      // 取消编辑
      fun cancelEdit(): Show? {
        if (editor.idModelEditor.cancelEdit()) {
          clickSaveCheck.clear()
          clickPrevCheck.clear()
          clickSwitchTimeCheck.clear()
          return Show(originModel)
        }
        return null
      }

      // 提交
      suspend fun commit(): Show? {
        return editor.idModelEditor.commit().also {
          commitCallback.invoke(it)
        }.map {
          clickSaveCheck.clear()
          clickPrevCheck.clear()
          clickSwitchTimeCheck.clear()
          Show(editor.dateModel)
        }.getOrNull()
      }
    }

    enum class EditState {
      EditBasic, EditTime
    }
  }
}


@Composable
fun AffairBottomSheetDialog(
  courseBottomSheetDialogState: CourseItemBottomSheetDialogState,
  affairBottomSheetDialogState: AffairBottomSheetDialogState,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(top = 16.dp, start = 16.dp, end = 16.dp)
  ) {
    when (val currentForm = affairBottomSheetDialogState.currentFormState.value) {
      is CurrentForm.Show -> AffairShowCompose(
        currentForm = currentForm,
        courseState = courseBottomSheetDialogState,
        affairState = affairBottomSheetDialogState,
      )
      is CurrentForm.Edit -> AffairEditCompose(
        currentForm = currentForm,
        courseState = courseBottomSheetDialogState,
        affairState = affairBottomSheetDialogState,
      )
    }
  }
}