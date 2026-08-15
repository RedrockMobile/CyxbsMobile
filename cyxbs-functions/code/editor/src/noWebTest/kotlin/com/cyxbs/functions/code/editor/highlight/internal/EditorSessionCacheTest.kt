package com.cyxbs.functions.code.editor.highlight.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorSessionCacheTest {

  @Test
  fun `相同文件与源码复用完整会话和高亮状态`() {
    val cache = EditorSessionCache(
      capacity = 20,
      initialFilePath = "main.js",
      initialSource = "let",
      initialSession = "main-session",
    )
    cache.markHighlighted(filePath = "main.js", source = "let")

    val session = cache.activate(filePath = "main.js", source = "let") { "replacement" }

    assertEquals("main-session", session)
    assertTrue(cache.hasHighlights(filePath = "main.js", source = "let"))
  }

  @Test
  fun `外部源码变化后重建同路径会话`() {
    val cache = EditorSessionCache(2, "main.js", "let", "old-session")
    cache.markHighlighted(filePath = "main.js", source = "let")

    val session = cache.activate(filePath = "main.js", source = "const") { "new-session" }

    assertEquals("new-session", session)
    assertFalse(cache.hasHighlights(filePath = "main.js", source = "const"))
  }

  @Test
  fun `编辑后的源码随会话保存并继续复用`() {
    val cache = EditorSessionCache(2, "main.js", "let", "main-session")

    cache.updateSource(filePath = "main.js", source = "let value = 1")
    cache.activate(filePath = "other.js", source = "export {}") { "other-session" }

    assertEquals(
      "main-session",
      cache.activate(filePath = "main.js", source = "let value = 1") { "replacement" },
    )
  }

  @Test
  fun `超过容量时淘汰最久未访问会话`() {
    var recreated = false
    val cache = EditorSessionCache(2, "first.js", "first", "first-session")
    cache.activate("second.js", "second") { "second-session" }
    cache.activate("first.js", "first") { "replacement" }
    cache.activate("third.js", "third") { "third-session" }

    val second = cache.activate("second.js", "second") {
      recreated = true
      "new-second-session"
    }

    assertTrue(recreated)
    assertEquals("new-second-session", second)
  }

  @Test
  fun `容量为零时只保留当前会话`() {
    var mainRecreated = false
    val cache = EditorSessionCache(0, "main.js", "main", "main-session")

    cache.activate("other.js", "other") { "other-session" }
    cache.activate("main.js", "main") {
      mainRecreated = true
      "new-main-session"
    }

    assertTrue(mainRecreated)
  }
}
