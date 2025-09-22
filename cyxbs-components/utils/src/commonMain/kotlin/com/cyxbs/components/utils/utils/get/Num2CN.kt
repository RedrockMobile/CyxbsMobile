package com.cyxbs.components.utils.utils.get

/**
 * @author Jovines
 * @date 2019/12/5 10:42
 * description：该工具类用来将阿拉伯数字转换为中文数字表达形式
 */
object Num2CN {
    /**
     * 单位进位，中文默认为4位即（万、亿）
     */
    private var UNIT_STEP = 4
    /**
     * 单位
     */
    private var CN_UNITS = arrayOf("个", "十", "百", "千", "万", "十",
            "百", "千", "亿", "十", "百", "千", "万")
    /**
     * 汉字
     */
    private var CN_CHARS = arrayOf("零", "一", "二", "三", "四",
            "五", "六", "七", "八", "九")

    /**
     * 数值转换为中文字符串(口语化)
     * @param num
     * 需要转换的数值
     * @param isColloquial 是否口语化，例如12转换为'十二'而不是'一十二'，默认为true，进行口语化
     * @return
     */
    fun number2ChineseNumber(num: Int, isColloquial: Boolean = true): String {
        val result = convert(num, isColloquial)
        return result.joinToString("")
    }

    /**
     * 将数值转换为中文
     *
     * @param num
     * 需要转换的数值
     * @param isColloquial
     * 是否口语化。例如12转换为'十二'而不是'一十二'。
     * @return
     */
    private fun convert(num: Int, isColloquial: Boolean): List<String> {
        if (num < 10) { // 10以下直接返回对应汉字
            return listOf(CN_CHARS[num]) // ASCII2int
        }
        val chars = num.toString().toCharArray()
        if (chars.size > CN_UNITS.size) { // 超过单位表示范围的返回空
            return emptyList()
        }
        var isLastUnitStep = false // 记录上次单位进位
        val cnChars = ArrayList<String>(chars.size * 2) // 创建数组，将数字填入单位对应的位置
        for (pos in chars.indices.reversed()) { // 从低位向高位循环
            val ch = chars[pos]
            val cnChar = CN_CHARS[ch - '0'] // ascii2int 汉字
            val unitPos = chars.size - pos - 1 // 对应的单位坐标
            val cnUnit = CN_UNITS[unitPos] // 单位
            val isZero = ch == '0' // 是否为0
            val isZeroLow = pos + 1 < chars.size && chars[pos + 1] == '0' // 是否低位为0
            val isUnitStep = unitPos >= UNIT_STEP && unitPos % UNIT_STEP == 0 // 当前位是否需要单位进位
            if (isUnitStep && isLastUnitStep) { // 去除相邻的上一个单位进位
                val size = cnChars.size
                cnChars.removeAt(size - 1)
                if (CN_CHARS[0] != cnChars[size - 2]) { // 补0
                    cnChars.add(CN_CHARS[0])
                }
            }
            if (isUnitStep || !isZero) { // 单位进位(万、亿)，或者非0时加上单位
                cnChars.add(cnUnit)
                isLastUnitStep = isUnitStep
            }
            if (isZero && (isZeroLow || isUnitStep)) { // 当前位为0低位为0，或者当前位为0并且为单位进位时进行省略
                continue
            }
            cnChars.add(cnChar)
            isLastUnitStep = false
        }
        cnChars.reverse()
        // 清除最后一位的0
        val chSize = cnChars.size
        val chEnd = cnChars[chSize - 1]
        if (CN_CHARS[0] == chEnd || CN_UNITS[0] == chEnd) {
            cnChars.removeAt(chSize - 1)
        }
        // 口语化处理
        if (isColloquial) {
            val chFirst = cnChars[0]
            val chSecond = cnChars[1]
            if (chFirst == CN_CHARS[1] && chSecond.startsWith(CN_UNITS[1])) { // 是否以'一'开头，紧跟'十'
                cnChars.removeAt(0)
            }
        }
        return cnChars
    }
}