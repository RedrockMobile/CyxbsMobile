package com.cyxbs.pages.noclass.ui.GroupDetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.compose.backHandler
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.noclass.api.GroupDetailArgument
import com.cyxbs.pages.noclass.bean.NoClassGroups
import com.cyxbs.pages.noclass.bean.NoClassTemporarySearchs
import com.cyxbs.pages.noclass.ui.NoClass.CommonSearchInput
import com.cyxbs.pages.noclass.ui.NoClass.NoClassBottomButton
import com.cyxbs.pages.noclass.ui.NoClass.StudentListItem
import com.cyxbs.pages.noclass.ui.NoClass.SwipeHintToast
import com.cyxbs.pages.noclass.ui.dialog.SearchNoExistDialog
import com.cyxbs.pages.noclass.ui.dialog.SearchResultSheet
import com.cyxbs.pages.noclass.viewmodel.GroupDetailViewModel
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.Res
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_back
import org.jetbrains.compose.resources.painterResource

/**
 * description ： TODO:分组详情页 — 组内管理
 * @author summer_palace2
 * @date 2026/5/3 18:39
 */
@Composable
internal fun GroupDetailPage(argument: GroupDetailArgument) {
    val viewModel = viewModel(key = argument.groupId, modelClass = GroupDetailViewModel::class)
    val currentGroup by viewModel.currentGroup.collectAsStateWithLifecycle()
    val searchResult by viewModel.searchResult.collectAsState()
    var openedStudentId by remember { mutableStateOf<String?>(null) }
    val members = currentGroup.members ?: emptyList()

    //在 GroupDetailPage 内部定义
    var hasShownHint by remember { mutableStateOf(false) }

    LaunchedEffect(members.size) {
        if (members.size > 1 && !hasShownHint) {
            viewModel.showHintText("试试左滑删除列表")
            hasShownHint = true // 标记为已显示
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White.dark(Color(0xFF000101)))
            .statusBarsPadding()
            .backHandler { MainNavController.popBackStack() }
    ) {
        //标题栏
        GroupDetailTopBar(
            title = currentGroup.name.ifEmpty { argument.groupName },
            onBackClick = { MainNavController.popBackStack() }
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
            NoClassBottomButton(text = "查询", onClick = { })
        }
    }

    //搜索弹窗层
    SearchDialogLayer(viewModel, searchResult)
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
