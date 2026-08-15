package com.cyxbs.functions.code.language.javascript

import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIcon
import com.cyxbs.functions.code.language.js.bridge.DynamicLanguageIconPath

/**
 * JavaScript 文件使用的轻量矢量图标。
 *
 * 黄色底板与深色 `JS` 字形全部使用填充路径表达，语言包无需携带图片或字体资源；文字轮廓已经
 * 在发布前展开，客户端只需要解析 pathData。
 */
internal val JavaScriptLanguageIcon = DynamicLanguageIcon(
  viewportWidth = 24F,
  viewportHeight = 24F,
  paths = listOf(
    DynamicLanguageIconPath(
      pathData = "M3 2H21C21.5523 2 22 2.44772 22 3V21C22 21.5523 21.5523 22 21 22H3C2.44772 22 2 21.5523 2 21V3C2 2.44772 2.44772 2 3 2Z",
      fillColor = "#F7DF1E",
    ),
    DynamicLanguageIconPath(
      pathData = "M7.5 7H10V15.3C10 16.4 9.5 17 8.5 17C7.7 17 7.2 16.6 6.7 15.9L5 17.4C5.8 18.5 7 19.1 8.6 19.1C11.2 19.1 12.5 17.7 12.5 15.2V7Z",
      fillColor = "#171717",
    ),
    DynamicLanguageIconPath(
      pathData = "M18.2 10C17.6 9.2 16.9 8.8 16 8.8C15.1 8.8 14.6 9.2 14.6 9.8C14.6 10.5 15.1 10.8 16.4 11.4C18.6 12.3 19.5 13.4 19.5 15.2C19.5 17.6 17.7 19.1 15.1 19.1C13.3 19.1 11.9 18.5 10.9 17.2L12.6 15.6C13.3 16.5 14.1 17 15.2 17C16.3 17 16.9 16.5 16.9 15.7C16.9 15 16.5 14.6 15.1 14C13 13.1 12.1 12 12.1 10.2C12.1 8 13.8 6.7 16.1 6.7C17.7 6.7 18.9 7.2 19.8 8.4Z",
      fillColor = "#171717",
    ),
  ),
)
