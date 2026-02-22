package com.cyxbs.pages.schoolcar.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.utils.compose.dark
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.Res
import cyxbsmobile.cyxbs_pages.schoolcar.generated.resources.schoolcar_ic_car_icon_0
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * description ： 线路选择器
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/19 18:37
 */
@Stable
sealed interface LineSelectorItem {
	val id: Int

	data class LineSelectorItemLine(
		override val id: Int,
		val name: String,
		val unSelectRes: DrawableResource,
		val selectRes: DrawableResource
	) : LineSelectorItem

	object Guide : LineSelectorItem {
		override val id: Int
			get() = -1

	}
}

// 线路选择器
@Composable
fun LineSelectorCompose(
	modifier: Modifier = Modifier,
	list: List<LineSelectorItem>,
	selectedId: Int?,
	onClick: (LineSelectorItem) -> Unit
) {
	Column(modifier) {
		LazyRow(
			modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
			contentPadding = PaddingValues(horizontal = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(39.dp)
		) {
			items(items = list, key = { it.id }) {
				LineSelectorItemCompose(
					isSelect = (it is LineSelectorItem.LineSelectorItemLine) && (it.id == selectedId),
					item = it,
					onClick = onClick
				)
			}
		}
		Spacer(
			modifier = Modifier.fillMaxWidth().height(2.dp)
				.background(0xFFF2F3F8.dark(0xFF676733).copy(0.1F))
		)
	}

}


@Composable
fun LineSelectorItemCompose(
	isSelect: Boolean,
	item: LineSelectorItem,
	onClick: (LineSelectorItem) -> Unit,
	modifier: Modifier = Modifier
) {
	val textColor = if (isSelect) {
		0xFF4A44E4.dark(0xFF5852FF)
	} else {
		0xFF94A6C4.dark(0xFFB1B1B2)
	}
	val iconRes: DrawableResource
	val name: String

	when (item) {
		is LineSelectorItem.LineSelectorItemLine -> {
			iconRes = if (isSelect) item.selectRes else item.unSelectRes
			name = item.name
		}

		is LineSelectorItem.Guide -> {
			iconRes = Res.drawable.schoolcar_ic_car_icon_0
			name = "乘车指南"
		}
	}

	Column(
		modifier = modifier.clickable {
			onClick.invoke(item)
		},
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Image(
			painter = painterResource(iconRes),
			contentDescription = null
		)
		Text(
			modifier = Modifier.padding(top = 6.dp),
			text = name,
			fontSize = 10.sp,
			color = textColor,
			fontWeight = if (isSelect) FontWeight.Bold else null
		)
	}
}