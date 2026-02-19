package com.cyxbs.pages.schoolcar.widget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.utils.compose.dark
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * description ： 线路选择器
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/19 18:37
 */
@Stable
data class LineSelectorItem(
	val id: Int,
	val name: String,
	val unSelectRes: DrawableResource,
	val selectRes: DrawableResource
)

// 线路选择器
@Composable
fun LineSelectorCompose(
	modifier: Modifier = Modifier,
	list: List<LineSelectorItem>,
	selectedId: Int?,
	onClick: (LineSelectorItem) -> Unit
) {

	LazyRow(
		modifier = modifier.padding(top = 16.dp, bottom = 12.dp),
		contentPadding = PaddingValues(horizontal = 16.dp),
		horizontalArrangement = Arrangement.spacedBy(39.dp)
	) {
		items(items = list, key = { it.id }) {
			LineSelectorItemCompose(
				isSelect = it.id == selectedId,
				item = it,
				onClick = onClick
			)
		}
	}
	Spacer(modifier = Modifier.fillMaxWidth().background(0x17F2F3F8.dark(0x67676733)))
}


@Composable
fun LineSelectorItemCompose(
	isSelect: Boolean,
	item: LineSelectorItem,
	onClick: (LineSelectorItem) -> Unit,
	modifier: Modifier = Modifier
) {

	Column(
		modifier = modifier.clickable {
			onClick.invoke(item)
		},
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Image(
			painter = painterResource(if (isSelect) item.selectRes else item.unSelectRes),
			contentDescription = null
		)
		Text(
			modifier = Modifier.padding(top = 6.dp),
			text = item.name,
			fontSize = 10.sp,
			color = if (isSelect) {
				0xFF4A44E4.dark(0xFF5852FF)
			} else {
				0xFF94A6C4.dark(0xFFB1B1B2)
			}
		)
	}
}