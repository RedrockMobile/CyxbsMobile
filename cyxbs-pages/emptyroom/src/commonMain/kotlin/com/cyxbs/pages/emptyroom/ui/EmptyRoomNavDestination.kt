package com.cyxbs.pages.emptyroom.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.navigation.DestinationParcel
import com.cyxbs.components.config.navigation.MainNavDestination
import com.cyxbs.components.config.navigation.NAV_EMPTY_ROOM
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.emptyroom.EmptyRoomArgument
import com.cyxbs.pages.emptyroom.viewmodel.EmptyRoomComposeViewModel
import com.g985892345.provider.api.annotation.ImplProvider
import cyxbsmobile.cyxbs_pages.emptyroom.generated.resources.Res
import cyxbsmobile.cyxbs_pages.emptyroom.generated.resources.emptyroom_ic_back
import cyxbsmobile.cyxbs_pages.emptyroom.generated.resources.emptyroom_ic_querying
import org.jetbrains.compose.resources.painterResource

/**
 * description ： TODO:空教室页面
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/3/3 15:34
 */

/**
 * 所有的静态常量集中定义，避免在重组时重复创建对象
 */

//周次
private val WEEK_ITEMS = (1..25).toList()
private val CHINESE_NUM_LIST = listOf(
    "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十", "二十一", "二十二", "二十三", "二十四", "二十五"
)


//星期
private val WEEKDAY_ITEMS = (1..7).toList()
private val WEEKDAY_NAME_LIST = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

//节次
private val SECTION_ITEMS = (1..6).toList()

//教学楼配置
private val BUILDING_ITEMS = listOf(2, 3, 4, 5, 8)
private val BUILDING_MAP = mapOf(2 to "二教", 3 to "三教", 4 to "四教", 5 to "五教", 8 to "八教")

//楼层
private val FLOOR_NAME_MAP = mapOf(
    '1' to "一楼",
    '2' to "二楼",
    '3' to "三楼",
    '4' to "四楼",
    '5' to "五楼",
    '6' to "六楼"
)

@ImplProvider(clazz = MainNavDestination::class, name = NAV_EMPTY_ROOM)
class EmptyRoomNavDestination : MainNavDestination<EmptyRoomArgument>(EmptyRoomArgument::class) {
    override val needLogin: Boolean = false

    @Composable
    override fun DestinationContent(parcel: DestinationParcel<EmptyRoomArgument>) {
        viewModel { EmptyRoomComposeViewModel() }
        EmptyRoomPage()
    }
}


/**
 * 空教室主界面的compose函数
 */
@Composable
private fun EmptyRoomPage() {
    val viewModel = viewModel(EmptyRoomComposeViewModel::class)

    //状态转换与缓存，减少不必要的重组
    val selectedWeekSet = remember(viewModel.selectedWeek) {
        setOfNotNull(viewModel.selectedWeek)
    }
    val selectedWeekDaySet = remember(viewModel.selectedWeekDayNum) {
        setOfNotNull(viewModel.selectedWeekDayNum)
    }
    val selectedBuildNumSet = remember(viewModel.selectedBuildNum) {
        setOfNotNull(viewModel.selectedBuildNum)
    }
    //列表型多选使用derivedStateOf，因为列表的引用没变,remember永远会返回第一次缓存的结果,导致UI不刷新，所以这里使用derivedStateOf
    val selectedSectionsSet = remember {
        derivedStateOf { viewModel.selectedSections.toSet() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppColors.current.middleBg)
            .statusBarsPadding()
    ) {

        //返回键和周次选择
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(LocalAppColors.current.middleBg),
            verticalAlignment = Alignment.CenterVertically //垂直居中对齐
        ) {
            //增大返回键点区域
            IconButton(
                onClick = { MainNavController.popBackStack() },
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(24.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.emptyroom_ic_back),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp), //内部图标大小
                    tint = Color.Unspecified
                )
            }
            //多少周
            UniversalTabSelector(
                items = WEEK_ITEMS,
                selectedItems = selectedWeekSet,
                isScrollable = true,
                modifier = Modifier.weight(1f).height(45.dp),
                onItemToggle = { viewModel.selectedWeek = it },
                label = {
                    val chineseNum = CHINESE_NUM_LIST
                    "第${chineseNum.getOrElse(it - 1) { it.toString() }}周"
                }
            )
        }

        //周几
        UniversalTabSelector(
            items = WEEKDAY_ITEMS,
            selectedItems = selectedWeekDaySet,
            isScrollable = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp)
                .background(LocalAppColors.current.middleBg).padding(start = 12.dp),
            onItemToggle = { viewModel.selectedWeekDayNum = it },
            label = { WEEKDAY_NAME_LIST.getOrElse(it - 1) { it.toString() } }
        )

        //内容显示
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = LocalAppColors.current.topBg,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                val navBarHeight =
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Box(modifier = Modifier.padding(top = 20.dp, bottom = 105.dp + navBarHeight)) {
                    //首次加载数据不提示toast
                    val result = viewModel.roomResult

                    if (!viewModel.isLoading && result != null) {

                        if (result.isNotEmpty()) {
                            ShowContentCompose(result)
                        } else {
                            LaunchedEffect(result) { "抱歉，暂无空教室".toast() }
                        }
                    }

                }
            }


            //加载动画图片
            RefreshCompose(viewModel.isLoading, modifier = Modifier.align(Alignment.Center))


            //底部面板(选择第几节课和教学楼)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(105.dp),
                color = LocalAppColors.current.bottomBg,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                elevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.Center
                ) {

                    //第几节课
                    UniversalTabSelector(
                        items = SECTION_ITEMS,
                        selectedItems = selectedSectionsSet.value,
                        isScrollable = true,
                        modifier = Modifier.height(45.dp),
                        onItemToggle = { section ->
                            if (viewModel.selectedSections.contains(section)) {
                                if (viewModel.selectedSections.size > 1) viewModel.selectedSections.remove(
                                    section
                                )
                            } else {
                                viewModel.selectedSections.add(section)
                            }
                        },
                        label = { "${it * 2 - 1}—${it * 2}" }
                    )


                    //教学楼
                    UniversalTabSelector(
                        items = BUILDING_ITEMS,
                        selectedItems = selectedBuildNumSet,
                        isScrollable = false,
                        modifier = Modifier.height(45.dp),
                        onItemToggle = { viewModel.selectedBuildNum = it },
                        label = {
                            val buildMap = BUILDING_MAP
                            buildMap[it] ?: "${it}教"
                        }
                    )
                }
            }
        }
    }
}

