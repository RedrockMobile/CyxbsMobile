plugins {
  id("manager.lib")
  id("kmp.compose")
}

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsPages.course.api)
    }
  }
}
