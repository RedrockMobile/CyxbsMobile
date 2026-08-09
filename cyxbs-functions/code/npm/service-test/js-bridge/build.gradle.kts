plugins {
  id("manager.npmJsApiBridge")
  alias(libs.plugins.kotlinSerialization)
}

useNpmJsService()
