package com.cyxbs.pages.mine.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.login.rememberLoginDialogState
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.ImageAvatarCompose
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.mine.about.ui.AboutNavArgument
import com.cyxbs.pages.mine.edit.EditInfoNavArgument
import com.cyxbs.pages.mine.home.viewmodel.MineComposeViewModel
import cyxbsmobile.cyxbs_pages.mine.generated.resources.Res
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_activity_center
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_arrow_right
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_feedback_center
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_info_header
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_notification_center
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_stamp_center
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/* ---------------- 颜色（还原 mine_color.xml light/night） ---------------- */

private val NicknameColor @Composable get() = 0xFF15315B.dark(0xFFFFFFFF)    // mine_text_nickname
private val IntroduceColor @Composable get() = 0xFF15315B.dark(0xFFFFFFFF)   // mine_text_introduce
private val SettingTextColor @Composable get() = 0xFF15315B.dark(0xFFF0F0F2) // mine_text_setting
private val SignTextColor @Composable get() = 0xFF15315B.dark(0xFFFFFFFF)    // mine_text_sign
private val SignBtnColor = Color(0xFF4A44E4)                                            // mine_shape_bg_user_btn_sign
private val SignedBtnBgColor @Composable get() = 0xFFEAEAEA.dark(0xFF4A4A4A) // mine_bg_round_corner_grey
private val SignedBtnTextColor @Composable get() = 0xFF9E9E9E.dark(0xFFBEBEBE)

/**
 * 「我的」主页（commonMain）
 *
 * 作为主页 ViewPager 的 tab 通过 [com.cyxbs.pages.home.api.IHomeMineTab] 嵌入。
 */
@Composable
fun MinePage() {
  val viewModel = viewModel { MineComposeViewModel() } // wasm/iOS 无法反射 new 对象，这里提供 factory
  val userInfo by viewModel.userInfo.collectAsStateWithLifecycle()
  val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
  val cornerRadius = 16.dp

  Column(
    modifier = Modifier
      .fillMaxSize()
      .navigationBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(-cornerRadius)
  ) {
    // 顶部头图 + 头像 + 昵称
    InfoHeader(
      name = userInfo?.nickname ?: "",
      avatarUrl = userInfo?.photoSrc ?: "",
      introduction = userInfo?.introduction,
    )

    // 主体卡片
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(
          color = LocalAppColors.current.middleBg,
          shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
        )
    ) {
      FunctionArea(unreadCount = unreadCount)

      SignArea(
        serialDays = viewModel.serialDays.intValue,
        isChecked = viewModel.isChecked.value,
      )

      SettingItem(text = "关于我们", topPadding = 32.dp)
      SettingItem(text = "设置", topPadding = 32.dp)

      // 空出课表头的 70dp 以便能完全展示页面
    }
  }
}

/**
 * 「我的」主页平台能力实例，commonMain 内统一通过它做登录拦截 + 平台跳转。
 * 平台无实现时（非 Android）各跳转优雅降级为 toast。
 */
@Composable
private fun rememberMinePlatform(): MineNavPlatform? =
  remember { MineNavPlatform::class.implOrNull() }


/**
 * 顶部头图区：drawBehind 绘制基底圆形 + 半透明圆角卡片 + 头像/昵称叠加
 */
@Composable
private fun InfoHeader(
  name: String,
  avatarUrl: String,
  introduction: String?
) {
  val loginDialogState = rememberLoginDialogState()
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clickableNoIndicator {
        loginDialogState.doIfLogin(function = "编辑资料") {
          EditInfoNavArgument.navigate()
        }
      }
  ) {
    // 半透明圆角卡片
    Image(
      painter = painterResource(Res.drawable.mine_ic_info_header),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier = Modifier.fillMaxWidth(),
    )

    Column(
      modifier = Modifier.padding(top = 100.dp)
    ) {
      Row(
        modifier = Modifier.padding(start = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // 头像 + 昵称 + 介绍语
        ImageAvatarCompose(
          url = avatarUrl,
          modifier = Modifier.size(64.dp).clip(CircleShape),
        )
        Text(
          text = name,
          color = NicknameColor,
          fontSize = 22.sp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 16.dp),
        )
      }
      Text(
        text = introduction ?: "快来红岩网校和我一起玩吧~",
        color = IntroduceColor,
        fontSize = 18.sp,
        modifier = Modifier.padding(start = 50.dp, top = 30.dp),
      )
    }
  }
}

/**
 * 功能区：消息中心 / 邮票中心 / 反馈中心（第一行），活动中心（第二行）
 */
