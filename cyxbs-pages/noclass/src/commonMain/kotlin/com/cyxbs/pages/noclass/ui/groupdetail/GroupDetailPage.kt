package com.cyxbs.pages.noclass.ui.groupdetail

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.compose.backHandler
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.course.api.CourseUtils
import com.cyxbs.pages.noclass.api.GroupDetailArgument
import com.cyxbs.pages.noclass.bean.NoClassGroups
import com.cyxbs.pages.noclass.bean.NoClassTemporarySearchs
import com.cyxbs.pages.noclass.ui.noclass.CommonSearchInput
import com.cyxbs.pages.noclass.ui.noclass.NoClassBottomButton
import com.cyxbs.pages.noclass.ui.noclass.StudentListItem
import com.cyxbs.pages.noclass.ui.noclass.SwipeHintToast
import com.cyxbs.pages.noclass.util.noClassArrangePlan
import com.cyxbs.pages.noclass.ui.dialog.SearchNoExistDialog
import com.cyxbs.pages.noclass.ui.dialog.SearchResultSheet
import com.cyxbs.pages.noclass.ui.noClassCourse.NoClassCourseContent
import com.cyxbs.pages.noclass.ui.noClassCourse.NoClassCourseFrame
import com.cyxbs.pages.noclass.ui.noClassCourse.NoClassLessonItem
import com.cyxbs.pages.noclass.ui.noClassCourse.NoClassLessonWhatTime
import com.cyxbs.pages.noclass.viewmodel.CourseQueryViewModel
import com.cyxbs.pages.noclass.viewmodel.GroupDetailViewModel
import kotlinx.coroutines.launch
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.Res
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_back
import org.jetbrains.compose.resources.painterResource

