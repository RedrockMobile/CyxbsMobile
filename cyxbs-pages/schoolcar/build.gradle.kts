plugins {
	id("manager.lib")
	id("kmp.compose")
}

useNetwork() // 网络请求
useKtProvider() // api 模块服务提供
useRoom(rxjava = true)

kotlin {
	sourceSets {
		commonMain.dependencies {
			subprojects.forEach { implementation(it) }
      implementation(projects.cyxbsComponents.view)
			implementation(projects.cyxbsComponents.base)
			implementation(projects.cyxbsComponents.utils)
			implementation(projects.cyxbsComponents.config)
			implementation(libs.okio)
		}
		androidMain.dependencies {
			implementation(libs.bundles.projectBase)
			implementation(libs.bundles.views)

			// https://lbs.amap.com/api/android-location-sdk/guide/create-project/android-studio-create-project
			implementation("com.amap.api:3dmap:latest.integration")
		}
	}
}
