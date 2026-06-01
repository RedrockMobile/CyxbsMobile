package com.cyxbs.pages.noclass.ui.noClassCourse

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.backHandler
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.components.view.ui.BottomSheetCompose
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.components.view.ui.rememberBottomSheetState
import com.cyxbs.pages.noclass.bean.NoClassBatchResponseInfo.BatchStudent
import com.cyxbs.pages.noclass.ui.dialog.BatchQueryErrorDialog
import com.cyxbs.pages.noclass.ui.dialog.SameNameSelectionSheet
import com.cyxbs.pages.noclass.util.InputFormatUtil
import com.cyxbs.pages.noclass.viewmodel.BatchAddViewModel
import com.cyxbs.pages.noclass.viewmodel.CheckState
import com.cyxbs.pages.noclass.viewmodel.CourseQueryViewModel
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.Res
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_back
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * description ： TODO:没课约批量添加页面
 * author : 我不抽火哪儿来的烟
 * email : 3114795332qq.com
 * date : 2026/5/18 00:00
 */

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

    Box(modifier = Modifier.fillMaxSize().backHandler(enabled = true) {
        if (bottomSheetState.state == BottomSheetValueState.Expanded) {
            coroutineScope.launch { bottomSheetState.collapse() }
        } else {
            MainNavController.popBackStack()
        }
    }) {
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
                // 顶部下拉提示横线
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
                // 课表内容区域，占满剩余空间
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    // 复用项目课表框架展示空闲/忙碌数据
                    NoClassCourseContent(
                        noClassCourseFrame = noClassCourseFrame,
                        noclassData = noclassData,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 加载中遮罩：查询课表数据时显示 loading
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
