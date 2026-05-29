package com.cyxbs.pages.course.find.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.compose.theme.LocalAppDark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.course.api.ILinkService2
import cyxbsmobile.cyxbs_pages.course.generated.resources.Res
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_find_course_link_arrow
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_find_course_link_delete
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_find_course_link_head_ing
import cyxbsmobile.cyxbs_pages.course.generated.resources.course_ic_find_course_link_head_no
import org.jetbrains.compose.resources.painterResource

// 关联人卡片色值（对齐老 course_find_course_link_*）
private val LinkCardBgLight = Color(0xFFE8F0FC)
private val LinkCardBgDark = Color(0xFF2C2C2C)
private val LinkCardDividerLight = Color(0xFFF2F3F8)
private val LinkCardDividerDark = Color(0xFFF0F0F0)
private val LinkCardHintLight = Color(0x66142C52)
private val LinkCardHintDark = Color(0xFFF0F0F0)

/**
 * 我的关联人卡片：包含标题行（含 0/1 计数 + 取消关联按钮）+ 卡片本体。
 * 关联/取消关联状态切换通过 [AnimatedContent] 淡入淡出过渡。
 */
@Composable
internal fun LinkCard(
  linkState: ILinkService2.LinkStu,
  onClick: (stuNum: String) -> Unit,
  onDelete: () -> Unit,
) {
  val colors = LocalAppColors.current
  val isDark = LocalAppDark.current
  val cardBg = if (isDark) LinkCardBgDark else LinkCardBgLight
  val divider = if (isDark) LinkCardDividerDark else LinkCardDividerLight
  val hintColor = if (isDark) LinkCardHintDark else LinkCardHintLight
  val isLinked = linkState.isNotNull()

  Column {
    // 标题行
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = "我的关联",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = colors.tvLv1,
      )
      Text(
        text = if (isLinked) "（1/1）" else "（0/1）",
        fontSize = 13.sp,
        color = hintColor,
        modifier = Modifier.padding(start = 2.dp),
      )
      Spacer(modifier = Modifier.weight(1f))
      AnimatedContent(
        targetState = isLinked,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(220)) },
        label = "link-card-delete-btn",
      ) { linked ->
        if (linked) {
          IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
            Icon(
              painter = painterResource(Res.drawable.course_ic_find_course_link_delete),
              contentDescription = "取消关联",
              tint = Color.Unspecified,
            )
          }
        } else {
          Spacer(modifier = Modifier.size(  24.dp))
        }
      }
    }
    // 卡片本体：固定高度 84dp（不再用宽高比，避免宽屏拉伸过高）
    // 通过 AnimatedContent 让关联/取消关联状态切换有淡入淡出过渡
    AnimatedContent(
      targetState = isLinked,
      modifier = Modifier
        .padding(top = 9.dp)
        .fillMaxWidth()
        .height(84.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(cardBg),
      transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(280)) },
      label = "link-card-body",
    ) { linked ->
      val unlinkText = "长按搜索结果添加关联关系吧！"
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight()
          .clickable {
            if (isLinked) {
              onClick(linkState.linkNum)
            } else {
              toast(unlinkText)
            }
          },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // 头像区：宽度 58dp，头像在其中水平居中
        Box(
          modifier = Modifier.width(58.dp).fillMaxHeight(),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            painter = painterResource(
              if (linked) Res.drawable.course_ic_find_course_link_head_ing
              else Res.drawable.course_ic_find_course_link_head_no
            ),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(22.dp),
          )
        }
        // 分割线
        Box(
          modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .padding(vertical = 12.dp)
            .background(divider),
        )
        if (linked) {
          // 已关联：三行文字 packed 垂直居中，左对齐分割线右侧 18dp
          Column(
            modifier = Modifier.weight(1f).padding(start = 18.dp, end = 12.dp),
            verticalArrangement = Arrangement.Center,
          ) {
            Text(
              text = linkState.linkName,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = colors.tvLv1,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
              text = linkState.linkMajor,
              fontSize = 14.sp,
              color = colors.tvLv1,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
              text = linkState.linkNum,
              fontSize = 13.sp,
              color = colors.tvLv4,
              maxLines = 1,
            )
          }
          Icon(
            painter = painterResource(Res.drawable.course_ic_find_course_link_arrow),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.padding(end = 18.dp),
          )
        } else {
          // 未关联：提示长按搜索结果添加关联
          Column(
            modifier = Modifier.weight(1f).padding(end = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "还没有关联同学哦",
              fontSize = 14.sp,
              color = hintColor,
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
              text = unlinkText,
              fontSize = 13.sp,
              color = hintColor,
            )
          }
        }
      }
    }
  }
}
