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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toastLong
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.home.api.HomeNavArgument
import com.cyxbs.pages.home.api.IHomeDiscoverTab
import com.cyxbs.pages.home.api.IHomeFairgroundTab
import com.cyxbs.pages.home.api.IHomeMineTab
import com.cyxbs.pages.home.mobile.viewmodel.BottomNavViewModel
import com.cyxbs.pages.home.mobile.viewmodel.CourseBottomSheetViewModel
import com.cyxbs.pages.home.mobile.viewmodel.MobileCourseFrameViewModel
import com.cyxbs.pages.home.ui.PlatformHomePage
import com.g985892345.provider.api.annotation.ImplProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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
@ImplProvider
class MobileHomePage : PlatformHomePage {

  @Composable
  override fun HomePageContent(argument: HomeNavArgument) {
    viewModel { BottomNavViewModel(argument) }
    viewModel { MobileCourseFrameViewModel() }
    viewModel { CourseBottomSheetViewModel() }
    PlatformMobileHomePage(argument) {
      Box(
        modifier = Modifier.fillMaxSize()
      ) {
        HomeViewPagerCompose(argument)
        HomeCourseCompose()
        HomeNavCompose(modifier = Modifier.align(Alignment.BottomCenter))
      }
    }
  }
}

// 提供给具体平台用于包裹 content，可以设置些特别的 LocalProvider 或者获取 ViewModel 啥的
@Composable
internal expect fun PlatformMobileHomePage(
  argument: HomeNavArgument,
  content: @Composable () -> Unit,
)

/**
 * 主页三个 tab 的 ViewPager 容器
 *
 * tab 内容由各 page 模块通过 [IHomeDiscoverTab] / [IHomeFairgroundTab] / [IHomeMineTab]
 * 这三个接口配合 `@ImplProvider` 注入，home 模块不直接依赖任何一个 page 模块。
 *
 * 状态保留：用 [rememberSaveableStateHolder] 给每个 tab 独立 key，切 tab 后回来不丢
 * Composable 内的 rememberSaveable 状态；ViewModel 与 home entry 同生命周期，三个 tab 的
 * ViewModel 类不冲突，可安全共享 store。
 */
@Composable
internal fun HomeViewPagerCompose(
  argument: HomeNavArgument,
  modifier: Modifier = Modifier,
) {
  val bottomNavViewModel = viewModel(BottomNavViewModel::class)
  val items = bottomNavViewModel.items
  val initialPage = remember {
    items.indexOf(bottomNavViewModel.selectedItem.value).coerceAtLeast(0)
  }
  val pagerState = rememberPagerState(
    initialPage = initialPage,
    pageCount = { items.size },
  )
  // 底部 tab 选中 -> Pager 滚动，对齐原 ViewPager2.currentItem = it（单参 setCurrentItem
  // 默认 smoothScroll=true）的带动画切换。isUserInputEnabled=false 只关用户手势，不影响
  // 程序触发的滚动动画。
  LaunchedEffect(Unit) {
    bottomNavViewModel.selectedItem
      .map { items.indexOf(it) }
      .collect { if (it >= 0) pagerState.animateScrollToPage(it) }
  }

  val discoverTab = remember { IHomeDiscoverTab::class.implOrNull() }
  val fairgroundTab = remember { IHomeFairgroundTab::class.implOrNull() }
  val mineTab = remember { IHomeMineTab::class.implOrNull() }
  val stateHolder = rememberSaveableStateHolder()

  HorizontalPager(
    state = pagerState,
    modifier = modifier
      .fillMaxSize()
      .navigationBarsPadding()
      .padding(bottom = bottomNavViewModel.height),
    userScrollEnabled = false, // 与原 ViewPager2.isUserInputEnabled=false 保持一致
    // 让所有 tab 始终保持组合，避免切走再回来 page 内 remember / 滚动位置 / 动画 丢失。
    // 原 ViewPager2 + FragmentStateAdapter 通过 Fragment savedState 恢复，Compose 这边
    // 用全员驻留替代。tab 总数固定为 3，开销可接受。
    beyondViewportPageCount = items.size - 1,
    key = { it },
  ) { page ->
    stateHolder.SaveableStateProvider(key = page) {
      when (items[page]) {
        bottomNavViewModel.discoverItem -> discoverTab?.Content()
        bottomNavViewModel.fairgroundItem -> fairgroundTab?.Content()
        bottomNavViewModel.mineItem -> mineTab?.Content()
      }
    }
  }
}

