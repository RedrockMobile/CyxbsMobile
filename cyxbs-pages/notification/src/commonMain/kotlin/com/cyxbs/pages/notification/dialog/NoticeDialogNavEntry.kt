package com.cyxbs.pages.notification.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.scene.DialogSceneStrategy
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.compose.theme.LocalAppDark
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.AppScheme
import com.cyxbs.components.navigation.NAV_DIALOG_NOTICE
import com.cyxbs.components.view.ui.DialogOneBtnCompose
import com.cyxbs.components.view.ui.DialogTwoBtnCompose
import com.cyxbs.pages.notification.api.NoticeNavArgument

/**
 * .
 *
 * @author 985892345
 * @date 2025/10/20
 */
@AppNav(route = NAV_DIALOG_NOTICE)
class NoticeDialogNavEntry : AppNavEntry<NoticeNavArgument>() {

  override fun isNeedLogin(argument: NoticeNavArgument): Boolean {
    return false
  }

  override fun buildMetadata(argument: NoticeNavArgument): Map<String, Any> {
    return DialogSceneStrategy.dialog(
      dialogProperties = DialogProperties(
        dismissOnBackPress = true, // 仅支持按返回键关闭 dialog
        dismissOnClickOutside = false,
      )
    )
  }

  @Composable
  override fun Content(argument: NoticeNavArgument) {
    NoticeDialogContent(argument = argument)
  }
}

@Composable
fun NoticeDialogContent(argument: NoticeNavArgument) {
  Column(
    modifier = Modifier.clip(RoundedCornerShape(16.dp))
      .background(LocalAppColors.current.topBg)
      .padding(top = 24.dp, start = 22.dp, end = 22.dp)
      .width(300.dp)
      .wrapContentSize(),
  ) {
    Text(
      text = argument.title,
      color = LocalAppColors.current.tvLv1,
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Text(
      text = buildNoticeDialogContentAnnotatedString(
        argument = argument,
        isDark = LocalAppDark.current,
      ),
      color = LocalAppColors.current.tvLv4,
      fontSize = 14.sp,
      modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
    )
    val button = argument.button
    if (button == null || button.action == null) {
      DialogOneBtnCompose(
        positiveBtnText = "关闭",
        onClickPositiveBtn = {
          argument.popBackStack()
        }
      )
    } else {
      DialogTwoBtnCompose(
        positiveBtnText = button.text,
        negativeBtnText = "关闭",
        onClickPositiveBtn = {
          AppScheme.jump(button.action!!)
        },
        onClickNegativeBtn = {
          argument.popBackStack()
        }
      )
    }
  }
}

private fun buildNoticeDialogContentAnnotatedString(
  argument: NoticeNavArgument,
  isDark: Boolean,
): AnnotatedString {
  val splitResult = splitByKeys(argument.content, argument.map.keys)
  return buildAnnotatedString {
    splitResult.forEach { text ->
      val textInfo = argument.map[text]
      if (textInfo == null) {
        append(text)
      } else {
        append(buildAnnotatedString {
          append(textInfo.text)
          val action = textInfo.action
          if (action != null) {
            addLink(
              LinkAnnotation.Clickable(
                tag = textInfo.text,
                styles = TextLinkStyles(
                  style = SpanStyle(
                    color = (if (isDark) textInfo.textDarkColor else textInfo.textColor)
                      ?: Color.Unspecified,
                    fontSize = textInfo.textSize.sp,
                    fontWeight = if (textInfo.isBold) FontWeight.Bold else null,
                    textDecoration = TextDecoration.Underline,
                  )
                )
              ) {
                AppScheme.jump(action)
              }, 0, length
            )
          }
        })
      }
    }
  }
}


/**
 * 以 keys 值来分割文本
 *
 * 比如：
 *   text = 123456789123456
 *   keys = [2, 6]
 * 输出：
 *   [1, 2, 345, 6, 7891, 2, 345, 6]
 *
 * @return 返回分割后的文本
 */
private fun splitByKeys(
  text: String,
  keys: Collection<String>,
): List<String> {
  val textList = mutableListOf(text)
  val result = mutableListOf<String>()
  keys.forEach { value ->
    result.clear()
    textList.forEach { content ->
      splitKey(content, value, result)
    }
    textList.clear()
    textList.addAll(result)
  }
  return textList
}

/**
 * 将 content 中的内容以 key 进行分割，类似于 split()，但是会包含 key 值
 */
private fun splitKey(
  content: String,
  key: String,
  result: MutableList<String>,
) {
  if (content.isEmpty()) return
  var end = 0
  while (true) {
    val start = content.indexOf(key, end)
    if (start == -1) break
    if (start > end) {
      result.add(content.substring(end, start))
    }
    result.add(key)
    end = start + key.length
  }
  if (end < content.length) {
    result.add(content.substring(end))
  }
}