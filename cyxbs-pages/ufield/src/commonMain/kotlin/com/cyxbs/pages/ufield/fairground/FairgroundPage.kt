package com.cyxbs.pages.ufield.fairground

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.login.rememberLoginDialogState
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.components.utils.extensions.ImageAvatarCompose
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.food.api.FoodNavArgument
import com.cyxbs.pages.ufield.fairground.constraint.Element
import com.cyxbs.pages.ufield.fairground.constraint.FairgroundConstraintSet
import com.cyxbs.pages.ufield.fairground.viewmodel.FairgroundComposeViewModel
import cyxbsmobile.cyxbs_pages.ufield.generated.resources.Res
import cyxbsmobile.cyxbs_pages.ufield.generated.resources.ufield_ic_activity
import cyxbsmobile.cyxbs_pages.ufield.generated.resources.ufield_ic_fairground_food
import cyxbsmobile.cyxbs_pages.ufield.generated.resources.ufield_ic_fairground_square
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource


/**
 * 邮乐园入口页（commonMain）
 *
 * 作为主页 ViewPager 的 tab 通过 [com.cyxbs.pages.home.api.IHomeFairgroundTab] 嵌入。
 */
@Composable
fun FairgroundPage() {
  val viewModel = viewModel { FairgroundComposeViewModel() } // wasm/iOS 无法反射 new 对象，这里提供 factory
  val loginDialogState = rememberLoginDialogState()
  ConstraintLayout(
    modifier = Modifier
      .fillMaxSize()
      .background(LocalAppColors.current.bottomBg),
    constraintSet = createConstraintSet(),
    animateChangesSpec = spring(
      stiffness = Spring.StiffnessMediumLow,
    ),
  ) {
    // 顶部用户信息卡片
    InfoCard(
      viewModel = viewModel,
      modifier = Modifier
        .size(width = 323.dp, height = 80.dp)
        .layoutId(Element.Info)
    )

    // 美食咨询处
    FairItem(
      image = Res.drawable.ufield_ic_fairground_food,
      imageWidth = 136.dp,
      label = "美食咨询处",
      onClick = {
        loginDialogState.doIfLogin(function = "美食咨询处") {
          // 美食咨询处已接入 Compose
          FoodNavArgument.navigate()
        }
      },
      modifier = Modifier.layoutId(Element.Food)
    )

    // 答疑广场
    FairItem(
      image = Res.drawable.ufield_ic_fairground_square,
      imageWidth = 140.dp,
      label = "答疑广场",
      onClick = {
        loginDialogState.doIfLogin(function = "答疑广场") {
          // TODO 跳转答疑广场（原 QA_ENTRY，为 Android Activity），待迁移成 cmp
          FairgroundNavPlatform::class.implOrNull()?.jumpQaEntry() ?: toast("暂不支持跳转")
        }
      },
      modifier = Modifier.layoutId(Element.QaSquare)
    )

    // 活动布告栏
    FairItem(
      image = Res.drawable.ufield_ic_activity,
      imageWidth = 168.dp,
      label = "活动布告栏",
      onClick = {
        loginDialogState.doIfLogin(function = "活动布告栏") {
          // TODO 跳转活动布告栏（原 UFIELD_MAIN_ENTRY，为 Android Activity），待迁移成 cmp
          FairgroundNavPlatform::class.implOrNull()?.jumpUfieldMainEntry() ?: toast("暂不支持跳转")
        }
      },
      modifier = Modifier.layoutId(Element.Activity)
    )
  }
}

@Composable
private fun createConstraintSet(): ConstraintSet {
  val windowSize = getWindowScreenSize()
  return ConstraintSet {
    FairgroundConstraintSet(
      scope = this,
      windowSize = windowSize,
    ).createConstrain() // 所有控件的位置由该函数统一调整
  }
}

/**
 * 顶部用户信息卡片：头像 + 昵称 + 来到天数
 */
