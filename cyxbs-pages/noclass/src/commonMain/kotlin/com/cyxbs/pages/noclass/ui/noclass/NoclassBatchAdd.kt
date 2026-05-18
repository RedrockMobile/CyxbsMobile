package com.cyxbs.pages.noclass.ui.noclass

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyxbs.components.init.MainNavController
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.Res
import cyxbsmobile.cyxbs_pages.noclass.generated.resources.noclass_ic_back
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview
@Preview
@Composable
fun NoclassBatchAddPage() {
    Column(
        modifier = Modifier
            .background(Color(0xFFFEFEFE))
            .statusBarsPadding()
            .fillMaxSize()
    ) {
        TopBar1()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF1A2A4E84))
        )
        TextField()
        Spacer(modifier = Modifier.weight(1f))
        query()
    }
}
@Preview
@Composable
fun TopBar1() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.noclass_ic_back),
            contentDescription = "返回",
            modifier = Modifier
                .background(Color(0xFF15315B ))
                .size(11.dp, 19.dp)
                .padding(10.dp)
                .clickable {
                    MainNavController.popBackStack()
                }
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "批量添加",
            modifier = Modifier.padding(10.dp),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF15315B)
        )
    }
}
@Preview
@Composable
fun TextField() {
    var text by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(470.dp)
            .padding(horizontal = 16.dp)
            .padding(top = 15.dp)
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xFFFEFEFE), RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFFE6EFFC), RoundedCornerShape(12.dp))
                .verticalScroll(ScrollState(0))
                .padding(start = 13.dp, top = 13.dp, end = 6.dp, bottom = 16.dp),
            minLines = 1,
            maxLines = Int.MAX_VALUE,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = Color(0xFF2D4D80),
                textAlign = TextAlign.Start,
                lineHeight = 22.sp
            )
        )

        if (text.isEmpty()) {
            Column(
                modifier = Modifier.padding(start = 13.dp, top = 13.dp)
            ) {
                Row(modifier = Modifier.padding(top = 17.dp)) {
                    Text(
                        text = "样例输入1：",
                        fontSize = 14.sp,
                        color = Color(0xFF6B89B7)
                    )
                    Text(
                        text = "卷卷\n卷娘",
                        fontSize = 14.sp,
                        color = Color(0xFF6B89B7),
                        lineHeight = 20.sp
                    )
                }

                Row(modifier = Modifier.padding(top = 15.dp)) {
                    Text(
                        text = "样例输入2：",
                        fontSize = 14.sp,
                        color = Color(0xFF6B89B7)
                    )
                    Text(
                        text = "2024212222\n2024212777",
                        fontSize = 14.sp,
                        color = Color(0xFF6B89B7),
                        lineHeight = 20.sp
                    )
                }

                Row(modifier = Modifier.padding(top = 24.dp)) {
                    Text(
                        text = "错误输入1：",
                        fontSize = 14.sp,
                        color = Color(0xFFEFB7AB)
                    )
                    Text(
                        text = "卷卷，卷娘\n卷卷，卷娘",
                        fontSize = 14.sp,
                        color = Color(0xFFEFB7AB),
                        lineHeight = 20.sp
                    )
                }

                Row(modifier = Modifier.padding(top = 15.dp)) {
                    Text(
                        text = "错误输入2：",
                        fontSize = 14.sp,
                        color = Color(0xFFEFB7AB)
                    )
                    Text(
                        text = "卷卷\n2024212777",
                        fontSize = 14.sp,
                        color = Color(0xFFEFB7AB),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
@Preview
@Composable
fun query() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 57.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 42.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF4741E0), Color(0xFF5D5EF7))
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .clickable { /* TODO: 查询逻辑 */ },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "查询",
                fontSize = 18.sp,
                color = Color.White
            )
        }
    }
}