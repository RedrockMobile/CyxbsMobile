package com.cyxbs.pages.map.widget

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.ConstraintSet
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.config.login.rememberLoginDialogState
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.clickableSingle
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.components.utils.compose.getWindowScreenSize
import com.cyxbs.components.utils.extensions.ImageFromUrlCompose
import com.cyxbs.components.view.ui.LocalBottomSheetScope
import com.cyxbs.pages.map.model.bean.PlaceDetails
import com.cyxbs.pages.map.ui.UploadPhotoDialog
import com.cyxbs.pages.map.ui.UploadPhotoResult
import com.cyxbs.pages.map.ui.UploadingPhotoProgressDialog
import com.cyxbs.pages.map.util.clickAnimation
import com.cyxbs.pages.map.viewmodel.MapComposeViewModel
import cyxbsmobile.cyxbs_pages.map.generated.resources.Res
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_detail_more
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_like
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_no_like
import cyxbsmobile.cyxbs_pages.map.generated.resources.map_ic_share
import org.jetbrains.compose.resources.painterResource

/**
 * @Desc : 底部抽屉展示地点详细信息
 * @Author : zzx
 * @Date : 2025/11/26 13:25
 */

/**
 * 地点详情 bottomSheet 的内层内容（不含 BottomSheetCompose 外壳）。
 *
 * 由 [com.cyxbs.pages.map.ui.PlaceDetailNavEntry] 作为 NavEntry 内容使用（横竖屏统一）。
 * draggable 通过 [LocalBottomSheetScope] 获取。
 */
@Composable
fun PlaceDetailBottomSheetContent() {
  val viewmodel = viewModel(MapComposeViewModel::class)
  val bottomSheetScope = LocalBottomSheetScope.current
  viewmodel.placeDetails.value?.let { placeDetails ->
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
    ConstraintLayout(
      constraintSet = createConstraintSet(),
      modifier = modifier
        .then(bottomSheetScope.bottomSheetDraggable())
        .shadow(
          elevation = 10.dp,
          shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        )
        .background(LocalAppColors.current.topBg)
        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        .padding(start = 16.dp, end = 16.dp)
        .navigationBarsPadding(),
      animateChangesSpec = spring(
        stiffness = Spring.StiffnessMediumLow
      )
    ) {
      ShapeTipCompose(modifier = Modifier.layoutId(Element.ShapeTip), placeDetails)
      PlaceTitleCompose(modifier = Modifier.layoutId(Element.PlaceTitle), placeDetails)
      PlaceAttributeListCompose(
        modifier = Modifier.layoutId(Element.PlaceAttributeList),
        placeDetails
      )
      PlaceFavoriteCompose(modifier = Modifier.layoutId(Element.PlaceFavorite), placeDetails)
      PlaceNavigationCompose(
        modifier = Modifier.layoutId(Element.PlaceNavigation),
        placeDetails
      )
      DetailTextCompose(modifier = Modifier.layoutId(Element.DetailText), placeDetails)
      DetailMoreTextCompose(modifier = Modifier.layoutId(Element.DetailMoreText), placeDetails)
      ImageBannerCompose(modifier = Modifier.layoutId(Element.ImageBanner), placeDetails)
      DetailShareCompose(modifier = Modifier.layoutId(Element.DetailShare), placeDetails)
      DetailAboutTextCompose(
        modifier = Modifier.layoutId(Element.DetailAboutText),
        placeDetails
      )
      DetailAboutListCompose(
        modifier = Modifier.layoutId(Element.DetailAboutList),
        placeDetails
      )
    }
  }
}

@Composable
private fun ShapeTipCompose(modifier: Modifier = Modifier, placeDetails: PlaceDetails) {
  Box(
    modifier = modifier
      .width(40.dp)
      .height(8.dp)
      .background(
        color = 0xFFE2EDFB.dark(0xFF000000),
        shape = RoundedCornerShape(6.dp)
      )
  )
}

