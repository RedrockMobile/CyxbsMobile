package com.cyxbs.pages.food.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.clickableNoIndicator
import com.cyxbs.components.utils.compose.dark
import cyxbsmobile.cyxbs_pages.food.generated.resources.Res
import cyxbsmobile.cyxbs_pages.food.generated.resources.food_ic_btn_pressed
import cyxbsmobile.cyxbs_pages.food.generated.resources.food_ic_notification_small
import org.jetbrains.compose.resources.painterResource

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
fun SelectorItem(tag: DiningTag, onClick: (DiningTag) -> Unit) {
	val selectTextColor = remember { Color(0xFF4A44E4) }
	val itemBackgroundColor = 0xFFF5F6F8.dark(0xFF111111)
	Box(
		modifier = Modifier
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
	onSelectChange: (DiningTag) -> Unit
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
		}

		Spacer(Modifier.height(14.dp))

		LazyVerticalGrid(
			columns = GridCells.Adaptive(74.dp),
			modifier = Modifier.fillMaxWidth(),
			verticalArrangement = Arrangement.spacedBy(10.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			userScrollEnabled = false,
		) {
			items(
				items = tagList,
				key = {
					it.name
				}) {
				SelectorItem(tag = it, onClick = onSelectChange)
			}
		}
	}
}