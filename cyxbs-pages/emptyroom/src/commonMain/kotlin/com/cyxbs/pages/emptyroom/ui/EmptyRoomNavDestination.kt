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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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

@ImplProvider(clazz = MainNavDestination::class, name = NAV_EMPTY_ROOM)
class EmptyRoomNavDestination : MainNavDestination<EmptyRoomArgument>(EmptyRoomArgument::class) {
    override val needLogin: Boolean = false

    @Composable
    override fun DestinationContent(parcel: DestinationParcel<EmptyRoomArgument>) {
        viewModel { EmptyRoomComposeViewModel() }
        EmptyRoomPage()
    }
}

@Composable
private fun EmptyRoomPage() {
    val viewModel = viewModel(EmptyRoomComposeViewModel::class)

    //加载中的动画
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

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
            verticalAlignment = Alignment.CenterVertically // 垂直居中对齐
        ) {
            // 增大返回键点击热区
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

            UniversalTabSelector(
                items = (1..25).toList(),
                selectedItems = setOfNotNull(viewModel.selectedWeek),
                isScrollable = true,
                modifier = Modifier.weight(1f).height(45.dp),
                onItemToggle = { viewModel.selectedWeek = it },
                label = {
                    val chineseNum = listOf(
                        "一",
                        "二",
                        "三",
                        "四",
                        "五",
                        "六",
                        "七",
                        "八",
                        "九",
                        "十",
                        "十一",
                        "十二",
                        "十三",
                        "十四",
                        "十五",
                        "十六",
                        "十七",
                        "十八",
                        "十九",
                        "二十",
                        "二十一",
                        "二十二",
                        "二十三",
                        "二十四",
                        "二十五"
                    )
                    "第${chineseNum.getOrElse(it - 1) { it.toString() }}周"
                }
            )
        }

        //周几
        UniversalTabSelector(
            items = listOf(1, 2, 3, 4, 5, 6, 7),
            selectedItems = setOfNotNull(viewModel.selectedWeekDayNum),
            isScrollable = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp)
                .background(LocalAppColors.current.middleBg).padding(start = 12.dp),
            onItemToggle = { viewModel.selectedWeekDayNum = it },
            label = { listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[it - 1] }
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = LocalAppColors.current.topBg,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Box(modifier = Modifier.padding(top = 20.dp, bottom = 105.dp)) {
                    if (!viewModel.isLoading) {
                        // 只有在非加载状态下才显示内容
                        if (viewModel.roomResult.isNotEmpty()) {
                            ShowContentCompose(viewModel.roomResult)
                        } else {
                            //不要在Composable直接调用 .toast()，
                            //否则重组一次弹一次。这里应该使用LaunchedEffect
                            LaunchedEffect(Unit) { "抱歉，数据获取失败".toast() }
                        }
                    }
                }
            }

            //加载动画图片
            if (viewModel.isLoading) {
                Image(
                    painter = painterResource(Res.drawable.emptyroom_ic_querying),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                        .offset(y = (-30).dp)
                        //在lambda中读取动画值，跳过重组阶段，在绘画阶段操作，防止过度重组
                        .graphicsLayer { rotationZ = rotation }

                )
            }


            //底部面板
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(105.dp),
                color = LocalAppColors.current.bottomBg,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                elevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    UniversalTabSelector(
                        items = listOf(1, 2, 3, 4, 5, 6),
                        selectedItems = viewModel.selectedSections.toSet(),
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
                    UniversalTabSelector(
                        items = listOf(2, 3, 4, 5, 8),
                        selectedItems = setOfNotNull(viewModel.selectedBuildNum),
                        isScrollable = false,
                        modifier = Modifier.height(45.dp),
                        onItemToggle = { viewModel.selectedBuildNum = it },
                        label = {
                            val buildMap = mapOf(
                                2 to "二教",
                                3 to "三教",
                                4 to "四教",
                                5 to "五教",
                                8 to "八教"
                            )
                            buildMap[it] ?: "${it}教"
                        }
                    )
                }
            }
        }
    }
}


@Composable
fun <T> UniversalTabSelector(
    items: List<T>,
    selectedItems: Set<T>,
    onItemToggle: (T) -> Unit,
    isScrollable: Boolean,
    modifier: Modifier = Modifier,
    label: (T) -> String = { it.toString() }
) {
    if (isScrollable) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            //这里设为0，间距由 TabItemContent 内部控制
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            // 侧边起始留白
            contentPadding = PaddingValues(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(items) { item ->
                val isSelected = selectedItems.contains(item)
                CustomTabWrapper(
                    onClick = { onItemToggle(item) },
                    content = { TabItemContent(text = label(item), isSelected = isSelected) }
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

fun List<String>.groupByFloor(): List<Pair<String, List<String>>> {
    val floorNameMap = mapOf(
        '1' to "一楼",
        '2' to "二楼",
        '3' to "三楼",
        '4' to "四楼",
        '5' to "五楼",
        '6' to "六楼"
    )

    return this.filter { it.length >= 2 }
        .groupBy { it[1] } //分组(楼层)
        .toList()
        .sortedBy { it.first } //按字符'1'<'2'<'3'排序
        .map { (floorChar, rooms) ->
            val floorName = floorNameMap[floorChar] ?: "${floorChar}楼"
            floorName to rooms
        }
}