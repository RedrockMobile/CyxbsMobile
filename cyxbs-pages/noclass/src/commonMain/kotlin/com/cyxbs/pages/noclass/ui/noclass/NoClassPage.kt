package com.cyxbs.pages.noclass.ui.noclass

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.compose.theme.LocalAppDark
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.compose.backHandler
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.course.api.CourseUtils
import com.cyxbs.pages.noclass.api.NoclassBatchAddArgument
import com.cyxbs.pages.noclass.ui.noClassCourse.NoClassCourseContent
import com.cyxbs.pages.noclass.util.noClassArrangePlan
import com.cyxbs.pages.noclass.ui.noClassCourse.NoClassCourseFrame
import com.cyxbs.pages.noclass.ui.noClassCourse.NoClassLessonItem
import com.cyxbs.pages.noclass.ui.noClassCourse.NoClassLessonWhatTime
import com.cyxbs.pages.noclass.viewmodel.CourseQueryViewModel
import com.cyxbs.pages.noclass.viewmodel.NoClassViewModel
import com.cyxbs.pages.noclass.viewmodel.TemporaryViewModel
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.Res
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_back
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_batch_add
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_tab_indicator
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * description ： TODO:没课约主页
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/5/2 17:34
 */

private val TAB_TITLES = listOf("临时分组", "固定分组")