// 旧版课表
interface IOldHomeCourse {
  val enable: Boolean
  val content: @Composable (modifier: Modifier) -> Unit
}

@Composable
private fun HomeCourseCompose(modifier: Modifier = Modifier) {
  val oldHomeCourse = IOldHomeCourse::class.implOrNull()
  if (oldHomeCourse?.enable == true) {
    // 使用旧版课表进行展示
    oldHomeCourse.content.invoke(modifier)
    return
  }
  val bottomNavViewModel = viewModel(BottomNavViewModel::class)
  val courseFrameViewModel = viewModel(MobileCourseFrameViewModel::class)
  // CourseBottomSheetViewModel 提供对外控制课表展示和监听当前展示状态
  val courseBottomSheetViewModel = viewModel(CourseBottomSheetViewModel::class)
  courseFrameViewModel.frame.HomeCourseContent(
    modifier = modifier,
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
    bottomNavViewModel.selectedItem.drop(1).collect {
      // 之前主页的第二页是邮问（掌邮社区，因为违规问题下架了）会将课表 bottomSheetState 设置成 hide 状态
      // 现在改成游乐园后没必要再 hide 了
      bottomSheetState.collapseAsync()
    }
  }
  LaunchedEffect(Unit) {
    courseBottomSheetViewModel.state.collectLatest {
      // 展开折叠在其他地方同时触发时会将上次触发的协程取消掉，所以需要 catch
      when (it) {
        true -> courseFrameViewModel.frame.bottomSheetState.expandAsync()
        false -> courseFrameViewModel.frame.bottomSheetState.collapseAsync()
        null -> courseFrameViewModel.frame.bottomSheetState.hideAsync()
      }
    }
  }
  LaunchedEffect(Unit) {
    courseFrameViewModel.frame.bottomSheetState.stateFlow.collect {
      when (it) {
        BottomSheetValueState.Hide -> {
          if (courseBottomSheetViewModel.state.value != null) {
            courseBottomSheetViewModel.state.value = null
          }
        }
        BottomSheetValueState.Collapsed -> {
          if (courseBottomSheetViewModel.state.value != false) {
            courseBottomSheetViewModel.state.value = false
          }
        }
        BottomSheetValueState.Expanded -> {
          if (courseBottomSheetViewModel.state.value != true) {
            courseBottomSheetViewModel.state.value = true
          }
        }
        BottomSheetValueState.Scrolling -> {}
      }
    }
  }
  LaunchedEffect(Unit) {
    courseFrameViewModel.frame.bottomSheetState.stateFlow
      .first { it == BottomSheetValueState.Expanded }
    toastLong("注意：新版课表在开发中，可能存在bug\n设置中可回退旧版课表")
  }
}

@Composable
private fun HomeNavCompose(modifier: Modifier = Modifier) {
  val bottomNavViewModel = viewModel(BottomNavViewModel::class)
  val courseFrameViewModel = viewModel(MobileCourseFrameViewModel::class)
  val shadowElevation by courseFrameViewModel.frame.bottomSheetState.stateFlow.map {
    if (it == BottomSheetValueState.Hide) 4.dp else 0.dp // 如果课表不展示了则添加阴影
  }.collectAsState(0.dp)
  Row(
    modifier = modifier
      .graphicsLayer {
        alpha = bottomNavViewModel.alpha.floatValue
        translationY = (bottomNavViewModel.offsetYRadio.floatValue * bottomNavViewModel.height).toPx()
      }
      .shadow(shadowElevation)
      .background(LocalAppColors.current.topBg)
      .navigationBarsPadding()
      .height(bottomNavViewModel.height)
      .fillMaxWidth(),
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