@Composable
private fun PlaceTitleCompose(modifier: Modifier = Modifier, placeDetails: PlaceDetails) {
  Text(
    modifier = modifier
      .width(300.dp)
      .padding(end = 15.dp)
      .basicMarquee(iterations = Int.MAX_VALUE),
    text = placeDetails.placeName,
    fontWeight = FontWeight.Bold,
    fontSize = 23.sp,
    color = LocalAppColors.current.tvLv2,
    maxLines = 1
  )
}

@Composable
private fun PlaceAttributeListCompose(modifier: Modifier = Modifier, placeDetails: PlaceDetails) {
  placeDetails.placeAttribute?.let { placeAttribute ->
    LazyRow(
      modifier = modifier
    ) {
      items(
        count = placeAttribute.size,
        key = { id -> placeAttribute[id] }
      ) { index ->
        val item = placeAttribute[index]
        val textColor = 0XFF778AA9.dark(0XFFA1A1A2)
        Text(
          modifier = modifier
            .padding(top = 10.dp, bottom = 8.dp, end = 12.dp)
            .clickAnimation()
            .border(
              width = 1.dp,
              color = textColor,
              shape = RoundedCornerShape(200.dp)
            )
            .padding(top = 3.dp, bottom = 3.dp, start = 7.dp, end = 7.dp),
          text = item,
          textAlign = TextAlign.Center,
          color = textColor,
          fontSize = 13.sp
        )
      }
    }
  }
}

@Composable
private fun PlaceFavoriteCompose(modifier: Modifier = Modifier, placeDetails: PlaceDetails) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  val isFavorite = remember { mutableStateOf(false) }
  val loginDialogState = rememberLoginDialogState()
  Image(
    modifier = modifier
      .clickableSingle {
        loginDialogState.doIfLogin(
          function = "收藏"
        ) {
          if (isFavorite.value) {
            viewmodel.deleteCollect(viewmodel.placeDetailsId.value)
          } else {
            viewmodel.addCollect(viewmodel.placeDetailsId.value)
          }
        }
      }
      .size(40.dp)
      .padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
    painter = painterResource(
      if (!isFavorite.value) Res.drawable.map_ic_no_like
      else Res.drawable.map_ic_like
    ),
    contentDescription = null
  )
  LaunchedEffect(viewmodel.placeDetailsId.value, viewmodel.collectListState.size) {
    var isFind = false
    viewmodel.collectListState.forEach {
      if (it == viewmodel.placeDetailsId.value) {
        isFind = true
      }
    }
    isFavorite.value = isFind
  }
}

@Composable
private fun PlaceNavigationCompose(modifier: Modifier = Modifier, placeDetails: PlaceDetails) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  Box(
    modifier = modifier
      .width(80.dp)
      .height(30.dp)
      .clip(RoundedCornerShape(100.dp))
      .background(Color(0XFF4841E2))
      .clickableNoIndicator {
        viewmodel.jumpToNavigation("重庆邮电大学" + placeDetails.placeName)
      },
  ) {
    Text(
      modifier = Modifier.align(Alignment.Center),
      text = "导航",
      color = Color.White,
      fontSize = 16.sp
    )
  }
}

@Composable
private fun DetailTextCompose(modifier: Modifier = Modifier, placeDetails: PlaceDetails) {
  Text(
    modifier = modifier,
    text = "详情",
    color = LocalAppColors.current.tvLv2,
    fontSize = 17.sp
  )
}

@Composable
private fun DetailMoreTextCompose(modifier: Modifier = Modifier, placeDetails: PlaceDetails) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  Row(
    modifier = modifier
      .clickableSingle {
        viewmodel.mapPagerState.value = 1
      },
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = "查看更多",
      fontSize = 12.sp,
      color = 0XFFABBBD7.dark(0XFF5A5A5A)
    )
    Spacer(modifier = Modifier.width(10.dp))
    Image(
      modifier = Modifier.width(5.dp).height(11.dp),
      painter = painterResource(Res.drawable.map_ic_detail_more),
      contentDescription = null
    )
  }
}

