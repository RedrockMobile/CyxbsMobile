package com.cyxbs.pages.schoolcar.utils

/**
 * description ： 一些对字符串的处理
 * author : HI-IR
 * email : qq2420226433@outlook.com
 * date : 2026/2/19 15:46
 */
//给每个字符之间增加换行
fun String.addNewLineBetweenChars(): String {
	return this.map { it.toString() }.joinToString("\n")
}
