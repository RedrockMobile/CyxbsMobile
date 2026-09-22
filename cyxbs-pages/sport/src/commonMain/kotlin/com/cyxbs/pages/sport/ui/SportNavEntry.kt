package com.cyxbs.pages.sport.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.res.ConfigRes
import com.cyxbs.components.config.time.SchoolCalendar
import com.cyxbs.components.navigation.AppNav
import com.cyxbs.components.navigation.AppNavEntry
import com.cyxbs.components.navigation.NAV_SPORT
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.pages.sport.api.SportNavArgument
import com.cyxbs.pages.sport.model.SportDetailBean
import com.cyxbs.pages.sport.model.SportDetailRepository
import com.cyxbs.pages.sport.widget.RefreshHeader
import com.cyxbs.pages.sport.widget.RefreshNestedScrollConnection
import com.cyxbs.pages.sport.widget.RefreshState
import cyxbsmobile.cyxbs_pages.sport.generated.resources.Res
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_ic_award
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_ic_holiday
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_ic_no_data
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_ic_not_valid
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_ic_other
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_ic_run
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_ic_shoes
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_ic_spot
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_ic_time
import cyxbsmobile.cyxbs_pages.sport.generated.resources.sport_ic_valid
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock

/**
 * @Desc : 体育打卡 Compose 页面及导航入口
 * @Author : xt
 */
@AppNav(route = NAV_SPORT)
class SportNavEntry : AppNavEntry<SportNavArgument>() {

    /**
     * 体育页面始终要求用户登录
     * @param argument 体育页面导航参数
     */
    override fun isNeedLogin(argument: SportNavArgument): Boolean {
        return true
    }

    /**
     * 渲染体育页面；数据由进程级 Repository 统一提供，避免依赖平台 ViewModel Factory。
     * @param argument 体育页面导航参数
     */
    @Composable
    override fun Content(argument: SportNavArgument) {
        SportPage(argument)
    }
}

/**
 * 渲染体育打卡详情页的统计、插图和记录列表
 * @param argument 体育页面导航参数
 */
@Composable
fun SportPage(argument: SportNavArgument) {
    val result by SportDetailRepository.sportData.collectAsState()
    val data = result?.getOrNull()
    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
            .background(LocalAppColors.current.bottomBg)
            .statusBarsPadding(),
        constraintSet = createConstraintSet()
    ) {
        TopBarCompose(modifier = Modifier.layoutId(SportElement.TopBar), argument)
        DetailTotalTitle(modifier = Modifier.layoutId(SportElement.DetailTotalTitle))
        DetailTotal(data = data, modifier = Modifier.layoutId(SportElement.DetailTotal))
        SportImage(modifier = Modifier.layoutId(SportElement.SportImage))
        SportDetailRun(data = data, modifier = Modifier.layoutId(SportElement.SportDetailRun))
        SportRecord(result = result, modifier = Modifier.layoutId(SportElement.SportRecord))
    }
}

//根据窗口尺寸创建页面约束集合
@Composable
private fun createConstraintSet(): ConstraintSet {
    val windowSize = getWindowScreenSize()
    return ConstraintSet {
        SportConstraintSet(
            scope = this,
            windowSize = windowSize
        ).createConstrain()
    }
}

/**
 * 渲染顶部标题栏及返回操作。
 * @param modifier 顶部栏布局修饰符
 * @param argument 体育页面导航参数
 */
@Composable
private fun TopBarCompose(
    modifier: Modifier = Modifier,
    argument: SportNavArgument
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            modifier = Modifier
                .padding(start = 15.dp)
                .size(16.dp)
                .clickableNoIndicator {
                    argument.popBackStack()
                },
            painter = painterResource(ConfigRes.configIcBack()),
            contentDescription = "back",
            contentScale = ContentScale.Crop
        )
        Text(
            modifier = Modifier
                .padding(start = 20.dp)
                .align(Alignment.CenterVertically),
            text = "体育打卡",
            fontWeight = FontWeight.Bold,
            color = LocalAppColors.current.tvLv3,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            modifier = Modifier
                .padding(end = 15.dp)
                .align(Alignment.CenterVertically),
            text = remember {
                val currentDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val year = currentDate.year
                val season = when (currentDate.month.number) {
                    1 -> "秋"
                    in 2..7 -> "春"
                    in 8..12 -> "秋"
                    else -> ""
                }
                "${year}年  $season"
            },
            color = 0xFF697c9b.dark(0xFF606061),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

//展示体育详情区域的标题文本
@Composable
private fun DetailTotalTitle(
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier
            .padding(start = 15.dp),
        text = "总计：",
        color = 0xFF15315b.dark(0xFFFFFFFF),
        fontSize = 16.sp
    )
}

