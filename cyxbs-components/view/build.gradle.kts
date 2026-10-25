plugins {
  id("manager.lib")
  id("kmp.compose")
}

useNavigation()
useKtProvider()

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
    }
    commonTest.dependencies {
      implementation(kotlin("test"))
    }
    androidMain.dependencies {
      implementation(libs.androidx.appcompat)
      implementation(libs.androidx.constraintlayout)
      implementation(libs.material)
    }
  }
}


