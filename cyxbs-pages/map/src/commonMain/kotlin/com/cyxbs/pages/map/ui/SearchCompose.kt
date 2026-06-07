package com.cyxbs.pages.map.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.map.api.MapNavArgument
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel
import cyxbsmobile.cyxbs_pages.map.generated.resources.Res
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_delete
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_search
import org.jetbrains.compose.resources.painterResource

/**
 * @Desc : 搜索页
 * @Author : zzx
 * @Date : 2025/12/6 12:49
 */

@Composable
fun SearchCompose(
  argument: MapNavArgument,
  modifier: Modifier = Modifier,
  needPlaceList: Boolean = false
) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  val backState = rememberNavigationEventState(NavigationEventInfo.None)
  NavigationBackHandler(
    state = backState,
    onBackCompleted = {
      if (viewmodel.mapSearchPagerState.value == 1) {
        viewmodel.mapSearchPagerState.value = 0
      } else {
        argument.popBackStack()
      }
    },
  )
  Box(
    modifier = modifier
  ) {
    if (viewmodel.searchTextFieldState.text.isNotEmpty()) {
      SearchResultCompose(
        modifier = Modifier.padding(top = 12.dp)
      )
    } else {
      SearchHistoryCompose(
        modifier = Modifier.padding(top = 12.dp),
        needPlaceList = needPlaceList
      )
    }
  }
}

@Composable
fun SearchResultCompose(modifier: Modifier = Modifier) {
  val textColor = 0xFF234780.dark(0xFFF0F0F2)
  val viewmodel = viewModel(MapComposeViewModel::class)
  LazyColumn(
    modifier = modifier.fillMaxSize()
  ) {
    items(
      count = viewmodel.searchResultList.size,
      key = { id -> viewmodel.searchResultList[id].placeId }
    ) { index ->
      val placeItem = viewmodel.searchResultList[index]
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .clickableSingle {
            viewmodel.mapSearchPagerState.value = 0
            viewmodel.addHot(placeItem.placeId)
            viewmodel.addSearchHistory(placeItem)
            viewmodel.searchToPlace(placeItem)
          }
          .animateItem(
            fadeInSpec = tween(durationMillis = 500),
            placementSpec = tween(durationMillis = 200),
            fadeOutSpec = tween(durationMillis = 200)
          )
      ) {
        Image(
          modifier = Modifier
            .padding(start = 16.dp)
            .size(15.dp),
          painter = painterResource(Res.drawable.map_ic_search),
          contentDescription = null
        )
        Text(
          modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
          text = placeItem.placeName,
          fontSize = 15.sp,
          color = textColor
        )
      }
    }
  }
}

@Composable
fun SearchHistoryCompose(
  modifier: Modifier = Modifier,
  needPlaceList: Boolean = false
) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  val showState = remember { mutableStateOf(false) }
  Column(
    modifier = modifier.fillMaxSize()
  ) {
    Row(
      modifier = Modifier.padding(top = 16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        modifier = Modifier.padding(start = 16.dp),
        text = "历史搜索",
        fontSize = 15.sp,
        color = 0xFF778AA9.dark(0xFFA1A1A2)
      )
      Spacer(
        modifier = Modifier.weight(1f)
      )
      Text(
        modifier = Modifier
          .padding(end = 16.dp)
          .clickableSingle {
            showState.value = true
          },
        text = "清除全部",
        fontSize = 11.sp,
        color = 0xFFABBCD8.dark(0xFF5A5A5A)
      )
    }
    LazyColumn(
      modifier = Modifier.padding(top = 8.dp)
    ) {
      items(
        count = viewmodel.searchHistory.size,
        key = { id -> viewmodel.searchHistory[id].placeId }
      ) { index ->
        val placeItem = viewmodel.searchHistory[index]
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            modifier = Modifier
              .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
              .weight(1f)
              .clickableSingle {
                viewmodel.mapSearchPagerState.value = 0
                viewmodel.searchToPlace(placeItem)
              },
            text = placeItem.placeName,
            fontSize = 16.sp,
            color = 0xFF23477F.dark(0xFFF0F0F2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Image(
            modifier = Modifier
              .padding(end = 16.dp)
              .clickableSingle {
                viewmodel.deleteSearchHistory(placeItem)
              }
              .size(31.dp)
              .padding(8.dp),
            painter = painterResource(Res.drawable.map_ic_delete),
            contentDescription = null
          )
        }
      }
    }
  }
  ClearAllHistoryDialog(showState)
  LaunchedEffect(Unit) {
    viewmodel.getSearchHistory()
  }
}