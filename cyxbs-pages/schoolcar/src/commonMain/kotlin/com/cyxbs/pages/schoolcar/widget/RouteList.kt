package com.cyxbs.pages.schoolcar.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.schoolcar.bean.CarLine
import com.cyxbs.pages.schoolcar.bean.CarStation
import com.cyxbs.pages.schoolcar.utils.addNewLineBetweenChars
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_site_line
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_site_line_first
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_site_line_last
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_site_line_select
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * description ： 线路表Compose
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/19 15:10
 */
enum class RouteListIcon(val res: DrawableResource) {
	Start(Res.drawable.schoolcar_ic_car_site_line_first),
	Common(Res.drawable.schoolcar_ic_car_site_line),
	Last(Res.drawable.schoolcar_ic_car_site_line_last),
	Select(Res.drawable.schoolcar_ic_car_site_line_select)
}


// siteId是当前站点的Id，line是当前显示的线路
// siteId为-1的时候表示此时为线路显示模式，不标注当前站点
@Composable
fun RouteListCompose(modifier: Modifier = Modifier, siteId: Int = -1, line: CarLine) {
	val selectIndex = remember(siteId, line.stations) {
		line.stations.indexOfFirst { it.id == siteId }
	}
	val lineLen = line.stations.size
	val lineId = line.id
	LazyRow(
		modifier = modifier.height(180.dp),
		contentPadding = PaddingValues(16.dp),
	) {
		itemsIndexed(items = line.stations, key = { index, item ->
			item.id
		}) { index, item ->
			val textColor = getTextColorByLine(index, selectIndex, lineId)

			// tips：这里顺序不能换，一定是先起点，再终点，再选中的站点，最后再是common
			when (index) {
				0 -> RouteListItem(icon = RouteListIcon.Start, item, textColor)
				lineLen - 1 -> RouteListItem(icon = RouteListIcon.Last, item, textColor)
				selectIndex -> RouteListItem(icon = RouteListIcon.Select, item, textColor)
				else -> RouteListItem(icon = RouteListIcon.Common, item, textColor)
			}
		}
	}
}

// 根据线路id生成对应的颜色
@Composable
fun getTextColorByLine(currentIndex: Int, selectIndex: Int, lineId: Int): Color {
	// 如果当前的item不是所选中的item，直接返回默认颜色
	if (currentIndex != selectIndex) {
		return 0xFF2A4E84.dark(0xFFB1B1B2)
	}

	// 如果是选中的item，返回line主题色
	return when (lineId + 1) {
		1 -> 0xFFFF45B9.dark(0xFFFF45B9)
		2 -> 0xFFFDA962.dark(0xFFFDA962)
		3 -> 0xFF6FCAFC.dark(0xFF6FCAFC)
		4 -> 0xFF80E7C8.dark(0xFF80E7C8)
		else -> 0xFF2A4E84.dark(0xF0F0F2B2)
	}
}

@Composable
fun RouteListItem(icon: RouteListIcon, site: CarStation, textColor: Color) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Image(
			modifier = Modifier.size(
				if (icon == RouteListIcon.Start || icon == RouteListIcon.Last) 30.dp else 46.dp,
				30.dp
			),
			painter = painterResource(icon.res),
			contentDescription = null
		)
		Text(
			modifier = Modifier.padding(top = 5.dp),
			text = site.name.addNewLineBetweenChars(),
			color = textColor,
			fontSize = 12.sp
		)
	}
}

