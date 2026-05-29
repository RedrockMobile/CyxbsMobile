package com.cyxbs.pages.course.find.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.compose.theme.LocalAppDark
import com.cyxbs.components.navigation.appNavBackStack
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.ui.rememberTextDialog
import com.cyxbs.pages.course.api.CourseNavArgument
import com.cyxbs.pages.course.api.FindCourseNavArgument
import com.cyxbs.pages.course.find.viewmodel.FindCourseViewModel

private const val FIND_DETAIL_STABLE_KEY = "course-find-detail"

@Composable
internal fun FindCourseScreen(
  argument: FindCourseNavArgument,
  modifier: Modifier = Modifier,
) {
  val vm = viewModel<FindCourseViewModel>()
  // 处理路由初始参数
  LaunchedEffect(argument) {
    argument.directStuNum.takeIf { it.isNotBlank() }?.let { navigateToCourse(it) }
    argument.initialQuery.takeIf { it.isNotBlank() }?.let { vm.setQuery(it) }
  }

  val searchState by vm.searchState.collectAsState()

  // 统一返回逻辑：搜索状态非 Idle 时先清空输入回到 Idle，否则真正退出页面。
  // 由顶部栏返回按钮、系统返回键 / 手势 / ESC 共用
  val onBack: () -> Unit = {
    if (searchState !is FindCourseViewModel.SearchState.Idle) {
      vm.setQuery("")
    } else {
      popBackStack(argument)
    }
  }
  val backEventState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
  NavigationBackHandler(
    state = backEventState,
    isBackEnabled = searchState !is FindCourseViewModel.SearchState.Idle,
    onBackCompleted = { vm.setQuery("") },
  )

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(LocalAppColors.current.bottomBg)
      .systemBarsPadding(),
  ) {
    FindCourseTopBar(onBack = onBack)
    SearchField(
      state = vm.queryTextFieldState,
      onClear = { vm.setQuery("") },
    )
    SearchBottomContent()
  }
}

@Composable
private fun SearchBottomContent() {
  val vm = viewModel<FindCourseViewModel>()
  val searchState by vm.searchState.collectAsState()
  val history by vm.history.collectAsState()
  val linkState by vm.linkState.collectAsState()

  // 关联/解除 确认弹窗（统一文本 dialog，文案与按钮回调通过 showAndCover 动态覆盖）
  val linkDialog = rememberTextDialog(
    text = "",
    negativeBtnText = "取消",
  )
  fun linkStuAction(stuNum: String, name: String) {
    val isLinked = stuNum == linkState.linkNum && linkState.isNotNull()
    val (msg, onConfirm) = when {
      isLinked -> "确定要取消关联吗？" to { vm.deleteLink(); toast("已取消关联") }
      linkState.isNotNull() -> "你已有一位关联的同学\n确定要替换吗？" to
          { vm.changeLink(stuNum, "关联成功") }
      else -> "确定要将「${name}」关联为你的同学吗？" to
          { vm.changeLink(stuNum, "关联成功") }
    }
    linkDialog.showAndCover(
      textProxy = msg,
      onClickPositiveBtnProxy = {
        onConfirm()
        dismiss()
      }
    )
  }

  AnimatedContent(
    targetState = searchState,
    modifier = Modifier.fillMaxSize(),
    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
    // 同一 sealed class 分支视为同一 content，避免 Success 内列表变化也触发整体淡入淡出
    contentKey = { it.contentKey() },
    label = "find-search-state",
  ) { state ->
    when (state) {
      FindCourseViewModel.SearchState.Idle -> IdleContent(
        history = history,
        linkState = linkState,
        onHistoryClick = { entity -> navigateToCourse(entity.stuNum) },
        onHistoryLongClick = { entity -> linkStuAction(entity.stuNum, entity.name) },
        onHistoryDelete = { entity -> vm.deleteHistory(entity.stuNum) },
        onLinkCardClick = { stuNum -> navigateToCourse(stuNum) },
        onLinkCardDelete = {
          linkDialog.showAndCover(
            textProxy = "确定要取消关联吗？",
            onClickPositiveBtnProxy = {
              vm.deleteLink()
              toast("已取消关联")
              dismiss()
            },
          )
        },
      )

      FindCourseViewModel.SearchState.Loading -> TopHintStatus {
        CircularProgressIndicator(modifier = Modifier.size(28.dp))
      }

      FindCourseViewModel.SearchState.Empty -> TopHintStatus {
        Text("查无此人", color = LocalAppColors.current.tvLv2, fontSize = 14.sp)
      }

      is FindCourseViewModel.SearchState.Error -> TopHintStatus {
        Text(state.message, color = LocalAppColors.current.tvLv2, fontSize = 14.sp)
      }

      is FindCourseViewModel.SearchState.Success -> SearchResultList(
        list = state.list,
        linkedStuNum = linkState.linkNum.takeIf { linkState.isNotNull() },
        onItemClick = { bean ->
          vm.rememberSelection(bean)
          navigateToCourse(bean.stuNum)
        },
        onItemLongClick = { bean ->
          linkStuAction(bean.stuNum, bean.name)
        },
      )
    }
  }
}

