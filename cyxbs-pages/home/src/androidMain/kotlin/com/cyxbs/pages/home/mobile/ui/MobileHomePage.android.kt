package com.cyxbs.pages.home.mobile.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.viewpager2.widget.ViewPager2
import com.cyxbs.pages.home.R
import com.cyxbs.pages.home.adapter.MainAdapter
import com.cyxbs.pages.home.mobile.viewmodel.BottomNavViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Composable
internal actual fun HomeViewPagerCompose(modifier: Modifier) {
  val bottomNavViewModel = viewModel(BottomNavViewModel::class)
  val coroutineScope = rememberCoroutineScope()
  AndroidView(
    modifier = modifier
      .fillMaxSize()
      .navigationBarsPadding()
      .padding(bottom = bottomNavViewModel.height),
    factory = { context ->
      ViewPager2(context).apply {
        id = R.id.home_view_pager_id // 这里需要赋值 id，否则 ViewPager2 不会使用系统重建的 Fragment
        adapter = MainAdapter(context as FragmentActivity)
        isUserInputEnabled = false
        bottomNavViewModel.selectedItem.map {
          bottomNavViewModel.items.indexOf(it)
        }.onEach {
          currentItem = it
        }.launchIn(coroutineScope)
      }
    }
  )
}