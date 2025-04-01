package com.cyxbs.pages.home.mobile.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.AppTheme
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.home.mobile.viewmodel.BottomNavViewModel
import com.cyxbs.pages.home.mobile.viewmodel.CourseFrameViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * .
 *
 * @author 985892345
 * @date 2025/3/16
 */

@Composable
fun MobileHomePage() {
  AppTheme {
    Box(
      modifier = Modifier.fillMaxSize()
    ) {
      HomeViewPagerCompose()
      HomeCourseCompose()
      HomeNavCompose(modifier = Modifier.align(Alignment.BottomCenter))
    }
  }
}

@Composable
internal expect fun HomeViewPagerCompose(modifier: Modifier = Modifier)

@Composable
private fun HomeCourseCompose(modifier: Modifier = Modifier) {
  val bottomNavViewModel = viewModel(BottomNavViewModel::class)
  val courseFrameViewModel = viewModel(CourseFrameViewModel::class)
  courseFrameViewModel.frame.HomeCourseContent(
    modifier = modifier.systemBarsPadding(),
    bottomBarHeight = bottomNavViewModel.height
  )
  LaunchedEffect(Unit) {
    val bottomSheetState = courseFrameViewModel.frame.bottomSheetState
    snapshotFlow {
      bottomSheetState.fraction.coerceIn(0F, 1F)
    }.onEach {
      // 底部按钮跟随课表展开而变化
      bottomNavViewModel.offsetYRadio.floatValue = it
      bottomNavViewModel.alpha.floatValue = 1 - it
    }.launchIn(this)
  }
  LaunchedEffect(Unit) {
    val bottomSheetState = courseFrameViewModel.frame.bottomSheetState
    bottomNavViewModel.selectedItem.collect {
      if (it === bottomNavViewModel.fairgroundItem) {
        bottomSheetState.hide()
      } else if (bottomSheetState.state == BottomSheetValueState.Hide) {
        bottomSheetState.collapse()
      }
    }
  }
}

@Composable
private fun HomeNavCompose(modifier: Modifier = Modifier) {
  val bottomNavViewModel = viewModel(BottomNavViewModel::class)
  val shadowElevation by bottomNavViewModel.selectedItem.map {
    if (it === bottomNavViewModel.discoverItem || it === bottomNavViewModel.mineItem) 0.dp else 4.dp
  }.collectAsState(0.dp)
  Row(
    modifier = modifier
      .navigationBarsPadding()
      .height(bottomNavViewModel.height)
      .fillMaxWidth()
      .shadow(shadowElevation)
      .graphicsLayer {
        alpha = bottomNavViewModel.alpha.floatValue
        translationY = (bottomNavViewModel.offsetYRadio.floatValue * bottomNavViewModel.height).toPx()
      }
      .background(LocalAppColors.current.topBg),
    horizontalArrangement = Arrangement.SpaceAround,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    bottomNavViewModel.items.fastForEach {
      HomeNavItemCompose(it)
    }
  }
}

@Composable
private fun HomeNavItemCompose(item: BottomNavViewModel.BottomNavItem, modifier: Modifier = Modifier) {
  val bottomNavViewModel = viewModel(BottomNavViewModel::class)
  val coroutineScope = rememberCoroutineScope()
  val selected by bottomNavViewModel.selectedItem.map { it === item }.collectAsState(false)
  val hasRedDot by item.observerRedDot().collectAsState()
  val scale = remember { Animatable(initialValue = 1F) }
  Column(
    modifier = modifier
      .pointerInput(Unit) {
        detectTapGestures(
          onPress = {
            scale.animateTo(0.9F)
            tryAwaitRelease()
            scale.animateTo(1F)
          },
          onTap = {
            coroutineScope.launch { scale.animateTo(1.1F) }
            bottomNavViewModel.select(item)
          },
        )
      }
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .graphicsLayer {
        scaleX = scale.value
        scaleY = scaleX
      },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Image(
      modifier = Modifier.size(26.dp),
      painter = painterResource(
        if (selected) item.selectedIcon
        else if (hasRedDot) item.unselectedRedDotIcon
        else item.unselectedIcon
      ),
      contentDescription = stringResource(item.title),
    )
    Text(
      modifier = Modifier.padding(top = 2.dp),
      text = stringResource(item.title),
      color = if (selected) 0xFF2923D2.dark(0xFF465FFF) else 0xFFAABCD8.dark(0xFF5B585C),
      fontSize = 10.sp,
    )
  }
  LaunchedEffect(item) {
    bottomNavViewModel.selectedItem.map { it === item }.distinctUntilChanged().collect {
      if (!it) {
        scale.animateTo(1F) // 取消选中时还原动画
      }
    }
  }
}