@Composable
private fun FunctionArea(
  unreadCount: Int,
) {
  val loginDialogState = rememberLoginDialogState()
  val platform = rememberMinePlatform()
  // 进入消息中心后本地隐藏红点；未读数变化时重新显示
  var notificationDismissed by remember { mutableStateOf(false) }
  LaunchedEffect(unreadCount) { notificationDismissed = false }

  Column(modifier = Modifier.fillMaxWidth().padding(top = 30.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      FuncItem(
        image = Res.drawable.mine_ic_notification_center,
        label = "消息中心",
        onClick = {
          // 消息中心不做登录拦截（与原页面一致）
          notificationDismissed = true
          platform?.launchNotification() ?: toast("暂不支持跳转")
        },
        badgeCount = if (notificationDismissed) 0 else unreadCount,
      )
      FuncItem(
        image = Res.drawable.mine_ic_stamp_center,
        label = "邮票中心",
        onClick = {
          loginDialogState.doIfLogin(function = "邮票中心") {
            platform?.jumpStore() ?: toast("暂不支持跳转")
          }
        },
      )
      FuncItem(
        image = Res.drawable.mine_ic_activity_center,
        label = "活动中心",
        onClick = {
          loginDialogState.doIfLogin(function = "活动中心") {
            platform?.jumpActivityCenter() ?: toast("暂不支持跳转")
          }
        },
      )
      FuncItem(
        image = Res.drawable.mine_ic_feedback_center,
        label = "反馈中心",
        onClick = {
          loginDialogState.doIfLogin(function = "反馈中心") {
            platform?.jumpFeedbackCenter() ?: toast("暂不支持跳转")
          }
        },
      )
      /* 一行最多放 3 ～ 4 个，多的放下一行，并改成 3 + 2 结构 */
    }
  }
}

/**
 * 单个功能入口：图标 + 文字（消息中心带未读红点）
 */
@Composable
private fun FuncItem(
  image: DrawableResource,
  label: String,
  onClick: () -> Unit,
  badgeCount: Int = 0,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.clickableNoIndicator(onClick = onClick),
  ) {
    Box {
      Image(
        painter = painterResource(image),
        contentDescription = label,
        modifier = Modifier.size(43.dp),
        contentScale = ContentScale.Fit,
      )
      if (badgeCount > 0) {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .size(15.dp)
            .drawWithContent {
              drawCircle(color = Color(0xFFFF6262))
              drawContent()
            },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = if (badgeCount > 99) "99+" else badgeCount.toString(),
            color = Color.White,
            fontSize = if (badgeCount > 99) 8.sp else 10.sp,
          )
        }
      }
    }
    Text(
      text = label,
      color = SettingTextColor,
      fontSize = 14.sp,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}

/**
 * 签到区：背景图 + 「已连续签到 N 天」+ 签到按钮
 */
@Composable
private fun SignArea(
  serialDays: Int,
  isChecked: Boolean,
) {
  val loginDialogState = rememberLoginDialogState()
  val platform = rememberMinePlatform()
  val shape = RoundedCornerShape(8.dp)
  Box(
    modifier = Modifier
      .padding(top = 24.dp, start = 16.dp, end = 16.dp)
      .fillMaxWidth()
      .height(50.dp)
      .shadow(
        elevation = 4.dp,
        shape = shape,
        ambientColor = LocalAppColors.current.bottomBg,
        spotColor = LocalAppColors.current.bottomBg,
      )
      .background(color = LocalAppColors.current.topBg, shape = shape)
      .clickableNoIndicator {
        loginDialogState.doIfLogin(function = "签到") {
          platform?.jumpSign() ?: toast("暂不支持跳转")
        }
      }
  ) {
    Text(
      text = "已连续签到 $serialDays 天",
      color = SignTextColor,
      fontSize = 16.sp,
      modifier = Modifier
        .align(Alignment.CenterStart)
        .padding(start = 16.dp)
    )
    SignButton(
      isChecked = isChecked,
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .padding(end = 16.dp),
    )
  }
}

/**
 * 签到按钮：未签到为蓝色胶囊「签到」，已签到为灰色胶囊「已签到」
 */
@Composable
private fun SignButton(
  isChecked: Boolean,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(percent = 50)
  Box(
    modifier = modifier
      .height(26.dp)
      .clip(shape)
      .background(if (isChecked) SignedBtnBgColor else SignBtnColor)
      .padding(horizontal = 10.dp, vertical = 3.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = if (isChecked) "已签到" else "签到",
      color = if (isChecked) SignedBtnTextColor else Color.White,
      fontSize = 14.sp,
    )
  }
}

/**
 * 「关于我们」「设置」行：文字 + 右箭头
 */
@Composable
private fun SettingItem(
  text: String,
  topPadding: androidx.compose.ui.unit.Dp,
) {
  val loginDialogState = rememberLoginDialogState()
  val platform = rememberMinePlatform()
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = topPadding)
      .clickableNoIndicator {
        loginDialogState.doIfLogin(function = text) {
          when (text) {
            "关于我们" -> AboutNavArgument.navigate()
            else -> platform?.jumpSetting() ?: toast("暂不支持跳转")
          }
        }
      }
  ) {
    Text(
      text = text,
      color = SettingTextColor,
      fontSize = 16.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier
        .align(Alignment.CenterStart)
        .padding(start = 16.dp),
    )
    Image(
      painter = painterResource(Res.drawable.mine_ic_arrow_right),
      contentDescription = null,
      modifier = Modifier
        .align(Alignment.CenterEnd)
        .padding(end = 17.dp)
        .size(width = 8.dp, height = 14.dp),
    )
  }
}
