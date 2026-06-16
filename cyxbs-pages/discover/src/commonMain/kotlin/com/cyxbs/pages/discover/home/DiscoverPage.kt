package com.cyxbs.pages.discover.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.login.rememberLoginDialogState
import com.cyxbs.components.config.service.impl
import com.cyxbs.components.config.service.implOrNull
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.rememberDerivedStateOfStructure
import com.cyxbs.components.utils.extensions.ImageFromUrlCompose
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.utils.utils.get.Num2CN
import com.cyxbs.pages.discover.home.functions.PlatformDiscoverFunctions
import com.cyxbs.pages.discover.home.viewmodel.DiscoverComposeViewModel
import com.cyxbs.pages.discover.home.widget.BannerConfig
import com.cyxbs.pages.discover.home.widget.CheckInImageVector
import com.cyxbs.pages.discover.home.widget.FunctionIndicator
import com.cyxbs.pages.discover.home.widget.InfiniteBanner
import com.cyxbs.pages.discover.home.widget.JwNewsFlipper
import com.cyxbs.pages.discover.home.widget.MsgImageVector
import com.cyxbs.pages.discover.home.widget.FunctionsRow
import com.cyxbs.pages.discover.home.widget.rememberCyxbsV6BannerPainter
import com.cyxbs.pages.electricity.api.IElectricityService
import com.cyxbs.pages.sport.api.ISportService
import com.cyxbs.pages.todo.api.ITodoService
import cyxbsmobile.cyxbs_pages.discover.generated.resources.Res
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover
import cyxbsmobile.cyxbs_pages.discover.generated.resources.discover_news_jwzx
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource

/* ---------------- 颜色（对齐 discover 模块原 light/dark） ---------------- */
private val JwLabelBgColor @Composable get() = 0xFFAFD2FB.dark(0xFF5A5A5A) // discover_academic_online_colors_background

/**
 * 发现入口页（commonMain）
 *
 * 作为主页 ViewPager 的 tab 通过 [com.cyxbs.pages.home.api.IHomeDiscoverTab] 嵌入。
 */
@Composable
fun DiscoverPage() {
  val viewModel = viewModel { DiscoverComposeViewModel() } // wasm/iOS 无法反射 new 对象，这里提供 factory
  val platform = remember { DiscoverNavPlatform::class.implOrNull() }
  val scrollState = rememberScrollState()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(LocalAppColors.current.bottomBg)
      .statusBarsPadding()
      .verticalScroll(scrollState),
    // 状态栏偏移：在 androidMain DiscoverHomeFragment 通过 setPadding 应用，
    // 因为外层 Compose 已消费 OnApplyWindowInsets，这里无法再拿到 inset。
  ) {
    Header()
    Banner(viewModel = viewModel, platform = platform)
    JwNewsSection(viewModel = viewModel, platform = platform)
    FunctionsSection(viewModel = viewModel)
    FeedSection()
  }
}

/* ----------------------------- 顶部 ------------------------------ */

@Composable
private fun Header() {
  val loginDialogState = rememberLoginDialogState()
  val platform = remember { DiscoverNavPlatform::class.implOrNull() }
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 16.dp, start = 16.dp, end = 16.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(
        text = greetingText(),
        color = LocalAppColors.current.tvLv4,
        fontSize = 14.sp,
        modifier = Modifier.alpha(0.65f),
      )
      Text(
        text = stringResource(Res.string.discover),
        color = LocalAppColors.current.tvLv2,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
      )
    }
    Row(
      modifier = Modifier.align(Alignment.BottomEnd),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = MsgImageVector,
        contentDescription = "消息中心",
        tint = LocalAppColors.current.tvLv2,
        modifier = Modifier
          .size(width = 24.dp, height = 20.dp)
          .clickableNoIndicator {
            // 与原页面一致，消息中心不做强制登录
            platform?.launchNotification() ?: toast("暂不支持跳转")
          },
      )
      Box(modifier = Modifier.width(33.87.dp))
      Icon(
        imageVector = CheckInImageVector,
        contentDescription = "签到",
        tint = LocalAppColors.current.tvLv2,
        modifier = Modifier
          .size(22.5.dp)
          .clickableNoIndicator {
            loginDialogState.doIfLogin(function = "签到") {
              platform?.jumpCheckIn() ?: toast("暂不支持跳转")
            }
          },
      )
    }
  }
}

/**
 * 还原原 `initTvDay`：根据登录状态、学期周数显示文案
 */
