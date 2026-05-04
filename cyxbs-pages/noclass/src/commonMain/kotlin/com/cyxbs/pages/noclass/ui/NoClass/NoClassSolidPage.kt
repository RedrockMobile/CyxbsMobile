package com.cyxbs.pages.noclass.ui.NoClass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.noclass.bean.NoClassGroups
import com.cyxbs.pages.noclass.ui.GroupDetail.navigateToGroupDetail
import com.cyxbs.pages.noclass.ui.dialog.AddToGroupSheet
import com.cyxbs.pages.noclass.ui.dialog.CreateGroupSheet
import com.cyxbs.pages.noclass.ui.dialog.SearchNoExistDialog
import com.cyxbs.pages.noclass.ui.dialog.SearchStudentSheet
import com.cyxbs.pages.noclass.viewmodel.SolidViewModel
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.Res
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_group
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt


/**
 * description ： TODO:固定分组— Compose 实现
 * author : summer_palace2
 * email : 2992203079qq.com
 * date : 2026/5/2 19:56
 */
@Composable
internal fun NoClassSolidPage() {
    val viewModel = viewModel(SolidViewModel::class)
    val groupList by viewModel.groupList.collectAsState()
    val solidResult by viewModel.solidSearchResult.collectAsState()
    var openedGroupId by remember { mutableStateOf<String?>(null) }

    // 1. 获取键盘控制器和焦点管理器
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (viewModel.groupList.value.isEmpty()) {
            viewModel.getAllGroup()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White.dark(Color(0xFF000101)))
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            CommonSearchInput(
                textState = viewModel.solidSearchText,
                onSearch = {
                    viewModel.searchStudent(it)
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
                hint = "添加同学"
            )
            Spacer(Modifier.height(10.dp))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = groupList, key = { it.id }) { group ->
                    //优化：缓存 Lambda，除非 group.id 或 group.isTop 变了，否则这两个 Lambda 对象引用不变
                    val onToggleTop = remember(group.id, group.isTop) {
                        { viewModel.toggleTopGroup(group.id, group.name, !group.isTop) }
                    }
                    val onDelete = remember(group.id) {
                        { viewModel.deleteGroup(group.id) }
                    }
                    val onOpenMenu = remember(group.id) { { openedGroupId = group.id } }
                    val onCloseMenu = remember(group.id) {
                        {
                            if (openedGroupId == group.id) openedGroupId = null
                        }
                    }

                    val isOpened = remember(openedGroupId) { openedGroupId == group.id }
                    GroupListItem(
                        group = group,
                        modifier = Modifier.animateItem(),
                        isOpened = isOpened,
                        onOpenMenu = onOpenMenu,
                        onCloseMenu = onCloseMenu,
                        onToggleTop = onToggleTop,
                        onDelete = onDelete,
                        onClick = { navigateToGroupDetail(group) }
                    )
                }
            }

        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            NoClassBottomButton(text = "创建", onClick = { viewModel.openCreateGroupDialog() })
        }
    }

    DialogLayer(viewModel)

}

//弹窗组合器
@Composable
private fun DialogLayer(viewModel: SolidViewModel) {
    val createResult by viewModel.createGroupResult.collectAsStateWithLifecycle()
    val showCreateDialog = viewModel.showCreateGroupDialog.value
    if (viewModel.showSolidSearchDialog.value) {
        val solidResult by viewModel.solidSearchResult.collectAsState()
        val sr = solidResult
        if (sr != null && sr.isNotEmpty()) {
            SearchStudentSheet(
                studentList = sr, onDismiss = { viewModel.dismissSolidSearchDialog() },
                onAddClick = { student ->
                    viewModel.openAddToGroupDialog(student)
                    viewModel.dismissSolidSearchDialog()
                })
        } else if (sr != null) {
            SearchNoExistDialog(onDismiss = { viewModel.dismissSolidSearchDialog() })
        }
    }

    if (viewModel.showAddToGroupDialog.value && viewModel.pendingStudent.value != null) {
        val groupList by viewModel.groupList.collectAsState() //独立观察状态流
        AddToGroupSheet(
            groupList = groupList, onDismiss = { viewModel.dismissAddToGroupDialog() },
            onDone = { groups ->
                groups.forEach {
                    viewModel.addMembers(
                        it.id,
                        listOf(viewModel.pendingStudent.value!!)
                    )
                }
                viewModel.dismissAddToGroupDialog()
            })
    }

    if (showCreateDialog) {

        CreateGroupSheet(
            onDismiss = {
                viewModel.dismissAfterCreate()
            },
            onCreate = { name -> viewModel.createGroup(name) },
            createResult = createResult,
            onResultHandled = { viewModel.clearCreateGroupResult() }
        )
    }

}