// 展示总完成次数、目标次数和奖励统计
@Composable
private fun DetailTotal(
    data: SportDetailBean?,
    modifier: Modifier = Modifier,
) {
    val impactFontFamily = ConfigRes.impactFontFamily()
    val impactMinFontFamily = ConfigRes.impactMinFontFamily()
    val textDone = data?.let { it.otherDone + it.runDone }?.toString() ?: "M"
    val textNeed = data?.let { "/${it.runTotal + it.otherTotal}" } ?: "/N"
    Row(
        modifier = modifier
            .padding(top = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .padding(start = 50.dp, bottom = 15.dp),
            text = textDone,
            color = Color(0xFF4A44E4),
            fontSize = 45.sp,
            fontFamily = impactFontFamily,
            style = TextStyle(fontWeight = if (impactFontFamily == null) FontWeight.Bold else null)
        )
        Text(
            modifier = Modifier
                .padding(start = 5.dp, top = 10.dp, bottom = 15.dp),
            text = textNeed,
            color = Color(0xFF4A44E4),
            fontSize = 25.sp,
            fontFamily = impactMinFontFamily,
            style = TextStyle(fontWeight = if (impactMinFontFamily == null) FontWeight.Bold else null)
        )
    }
}

// 展示体育页面右侧装饰图片
@Composable
private fun SportImage(
    modifier: Modifier = Modifier,
) {
    Image(
        modifier = modifier
            .padding(start = 45.dp, top = 5.dp),
        painter = painterResource(Res.drawable.sport_ic_shoes),
        contentDescription = null,
    )
}

//展示跑步和其他项目的完成进度
@Composable
private fun SportDetailRun(
    data: SportDetailBean?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SportDetailItem(
            modifier = Modifier
                .padding(start = 19.dp),
            title = "跑步:",
            done = data?.runDone?.toString() ?: "X",
            need = data?.let { "/${it.runTotal}" }.orEmpty(),
        )
        Spacer(modifier = Modifier.weight(1f))
        SportDetailItem(
            modifier = Modifier
                .padding(start = 35.dp),
            title = "其他:",
            done = data?.otherDone?.toString() ?: "Y",
            need = data?.let { "/${it.otherTotal}" }.orEmpty(),
        )
        Spacer(modifier = Modifier.weight(1f))
        SportDetailItem(
            modifier = Modifier
                .padding(start = 35.dp, end = 10.dp),
            title = "奖励:",
            done = data?.award?.toString() ?: "Z",
            need = "",
        )
    }
}

/**
 * 渲染单条体育打卡记录卡片
 * @param modifier 记录卡片布局修饰符
 * @param title 项目标题
 * @param done 已完成次数
 * @param need 目标次数
 */
@Composable
private fun SportDetailItem(
    modifier: Modifier = Modifier,
    title: String,
    done: String,
    need: String
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier,
            text = title,
            color = 0xFF15315b.dark(0xFFFFFFFF),
            fontSize = 16.sp,
        )
        Text(
            modifier = Modifier
                .padding(start = 2.dp),
            text = done,
            color = 0xFF15315b.dark(0xFFFFFFFF),
            fontSize = 16.sp,
        )
        Text(
            modifier = Modifier
                .padding(2.dp),
            text = need,
            color = 0xFF697c9b.dark(0xFF606061),
            fontSize = 16.sp,
        )
    }
}

/**
 * 根据体育详情请求结果渲染记录列表或空状态提示。
 *
 * @param result null 表示加载中，失败结果展示错误态，成功结果直接读取网络模型。
 * @param modifier 记录区域布局修饰符
 */