@Composable
private fun ImageBannerCompose(modifier: Modifier = Modifier, placeDetails: PlaceDetails) {
  // 这个Box主要用于images为空时的占位，BannerCompose内部实现了Box
  Box(
    modifier = modifier.fillMaxWidth().height(180.dp)
  ) {
    placeDetails.images?.let { images ->
      val bannerPagerState = rememberBannerPagerState(
        pageCount = images.size,
        isScrollInfinite = images.size >= 3
      )
      BannerCompose(
        pageCount = images.size,
        pagerState = bannerPagerState,
        modifier = Modifier.fillMaxSize(),
        isAutoScroll = true,
        scrollTime = 3000L,
        scrollDuration = 2000,
        contentPadding = PaddingValues(horizontal = 20.dp),
        pageSpacing = 4.dp
      ) { index, virtualIndex ->
        ImageFromUrlCompose(
          url = images[index],
          modifier = Modifier
            .bannerTransition(pagerState = bannerPagerState, virtualIndex)
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
        )
      }
      BannerIndicatorCompose(
        pagerState = bannerPagerState,
        count = images.size,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 16.dp),
        radius = 2.5.dp,
        selectedWidth = 20.dp,
        space = 20.dp,
        shadow = 2.dp
      )
    }
  }
}

@Composable
private fun DetailShareCompose(modifier: Modifier = Modifier, placeDetails: PlaceDetails) {
  val viewmodel = viewModel(MapComposeViewModel::class)
  val showState = remember { mutableStateOf(false) }
  val loginDialogState = rememberLoginDialogState()
  Row(
    modifier = modifier
      .clickableSingle {
        loginDialogState.doIfLogin(
          function = "上传图片"
        ) {
          showState.value = true
        }
      }
      .padding(top = 10.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Image(
      modifier = Modifier.padding(end = 8.dp).size(16.dp),
      painter = painterResource(Res.drawable.map_ic_share),
      contentDescription = null
    )
    Text(
      text = "与大家分享你拍摄的此地点",
      fontSize = 13.sp,
      color = LocalAppColors.current.tvLv4
    )
  }
  UploadPhotoDialog(showState)
  UploadPhotoResult(viewmodel.uploadPhotoResultState)
  UploadingPhotoProgressDialog()
}

@Composable
private fun DetailAboutTextCompose(modifier: Modifier = Modifier, placeDetails: PlaceDetails) {
  placeDetails.tags?.let {
    Text(
      modifier = modifier.padding(top = 10.dp),
      text = "关于该地点",
      fontSize = 17.sp,
      color = LocalAppColors.current.tvLv2
    )
  }
}

@Composable
private fun DetailAboutListCompose(modifier: Modifier = Modifier, placeDetails: PlaceDetails) {
  placeDetails.tags?.let { tags ->
    FlowRow(
      modifier = modifier
    ) {
      tags.forEach { tag ->
        val textColor = 0XFF234780.dark(0XFFA1A1A2)
        Text(
          modifier = modifier
            .padding(bottom = 12.dp, end = 12.dp)
            .clickAnimation()
            .border(
              width = 1.dp,
              color = textColor,
              shape = RoundedCornerShape(200.dp)
            )
            .padding(top = 4.dp, bottom = 4.dp, start = 11.dp, end = 11.dp),
          text = tag,
          textAlign = TextAlign.Center,
          color = textColor,
          fontSize = 13.sp
        )
      }
    }
  }
}

@Composable
private fun createConstraintSet(): ConstraintSet {
  val windowSize = getWindowScreenSize()
  return ConstraintSet {
    PlaceDetailConstraintSet(
      scope = this,
      windowSize = windowSize
    ).createConstrain()
  }
}