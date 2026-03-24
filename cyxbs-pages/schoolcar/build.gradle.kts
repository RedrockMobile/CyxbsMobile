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
			implementation(libs.filekit.core)
		}
		androidMain.dependencies {
			implementation(libs.bundles.projectBase)
			implementation(libs.bundles.views)

			implementation(libs.moko.permissions)
			// moko-permissions将权限分开了，这是定位权限的依赖
			implementation(("dev.icerock.moko:permissions-location:0.20.1"))

			// 移除高德3d map，但是还是使用高德来定位
			// 因为在国内的定位的经纬度信息是被非线性加密的，为了使转化后的坐标和图片地图对应，所以还是得用高德定位
			// https://lbs.amap.com/api/android-location-sdk/guide/create-project/android-studio-create-project
			//implementation("com.amap.api:location:latest.integration")

			//TODO 高德3d地图等待完全移除
			implementation("com.amap.api:3dmap:latest.integration")
		}
		noWebMain.dependencies {
			implementation(libs.scale.image.viewer)
			implementation(libs.scale.sampling.decoder)
		}
	}
}
