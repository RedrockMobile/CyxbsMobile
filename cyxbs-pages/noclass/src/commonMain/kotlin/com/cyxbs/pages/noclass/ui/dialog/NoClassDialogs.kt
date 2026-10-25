package com.cyxbs.pages.noclass.ui.dialog

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.noclass.bean.Clss
import com.cyxbs.pages.noclass.bean.NoClassGroups
import com.cyxbs.pages.noclass.bean.NoClassTemporarySearchs
import com.cyxbs.pages.noclass.bean.Students
import com.cyxbs.pages.noclass.ui.noclass.NoClassBottomButton
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.Res
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_add_group
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_add_student
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_choose_group
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt


/**
 * description ： TODO:没课约的浮窗
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/5/2 11:34
 */


/*搜索全部弹窗 (学生/班级/分组)  SearchAllDialog */
@Composable
fun SearchResultSheet(
    searchResult: NoClassTemporarySearchs,
    onDismiss: () -> Unit,
    onSelectStudent: (Students) -> Unit,
    onSelectClass: (Clss) -> Unit,
    onSelectGroup: (NoClassGroups) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        //全屏背景，内容底部对齐
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(
                        LocalAppColors.current.middleBg,
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
            ) {
                //取消按钮
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        "取消", color = Color(0xFFABB5C4), fontSize = 12.sp,
                        modifier = Modifier.clickable { onDismiss() }.padding(8.dp)
                    )
                }
                Spacer(Modifier.height(7.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 20.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    val types = searchResult.types ?: emptyList()
                    if ("学生" in types) {
                        items(searchResult.students ?: emptyList(), key = { it.id }) { student ->
                            SearchStudentItem(student, onClick = { onSelectStudent(student) })
                        }
                    }
                    if ("班级" in types && searchResult.`class`.name != null) {
                        item {
                            SearchClassItem(
                                searchResult.`class`,
                                onClick = { onSelectClass(searchResult.`class`) })
                        }
                    }
                    if ("分组" in types && searchResult.group.name.isNotEmpty()) {
                        item {
                            SearchGroupItem(
                                searchResult.group,
                                onClick = { onSelectGroup(searchResult.group) })
                        }
                        items(
                            searchResult.group.members ?: emptyList(),
                            key = { it.id }) { student ->
                            SearchStudentItem(student, onClick = { onSelectStudent(student) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchStudentItem(student: Students, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                student.name,
                color = Color(0xFF2D4D80).dark(Color(0xFFF0F0F2)),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row {
                if (!student.major.isNullOrEmpty()) {
                    Text(student.major, color = Color(0xFF7B8899), fontSize = 12.sp, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                }
                Text(student.id, color = Color(0xFF7B8899), fontSize = 12.sp)
            }
        }
        Icon(
            painter = painterResource(Res.drawable.noclass_ic_add_student),
            contentDescription = "添加",
            tint = LocalAppColors.current.positive,
            modifier = Modifier.size(24.dp).padding(4.dp)
        )
    }
}

@Composable
private fun SearchClassItem(cls: Clss, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            cls.name ?: cls.id,
            color = Color(0xFF2D4D80).dark(Color(0xFFF0F0F2)),
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(Res.drawable.noclass_ic_add_student),
            contentDescription = "添加",
            tint = LocalAppColors.current.positive,
            modifier = Modifier.size(24.dp).padding(4.dp)
        )
    }
}

@Composable
private fun SearchGroupItem(group: NoClassGroups, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Spacer(Modifier.width(10.dp))
        Text(
            group.name,
            color = LocalAppColors.current.tvLv3,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(Res.drawable.noclass_ic_add_group),
            contentDescription = "添加分组",
            tint = LocalAppColors.current.positive,
            modifier = Modifier.size(24.dp).padding(4.dp)
        )
    }
}

/* 搜索学生弹窗 — SearchStudentDialog*/
@Composable
fun SearchStudentSheet(
    studentList: List<Students>,
    onDismiss: () -> Unit,
    onAddClick: (Students) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        //底部对齐 BottomSheet
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .background(
                        LocalAppColors.current.middleBg,
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        "取消", color = Color(0xFFABB5C4), fontSize = 12.sp,
                        modifier = Modifier.clickable { onDismiss() }.padding(8.dp)
                    )
                }
                Spacer(Modifier.height(7.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 20.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(studentList, key = { it.id }) { student ->
                        SearchStudentItem(student, onClick = { onAddClick(student) })
                    }
                }
            }
        }
    }
}

