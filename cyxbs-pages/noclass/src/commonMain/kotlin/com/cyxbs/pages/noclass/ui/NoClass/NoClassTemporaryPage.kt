package com.cyxbs.pages.noclass.ui.NoClass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.extensions.toast
import com.cyxbs.pages.noclass.bean.NoClassTemporarySearchs
import com.cyxbs.pages.noclass.bean.Students
import com.cyxbs.pages.noclass.ui.dialog.SearchNoExistDialog
import com.cyxbs.pages.noclass.ui.dialog.SearchResultSheet
import com.cyxbs.pages.noclass.viewmodel.TemporaryViewModel
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.Res
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_user
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * description ： TODO:临时分组页
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/5/2 20:22
 */
@Composable
internal fun NoClassTemporaryPage() {
    val viewModel = viewModel(TemporaryViewModel::class)
    val searchResult by viewModel.tempSearchResult.collectAsState()

    var openedStudentId by remember { mutableStateOf<String?>(null) }
    //在 GroupDetailPage 内部定义
    var hasShownHint by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.tempStudentList.size) {
        if (viewModel.tempStudentList.size > 1 && !hasShownHint) {
            viewModel.showTempHintText("试试左滑删除列表")
            hasShownHint = true //标记为已显示
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.dark(Color(0xFF000101)))
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            CommonSearchInput(
                textState = viewModel.tempSearchText, // 传递引用
                onSearch = { viewModel.searchAll(it) },
                hint = "添加同学、分组或班级"
            )
            Spacer(Modifier.height(10.dp))
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(viewModel.tempStudentList, key = { it.id }) { student ->
                    //remember 回调函数，防止对象地址变更触发重组
                    val isOpened = remember(openedStudentId) { openedStudentId == student.id }
                    val onOpenMenu = remember(student.id) { { openedStudentId = student.id } }
                    val onCloseMenu = remember(student.id) {
                        {
                            if (openedStudentId == student.id) openedStudentId = null
                        }
                    }
                    val onDelete =
                        remember(student.id) { { viewModel.removeTempStudent(student.id) } }
                    StudentListItem(
                        student = student,
                        modifier = Modifier.animateItem(),
                        isOpened = isOpened,
                        onOpenMenu = onOpenMenu,
                        onCloseMenu = onCloseMenu,
                        onDelete = onDelete
                    )
                }
            }

            SwipeHintToast(
                visible = viewModel.showTempHint.value && viewModel.tempStudentList.size > 1,
                text = viewModel.tempHintText.value,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp) //保持原有的位置
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            NoClassBottomButton(text = "查询", onClick = { viewModel.queryCourse() })
        }
    }

    TemporaryDialogLayer(viewModel, searchResult)
}


//弹窗组合器
@Composable
fun TemporaryDialogLayer(
    viewModel: TemporaryViewModel,
    searchResult: NoClassTemporarySearchs?
) {
    if (viewModel.showTempSearchDialog.value) {
        val result = searchResult
        if (result != null && !result.types.isNullOrEmpty()) {
            SearchResultSheet(
                searchResult = result,
                onDismiss = { viewModel.dismissTempSearchDialog() },
                onSelectStudent = { student ->
                    viewModel.addTempStudent(student)
                    viewModel.clearSearchText()
                    viewModel.dismissTempSearchDialog()
                },
                onSelectClass = { clss ->
                    viewModel.addTempClass(clss)
                    viewModel.clearSearchText()
                    viewModel.dismissTempSearchDialog()
                },
                onSelectGroup = { group ->
                    viewModel.addTempGroup(group)
                    viewModel.clearSearchText()
                    viewModel.dismissTempSearchDialog()
                }
            )
        } else {
            SearchNoExistDialog(onDismiss = { viewModel.dismissTempSearchDialog() })
        }
    }
}

//共用的搜索框
@Composable
fun CommonSearchInput(
    textState: MutableState<String>,
    onSearch: (String) -> Unit,
    hint: String
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    BasicTextField(
        value = textState.value, // 读取点下沉到此处
        onValueChange = { textState.value = it },
        modifier = Modifier.fillMaxWidth().height(44.dp)
            .background(color = Color(0xFFE8F0FC).dark(Color(0xFF1F1F1F)), shape = CircleShape),
        singleLine = true,
        cursorBrush = SolidColor(Color(0xFF788EFA)),
        textStyle = TextStyle(color = LocalAppColors.current.tvLv1, fontSize = 15.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            if (textState.value.isNotBlank()) {
                onSearch(textState.value)
                keyboardController?.hide()
                focusManager.clearFocus()
            } else {
                "输入为空".toast()
            }
        }),
        decorationBox = { innerTextField ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (textState.value.isEmpty()) {
                        Text(
                            text = hint,
                            color = Color(0x66142C52).dark(Color(0x7AF0F0F2)),
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            }
        }
    )
}


