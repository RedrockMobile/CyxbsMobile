package com.cyxbs.pages.mine.sign.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.pages.mine.sign.util.SignUtil
import com.cyxbs.pages.mine.sign.viewmodel.SignComposeViewModel
import com.cyxbs.pages.mine.sign.viewmodel.SignState
import com.cyxbs.pages.mine.sign.widget.WeekSignProgress
import cyxbsmobile.cyxbs_pages.mine.generated.resources.Res
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_back
import cyxbsmobile.cyxbs_pages.mine.generated.resources.mine_ic_bg_sign
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

/**  
 * description: 签到页UI
 * author: zzx
 * email: 1487144524@qq.com
 * date: 2026/7/19 14:46
 */
@Composable
fun SignScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val panelVisible = remember {
    MutableTransitionState(false).apply {
      targetState = true
    }
  }
  Box(modifier.fillMaxSize()) {
    Image(
      modifier = Modifier.fillMaxSize(),
      contentDescription = null,
      painter = painterResource(Res.drawable.mine_ic_bg_sign),
      contentScale = ContentScale.Crop
    )

    Column(
      modifier = Modifier
        .fillMaxSize()
        .systemBarsPadding()
    ) {
      SignTopBar(onBack = onBack)
      SignHeader()
      Spacer(modifier = Modifier.weight(1f))
      AnimatedVisibility(
        visibleState = panelVisible,
        enter = slideInVertically(
          initialOffsetY = { height -> height },
          animationSpec = tween(
            durationMillis = 600,
            easing = FastOutSlowInEasing
          )
        )
      ) {
        SignContent()
      }
    }
  }

}

@Composable
fun SignTopBar(
  modifier: Modifier = Modifier,
  onBack: () -> Unit
) {
  IconButton(
    modifier = modifier.padding(top = 4.dp),
    onClick = onBack
  ) {
    Image(
      modifier = Modifier.size(16.dp),
      painter = painterResource(Res.drawable.mine_ic_back),
      contentDescription = null
    )
  }
}

@Composable
fun SignHeader(
  modifier: Modifier = Modifier
    .padding(start = 18.dp)
) {
  val viewmodel: SignComposeViewModel = viewModel()
  val signState = viewmodel.signStatus.collectAsStateWithLifecycle()
  signState.value?.let { signStatus ->
    Column(
      modifier = modifier
    ) {
      Text(
        modifier = Modifier
          .padding(top = 32.dp),
        text = SignUtil.getYearPair(),
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
      )
      Text(
        modifier = Modifier
          .padding(top = 8.dp),
        text = if (SignUtil.getDayOfTerm() < 0) {
          "距离${SignUtil.getSemesterOfTerm()}学期开学还有${abs(SignUtil.getDayOfTerm())}天"
        } else {
          "${SignUtil.getSemesterOfTerm()}学期第${SignUtil.getChineseWeekOfTerm()}周"
        },
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
      )
      Text(
        modifier = Modifier
          .padding(top = 16.dp)
          .alpha(0.86f),
        text = "已连续打卡${signStatus.serialDays}天",
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
      )
      Text(
        modifier = Modifier
          .padding(top = 6.dp)
          .alpha(0.64f),
        text = "超过${signStatus.percent}的邮子",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
      )
    }
  }
}

@Composable
fun SignContent(
  modifier: Modifier = Modifier
) {
  val viewmodel: SignComposeViewModel = viewModel()
  val signState = viewmodel.signState.collectAsStateWithLifecycle()
  val signStatus = viewmodel.signStatus.collectAsStateWithLifecycle()
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .aspectRatio(1.04f),
    shape = RoundedCornerShape(
      topStart = 16.dp,
      topEnd = 16.dp
    ),
    color = LocalAppColors.current.bottomBg
  ) {
    signStatus.value?.let { signStatus ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 18.dp)
      ) {
        Text(
          modifier = Modifier.padding(top = 32.dp),
          text = when (signState.value) {
            SignState.SIGNED -> "今日第${signStatus.rank}位打卡"
            SignState.UNSIGNED -> "还没有打卡哦"
            SignState.INVOCATION -> "寒暑假不可签到呢(●'ᴗ'σ)σணღ*"
          },
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          color = LocalAppColors.current.tvLv2
        )
        Spacer(Modifier.height(42.dp))
        WeekSignProgress(
          modifier = Modifier.align(Alignment.CenterHorizontally),
          signStatus = signStatus
        )
        Button(
          modifier = Modifier
            .padding(top = 30.dp)
            .align(Alignment.CenterHorizontally)
            .width(120.dp)
            .height(40.dp),
          shape = RoundedCornerShape(25.dp),
          colors = ButtonDefaults.buttonColors(
            backgroundColor = if (signStatus.isChecked or !signStatus.canCheckIn) {
              Color(0xFFE1DFE0)
            } else {
              Color(0xFF3D35E2)
            },
            contentColor = if (signStatus.isChecked or !signStatus.canCheckIn) {
              Color(0xFFC3C0C1)
            } else {
              Color.White
            }
          ),
          enabled = (!signStatus.isChecked && signStatus.canCheckIn),
          onClick = {
            viewmodel.checkIn()
          }
        ) {
          Text(
            text = if (signStatus.canCheckIn && signStatus.isChecked) "已签到" else "签到",
            fontSize = 18.sp
          )
        }
      }
    }
  }
}