/**
 * description ： TODO:分组详情页 — 组内管理
 * @author summer_palace2
 * @date 2026/5/3 18:39
 */
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
internal fun GroupDetailPage(argument: GroupDetailArgument) {
    val viewModel = viewModel(key = argument.groupId, modelClass = GroupDetailViewModel::class)
    val currentGroup by viewModel.currentGroup.collectAsStateWithLifecycle()
    val searchResult by viewModel.searchResult.collectAsState()
    val courseQueryVm = viewModel(CourseQueryViewModel::class)
    val noclassData by courseQueryVm.noclassData.collectAsStateWithLifecycle()
    val isLoading by courseQueryVm.isLoading.collectAsStateWithLifecycle()
    var openedStudentId by remember { mutableStateOf<String?>(null) }
    val members = currentGroup.members ?: emptyList()
    val coroutineScope = rememberCoroutineScope()
    //复用的课表框架实例
    val noClassCourseFrame = remember { NoClassCourseFrame() }
    //聚集弹窗数据，非 null 时弹出
    var gatherSheetData by remember { mutableStateOf<GatherSheetData?>(null) }

    //课表 BottomSheet 状态控制器
    val sheetState = remember {
        BottomSheetState(onDismissRequest = { collapse() }, hideable = true)
    }

        //在 GroupDetailPage 内部定义
    var hasShownHint by remember { mutableStateOf(false) }

    LaunchedEffect(members.size) {
        if (members.size > 1 && !hasShownHint) {
            viewModel.showHintText("试试左滑删除列表")
            hasShownHint = true // 标记为已显示
        }
    }

    //查询数据到达后自动展开 BottomSheet
    LaunchedEffect(noclassData) {
        if (noclassData.isNotEmpty()) {
            sheetState.expand()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.dark(Color(0xFF000101)))
                .statusBarsPadding()
                .backHandler {
                    if (sheetState.state == BottomSheetValueState.Expanded) {
                        coroutineScope.launch { sheetState.collapse() }
                    } else {
                        MainNavController.popBackStack()
                    }
                }
        ) {
            //标题栏
            GroupDetailTopBar(
                title = currentGroup.name.ifEmpty { argument.groupName },
                onBackClick = {
                    if (sheetState.state == BottomSheetValueState.Expanded) {
                        coroutineScope.launch { sheetState.collapse() }
                    } else {
                        MainNavController.popBackStack()
                    }
                }
            )

            //搜索框
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(4.dp))
                //复用写好的逻辑  NoClassTemporaryPage
                CommonSearchInput(
                    textState = viewModel.searchText,
                    onSearch = { viewModel.searchAll(it) },
                    hint = "添加同学、分组或班级"
                )
                Spacer(Modifier.height(10.dp))
            }

            //成员列表
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                val members = currentGroup.members ?: emptyList()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(members, key = { it.id }) { student ->
                        val isOpened = remember(openedStudentId) { openedStudentId == student.id }
                        val onOpenMenu = remember(student.id) { { openedStudentId = student.id } }
                        val onCloseMenu = remember(student.id) { { if (openedStudentId == student.id) openedStudentId = null } }
                        val onDelete = remember(student.id) { { viewModel.deleteMember(student) } }
                        //复用写好的逻辑  NoClassTemporaryPage
                        StudentListItem(
                            student = student,
                            modifier = Modifier.animateItem(),
                            isOpened = isOpened,
                            onOpenMenu = onOpenMenu,
                            onCloseMenu = onCloseMenu,
                            onDelete = onDelete,
                        )
                    }
                }

                SwipeHintToast(
                    visible = viewModel.showHint.value && members.size > 1,
                    text = viewModel.hintText.value,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 60.dp) // 保持原有的位置
                )
            }

            //底部查询按钮
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                //"查询"按钮：用当前成员列表发起课表查询
                NoClassBottomButton(
                    text = "查询",
                    onClick = {
                        val students = members
                        if (students.isNotEmpty()) {
                            courseQueryVm.queryLessons(
                                stuNumList = students.map { it.id },
                                students = students
                            )
                        } else {
                            "暂无成员可查询".toast()
                        }
                    }
                )
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
                        //点击忙碌色块 → 弹出聚集弹窗
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

    //搜索弹窗层
    SearchDialogLayer(viewModel, searchResult)

    //聚集弹窗 — 选中忙碌色块时弹出，显示该时段忙碌人员列表
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

        Dialog(
            onDismissRequest = { gatherSheetData = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickableNoIndicator { gatherSheetData = null }
                )
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0x0D2A4E84))
                    )
                    Spacer(Modifier.height(18.dp))
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
private fun SearchDialogLayer(
    viewModel: GroupDetailViewModel,
    searchResult: NoClassTemporarySearchs?,
) {
    if (viewModel.showSearchDialog.value) {
        val result = searchResult
        if (result != null && !result.types.isNullOrEmpty()) {
            SearchResultSheet(
                searchResult = result,
                onDismiss = { viewModel.dismissSearchDialog() },
                onSelectStudent = { student ->
                    viewModel.addMember(student)
                    viewModel.dismissSearchDialog()
                },
                onSelectClass = { clss ->
                    clss.members?.let { viewModel.addClassMembers(it) }
                    viewModel.dismissSearchDialog()
                },
                onSelectGroup = { group ->
                    group.members?.let { viewModel.addClassMembers(it) }
                    viewModel.dismissSearchDialog()
                },
            )
        } else {
            SearchNoExistDialog(onDismiss = { viewModel.dismissSearchDialog() })
        }
    }
}

@Composable
private fun GroupDetailTopBar(
    title: String,
    onBackClick: () -> Unit
) {
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
            modifier = Modifier
                .size(24.dp)
                .padding(top = 1.dp)
                .clickableNoIndicator { onBackClick() },
            tint = Color.Unspecified
        )
        Text(
            text = title,
            color = LocalAppColors.current.tvLv1,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 13.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 跳转到分组详情页
 */
fun navigateToGroupDetail(group: NoClassGroups) {
    MainNavController.navigate(
        GroupDetailArgument(
            groupId = group.id,
            groupName = group.name,
        )
    )
}
