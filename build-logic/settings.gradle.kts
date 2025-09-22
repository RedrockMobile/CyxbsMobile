@file:Suppress("UnstableApiUsage")

// 开启模块的简化依赖方式，例如：module.course.api.course
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild(".")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") // compose multiplatform
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://jitpack.io")
        mavenLocal() // 本地仓库，位置在 用户名/.m2/ 下
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral() // 优先 MavenCentral，一是：github CI 下不了 aliyun 依赖；二是：开 VPN 访问 aliyun 反而变慢了
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") // compose multiplatform
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://jitpack.io")
        mavenLocal() // 本地仓库，位置在 用户名/.m2/ 下
    }
    // 开启 versionCatalogs 功能
    versionCatalogs {
        // 名称固定写成 libs
        create("libs") {
            // 这个 libs.versions.toml 名字也必须固定，不能改成其他的
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"

// 项目全局配置
include(":config")
// 项目模块插件
include(":manager")
//其他业务插件
include(":plugin")
include(":plugin:cache")
include(":plugin:checker")
include(":plugin:kmp")

