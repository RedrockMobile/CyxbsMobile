package com.cyxbs.pages.noclass.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cyxbs.components.config.compose.theme.LocalAppColors
import com.cyxbs.components.utils.compose.dark
import com.cyxbs.pages.noclass.bean.NoClassBatchResponseInfo
import com.cyxbs.pages.noclass.ui.NoClass.NoClassBottomButton
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.Res
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_same_name_selected
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_same_name_not_selected
import org.jetbrains.compose.resources.painterResource

/**
 * description ： TODO:没课约批量添加重名选择弹窗
 * author : 我不抽火哪儿来的烟
 * email : 3114795332qq.com
 * date : 2026/5/24 16:30
 */

/* 重名选择弹窗 — SameNameSelectionSheet*/
@Composable
fun SameNameSelectionSheet(
    repeatList: List<NoClassBatchResponseInfo.BatchStudent>,
    onDismiss: () -> Unit,
    onConfirm: (List<NoClassBatchResponseInfo.BatchStudent>) -> Unit,
) {
    val selectedStates = remember { mutableStateListOf<Boolean>().apply { addAll(repeatList.map { it.isSelected }) } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = LocalAppColors.current.middleBg
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "取消",
                        color = Color(0xFFABB5C4),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    )

                    Column(
                        modifier = Modifier.fillMaxSize().padding(top = 45.dp)
                    ) {
                        Text(
                            text = "有重名现象，请勾选",
                            color = Color(0xFF15315B).dark(Color(0xFFF0F0F2)),
                            fontSize = 18.sp,
                            modifier = Modifier.padding(start = 16.dp)
                        )

                        Spacer(Modifier.height(15.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(bottom = 15.dp)
                        ) {
                            items(repeatList.size) { index ->
                                val student = repeatList[index]
                                val isSelected = selectedStates[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedStates[index] = !selectedStates[index]
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (isSelected) Res.drawable.noclass_ic_same_name_selected
                                            else Res.drawable.noclass_ic_same_name_not_selected
                                        ),
                                        contentDescription = null,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            student.name,
                                            color = Color(0xFF2D4D80).dark(Color(0xFFF0F0F2)),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Row {
                                            Text(student.id, color = Color(0xFF7B8899), fontSize = 12.sp)
                                            Spacer(Modifier.width(8.dp))
                                            Text(student.major, color = Color(0xFF7B8899), fontSize = 12.sp, maxLines = 1)
                                        }
                                        Spacer(Modifier.height(1.dp))
                                        Text(student.classNum, color = Color(0xFF7B8899), fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(15.dp))
                    }

                    NoClassBottomButton(
                        text = "确认",
                        onClick = {
                            val selected = repeatList.filterIndexed { index, _ -> selectedStates[index] }
                            onConfirm(selected)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp),
                    )
                }
            }
        }
    }
}
