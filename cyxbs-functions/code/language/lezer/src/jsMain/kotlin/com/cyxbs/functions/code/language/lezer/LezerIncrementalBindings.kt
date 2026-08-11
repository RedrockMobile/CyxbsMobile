@file:JsModule("@lezer/common")

package com.cyxbs.functions.code.language.lezer

/**
 * Lezer 增量解析可复用的语法树片段。
 *
 * 片段必须先通过 [applyChanges] 映射到新文档坐标，再传给 [LezerParser.parse]；解析完成后使用
 * [addTree] 生成下一轮缓存。该句柄只在 JavaScript 语言包内部保存，不跨 QuickJS 边界传输。
 */
@JsName("TreeFragment")
external class LezerTreeFragment {
  companion object {

    /** 将本轮完整语法树转换为下一轮可复用的片段集合。 */
    fun addTree(tree: LezerTree): Array<LezerTreeFragment>

    /**
     * 根据旧、新文档坐标中的变更区间裁剪并移动旧片段。
     *
     * [minGap] 保留 Lezer 默认值，避免为了很小的未变区域维护过多碎片。
     */
    fun applyChanges(
      fragments: Array<LezerTreeFragment>,
      changes: Array<LezerChangedRange>,
      minGap: Int = definedExternally,
    ): Array<LezerTreeFragment>
  }
}

/**
 * 一次文本修改在旧文档（A）和新文档（B）中的坐标。
 *
 * Lezer 接收结构化 JavaScript 对象，因此属性保持可写，由 Kotlin/JS 适配层创建后立即填充。
 */
external interface LezerChangedRange {
  var fromA: Int
  var toA: Int
  var fromB: Int
  var toB: Int
}
