package com.cyxbs.pages.mine.edit

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavArgument
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_EDIT_INFO
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.extensions.ImageAvatarCompose
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.mine.edit.viewmodel.EditInfoComposeViewModel
import cyxbsmobile.cyxbs_pages.mine.generated.resources.Res
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_avatar_question_mask
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.painterResource

/**
 * 「资料编辑」页路由参数
 */
@Serializable
object EditInfoNavArgument : AppNavArgument

/**
 * 「资料编辑」页（commonMain）
 *
 * 进入页面默认是预览态（所有字段只读展示），点击右上角「编辑」进入编辑态：
 * - 昵称 / 个人介绍 / 电话 / QQ 变成 BasicTextField，可输入
 * - 头像可点击触发选图 / 裁剪 / 上传
 *
 * 提交走 PUT /magipoke/person/info，头像文件单独走 upload/avatar 接口再回写 photo_src。
 */
@AppNav(route = NAV_EDIT_INFO)
class EditInfoNavEntry : AppNavEntry<EditInfoNavArgument>() {

  override fun isNeedLogin(argument: EditInfoNavArgument): Boolean = true

  @Composable
  override fun Content(argument: EditInfoNavArgument) {
    viewModel { EditInfoComposeViewModel() } // wasm 无法反射 new 对象，这里提供 factory
    EditInfoPage(argument)
  }
}

@Composable
private fun EditInfoPage(argument: EditInfoNavArgument) {
  val viewModel = viewModel(EditInfoComposeViewModel::class)
  val isEditing = viewModel.isEditing.value
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(LocalAppColors.current.bottomBg)
      .statusBarsPadding()
  ) {
    EditInfoTopBar(
      isEditing = isEditing,
      saving = viewModel.saving.value,
      onBack = { argument.popBackStack() },
      onEnterEdit = { viewModel.enterEditing() },
      onCancel = { viewModel.cancelEditing() },
      onSave = { viewModel.save() },
    )
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .imePadding()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
      // 顶部信息区：头像 + 姓名 / 学号 / 学院（不可改），头像始终可点击
      ProfileHeaderSection(viewModel)
      Spacer(modifier = Modifier.height(24.dp))
      // 可编辑卡片
      EditableSection(viewModel, isEditing = isEditing)
    }
  }
}

@Composable
private fun EditInfoTopBar(
  isEditing: Boolean,
  saving: Boolean,
  onBack: () -> Unit,
  onEnterEdit: () -> Unit,
  onCancel: () -> Unit,
  onSave: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(56.dp),
  ) {
    // 左侧：编辑态显示「取消」，预览态显示返回箭头。
    if (isEditing) {
      Text(
        text = "取消",
        fontSize = 16.sp,
        color = LocalAppColors.current.tvLv2,
        modifier = Modifier
          .align(Alignment.CenterStart)
          .padding(start = 16.dp)
          .clickableSingle(enabled = !saving, onClick = onCancel),
      )
    } else {
      IconButton(
        onClick = onBack,
        modifier = Modifier
          .align(Alignment.CenterStart)
          .padding(start = 4.dp),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "返回",
          tint = LocalAppColors.current.tvLv1,
        )
      }
    }
    Text(
      text = "资料编辑",
      modifier = Modifier.align(Alignment.Center),
      fontSize = 18.sp,
      fontWeight = FontWeight.Bold,
      color = LocalAppColors.current.tvLv1,
      textAlign = TextAlign.Center,
    )
    // 右侧：预览态显示「编辑」，编辑态显示「保存」/「保存中」
    val (rightText, rightEnabled) = when {
      saving -> "保存中" to false
      isEditing -> "保存" to true
      else -> "编辑" to true
    }
    Text(
      text = rightText,
      fontSize = 16.sp,
      color = if (rightEnabled) LocalAppColors.current.positive
      else LocalAppColors.current.tvLv2.copy(alpha = 0.4f),
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .padding(end = 16.dp)
        .clickableSingle(
          enabled = rightEnabled,
          onClick = if (isEditing) onSave else onEnterEdit,
        ),
    )
  }
}

/**
 * 顶部信息区：头像 + 不可改的身份信息（姓名 / 学号 / 学院）。
 *
 * - 头像始终可点击（不依赖 isEditing），点击触发平台层选图 / 裁剪 / 上传
 * - 行末尾放「头像使用协议」气泡图标，点击弹协议 dialog
 * - 不再使用 label-value 列表的形式，姓名作为主标题，学号 / 学院作为副标题
 */
