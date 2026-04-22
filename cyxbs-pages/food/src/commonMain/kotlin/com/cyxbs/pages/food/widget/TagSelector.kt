package com.cyxbs.pages.food.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import cyxbsmobile.cyxbs_pages.food.generated.resources.Res
import cyxbsmobile.cyxbs_pages.food.generated.resources.food_ic_btn_pressed
import cyxbsmobile.cyxbs_pages.food.generated.resources.food_ic_food_main_refresh
import cyxbsmobile.cyxbs_pages.food.generated.resources.food_ic_notification_small
import org.jetbrains.compose.resources.painterResource
import kotlin.math.ceil

/**
 * description ： 美食咨询处的单选器
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2025/10/30 22:40
 */
@Stable
data class DiningTag(
	val name: String,
	var isSelected: Boolean = false
)

//选择器的单个Item
@Composable
fun SelectorItem(tag: DiningTag, onClick: (DiningTag) -> Unit,modifier: Modifier = Modifier) {
	val selectTextColor = remember { Color(0xFF4A44E4) }
	val itemBackgroundColor = 0xFFF5F6F8.dark(0xFF111111)
	Box(
		modifier = modifier
			.padding(3.dp)
			.size(74.dp, 29.dp)
			.clip(RoundedCornerShape(8.dp))
			.background(color = itemBackgroundColor)
			.clickableNoIndicator {
				onClick.invoke(tag)
			}
	) {
		//选中状态的外边框
		if (tag.isSelected) {
			Image(
				painter = painterResource(Res.drawable.food_ic_btn_pressed),
				contentDescription = null,
				contentScale = ContentScale.FillBounds,
				modifier = Modifier.matchParentSize()
			)
		}
		//文字
		Text(
			modifier = Modifier.align(Alignment.Center),
			text = tag.name,
			fontSize = 12.sp,
			color = if (tag.isSelected) {
				selectTextColor
			} else {
				LocalAppColors.current.tvLv3
			}
		)
	}

}


@Composable
fun TagSelector(
	modifier: Modifier,
	title: String,
	subTitle: String,
	tagList: List<DiningTag>,
	onSelectChange: (DiningTag) -> Unit,
	canRefresh: Boolean = false,
	onRefresh: () -> Unit = {}
) {
	val unimportanceTipColor = remember { Color(0xFFAABCD8) }
	Column(
		modifier = modifier.fillMaxWidth()
	) {
		Row(
			Modifier.padding(start = 6.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = title,
				fontSize = 14.sp,
				color = LocalAppColors.current.tvLv3
			)

			Image(
				modifier = Modifier.padding(start = 8.dp).size(8.dp, 9.dp),
				painter = painterResource(Res.drawable.food_ic_notification_small),
				contentDescription = null,
				contentScale = ContentScale.Crop
			)
			Text(
				modifier = Modifier.padding(1.dp),
				text = subTitle,
				fontSize = 10.sp,
				color = unimportanceTipColor
			)
			//用来把刷新撑到最后面
			Spacer(modifier = Modifier.weight(1f))
			if (canRefresh) {
				Image(
					modifier = Modifier.size(15.dp, 14.dp).clickableNoIndicator{
						onRefresh.invoke()
					},
					painter = painterResource(Res.drawable.food_ic_food_main_refresh),
					contentScale = ContentScale.Crop,
					contentDescription = null,
					colorFilter = ColorFilter.tint(0xFF15315B.dark(0xFFFFFFFF))
				)
			}

		}

		Spacer(Modifier.height(14.dp))
		BoxWithConstraints(Modifier.fillMaxWidth()) {
			val spacing = 10.dp
			val minCellWidth = 74.dp

			//maxWidth = columns * minCellWidth + (columns -1) * spacing 解一下方程
			val columns = maxOf(1, ((maxWidth + spacing) / (minCellWidth + spacing)).toInt())
			val rows = ceil(tagList.size.toDouble() / columns).toInt()

			val itemHeight = 35.dp
			val gridHeight = if (rows > 0) (itemHeight * rows) + (spacing * (rows - 1)) else 0.dp

			LazyVerticalGrid(
				columns = GridCells.Adaptive(minCellWidth),
				modifier = Modifier.fillMaxWidth().height(gridHeight),
				verticalArrangement = Arrangement.spacedBy(spacing),
				horizontalArrangement = Arrangement.spacedBy(spacing),
				userScrollEnabled = false,
			) {
				items(
					items = tagList,
					key = { it.name }
				) {
					SelectorItem(
						tag = it,
						onClick = onSelectChange,
						modifier = Modifier.animateItem()
					)
				}
			}
		}
	}
}