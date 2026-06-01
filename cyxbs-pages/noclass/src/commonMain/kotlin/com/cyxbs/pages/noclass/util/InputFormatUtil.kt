
package com.cyxbs.pages.noclass.util

/**
 * description ： TODO:没课约输入格式校验工具
 * author : 我不抽火哪儿来的烟
 * email : 3114795332qq.com
 * date : 2026/5/17 11:34
 */

object InputFormatUtil {
    fun isNoInput(s: String): Boolean {
        return s.trim().isEmpty()
    }

    fun isIncludePunctuate(s: String): Boolean {
        val punctuateRegex = Regex("\\p{P}")
        val normalSymbol =
            Regex("[`~!@#$^&*()=|{}':;,\\[\\].·<>《》/?！￥…（）—【】‘；：”“。，、？+-/ ]")
        return punctuateRegex.containsMatchIn(s) && normalSymbol.containsMatchIn(s)
    }

    fun isNumbersSequence(s: String): Boolean {
        return s.all { it.isDigit() }
    }

    fun isChineseCharacters(s: String): Boolean {
        val chineseRegex = Regex("^[\\u4e00-\\u9fa5]+([·.]?[\\u4e00-\\u9fa5]+)+$")
        return chineseRegex.matches(s)
    }

    /**
     * @return 0=未知, 1=纯数字序列, 2=中文序列
     */
    fun isWhatType(s: String): Int {
        return if (!isNumbersSequence(s)) {
            if (!isChineseCharacters(s)) 0 else 2
        } else 1
    }
}