@Composable
private fun ProfileHeaderSection(viewModel: EditInfoComposeViewModel) {
  val platform = remember { EditInfoPlatform::class.implOrNull() }
  val userInfo by viewModel.userInfo.collectAsStateWithLifecycle()
  var showAgreement by remember { mutableStateOf(false) }
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ImageAvatarCompose(
      url = viewModel.photoSrc.value,
      modifier = Modifier
        .size(72.dp)
        .clip(CircleShape)
        .clickableNoIndicator {
          // 上传结果由 ViewModel 通过 EditInfoPlatform.avatarUpdatedEvents 接收，
          // 这里不传 callback，避免 ViewModel 被平台层静态字段持有导致泄漏
          if (platform != null) {
            platform.editAvatar()
          } else {
            toast("当前平台暂不支持修改头像")
          }
        },
    )
    Column(
      modifier = Modifier
        .weight(1f)
        .padding(start = 16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = userInfo?.username.orEmpty(),
        fontSize = 18.sp,
        color = LocalAppColors.current.tvLv1,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = listOfNotNull(
          userInfo?.stuNum?.takeIf { it.isNotEmpty() },
          userInfo?.college?.takeIf { it.isNotEmpty() },
        ).joinToString(" · "),
        fontSize = 13.sp,
        color = LocalAppColors.current.tvLv2.copy(alpha = 0.6f),
      )
    }
    // 头像协议气泡：放在行末尾，与原 XML 中 mine_edit_iv_agreement 的位置一致
    Image(
      painter = painterResource(Res.drawable.mine_ic_avatar_question_mask),
      contentDescription = "头像使用协议",
      modifier = Modifier
        .size(18.dp)
        .clickableNoIndicator {
          viewModel.loadPortraitAgreement()
          showAgreement = true
        },
    )
  }
  if (showAgreement) {
    PortraitAgreementDialog(viewModel = viewModel, onDismiss = { showAgreement = false })
  }
}

/**
 * 头像使用协议弹窗，文案来自 [EditInfoComposeViewModel.portraitAgreement]
 */
@Composable
private fun PortraitAgreementDialog(
  viewModel: EditInfoComposeViewModel,
  onDismiss: () -> Unit,
) {
  Dialog(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(LocalAppColors.current.topBg, shape = RoundedCornerShape(16.dp))
        .padding(20.dp),
    ) {
      val items = viewModel.portraitAgreement.value
      // 内容区：固定最大高度 + 可上下滑动；中间空数据时显示居中 loader（对齐老代码）
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 420.dp),
      ) {
        if (items.isEmpty()) {
          if (viewModel.portraitAgreementLoading.value) {
            CircularProgressIndicator(
              modifier = Modifier
                .size(32.dp)
                .align(Alignment.Center),
              color = LocalAppColors.current.positive,
            )
          } else {
            Text(
              text = "暂无内容",
              fontSize = 14.sp,
              color = LocalAppColors.current.tvLv2.copy(alpha = 0.6f),
              modifier = Modifier.align(Alignment.Center),
            )
          }
        } else {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
          ) {
            items.forEach { item ->
              PortraitAgreementItem(item)
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
      Text(
        text = "我知道了",
        fontSize = 15.sp,
        color = LocalAppColors.current.positive,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
          .align(Alignment.End)
          .clickableSingle(onClick = onDismiss),
      )
    }
  }
}

/**
 * 协议弹窗的单个 item。沿用老 DynamicRVAdapter 的渲染规则：
 * - title == "title"：当作大标题展示（20sp 居中），content 隐藏（用 title 控件渲染 content）
 * - title == "time"：仅展示 content，标题隐藏
 * - 其他：title 18sp 加粗 + content 18sp 普通；同时把后端嵌入的 `ß` 字符替换为 6 空格做缩进
 *   （"3.我的" 后端漏字符的兜底也保留）
 */