/*添加到分组弹窗 AddToGroupDialog*/
@Composable
fun AddToGroupSheet(
    groupList: List<NoClassGroups>,
    onDismiss: () -> Unit,
    onDone: (List<NoClassGroups>) -> Unit,
) {
    var selectedGroups by remember { mutableStateOf(setOf<String>()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = LocalAppColors.current.middleBg
            ) {
                Box(modifier = Modifier.fillMaxSize()) {

                    Text(
                        text = "取消",
                        color = Color(0xFFABB5C4),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .clickable { onDismiss() }
                            .padding(8.dp) // 增大点击热区
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 45.dp) // 约等于 16dp(取消区域) + 13dp(间距) + 文本高度偏移
                    ) {

                        Text(
                            text = "添加到",
                            color = Color(0xFF15315B).dark(Color(0xFFF0F0F2)),
                            fontSize = 18.sp,
                            modifier = Modifier.padding(start = 16.dp)
                        )

                        Spacer(Modifier.height(15.dp)) // layout_marginTop="15dp"

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(bottom = 15.dp)
                        ) {
                            items(groupList, key = { it.id }) { group ->
                                val selected = group.id in selectedGroups
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedGroups = if (selected) selectedGroups - group.id
                                            else selectedGroups + group.id
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = group.name,
                                        color = if (selected) LocalAppColors.current.positive else Color(
                                            0xFFABB5C4
                                        ),
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selected) {
                                        Icon(
                                            painter = painterResource(Res.drawable.noclass_ic_choose_group),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(15.dp + 3.dp + 15.dp))
                    }

                    NoClassBottomButton(
                        text = "完成",
                        onClick = { onDone(groupList.filter { it.id in selectedGroups }) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp),
                    )
                }
            }
        }
    }
}

/*创建分组弹窗 — CreateGroupDialog*/
@Composable
fun CreateGroupSheet(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    createResult: String? = null, //null=空闲, "-1"=失败, "-2"=重名, 其他=成功ID
    onResultHandled: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    //初始提示语
    var currentHint by remember { mutableStateOf("请输入你的分组名称") }

    val scope = rememberCoroutineScope()
    //抖动动画偏移量
    val shakeOffset = remember { Animatable(0f) }

    //抖动动画逻辑
    val triggerShake: suspend () -> Unit = {
        shakeOffset.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 300
                -15f at 50
                15f at 100
                -10f at 150
                10f at 200
                -5f at 250
            }
        )
    }

    //监听逻辑反馈
    LaunchedEffect(createResult) {
        val res = createResult ?: return@LaunchedEffect
        when (res) {
            "-1" -> {
                "似乎出现了什么问题呢,请稍后再试".toast()
                onResultHandled()
            }

            "-2" -> {
                currentHint = "名称重复，请重新输入"
                triggerShake()
                onResultHandled()
            }

            else -> {
                "创建成功".toast()
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(234.dp),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = LocalAppColors.current.middleBg
            ) {
                Box(modifier = Modifier.fillMaxSize()) {

                    //取消按钮
                    Text(
                        text = "取消",
                        color = Color(0xFFABB5C4),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    )

                    //标题
                    Text(
                        text = "创建新的分组",
                        color = Color(0xFF15315B).dark(Color(0xFFF0F0F2)),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(BiasAlignment(0f, -0.7f))
                    )

                    //输入区域与提示
                    Column(
                        modifier = Modifier
                            .align(BiasAlignment(0f, -0.2f))
                            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }, //应用抖动
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "分组名称：",
                                color = Color(0xFF556C8B).dark(Color(0xFFF0F0F2)),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 16.dp)
                            )

                            BasicTextField(
                                value = name,
                                onValueChange = {
                                    if (it.length <= 10) {
                                        name = it
                                        currentHint = when {
                                            it.isEmpty() -> "名称不能为空"
                                            it.length >= 10 -> "分组名称不能超过10个字符"
                                            else -> "请输入你的分组名称"
                                        }
                                    }
                                },
                                modifier = Modifier.width(183.dp).padding(end = 16.dp),
                                singleLine = true,
                                //光标颜色
                                cursorBrush = SolidColor(Color(0xFF788EFA)),
                                textStyle = TextStyle(
                                    color = Color(0xFF2D4D80).dark(Color(0xFFF0F0F2)),
                                    fontSize = 14.sp,
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = Color(0x99F2F5FF),
                                                shape = RoundedCornerShape(56.dp)
                                            )
                                            .padding(vertical = 10.5.dp, horizontal = 21.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (name.isEmpty()) {
                                            Text(
                                                text = "10个字以内",
                                                color = Color(0xFFABB5C4).dark(Color(0xFFF0F0F0)),
                                                fontSize = 14.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        //动态提示文字
                        Text(
                            text = currentHint,
                            color = Color(0xFF4A44E4).dark(Color(0xFF9A98FF)),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    //完成按钮，这里调用了在NoClassTemporaryPage定义的通用button
                    NoClassBottomButton(
                        text = "完成",
                        onClick = {
                            if (name.isBlank()) {
                                currentHint = "名称不能为空"
                                scope.launch { triggerShake() }
                            } else {
                                onCreate(name)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp)
                    )
                }
            }
        }
    }
}


/*搜索不存在弹窗 —  SearchNoExistDialog*/
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SearchNoExistDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(238.dp).height(133.dp),
            shape = RoundedCornerShape(11.dp),
            color = Color.White.dark(Color(0xFF2D2D2D))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(25.dp))
                Text(
                    "信息有误，请重新输入",
                    color = Color(0xFF15315B).dark(Color(0xFFF0F0F2)),
                    fontSize = 15.sp
                )
                Spacer(Modifier.weight(1f))

                //使用 Surface 制作自定义按钮
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier
                        .padding(bottom = 25.dp)
                        .width(92.dp)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = LocalAppColors.current.positive,
                    elevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "确定",
                            color = Color.White,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
}