@Composable
private fun greetingText(): String {
  val isLogin = remember { IAccountService::class.impl().isLogin() }
  if (!isLogin) return "登录解锁更多功能~"
  val nowWeek = remember { SchoolCalendar.getWeekOfTerm() } ?: return ""
  val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
  val monthNumber = now.month.number
  val isSummerVacationMonth = monthNumber in 6..9
  return when {
    nowWeek > 0 -> {
      // dayOfWeek 在 kotlinx-datetime 中是 ISO 顺序（MONDAY=1..SUNDAY=7）
      val dow = now.dayOfWeek.isoDayNumber
      val dayText = if (dow == 7) "日" else Num2CN.number2ChineseNumber(dow)
      "第${Num2CN.number2ChineseNumber(nowWeek)}周 周$dayText"
    }
    monthNumber == 8 || monthNumber == 9 -> "欢迎新同学～"
    nowWeek !in 1..21 && isSummerVacationMonth -> "暑假快乐鸭"
    nowWeek !in 1..21 && !isSummerVacationMonth -> "寒假快乐鸭"
    else -> ""
  }
}

/* ----------------------------- Banner ------------------------------ */

@Composable
private fun Banner(
  viewModel: DiscoverComposeViewModel,
  platform: DiscoverNavPlatform?,
) {
  val list by viewModel.banner.collectAsStateWithLifecycle()
  val cornerShape = RoundedCornerShape(8.dp)
  // 纯 Compose 绘制的兜底图：同时作为外层渐隐层，以及内层单张图的 placeholder / error，
  // 替代已废弃的 discover_ic_cyxbsv6.webp。圆角由 Painter 自绘，无需再 clip。
  val placeholderPainter = rememberCyxbsV6BannerPainter(cornerRadius = 8.dp)

  // 进入页面后从 0 → 1 的整页淡入（对应原 SlideShow alpha 600ms 动画）
  val ssAlpha = remember { Animatable(0F) }
  LaunchedEffect(list) {
    viewModel.banner.first { it.isNotEmpty() }
    ssAlpha.animateTo(targetValue = 1F, animationSpec = tween(BannerConfig.EnterFadeMillis))
  }
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 16.dp, end = 16.dp, top = 12.dp)
      .aspectRatio(2.56f), // 还原原约束 layout_constraintDimensionRatio="2.56"
  ) {
    // 底层兜底图（首屏 / banner 列表为空时显示，list 来了之后淡出）
    if (rememberDerivedStateOfStructure { ssAlpha.value != 1F }.value) {
      Image(
        painter = placeholderPainter,
        contentDescription = null,
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            // list 还没来时 alpha=1，来了之后淡出
            alpha = 1f - ssAlpha.value
          },
        contentScale = ContentScale.FillBounds,
      )
    }
    if (list.isNotEmpty()) {
      InfiniteBanner(
        itemCount = list.size,
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            alpha = ssAlpha.value
          },
      ) { realPage ->
        val data = list[realPage]
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 0.dp)) {
          ImageFromUrlCompose(
            url = data.pictureUrl,
            placeholder = placeholderPainter,
            error = placeholderPainter,
            modifier = Modifier
              .fillMaxSize()
              .clip(cornerShape)
              .clickableNoIndicator {
                platform?.onBannerClick(data.pictureGotoUrl, data.keyword)
                  ?: toast("暂不支持跳转")
              },
            contentScale = ContentScale.Crop,
          )
        }
      }
    }
  }
}

/* ----------------------------- 教务在线 ------------------------------ */

@Composable
private fun JwNewsSection(
  viewModel: DiscoverComposeViewModel,
  platform: DiscoverNavPlatform?,
) {
  val news by viewModel.jwNews.collectAsStateWithLifecycle()
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 15.dp, start = 16.dp, end = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // 「教务在线」标签：bottomLeft / topRight 14dp 圆角（还原原 discover_shape_jwzx_title.xml）
    val labelShape = RoundedCornerShape(
      topStart = 0.dp,
      topEnd = 14.dp,
      bottomEnd = 0.dp,
      bottomStart = 14.dp,
    )
    Box(
      modifier = Modifier
        .clip(labelShape)
        .background(JwLabelBgColor)
        .clickableNoIndicator { platform?.jumpJwNewsList() ?: toast("暂不支持跳转") }
        .padding(horizontal = 13.dp, vertical = 2.dp),
    ) {
      Text(
        text = stringResource(Res.string.discover_news_jwzx),
        color = LocalAppColors.current.whiteBlack,
        fontSize = 11.sp,
      )
    }
    Box(modifier = Modifier.width(14.dp))
    JwNewsFlipper(
      items = news,
      modifier = Modifier
        .fillMaxWidth()
        .height(20.dp),
      textColor = LocalAppColors.current.tvLv2,
      onItemClick = { id -> platform?.jumpJwNewsItem(id) ?: toast("暂不支持跳转") },
    )
  }
}

