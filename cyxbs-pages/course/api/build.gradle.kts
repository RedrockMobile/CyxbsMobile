plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNavigation() // navigation 跳转
useUnitTest() // 课程详情身份协议等 commonTest 在 Android Host 上执行

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.view)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
    androidMain.dependencies {
      // ICourseService 的旧 View 事务接口仅存在于 androidMain。
      implementation(projects.cyxbsPages.affair.api)
      implementation(libs.androidx.appcompat)
      implementation(libs.rxjava)
    }
  }
}
