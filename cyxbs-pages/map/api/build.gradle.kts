plugins {
    id("manager.lib")
}

useNavigation() // navigation 跳转

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.cyxbsComponents.init)
        }
    }
}