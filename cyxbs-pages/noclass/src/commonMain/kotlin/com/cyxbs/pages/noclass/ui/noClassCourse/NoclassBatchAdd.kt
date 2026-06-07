package com.cyxbs.pages.noclass.ui.noClassCourse

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.time.MinuteTime
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.backHandler
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.components.view.ui.rememberBottomSheetState
import com.cyxbs.pages.course.api.CourseUtils
import com.cyxbs.pages.noclass.bean.NoClassBatchResponseInfo.BatchStudent
import com.cyxbs.pages.noclass.ui.dialog.BatchQueryErrorDialog
import com.cyxbs.pages.noclass.ui.dialog.SameNameSelectionSheet
import com.cyxbs.pages.noclass.util.InputFormatUtil
import com.cyxbs.pages.noclass.util.noClassArrangePlan
import com.cyxbs.pages.noclass.viewmodel.BatchAddViewModel
import com.cyxbs.pages.noclass.viewmodel.CheckState
import com.cyxbs.pages.noclass.viewmodel.CourseQueryViewModel
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.Res
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_back
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

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
fun NoclassBatchAddPage() {
    val batchAddVm = viewModel(BatchAddViewModel::class)
    val courseQueryVm = viewModel(CourseQueryViewModel::class)

    val checkState by batchAddVm.checkState.collectAsStateWithLifecycle()
    val noclassData by courseQueryVm.noclassData.collectAsStateWithLifecycle()
    val isLoading by courseQueryVm.isLoading.collectAsStateWithLifecycle()
    val preparedStudents by batchAddVm.preparedStudents.collectAsStateWithLifecycle()

    val bottomSheetState = rememberBottomSheetState()
    val coroutineScope = rememberCoroutineScope()
    val noClassCourseFrame = remember { NoClassCourseFrame() }

    var showErrorDialog by remember { mutableStateOf<List<String>?>(null) }
    var showRepeatSheet by remember { mutableStateOf<List<BatchStudent>?>(null) }
    var alreadyQueried by remember { mutableStateOf(false) }
    var gatherSheetData by remember { mutableStateOf<GatherSheetData?>(null) }

    LaunchedEffect(checkState) {
        when (val state = checkState) {
            is CheckState.Checking -> alreadyQueried = false
            is CheckState.Error -> showErrorDialog = state.errList
            is CheckState.HasRepeat -> showRepeatSheet = state.repeatList
            is CheckState.Ready -> {
                alreadyQueried = true
                courseQueryVm.queryLessonsFromPairs(
                    state.students.map { it.first },
                    state.students
                )
            }
            is CheckState.NoResult -> "没有查到任何结果".toast()
            else -> {}
        }
    }

    LaunchedEffect(preparedStudents) {
        if (preparedStudents.isNotEmpty() && !alreadyQueried) {
            alreadyQueried = true
            courseQueryVm.queryLessonsFromPairs(
                preparedStudents.map { it.first },
                preparedStudents
            )
        }
    }

    LaunchedEffect(noclassData) {
        if (noclassData.isNotEmpty()) {
            bottomSheetState.expand()
        }
    }

    Box(modifier = Modifier.fillMaxSize().backHandler(
        enabled = true,
        onBack = {
            if (bottomSheetState.state == BottomSheetValueState.Expanded) {
                coroutineScope.launch { bottomSheetState.collapse() }
            } else {
                MainNavController.popBackStack()
            }
        }
    )) {
        Column(
            modifier = Modifier
                .background(Color(0xFFFEFEFE))
                .statusBarsPadding()
                .fillMaxSize()
        ) {
            TopBar1()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF1A2A4E84))
            )
            InputField(
                text = batchAddVm.inputText.value,
                onTextChange = { batchAddVm.inputText.value = it }
            )
            Spacer(modifier = Modifier.weight(1f))
            QueryButton(
                isLoading = checkState is CheckState.Checking,
                onClick = {
                    val rawContent = batchAddVm.inputText.value.trim()
                    if (rawContent.isEmpty()) {
                        "请输入内容".toast()
                        return@QueryButton
                    }
                    val contentList = rawContent.split("\n").map { it.trim() }
                    val firstType = InputFormatUtil.isWhatType(contentList[0])
                    val isValid = when (firstType) {
                        1 -> contentList.all { InputFormatUtil.isNumbersSequence(it) }
                        2 -> contentList.all { InputFormatUtil.isChineseCharacters(it) }
                        else -> false
                    }
                    if (!isValid) {
                        "输入格式有误，请检查后重试".toast()
                        return@QueryButton
                    }
                    batchAddVm.checkUpload(contentList)
                }
            )
        }

        BottomSheetCompose(
            bottomSheetState = bottomSheetState,
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
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    NoClassCourseContent(
                        noClassCourseFrame = noClassCourseFrame,
                        noclassData = noclassData,
                        modifier = Modifier.fillMaxSize(),
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

    showErrorDialog?.let { errList ->
        BatchQueryErrorDialog(
            errList = errList,
            onDismiss = { showErrorDialog = null }
        )
    }

    showRepeatSheet?.let { repeatList ->
        SameNameSelectionSheet(
            repeatList = repeatList,
            onDismiss = { showRepeatSheet = null },
            onConfirm = { selected ->
                showRepeatSheet = null
                batchAddVm.selectRepeatStudents(selected)
            }
        )
    }

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
                                        .clickable { /* 预留 */ }
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
private fun TopBar1() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.noclass_ic_back),
            contentDescription = "返回",
            modifier = Modifier
                .size(11.dp, 19.dp)
                .padding(10.dp)
                .clickable {
                    MainNavController.popBackStack()
                }
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "批量添加",
            modifier = Modifier.padding(10.dp),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF15315B)
        )
    }
}