@Composable
private fun InfoCard(
  viewModel: FairgroundComposeViewModel,
  modifier: Modifier = Modifier,
) {
  val userInfo by viewModel.userInfo.collectAsStateWithLifecycle()
  // 还原原 ufield_layer_list_fairground_dialog：config_common_background_color 填充 + 1dp 白边 + 14dp 圆角 + 4dp 阴影
  val cardShape = RoundedCornerShape(14.dp)

  Box(
    modifier = modifier
      .background(color = LocalAppColors.current.topBg, shape = cardShape)
      .border(width = 1.dp, color = Color.White, shape = cardShape),
    contentAlignment = Alignment.Center
  ) {
    val height = 45.dp
    Row(
      modifier = Modifier.height(height),
      verticalAlignment = Alignment.CenterVertically
    ) {
      ImageAvatarCompose(
        url = userInfo?.photoSrc ?: "",
        modifier = Modifier
          .size(height)
          .clip(CircleShape),
      )
      Box(modifier = Modifier.fillMaxHeight().padding(start = 15.dp)) {
        Text(
          text = "Hi, ${userInfo?.nickname ?: "tutu"}",
          color = LocalAppColors.current.tvLv3,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(top = 1.dp) // verticalBias 0.3
        )
        DaysText(
          days = viewModel.days.value,
          modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 1.dp)
        )
      }
    }
  }
}

/**
 * 「这是你来到邮乐园的第 N 天」，其中 N 加粗、20sp、强调色
 */
@Composable
private fun DaysText(days: String, modifier: Modifier = Modifier) {
  val describeColor = 0x8015315B.dark(0xA087CEFA)
  val text = buildAnnotatedString {
    withStyle(SpanStyle(color = describeColor, fontSize = 14.sp)) {
      append("这是你来到邮乐园的第 ")
    }
    withStyle(
      SpanStyle(
        color = Color(0xFF5D5DF7),
        fontSize = 20.sp,
      )
    ) {
      append(days)
    }
    withStyle(SpanStyle(color = describeColor, fontSize = 14.sp)) {
      append(" 天")
    }
  }
  Text(text = text, modifier = modifier)
}

/**
 * 单个入口项：底图 + 胶囊按钮（按钮仅作视觉，点击区域为整个项，与原 FrameLayout 一致）
 */
@Composable
private fun FairItem(
  image: DrawableResource,
  imageWidth: Dp,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .width(imageWidth)
      .clickableNoIndicator(onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy((-24).dp)
  ) {
    Image(
      painter = painterResource(image),
      contentDescription = label,
      modifier = Modifier.fillMaxWidth(),
      contentScale = ContentScale.Fit
    )
    FairButton(
      text = label,
      modifier = Modifier
    )
  }
}

// 按钮描边渐变，还原原 ufield_ic_fairgroundbutton.xml 的线性渐变描边
private val FairButtonBorderBrush = Brush.linearGradient(
  0.0f to Color(0xFFE1EBFF),
  0.45f to Color(0xFFD5D5FF),
  0.7f to Color(0xFFE0F4FF),
  0.98f to Color(0xFFE6EDFF),
)

/**
 * 胶囊按钮（纯视觉），还原原 vector 按钮：浅色填充 + 渐变描边 + 蓝色文字
 */
@Composable
private fun FairButton(
  text: String,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(percent = 50)
  // 原 vector(ufield_ic_fairgroundbutton) 在按钮 View 内四周留有透明内边距，
  // 这里按其比例内缩，使可见胶囊大小与原版对齐，而非铺满整个点击区域
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .clip(shape)
        .background(0xFFFFFFFF.dark(0xFF2D2D2D))
        .border(width = 1.dp, brush = FairButtonBorderBrush, shape = shape)
        .padding(horizontal = 12.dp, vertical = 7.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = text,
        color = Color(0xFF4A44E4),
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

interface FairgroundNavPlatform {

  // 跳转到答疑广场，迁移为 cmp 后可删除
  fun jumpQaEntry()

  // 跳转到活动布告栏，迁移为 cmp 后删除
  fun jumpUfieldMainEntry()
}