package com.cyxbs.functions.code.editor.highlight.internal

/**
 * 按文件保存完整编辑会话的 LRU 缓存。
 *
 * 一个缓存项同时记录会话对应的源码及最近完成高亮的源码。切换文件时复用完整会话，能够让
 * 文档、光标、撤销栈和高亮装饰一起恢复，避免先把旧文件装饰映射到新文档后再刷新产生闪烁。
 * [capacity] 表示最多保留的文件数；设为 0 时仍保留当前文件，但不会跨文件复用旧会话。
 * 本类不保证线程安全，应由编辑器所在的同一 UI 线程访问。
 */
internal class EditorSessionCache<T>(
  capacity: Int,
  initialFilePath: String,
  initialSource: String,
  initialSession: T,
) {
  private val entries = linkedMapOf(
    initialFilePath to Entry(source = initialSource, session = initialSession),
  )

  var capacity: Int = capacity
    set(value) {
      require(value >= 0) { "Editor session cache capacity must not be negative." }
      field = value
      trimToCapacity()
    }

  init {
    require(capacity >= 0) { "Editor session cache capacity must not be negative." }
  }

  /**
   * 激活与 [filePath]、[source] 匹配的会话并将其标记为最近访问。
   *
   * 同一路径的源码已被外部改写时会丢弃旧会话，通过 [create] 构造干净状态。
   */
  fun activate(filePath: String, source: String, create: () -> T): T {
    val cached = entries.remove(filePath)
    val entry = cached?.takeIf { it.source == source }
      ?: Entry(source = source, session = create())
    entries[filePath] = entry
    trimToCapacity()
    return entry.session
  }

  /** 同步当前会话的最新源码；保留旧高亮版本用于判断下一次分析是否仍可复用。 */
  fun updateSource(filePath: String, source: String) {
    entries[filePath]?.source = source
  }

  /** 记录 [filePath] 已完成 [source] 对应的高亮，源码不一致时忽略迟到结果。 */
  fun markHighlighted(filePath: String, source: String) {
    entries[filePath]
      ?.takeIf { it.source == source }
      ?.highlightedSource = source
  }

  /** 当前缓存会话是否已经携带 [source] 对应的高亮装饰。 */
  fun hasHighlights(filePath: String, source: String): Boolean {
    val entry = entries[filePath] ?: return false
    return entry.source == source && entry.highlightedSource == source
  }

  /**
   * 使所有会话的高亮版本失效，并通过 [clearSessionHighlights] 清除其现有装饰。
   *
   * 动态语言服务替换后调用，避免重新打开文件时短暂展示旧规则产生的颜色。
   */
  fun clearHighlights(clearSessionHighlights: (T) -> Unit) {
    entries.values.forEach { entry ->
      entry.highlightedSource = null
      clearSessionHighlights(entry.session)
    }
  }

  /** 从最久未访问的文件开始移除；当前文件总是最近一次激活的末项。 */
  private fun trimToCapacity() {
    val retainedCount = capacity.coerceAtLeast(1)
    while (entries.size > retainedCount) {
      entries.remove(entries.keys.first())
    }
  }

  private class Entry<T>(
    var source: String,
    val session: T,
    var highlightedSource: String? = null,
  )
}