/**
数据加载过程中的动画compose，实际执行一个图片的旋转逻辑
 */
@Composable
fun RefreshCompose(isLoading: Boolean, modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    //加载动画图片
    if (isLoading) {
        Image(
            painter = painterResource(Res.drawable.emptyroom_ic_querying),
            contentDescription = null,
            modifier = modifier
                .size(24.dp)
                .offset(y = (-30).dp)
                //在lambda中读取动画值，跳过重组阶段，在绘画阶段操作，防止过度重组
                .graphicsLayer { rotationZ = rotation }

        )
    }
}

/**
 * 所有Tablayout通用的一个Tab生成器
 * 可以选择滑动和非滑动版
 * @param selectedItems 是一个Set类型的数据接收器
 */
@Composable
fun <T> UniversalTabSelector(
    items: List<T>,
    selectedItems: Set<T>,
    onItemToggle: (T) -> Unit,
    isScrollable: Boolean,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() }
) {

    //创建滚动状态
    val listState = rememberLazyListState()

    //监听选中项变化，实现自动定位
    //当选中的集合发生变化时，触发滚动
    //只在初始化时使用
    LaunchedEffect(Unit) {
        if (isScrollable && selectedItems.isNotEmpty()) {
            //找到第一个选中项在items列表中的索引
            val index = items.indexOfFirst { it in selectedItems }
            //安全性检查,-1作为无用的值
            if (index != -1) {
                listState.scrollToItem(
                    index = index,
                    scrollOffset = -100 //这里的负偏移量是为了让选中的项稍微靠左/靠中一点，不至于贴着边
                )
            }
        }
    }
    if (isScrollable) {
        LazyRow(
            state = listState,
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(
                items = items,
                //key,帮助 Compose 识别元素
                key = { it.toString() },
                //contentType，帮助LazyRow在复用池中快速找回对象
                contentType = { "TabItem" }
            ) { item ->
                //判断是否选中
                //这里的contains操作在每一帧重组时都会执行
                val isSelected = remember(selectedItems, item) {
                    selectedItems.contains(item)
                }

                CustomTabWrapper(
                    onClick = { onItemToggle(item) },
                    content = {
                        TabItemContent(
                            text = label(item),
                            isSelected = isSelected
                        )
                    }
                )
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = selectedItems.contains(item)
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CustomTabWrapper(
                        onClick = { onItemToggle(item) },
                        content = { TabItemContent(text = label(item), isSelected = isSelected) }
                    )
                }
            }
        }
    }
}

/**
 * 自定义点击包裹层:彻底去除水波纹
 */
@Composable
private fun CustomTabWrapper(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(
                //传入null的indication以彻底去除水波纹点击效果
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Tab内部内容
 */
@Composable
private fun TabItemContent(text: String, isSelected: Boolean) {
    Box(
        modifier = Modifier
            //外边距
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(28.dp)
            .background(
                color = if (isSelected) Color(0xFFDDE3F8) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            //内边距
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (isSelected) 0xFF122D55.dark(0xFF122D55) else LocalAppColors.current.tvLv2,
            maxLines = 1
        )
    }
}

/**
内容显示区的compose函数
ToDo:这里使用了FlowRow，通过循环模拟实现了网格布局
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShowContentCompose(rawRoomList: List<String>) {
    val groupedData = remember(rawRoomList) { rawRoomList.groupByFloor() }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)
    ) {
        items(groupedData) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                //楼层文字
                Text(
                    text = pair.first,
                    color = Color(0xFF5E5E5E),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(60.dp) //固定宽度使右侧对齐
                )

                //教室代号
                FlowRow(
                    modifier = Modifier.weight(1f),
                    maxItemsInEachRow = 5,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    pair.second.forEach { roomNumber ->
                        Text(
                            text = roomNumber,
                            color = LocalAppColors.current.tvLv2,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 处理网络请求返回函数的辅助函数
 */
fun List<String>.groupByFloor(): List<Pair<String, List<String>>> {
    val floorNameMap = FLOOR_NAME_MAP

    return this.filter { it.length >= 2 }
        .groupBy { it[1] } //分组(楼层)
        .toList()
        .sortedBy { it.first } //按字符'1'<'2'<'3'排序
        .map { (floorChar, rooms) ->
            val floorName = floorNameMap[floorChar] ?: "${floorChar}楼"
            floorName to rooms
        }
}