@Composable
private fun InputField(
    text: String,
    onTextChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(470.dp)
            .padding(horizontal = 16.dp)
            .padding(top = 15.dp)
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xFFFEFEFE), RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFFE6EFFC), RoundedCornerShape(12.dp))
                .verticalScroll(ScrollState(0))
                .padding(start = 13.dp, top = 13.dp, end = 6.dp, bottom = 16.dp),
            minLines = 1,
            maxLines = Int.MAX_VALUE,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = Color(0xFF2D4D80),
                textAlign = TextAlign.Start,
                lineHeight = 22.sp
            )
        )

        if (text.isEmpty()) {
            Column(
                modifier = Modifier.padding(start = 13.dp, top = 13.dp)
            ) {
                Row(modifier = Modifier.padding(top = 17.dp)) {
                    Text(
                        text = "样例输入1：",
                        fontSize = 14.sp,
                        color = Color(0xFF6B89B7)
                    )
                    Text(
                        text = "卷卷\n卷娘",
                        fontSize = 14.sp,
                        color = Color(0xFF6B89B7),
                        lineHeight = 20.sp
                    )
                }

                Row(modifier = Modifier.padding(top = 15.dp)) {
                    Text(
                        text = "样例输入2：",
                        fontSize = 14.sp,
                        color = Color(0xFF6B89B7)
                    )
                    Text(
                        text = "2024212222\n2024212777",
                        fontSize = 14.sp,
                        color = Color(0xFF6B89B7),
                        lineHeight = 20.sp
                    )
                }

                Row(modifier = Modifier.padding(top = 24.dp)) {
                    Text(
                        text = "错误输入1：",
                        fontSize = 14.sp,
                        color = Color(0xFFEFB7AB)
                    )
                    Text(
                        text = "卷卷，卷娘\n卷卷，卷娘",
                        fontSize = 14.sp,
                        color = Color(0xFFEFB7AB),
                        lineHeight = 20.sp
                    )
                }

                Row(modifier = Modifier.padding(top = 15.dp)) {
                    Text(
                        text = "错误输入2：",
                        fontSize = 14.sp,
                        color = Color(0xFFEFB7AB)
                    )
                    Text(
                        text = "卷卷\n2024212777",
                        fontSize = 14.sp,
                        color = Color(0xFFEFB7AB),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QueryButton(
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 57.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 42.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF4741E0), Color(0xFF5D5EF7))
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .clickable(enabled = !isLoading) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "查询",
                    fontSize = 18.sp,
                    color = Color.White
                )
            }
        }
    }
}
