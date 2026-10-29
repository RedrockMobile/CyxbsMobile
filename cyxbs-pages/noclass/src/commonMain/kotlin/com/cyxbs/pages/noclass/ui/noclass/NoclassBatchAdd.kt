package com.cyxbs.pages.noclass.ui.noclass

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
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.navigation.appNavBackStack
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.noclass.bean.NoClassBatchResponseInfo
import com.cyxbs.pages.noclass.bean.Students
import com.cyxbs.pages.noclass.ui.dialog.BatchQueryErrorDialog
import com.cyxbs.pages.noclass.ui.dialog.SameNameSelectionSheet
import com.cyxbs.pages.noclass.util.InputFormatUtil
import com.cyxbs.pages.noclass.viewmodel.BatchAddResult
import com.cyxbs.pages.noclass.viewmodel.BatchAddViewModel
import com.cyxbs.pages.noclass.viewmodel.CheckState
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.Res
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_back
import org.jetbrains.compose.resources.painterResource

@Composable
fun NoclassBatchAddPage() {
    val batchAddVm = viewModel(BatchAddViewModel::class)
    val checkState by batchAddVm.checkState.collectAsStateWithLifecycle()
    val preparedStudents by batchAddVm.preparedStudents.collectAsStateWithLifecycle()

    var showErrorDialog by remember { mutableStateOf<List<String>?>(null) }
    var showRepeatSheet by remember {
        mutableStateOf<List<NoClassBatchResponseInfo.BatchStudent>?>(null)
    }

    // 「查询」后的校验结果处理
    LaunchedEffect(checkState) {
        when (val state = checkState) {
            is CheckState.Error -> showErrorDialog = state.errList
            is CheckState.HasRepeat -> showRepeatSheet = state.repeatList
            is CheckState.Ready -> submitToHomeAndBack(state.students)
            is CheckState.NoResult -> "没有查到任何结果".toast()
            else -> Unit
        }
    }

    // 重名选择完成后提交
    LaunchedEffect(preparedStudents) {
        if (preparedStudents.isNotEmpty()) {
            submitToHomeAndBack(preparedStudents)
        }
    }

    Column(
        modifier = Modifier
            .background(Color(0xFFFEFEFE))
            .statusBarsPadding()
            .fillMaxSize()
    ) {
        TopBar1(onBackClick = { appNavBackStack.lastOrNull()?.popBackStack() })
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF1A2A4E84))
        )
        TextField(
            text = batchAddVm.inputText.value,
            onTextChange = { batchAddVm.inputText.value = it }
        )
        Spacer(modifier = Modifier.weight(1f))
        query(
            isLoading = checkState is CheckState.Checking,
            onClick = {
                val raw = batchAddVm.inputText.value.trim()
                if (raw.isEmpty()) {
                    "请输入内容".toast()
                } else {
                    val contentList = raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    val firstType = InputFormatUtil.isWhatType(contentList.first())
                    val isValid = when (firstType) {
                        1 -> contentList.all { InputFormatUtil.isNumbersSequence(it) }
                        2 -> contentList.all { InputFormatUtil.isChineseCharacters(it) }
                        else -> false
                    }
                    if (isValid) {
                        batchAddVm.checkUpload(contentList)
                    } else {
                        "输入格式有误，请检查后重试".toast()
                    }
                }
            }
        )
    }

    showErrorDialog?.let { errList ->
        BatchQueryErrorDialog(errList = errList, onDismiss = { showErrorDialog = null })
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

/**
 * 校验成功后把学生交给没课约主页，并出栈回到主页。
 *
 * 这里用「出栈」而不是压入新的 NoClassPage：只有回到栈里原有的那个 NoClassPage，
 * 才能复用它已经持有的 [TemporaryViewModel]（临时分组里的学生），进而弹出查询结果面板。
 */
private fun submitToHomeAndBack(students: List<Pair<String, String>>) {
    if (students.isEmpty()) return
    BatchAddResult.publish(
        students.map { (id, name) -> Students(stunum1 = id, name1 = name) }
    )
    appNavBackStack.lastOrNull()?.popBackStack()
}

@Composable
fun TopBar1(
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                painter = painterResource(Res.drawable.noclass_ic_back),
                contentDescription = "返回",
                modifier = Modifier.padding(top = 1.dp),
                tint = Color.Unspecified
            )
        }
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
fun TextField(
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
fun query(
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
            Text(
                text = if (isLoading) "查询中..." else "查询",
                fontSize = 18.sp,
                color = Color.White
            )
        }
    }
}