private data class GatherSheetData(
    val week: Int,
    val day: Int,
    val beginLesson: Int,
    val lessonLength: Int,
    val spareIds: List<String>,
    val idToNameMap: Map<String, String>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoClassPage() {
    val viewModel = viewModel(NoClassViewModel::class)
    val tempViewModel = viewModel(TemporaryViewModel::class)
    val courseQueryVm = viewModel(CourseQueryViewModel::class)
    val isSheetExpanded by tempViewModel.isSheetExpanded.collectAsStateWithLifecycle()
    val noclassData by courseQueryVm.noclassData.collectAsStateWithLifecycle()
    val isLoading by courseQueryVm.isLoading.collectAsStateWithLifecycle()

    //课表 BottomSheet 状态控制器
    val sheetState =
        remember { BottomSheetState(onDismissRequest = { collapse() }, hideable = true) }
    val coroutineScope = rememberCoroutineScope()
    //复用的课表框架实例（内含周切换、翻页等）
    val noClassCourseFrame = remember { NoClassCourseFrame() }
    //聚集弹窗数据，非 null 时显示忙碌人员弹窗
    var gatherSheetData by remember { mutableStateOf<GatherSheetData?>(null) }

    //当点击"查询"时：触发课表查询请求
    LaunchedEffect(isSheetExpanded) {
        if (isSheetExpanded) {
            val students = tempViewModel.tempStudentList.toList()
            if (students.isNotEmpty()) {
                courseQueryVm.queryLessons(
                    stuNumList = students.map { it.id },
                    students = students
                )
            }
        } else {
            sheetState.collapse()
        }
    }

    //监听到 Sheet 收起后同步重置 ViewModel 状态，确保下次查询可再次触发
    LaunchedEffect(sheetState.state) {
        if (sheetState.state == BottomSheetValueState.Collapsed) {
            tempViewModel.dismissQuerySheet()
        }
    }

    //查询数据到达后自动展开 Sheet
    LaunchedEffect(noclassData) {
        if (noclassData.isNotEmpty()) {
            sheetState.expand()
        }
    }

    val pagerState = rememberPagerState(
        initialPage = viewModel.currentTabIndex.intValue,
        pageCount = { TAB_TITLES.size })

    //监听 ViewModel 变化驱动 Pager
    LaunchedEffect(viewModel.currentTabIndex.intValue) {
        pagerState.animateScrollToPage(viewModel.currentTabIndex.intValue)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(if (LocalAppDark.current) Color(0xFF000101) else Color.White) // 对应 XML background
                .statusBarsPadding()
                .backHandler(enabled = true) {
                    if (sheetState.state == BottomSheetValueState.Expanded)
                        coroutineScope.launch { sheetState.collapse() }
                    else MainNavController.popBackStack()
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(17.dp)) // 对应 XML Toolbar 的 layout_marginTop="17dp"
                TopBar()

                //TabLayout 部分
                TabSwitchBar(
                    currentTabIndexState = viewModel.currentTabIndex,
                    onTabSelected = { viewModel.selectTab(it) }
                )
            }

            //中间阴影分割线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )

            //vp2+fragment
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
                    .background(Color.White.dark(Color(0xFF000101))),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    NoClassViewModel.TAB_TEMPORARY -> NoClassTemporaryPage()
                    NoClassViewModel.TAB_SOLID -> NoClassSolidPage()
                }
            }
        }

        //课表 BottomSheet — 展示查询到的空闲时间课程表
        BottomSheetCompose(
            bottomSheetState = sheetState,
            peekHeight = 0.dp,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 45.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(LocalAppColors.current.middleBg)
            ) {
                //顶部拖拽手柄
                Box(
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFD0D5E0))
                    )
                }
                //课程表主体内容
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    NoClassCourseContent(
                        noClassCourseFrame = noClassCourseFrame,
                        noclassData = noclassData,
                        modifier = Modifier.fillMaxSize(),
                        //点击忙碌色块 → 计算该时段空闲人员 → 弹出聚集弹窗
                        onItemClick = { item ->
                            val whatTime = item.whatTime as? NoClassLessonWhatTime ?: return@NoClassCourseContent
                            val week = whatTime.now.value.page
                            val day = whatTime.now.value.dayOfWeek.ordinal
                            val begin = whatTime.beginLesson
                            val length = whatTime.lessonLength
                            val weekData = noclassData[week]
                            if (weekData == null) {
                                "无法获取该周数据".toast()
                                return@NoClassCourseContent
                            }
                            val line = weekData.spareDayTime[day] ?: return@NoClassCourseContent
                            val spareIds = line.SpareItem.getOrNull(begin)?.spareId?.toList() ?: return@NoClassCourseContent
                            gatherSheetData = GatherSheetData(
                                week = week,
                                day = day,
                                beginLesson = begin,
                                lessonLength = length,
                                spareIds = spareIds,
                                idToNameMap = weekData.mIdToNameMap,
                            )
                        },
                    )
                    //查询加载中指示器
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = LocalAppColors.current.positive,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
            }
        }
    }

    //聚集弹窗 — 选中某个忙碌色块时弹出，显示该时段忙碌人员列表
    gatherSheetData?.let { data ->
        val idToName = data.idToNameMap
        val allStudents = idToName.values.toList()
        val busyStudents = idToName
            .filterKeys { it !in data.spareIds }
            .values
            .toList()
        val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val timeText = "${dayNames[data.day]} ${data.beginLesson}-${data.beginLesson + data.lessonLength - 1}节"
        val beginTime = CourseUtils.getStartMinuteTime(data.beginLesson)
        val endTime = CourseUtils.getEndMinuteTime(data.beginLesson + data.lessonLength - 1)
        val timeDetail = "${beginTime.hour.toString().padStart(2, '0')}:${beginTime.minute.toString().padStart(2, '0')}-${endTime.hour.toString().padStart(2, '0')}:${endTime.minute.toString().padStart(2, '0')}"

        var dragOffsetY by remember { mutableStateOf(0f) }

        //弹窗主体：遮罩 + 下滑关闭 + 忙碌人员列表 + 安排行程按钮
        Dialog(
            onDismissRequest = { gatherSheetData = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                //遮罩层，点击关闭
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickableNoIndicator { gatherSheetData = null }
                )
                //底部卡片，可下滑拖拽关闭
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(250.dp)
                        .offset { IntOffset(0, dragOffsetY.toInt()) }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragOffsetY > 100f) gatherSheetData = null
                                    else dragOffsetY = 0f
                                },
                                onDragCancel = { dragOffsetY = 0f },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    dragOffsetY = (dragOffsetY + amount).coerceAtLeast(0f)
                                }
                            )
                        }
                        .background(
                            Color.White,
                            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                        )
                        .padding(start = 16.dp, end = 16.dp)
                ) {
                    Spacer(Modifier.height(20.dp))
                    //标题行：忙碌人数 + 安排行程按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "忙碌${busyStudents.size}人",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15315B),
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF4741E0), Color(0xFF5D5EF7))
                                    ),
                                    shape = RoundedCornerShape(100.dp)
                                )
                                .clickable {
                                    gatherSheetData = null
                                    noClassArrangePlan(
                                        week = data.week,
                                        day = data.day,
                                        beginLesson = data.beginLesson,
                                        lessonLength = data.lessonLength,
                                        spareIds = data.spareIds,
                                        idToNameMap = data.idToNameMap,
                                    )
                                }
                                .padding(horizontal = 15.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "安排行程",
                                fontSize = 12.sp,
                                color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    //人数信息
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "人数：",
                            fontSize = 14.sp,
                            color = Color(0xFF8F9CAF).copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "共计 ${allStudents.size} 人",
                            fontSize = 14.sp,
                            color = Color(0xFF73839D).copy(alpha = 0.6f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    //时间信息
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "时间：",
                            fontSize = 14.sp,
                            color = Color(0xFF8F9CAF).copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "$timeText $timeDetail",
                            fontSize = 14.sp,
                            color = Color(0xFF73839D).copy(alpha = 0.6f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    //分割线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0x0D2A4E84))
                    )
                    Spacer(Modifier.height(18.dp))
                    //忙碌人员姓名标签列表
                    if (busyStudents.isEmpty()) {
                        Text(
                            text = "该时段无人忙碌",
                            fontSize = 14.sp,
                            color = Color(0xFF73839D).copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            busyStudents.forEach { name ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(63.dp))
                                        .border(
                                            width = 1.14.dp,
                                            color = Color(0xFFE8F0FC),
                                            shape = RoundedCornerShape(63.dp)
                                        )
                                        .clickable { }
                                        .padding(horizontal = 15.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = name,
                                        fontSize = 12.sp,
                                        color = Color(0xFF969FD2),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            painter = painterResource(Res.drawable.noclass_ic_back),
            contentDescription = null,
            modifier = Modifier.padding(top = 1.dp)
                .clickableNoIndicator { MainNavController.popBackStack() },
            tint = Color.Unspecified
        )


        //标题
        Text(
            text = "没课约",
            color = LocalAppColors.current.tvLv1,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        //批量添加按钮
        Row(
            modifier = Modifier
                .padding(end = 16.dp)
                //外环边框
                .border(
                    width = 1.dp,
                    color = LocalAppColors.current.positive, // 边框颜色
                    shape = CircleShape // 必须与背景形状一致
                )
                .background(
                    color = if (LocalAppDark.current) Color(0xFF000101) else Color.White,
                    shape = CircleShape
                )
                .clickableNoIndicator {
                    MainNavController.navigate(NoclassBatchAddArgument)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                painter = painterResource(Res.drawable.noclass_ic_batch_add),
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 11.dp, top = 7.dp, bottom = 7.dp)
                    .size(12.dp),
                tint = LocalAppColors.current.positive
            )

            Text(
                text = "批量添加",
                color = LocalAppColors.current.positive,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(
                        start = 6.dp,
                        end = 11.dp,
                        top = 3.2.dp,
                        bottom = 2.8.dp
                    ),

                )
        }
    }
}

@Composable
private fun TabSwitchBar(currentTabIndexState: IntState, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 15.dp)
    ) {
        TAB_TITLES.forEachIndexed { index, title ->
            val isSelected = currentTabIndexState.intValue == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickableNoIndicator { onTabSelected(index) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title,
                        color = if (isSelected) LocalAppColors.current.tvLv1 else LocalAppColors.current.tvLv3,
                        fontSize = 16.sp, // 根据 TabLayoutTextStyle 调整
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    //指示器
                    if (isSelected) {
                        Image(
                            painter = painterResource(Res.drawable.noclass_ic_tab_indicator),
                            contentDescription = null,
                            modifier = Modifier.padding(top = 6.dp).width(66.dp).height(3.dp)
                        )
                    } else {

                        Spacer(modifier = Modifier.padding(top = 6.dp).height(3.dp))
                    }
                }
            }
        }
    }
}