@Composable
private fun SportRecord(
    result: Result<SportDetailBean>?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val triggerOffset = with(LocalDensity.current) {
        70.dp.toPx()
    }
    val refreshState = remember {
        RefreshState(triggerOffset = triggerOffset)
    }

    Box(
        modifier = modifier
            .padding(top = 8.dp)
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(0xFFFBFCFF.dark(0xFF1D1D1D))
            .navigationBarsPadding()
    ) {
        PullToRefresh(
            state = refreshState,
            canPull = {
                listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
            },
            onRefresh = {
                SportDetailRepository.refresh()
            },
            modifier = Modifier,
        ) {
            LazyColumn(
                state = listState,
                // 自定义刷新负责边界拖动，避免 iOS 弹性回滚先消费下拉距离。
                overscrollEffect = null,
                userScrollEnabled = !refreshState.isRefreshing,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 15.dp, end = 15.dp),
                contentPadding = PaddingValues(vertical = 5.dp)
            ) {
                val week = SchoolCalendar.getWeekOfTerm() ?: 22
                when {
                    week !in 1..21 -> item {
                        HintItem(
                            modifier = Modifier.fillParentMaxSize(),
                            drawableResource = Res.drawable.sport_ic_holiday,
                            content = "大家都放假了，好好度假吧",
                        )
                    }
                    result == null -> Unit
                    result.isFailure -> item {
                        HintItem(
                            modifier = Modifier.fillParentMaxSize(),
                            drawableResource = ConfigRes.configIc404(),
                            content = "数据异常，请刷新重试",
                        )
                    }
                    else -> {
                        // 后端按时间正序返回；页面保持最新记录在前。
                        val records = result.getOrThrow().item.orEmpty().asReversed()
                        if (records.isEmpty()) {
                            item {
                                HintItem(
                                    modifier = Modifier.fillParentMaxSize(),
                                    drawableResource = Res.drawable.sport_ic_no_data,
                                    content = "暂时还没有记录哦~",
                                )
                            }
                        } else {
                            items(records.size) { index ->
                                ContentItem(
                                    modifier = Modifier,
                                    item = records[index],
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val vmRefreshing by SportDetailRepository.isRefreshing.collectAsStateWithLifecycle()
    LaunchedEffect(vmRefreshing) {
        if (refreshState.isRefreshing && !vmRefreshing) {
            refreshState.finishRefresh()
        }
    }
}

/**
 * 将下拉刷新状态连接到内容区域
 * @param state 下拉刷新状态
 * @param canPull 当前列表是否允许下拉
 * @param onRefresh 刷新触发回调
 * @param modifier 容器布局修饰符
 * @param header 刷新头部内容
 * @param content 页面内容
 */
@Composable
private fun PullToRefresh(
    state: RefreshState,
    canPull: () -> Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
    header: @Composable (RefreshState) -> Unit = {
        RefreshHeader(state = it)
    },
    content: @Composable BoxScope.() -> Unit,
) {
    val connection = remember(state) {
        RefreshNestedScrollConnection(
            state = state,
            canPull = canPull,
            onRelease = {
                if (state.release()) {
                    onRefresh()
                }
            }
        )
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = state.pullOffset - state.triggerOffset
                }
        ) {
            header(state)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = state.pullOffset
                }
                .nestedScroll(connection),
            content = content
        )
    }
}

// 渲染记录卡片的容器和点击内容
@Composable
private fun ContentItem(
    modifier: Modifier = Modifier,
    item: SportDetailBean.Item
) {
    Box(
        modifier = modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(0xFFFFFFFF.dark(0xFF1D1D1D)),
    ) {
        Column {
            LabelItem(
                modifier = Modifier.padding(top = 8.dp, bottom = 5.dp),
                date = item.date,
                isValid = item.valid,
                isAward = item.isAward
            )
            Info(
                modifier = Modifier.padding(bottom = 8.dp),
                time = item.time,
                location = item.spot,
                type = item.type,
            )
        }
    }
}

// 渲染记录日期、奖励和有效性标签
@Composable
private fun LabelItem(
    modifier: Modifier = Modifier,
    date: String,
    isValid: Boolean,
    isAward: Boolean
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .padding(start = 10.dp),
            text = date,
            color = 0xFF15315b.dark(0xFFFFFFFF),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        if (isAward) {
            Image(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .height(18.dp),
                painter = painterResource(Res.drawable.sport_ic_award),
                contentDescription = null,
                contentScale = ContentScale.FillHeight
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (isValid) {
            Image(
                modifier = Modifier
                    .padding(end = 15.dp),
                painter = painterResource(Res.drawable.sport_ic_valid),
                contentDescription = null,
            )
        } else {
            Image(
                modifier = Modifier
                    .padding(end = 15.dp),
                painter = painterResource(Res.drawable.sport_ic_not_valid),
                contentDescription = null,
            )
        }
    }
}

// 渲染记录的时间、地点和运动类型信息
@Composable
private fun Info(
    modifier: Modifier = Modifier,
    time: String,
    location: String,
    type: String
) {
    Row(
        modifier = modifier
            .padding(top = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InfoItem(
            modifier = Modifier,
            content = time,
            drawableResource = Res.drawable.sport_ic_time
        )
        Spacer(modifier = Modifier.weight(1f))
        InfoItem(
            modifier = Modifier,
            content = location,
            drawableResource = Res.drawable.sport_ic_spot
        )
        Spacer(modifier = Modifier.weight(1f))
        InfoItem(
            modifier = Modifier,
            content = type,
            drawableResource = if (type == "跑步") {
                Res.drawable.sport_ic_run
            } else {
                Res.drawable.sport_ic_other
            }
        )
    }
}

// 渲染带图标的单项记录信息
@Composable
private fun InfoItem(
    modifier: Modifier = Modifier,
    drawableResource: DrawableResource,
    content: String
) {
    Row(
        modifier = modifier
            .padding(end = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier
                .padding(start = 10.dp),
            painter = painterResource(drawableResource),
            contentDescription = null,
        )
        Text(
            modifier = Modifier
                .padding(start = 5.dp),
            text = content,
            fontSize = 14.sp,
            color = 0xFF697C9B.dark(0xFF606061)
        )
    }
}

// 展示空状态插图和提示文案
@Composable
private fun HintItem(
    modifier: Modifier = Modifier,
    drawableResource: DrawableResource,
    content: String
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            modifier = Modifier
                .padding(bottom = 10.dp),
            painter = painterResource(drawableResource),
            contentDescription = null
        )
        Text(
            modifier = Modifier,
            text = content,
            fontSize = 14.sp
        )
    }
}
