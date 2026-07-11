plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNavigation() // navigation 跳转

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsPages.course.view) // 装饰物工厂返回类型 CoursePageDecoration/AbstractCourseFrame
    }
  }
}