@Composable
private fun PortraitAgreementItem(item: com.cyxbs.pages.mine.user.bean.DownMessageBean.DownMessageText) {
  val tvLv2 = LocalAppColors.current.tvLv2
  when (item.title) {
    "title" -> {
      Text(
        text = item.content,
        fontSize = 20.sp,
        color = tvLv2,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp),
      )
    }
    "time" -> {
      Text(
        text = item.content,
        fontSize = 18.sp,
        color = tvLv2,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp),
      )
    }
    else -> {
      val titleText = item.title.replace("ß", "      ")
      val contentText = if (item.title == "3.我的") {
        item.content.replace("优化了", "        优化了")
      } else {
        item.content.replace("ß", "      ")
      }
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = titleText,
          fontSize = 18.sp,
          color = tvLv2,
          fontWeight = FontWeight.Bold,
          lineHeight = 28.sp,
          modifier = Modifier.padding(top = 10.dp),
        )
        Text(
          text = contentText,
          fontSize = 18.sp,
          color = tvLv2,
          lineHeight = 28.sp,
          modifier = Modifier.padding(top = 10.dp),
        )
      }
    }
  }
}

/**
 * 可编辑字段卡片：昵称 / 个人介绍 / 电话 / QQ
 * 预览态下渲染为只读文本，编辑态下渲染为 BasicTextField。
 */
@Composable
private fun EditableSection(viewModel: EditInfoComposeViewModel, isEditing: Boolean) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(LocalAppColors.current.topBg, shape = RoundedCornerShape(12.dp))
      .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    FieldRow(
      label = "昵称",
      state = viewModel.nicknameState,
      hint = "请输入昵称",
      keyboardType = KeyboardType.Text,
      singleLine = true,
      isEditing = isEditing,
    )
    DividerLine()
    FieldRow(
      label = "签名",
      state = viewModel.introductionState,
      hint = "写句话介绍自己吧~",
      keyboardType = KeyboardType.Text,
      singleLine = true,
      isEditing = isEditing,
    )
    DividerLine()
    FieldRow(
      label = "电话",
      state = viewModel.phoneState,
      hint = "请输入电话号码",
      keyboardType = KeyboardType.Phone,
      singleLine = true,
      isEditing = isEditing,
    )
    DividerLine()
    FieldRow(
      label = "QQ",
      state = viewModel.qqState,
      hint = "请输入 QQ 号",
      keyboardType = KeyboardType.Number,
      singleLine = true,
      isEditing = isEditing,
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FieldRow(
  label: String,
  state: TextFieldState,
  hint: String,
  keyboardType: KeyboardType,
  singleLine: Boolean,
  isEditing: Boolean,
) {
  val bringIntoViewRequester = remember { BringIntoViewRequester() }
  val scope = rememberCoroutineScope()
  val contentTextStyle = TextStyle(
    fontSize = 15.sp,
    lineHeight = 22.sp,
    color = LocalAppColors.current.tvLv2,
  )
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      fontSize = 15.sp,
      lineHeight = 22.sp,
      color = LocalAppColors.current.tvLv2,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(end = 16.dp),
    )
    Box(modifier = Modifier.fillMaxWidth()) {
      BasicTextField(
        modifier = Modifier
          .fillMaxWidth()
          .bringIntoViewRequester(bringIntoViewRequester)
          .onFocusChanged { focusState ->
            if (focusState.isFocused) {
              // 聚焦时把字段滚到 IME 上方，防止键盘遮挡
              scope.launch { bringIntoViewRequester.bringIntoView() }
            }
          },
        state = state,
        enabled = isEditing,
        cursorBrush = SolidColor(LocalAppColors.current.positive),
        lineLimits = if (singleLine) {
          TextFieldLineLimits.SingleLine
        } else {
          TextFieldLineLimits.MultiLine(maxHeightInLines = 4)
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = contentTextStyle.copy(
          // 预览态用稍暗的字色（alpha = 0.7f）做视觉区分；编辑态全色。
          color = contentTextStyle.color.copy(alpha = if (isEditing) 1f else 0.7f),
        ),
      )
      // 空值占位：编辑态显示 hint，预览态显示「未填写」
      if (state.text.isEmpty()) {
        Text(
          text = if (isEditing) hint else "未填写",
          style = contentTextStyle.copy(color = contentTextStyle.color.copy(alpha = 0.3f)),
          modifier = Modifier.align(Alignment.CenterStart)
        )
      }
    }
  }
}

@Composable
private fun DividerLine() {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(0.5.dp)
      .background(LocalAppColors.current.tvLv2.copy(alpha = 0.1f)),
  )
}
