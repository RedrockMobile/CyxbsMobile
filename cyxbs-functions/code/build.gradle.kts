plugins {
  // 应用壳会依赖所有 Gradle 子项目；父模块需保留空的 KMP variant，实际编辑器实现位于 :code:editor。
  id("manager.lib")
}