//学生临时列表，被分组详情复用过
@Composable
fun StudentListItem(
    student: Students,
    isOpened: Boolean,
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(student.id) { Animatable(0f) }
    val density = LocalDensity.current
    val deleteBtnWidthPx = with(density) { 82.dp.toPx() }

    //实时捕捉外部传入的 isOpened 状态
    //因为 pointerInput 是一个协程块，直接使用 isOpened 可能会存在闭包捕获旧值的问题
    val currentIsOpened by rememberUpdatedState(isOpened)


    LaunchedEffect(isOpened) {
        if (!isOpened && offsetX.value < 0f) {
            offsetX.animateTo(0f, tween(300))
        }
    }

    Box(
        modifier = modifier.fillMaxWidth().height(50.dp)
            .background(Color.White.dark(Color(0xFF000101)))
    ) {
        Box(
            modifier = Modifier.align(Alignment.CenterEnd).width(82.dp).fillMaxHeight()
                .background(Color(0xFFED535C))
                .clickable {

                    onCloseMenu()
                    onDelete()

                    scope.launch {
                        offsetX.snapTo(0f)
                    }
                },
            contentAlignment = Alignment.Center
        ) { Text(text = "删除", color = Color.White, fontSize = 14.sp) }

        Box(
            modifier = Modifier.fillMaxSize().offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(Color.White.dark(Color(0xFF000101)))
                .pointerInput(student.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { onOpenMenu() },
                        onHorizontalDrag = { change, dragAmount ->

                            //处理结果
                            //如果此时全局 ID 锁已经被别人抢走了（currentIsOpened 变成 false）
                            //那么即便这根手指还在动，我也直接 return，不再更新自己的 offsetX

                            //Todo:当 Item A 发现锁被 Item B 抢走时，它会停止跟随手指。
                            if (!currentIsOpened) return@detectHorizontalDragGestures

                            change.consume()
                            scope.launch {
                                offsetX.snapTo(
                                    (offsetX.value + dragAmount).coerceIn(-deleteBtnWidthPx, 0f)
                                )
                            }
                        },
                        onDragEnd = {
                            //只有当我依然持有 ID 锁时，才根据滑动距离判断最终停在哪
                            if (currentIsOpened) {
                                val target =
                                    if (offsetX.value < -deleteBtnWidthPx * 0.3f) -deleteBtnWidthPx else 0f
                                scope.launch {
                                    offsetX.animateTo(target, tween(300))
                                    if (target == 0f) onCloseMenu()
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    tween(300)
                                ); onCloseMenu()
                            }
                        }
                    )
                }
                .clickable {
                    if (offsetX.value < 0f) scope.launch {
                        offsetX.animateTo(
                            0f,
                            tween(300)
                        ); onCloseMenu()
                    }
                }
                .padding(horizontal = 11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(Res.drawable.noclass_ic_user),
                    contentDescription = null, tint = Color.Unspecified,
                    modifier = Modifier.padding(5.dp).size(35.5.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
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
                            Text(
                                student.major, color = Color(0xFF7B8899), fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            student.id, color = Color(0xFF7B8899), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

//共用button
@Composable
fun NoClassBottomButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.width(120.dp).height(42.dp),
        contentPadding = PaddingValues(0.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = LocalAppColors.current.positive, contentColor = Color.White
        ),
        elevation = ButtonDefaults.elevation(0.dp, 0.dp)
    ) { Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

/**
 * 共用提示文字
 * @param visible 是否显示
 * @param text 提示文字
 * @param modifier 外部传入的位置控制
 */
@Composable
fun SwipeHintToast(
    visible: Boolean,
    text: String,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500)),
        exit = fadeOut(tween(500)),
        modifier = modifier // 接收外部传入的位置控制
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(Color(0xFF2D4D80), CircleShape)
                .padding(horizontal = 24.dp, vertical = 9.dp)
        )
    }
}
