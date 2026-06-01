package com.cyxbs.pages.map.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.components.view.ui.LocalBottomSheetScope
import com.cyxbs.pages.map.api.MapNavArgument
import com.cyxbs.pages.map.ui.SearchCompose
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel
import cyxbsmobile.cyxbs_pages.map.generated.resources.Res
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_search_clear
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_search_edit_text_icon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.vectorResource

/**
 * @Desc : 搜索栏底部bottomSheet
 * @Author : zzx
 * @Date : 2026/1/13 13:08
 */

/**
 * 搜索 bottomSheet 的内层内容（不含 BottomSheetCompose 外壳）。
 *
 * 由 [com.cyxbs.pages.map.ui.SearchNavEntry] 作为 NavEntry 内容使用，
 * draggable 通过 [LocalBottomSheetScope] 获取。
 */
@Composable
fun SearchBottomSheetContent(argument: MapNavArgument) {
  val bottomSheetScope = LocalBottomSheetScope.current
  val ratio = getWindowScreenSize().height / getWindowScreenSize().width
  val modifier = when {
    ratio > 1.5 -> {
      Modifier.fillMaxWidth()
    }

    else -> {
      Modifier
        .padding(start = 30.dp)
        .width(getWindowScreenSize().width / 3)
    }
  }
  Column(
    modifier = modifier
      .then(bottomSheetScope.bottomSheetDraggable())
      .shadow(
        elevation = 10.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
      )
      .background(LocalAppColors.current.topBg)
      .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
      .padding(start = 16.dp, end = 16.dp)
      .navigationBarsPadding()
  ) {
    Box(
      modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(top = 8.dp)
        .width(40.dp)
        .height(8.dp)
        .background(
          color = 0xFFE2EDFB.dark(0xFF000000),
          shape = RoundedCornerShape(6.dp)
        )
    )
    BottomSearchBar(
      modifier = Modifier
        .padding(top = 8.dp, bottom = 8.dp)
        .fillMaxWidth()
        .height(48.dp)
    )
    SearchCompose(
      argument = argument,
      modifier = Modifier
        .fillMaxWidth()
        .height(getWindowScreenSize().height / 3 * 2)
        .background(LocalAppColors.current.topBg)
    )
  }
}

@Composable
private fun BottomSearchBar(modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  val viewmodel = viewModel(MapComposeViewModel::class)
  BasicTextField(
    modifier = modifier
      .background(
        color = 0xFFF0F4FD.dark(0xFF202020),
        shape = RoundedCornerShape(20.dp)
      )
      .onFocusChanged { focusState ->
        if (focusState.isFocused) {
          scope.launch {
            viewmodel.searchBottomSheetState.expand()
          }
        }
      }
      .padding(4.dp),
    state = viewmodel.searchTextFieldState,
    textStyle = TextStyle(
      fontSize = 14.sp,
      color = 0xFF16305C.dark(0xFFF0F0F2)
    ),
    lineLimits = TextFieldLineLimits.SingleLine,
    cursorBrush = SolidColor(0xFF788EFA.dark(0xFFFFFFFF)),
    decorator = { innerTextField ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
      ) {
        Image(
          modifier = Modifier.size(24.dp).padding(start = 8.dp),
          painter = painterResource(Res.drawable.map_ic_search_edit_text_icon),
          contentDescription = null
        )
        Box(
          modifier = Modifier.padding(start = 8.dp, end = 4.dp).weight(1f)
        ) {
          if (viewmodel.searchTextFieldState.text.isEmpty()) {
            Text(
              color = 0xFF94969E.dark(0xFF8C8C8C),
              text = getHotWord(viewmodel.mapInfo.value?.hotWord),
              fontSize = 16.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          innerTextField()
        }
        if (viewmodel.searchTextFieldState.text.isNotEmpty()) {
          Icon(
            modifier = Modifier
              .padding(start = 4.dp, end = 4.dp)
              .clickableNoIndicator {
                viewmodel.searchTextFieldState.edit {
                  replace(0, length, "")
                }
              },
            imageVector = vectorResource(Res.drawable.map_ic_search_clear),
            tint = 0xFF16305C.dark(0xFFF0F0F2),
            contentDescription = null
          )
        }
      }
    }
  )
  LaunchedEffect(viewmodel.searchTextFieldState.text) {
    delay(500)
    viewmodel.search()
  }
}

fun getHotWord(text: String? = null): String {
  return "大家都在搜：" + (text ?: "风雨操场")
}