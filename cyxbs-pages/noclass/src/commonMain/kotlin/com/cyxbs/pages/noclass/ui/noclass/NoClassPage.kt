package com.cyxbs.pages.noclass.ui.noclass

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.compose.theme.LocalAppDark
import com.cyxbs.components.init.MainNavController
import com.cyxbs.components.utils.compose.backHandler
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.view.ui.BottomSheetState
import com.cyxbs.components.view.ui.BottomSheetValueState
import com.cyxbs.pages.noclass.api.NoclassBatchAddArgument
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

@Composable
internal fun NoClassPage() {
    val viewModel = viewModel(NoClassViewModel::class)
    val tempViewModel = viewModel(TemporaryViewModel::class)
    val isSheetExpanded by tempViewModel.isSheetExpanded.collectAsStateWithLifecycle()

    val sheetState =
        remember { BottomSheetState(onDismissRequest = { collapse() }, hideable = true) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isSheetExpanded) {
        if (isSheetExpanded) sheetState.expand() else sheetState.collapse()
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
            //顶部 Header 部分
            Column(
                modifier = Modifier
                    .fillMaxWidth()

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