@Composable
private fun FindCourseTopBar(onBack: () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(56.dp)
      .padding(horizontal = 4.dp),
  ) {
    IconButton(modifier = Modifier.align(Alignment.CenterStart), onClick = onBack) {
      Icon(
        Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = null,
        tint = LocalAppColors.current.tvLv1
      )
    }
    Text(
      text = "查课表",
      modifier = Modifier.align(Alignment.Center),
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      color = LocalAppColors.current.tvLv1,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.size(40.dp))
  }
}

// 搜索框样式色值（对齐老 course_edit_text_bg / course_edit_text_hint）
internal val SearchBgLight = Color(0xACE8F0FC)
internal val SearchBgDark = Color(0xB32C2C2C)
private val SearchHintLight = Color(0xFF94A6C4)
private val SearchHintDark = Color(0x85F0F0F2)

@Composable
private fun SearchField(
  state: TextFieldState,
  onClear: () -> Unit,
) {
  val colors = LocalAppColors.current
  val isDark = LocalAppDark.current
  val bg = if (isDark) SearchBgDark else SearchBgLight
  val hintColor = if (isDark) SearchHintDark else SearchHintLight
  Box(
    modifier = Modifier
      .padding(horizontal = 18.dp, vertical = 16.dp)
      .fillMaxWidth()
      .height(44.dp)
      .background(bg, RoundedCornerShape(29.dp))
      .padding(horizontal = 18.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    BasicTextField(
      state = state,
      lineLimits = TextFieldLineLimits.SingleLine,
      modifier = Modifier.fillMaxWidth(),
      textStyle = TextStyle(color = colors.tvLv2, fontSize = 18.sp),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      cursorBrush = SolidColor(MaterialTheme.colors.primary),
      decorator = { inner ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          val testIsEmpty by rememberDerivedStateOfStructure { state.text.isEmpty() }
          Box(modifier = Modifier.weight(1f)) {
            if (testIsEmpty) {
              Text("输入学号或姓名", color = hintColor, fontSize = 18.sp)
            }
            inner()
          }
          if (!testIsEmpty) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = null,
              tint = hintColor,
              modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onClear),
            )
          }
        }
      },
    )
  }
}

// IdleContent / LinkCard 已拆至同包 IdleContent.kt / LinkCard.kt

/** 搜索框正下方一定距离的提示位（Loading / Empty / Error 共用），不再撑满剩余空间居中 */
@Composable
private fun TopHintStatus(content: @Composable () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 60.dp),
    contentAlignment = Alignment.TopCenter,
  ) { content() }
}
/**
 * AnimatedContent 切换的 key：把 Success 内列表变化收敛为同一 key，避免列表更新触发整体淡入淡出
 */
private fun FindCourseViewModel.SearchState.contentKey(): Int = when (this) {
  FindCourseViewModel.SearchState.Idle -> 0
  FindCourseViewModel.SearchState.Loading -> 1
  FindCourseViewModel.SearchState.Empty -> 2
  is FindCourseViewModel.SearchState.Error -> 3
  is FindCourseViewModel.SearchState.Success -> 4
}

/* ---------- 跳转 ---------- */

private fun popBackStack(argument: FindCourseNavArgument) {
  val index = appNavBackStack.lastIndexOf(argument)
  val course = appNavBackStack.getOrNull(index + 1)
  if (course is CourseNavArgument) {
    course.popBackStack()
  }
  argument.popBackStack()
}

internal fun navigateToCourse(stuNum: String) {
  val arg = CourseNavArgument(stuNum = stuNum, stableKey = FIND_DETAIL_STABLE_KEY)
  val last = appNavBackStack.last()
  if (last is CourseNavArgument) {
    // 弹出旧的 CourseNavArgument，后续添加新的
    last.popBackStack()
  }
  arg.navigate()
}