//分组列表    处理逻辑链接 https://zcnpvvo6f9jh.feishu.cn/wiki/DfYzwHlR2ipIzSkG0cSctyBDnXJ
@Composable
fun GroupListItem(
    group: NoClassGroups,
    isOpened: Boolean, // 外部判断：openedGroupId == group.id
    onOpenMenu: () -> Unit,
    onCloseMenu: () -> Unit,
    onToggleTop: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(group.id) { Animatable(0f) }
    val density = LocalDensity.current
    val menuWidthPx = with(density) { 182.dp.toPx() }

    //实时捕捉外部传入的 isOpened 状态
    //因为 pointerInput 是一个协程块，直接使用 isOpened 可能会存在闭包捕获旧值的问题
    val currentIsOpened by rememberUpdatedState(isOpened)

    //监听数据源状态
    LaunchedEffect(group.isOpen) {
        if (!group.isOpen && offsetX.value != 0f) {
            offsetX.animateTo(0f, tween(300))
            onCloseMenu()
        }
    }

    //监听全局互斥状态：如果我不再是“被打开的”那个，立即回弹
    LaunchedEffect(isOpened) {
        if (!isOpened && offsetX.value != 0f) {
            offsetX.animateTo(0f, tween(300))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(vertical = 1.dp)
            .background(Color.White.dark(Color(0xFF000101)))
    ) {
        //右侧菜单 (置顶、删除) 保持不变
        Row(modifier = Modifier.align(Alignment.CenterEnd).width(182.dp).fillMaxHeight()) {
            Box(
                modifier = Modifier.width(100.dp).fillMaxHeight().background(Color(0xFF4741E0))
                    .clickable { scope.launch { offsetX.animateTo(0f); onCloseMenu(); onToggleTop() } },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (group.isTop) "取消置顶" else "置顶",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Box(
                modifier = Modifier.width(82.dp).fillMaxHeight().background(Color(0xFFED535C))
                     .clickable {

                        onCloseMenu()
                        onDelete()

                        scope.launch {
                            offsetX.snapTo(0f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) { Text(text = "删除", color = Color.White, fontSize = 14.sp) }
        }

        //内容主体
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(Color.White.dark(Color(0xFF000101)))
                .pointerInput(group.id) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            //只要开始滑，就去尝试夺取全局 ID 锁
                            onOpenMenu()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            //处理结果
                            //如果此时全局 ID 锁已经被别人抢走了（currentIsOpened 变成 false）
                            //那么即便这根手指还在动，我也直接 return，不再更新自己的 offsetX
                            if (!currentIsOpened) return@detectHorizontalDragGestures

                            change.consume()
                            scope.launch {
                                offsetX.snapTo(
                                    (offsetX.value + dragAmount).coerceIn(-menuWidthPx, 0f)
                                )
                            }
                        },
                        onDragEnd = {
                            //只有当我依然持有 ID 锁时，才根据滑动距离判断最终停在哪
                            if (currentIsOpened) {
                                val target =
                                    if (offsetX.value < -menuWidthPx * 0.2f) -menuWidthPx else 0f
                                scope.launch {
                                    offsetX.animateTo(target, tween(300))
                                    if (target == 0f) onCloseMenu()
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, tween(300)); onCloseMenu() }
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
                    else onClick()
                }
                .padding(horizontal = 11.dp)
        ) {
            // Row 内容保持不变
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(Res.drawable.noclass_ic_group),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.padding(5.dp).size(35.5.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = group.name,
                    color = Color(0xFF2D4D80).dark(Color(0xFFF0F0F2)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


