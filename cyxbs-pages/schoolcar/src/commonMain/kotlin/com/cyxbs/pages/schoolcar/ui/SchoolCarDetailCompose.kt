package com.cyxbs.pages.schoolcar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.schoolcar.bean.CarLine
import com.cyxbs.pages.schoolcar.bean.CarLineJson
import com.cyxbs.pages.schoolcar.widget.ErrorInfoCompose
import com.cyxbs.pages.schoolcar.widget.LineTypeCompose
import com.cyxbs.pages.schoolcar.widget.RouteListIcon
import com.cyxbs.pages.schoolcar.widget.RouteListItem
import com.cyxbs.pages.schoolcar.widget.RuntimeCompose
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_1
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_2
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_3
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_4
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_map_back
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * description ： 详情页的一些组件
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/20 18:27
 */
@Composable
fun SchoolCarTopbarCompose(modifier: Modifier = Modifier, onBack: () -> Unit) {
	TopAppBar(
		modifier = modifier.height(70.dp)
			.clip(RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp)),
		backgroundColor = Color.Transparent,
		elevation = 0.dp
	) {
		Box(
			modifier = Modifier.fillMaxWidth()
		) {
			Image(
				modifier = Modifier.padding(start = 16.dp).align(Alignment.CenterStart)
					.clickableNoIndicator {
						onBack()
					},
				painter = painterResource(Res.drawable.schoolcar_ic_map_back),
				contentDescription = "back",
				contentScale = ContentScale.Crop,
			)

			Text(
				modifier = Modifier.padding(start = 37.dp).align(Alignment.CenterStart),
				text = "乘车指南",
				fontWeight = FontWeight.Bold,
				color = LocalAppColors.current.tvLv3,
				fontSize = 22.sp,
				textAlign = TextAlign.Center
			)
		}
	}
}

@Composable
fun LineInfoCompose(carLineJson: CarLineJson?) {
	val backgroundColor = 0xFFF2F3F8.dark(0xFF000000)
	if (carLineJson == null) {
		Box(Modifier.fillMaxSize()) {
			ErrorInfoCompose(Modifier.align(Alignment.Center))
		}
	} else {
		LazyColumn {
			items(carLineJson.lines, key = { it -> it.id }) {
				LineInfoItemCompose(modifier = Modifier.background(backgroundColor), line = it)
				Spacer(Modifier.height(8.dp).background(LocalAppColors.current.topBg))
			}
		}
	}
}
@Composable
fun LineInfoItemCompose(modifier: Modifier = Modifier, line: CarLine) {
	val textColor = 0xFF2A4E84.dark(0xFFB1B1B2)
	val lineLen = line.stations.size
	val scrollState = rememberScrollState()

	Column(modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
		) {
			LineFlag(line = line)
			Spacer(Modifier.weight(1f))
			Column {
				RuntimeCompose(line.runTime)
				LineTypeCompose(line.sendType, line.runType, Modifier.padding(top = 8.dp))
			}
		}
		// 因为每个条线的站点并不多，所以这里直接用Row了
		Row(
			modifier = Modifier
				.fillMaxWidth()
				// 给一个固定最小高度，防止跳动
				.heightIn(min = 130.dp)
				.horizontalScroll(scrollState)
				.padding(horizontal = 16.dp),
		) {
			line.stations.forEachIndexed { index, item ->
				when (index) {
					0 -> RouteListItem(icon = RouteListIcon.Start, item, textColor)
					lineLen - 1 -> RouteListItem(icon = RouteListIcon.Last, item, textColor)
					else -> RouteListItem(icon = RouteListIcon.Common, item, textColor)
				}
			}
		}
		Spacer(Modifier.height(10.dp))
	}
}

//线路标识
@Composable
fun LineFlag(line: CarLine) {
	// 线路标识
	Row(
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Image(
			painter = painterResource(getLineIcon(line.id)),
			contentDescription = null
		)
		Text(
			text = line.name,
			fontWeight = FontWeight.Bold,
			color = LocalAppColors.current.tvLv3,
			fontSize = 22.sp,
			textAlign = TextAlign.Center
		)
	}
}


private fun getLineIcon(id: Int): DrawableResource {
	// id=0 对应 一号线
	return when (id + 1) {
		1 -> Res.drawable.schoolcar_ic_car_icon_1
		2 -> Res.drawable.schoolcar_ic_car_icon_2
		3 -> Res.drawable.schoolcar_ic_car_icon_3
		4 -> Res.drawable.schoolcar_ic_car_icon_4
		else -> Res.drawable.schoolcar_ic_car_icon_1
	}
}