/* ----------------------------- 功能按钮 ------------------------------ */

@Composable
private fun FunctionsSection(
  viewModel: DiscoverComposeViewModel,
) {
  val functions = PlatformDiscoverFunctions.rememberFunctions()
  if (functions.isEmpty()) return

  // 持久化的 pin 顺序（最早 pin 的在第 0 位，更晚 pin 的依次往后）；未 pin 的按规范顺序紧跟其后。
  // 用 mutableStateOf 持有以便双击后立即重排，不必等下次重组才生效。
  val pinnedIdsState = remember(functions) {
    mutableStateOf(viewModel.loadSavedOrder(DiscoverComposeViewModel.KEY_FUNCTION_PINS).orEmpty())
  }
  val pinnedIds = pinnedIdsState.value
  val ordered = remember(functions, pinnedIds) {
    val map = functions.associateBy { it.id }
    val pinnedSet = pinnedIds.toHashSet()
    val pinned = pinnedIds.mapNotNull { map[it] }
    val rest = functions.filter { it.id !in pinnedSet }
    pinned + rest
  }

  // 上提 LazyListState，pin / 取消 pin 后主动滚回起点展示 pinned 区
  val listState = rememberLazyListState()
  val scope = rememberCoroutineScope()

  var progress by remember { mutableStateOf(0f) }
  Column(modifier = Modifier.fillMaxWidth().padding(top = 25.dp, start = 16.dp, end = 16.dp)) {
    FunctionsRow(
      items = ordered,
      modifier = Modifier.fillMaxWidth(),
      state = listState,
      pinnedIds = remember(pinnedIds) { pinnedIds.toHashSet() },
      onPin = { id ->
        val current = pinnedIdsState.value
        val wasPinned = id in current
        val next = if (wasPinned) current.filter { it != id } else current + id
        if (listState.firstVisibleItemIndex == 0) {
          // 默认 LazyList 的 key 追踪（items(key = { it.id })）会让 firstVisibleItemIndex
          // 跟着该 item 一起跳到它的新远端索引，视口看起来「跳了一格甚至更多」。
          // requestScrollToItem 的语义就是：仅本次 remeasure 跳过 key 追踪，按给定
          // index/offset 落位。把视口锚回当前索引位置，pinned 区其余 item 借 animateItem
          // 整体左移补位即可。
          listState.requestScrollToItem(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset,
          )
        }
        pinnedIdsState.value = next
        viewModel.saveOrder(DiscoverComposeViewModel.KEY_FUNCTION_PINS, next)
        if (!wasPinned) {
          // 先改 state 让 reorder + LazyList 重新 layout 进行，
          // 等一帧（withFrameNanos）让布局 settle 后再 animateScrollToItem(0)。
          // 这样滚动起点 / 目标都基于 reorder 之后的真实布局，避免「先朝旧 index 0 滚一段，
          // 再调头朝新 index 0」的割裂动画。
          scope.launch {
            withFrameNanos {}
            listState.animateScrollToItem(0)
          }
        }
      },
      onProgressChanged = { progress = it },
    )
    FunctionIndicator(
      progress = progress,
      modifier = Modifier
        .padding(top = 13.dp)
        .width(29.dp)
        .height(4.dp)
        .align(Alignment.CenterHorizontally),
    )
  }
}

/* ----------------------------- Feed 区 ------------------------------ */

@Composable
private fun FeedSection() {
  val cornerShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
  val containerColor = LocalAppColors.current.middleBg

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 18.dp)
      .clip(cornerShape)
      .background(color = containerColor),
  ) {
    // 体育打卡
    ISportService::class.impl().SportFeed(Modifier.fillMaxWidth())
    Spacer(
      modifier = Modifier.fillMaxWidth().height(1.dp)
        .alpha(0.1F)
        .background(color = LocalAppColors.current.tvLv4)
    )
    // 邮子清单
    ITodoService::class.impl().TodoFeed(Modifier.fillMaxWidth())
    Spacer(
      modifier = Modifier.fillMaxWidth().height(1.dp)
        .alpha(0.1F)
        .background(color = LocalAppColors.current.tvLv4)
    )
    // 电费查询
    IElectricityService::class.impl().ElectricityFeed(Modifier.fillMaxWidth())
    // 80dp 顶起来课表与底部按钮
    Spacer(modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(80.dp))
  }
}

