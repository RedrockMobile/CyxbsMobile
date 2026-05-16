@file:Suppress("UnstableApiUsage")

// 如果 build 窗口乱码，去 顶部栏 - Help - Edit Custom VM Options 里面添加 -Dfile.encoding=UTF-8，然后重启 AS
// 制作网址：http://patorjk.com/software/taag/
val redrock = """
  
   _______                   __  _______                 sh      __
  |       \                 |  \|       \                     |  \      
  | ▓▓▓▓▓▓▓\  ______    ____| ▓▓| ▓▓▓▓▓▓▓\  ______    _______ | ▓▓   __ 
  | ▓▓__| ▓▓ /      \  /      ▓▓| ▓▓__| ▓▓ /      \  /       \| ▓▓  /  \
  | ▓▓    ▓▓|  ▓▓▓▓▓▓\|  ▓▓▓▓▓▓▓| ▓▓    ▓▓|  ▓▓▓▓▓▓\|  ▓▓▓▓▓▓▓| ▓▓_/  ▓▓
  | ▓▓▓▓▓▓▓\| ▓▓    ▓▓| ▓▓  | ▓▓| ▓▓▓▓▓▓▓\| ▓▓  | ▓▓| ▓▓      | ▓▓   ▓▓ 
  | ▓▓  | ▓▓| ▓▓▓▓▓▓▓▓| ▓▓__| ▓▓| ▓▓  | ▓▓| ▓▓__/ ▓▓| ▓▓_____ | ▓▓▓▓▓▓\ 
  | ▓▓  | ▓▓ \▓▓     \ \▓▓    ▓▓| ▓▓  | ▓▓ \▓▓    ▓▓ \▓▓     \| ▓▓  \▓▓\
   \▓▓   \▓▓  \▓▓▓▓▓▓▓  \▓▓▓▓▓▓▓ \▓▓   \▓▓  \▓▓▓▓▓▓   \▓▓▓▓▓▓▓ \▓▓   \▓▓

""".trimIndent()
println(redrock)

rootProject.name = "CyxbsMobile"
// 开启模块的简化依赖方式，例如：module.course.api.course
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

/*
* 这里每次新建模块都会 include，把它们删掉，因为已经默认 include 了
*
* 新建模块直接创建好模块目录，并添加 build.gradle.kts 文件，sync 后会自动引导添加
* */

pluginManagement {
  includeBuild("build-logic")
  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
    mavenLocal() // 本地仓库，位置在 用户名/.m2/ 下
  }
}
plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_PROJECT) // wasmJs 会单独声明仓库，这里需要放开限制
  repositories {
    google()
    mavenCentral() // 优先 MavenCentral，一是：github CI 下不了 aliyun 依赖；二是：开 VPN 访问 aliyun 反而变慢了
    maven("https://central.sonatype.com/repository/maven-snapshots/") // mavenCentral 快照仓库
    maven("https://jitpack.io")
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/google")
    mavenLocal() // 本地仓库，位置在 用户名/.m2/ 下
  }
}

// 测试使用，排除掉不需要的模块，记得还原！！！
val excludeList = setOf<String>(
)

fun includeModule(topName: String, file: File) {
  if (!file.resolve("settings.gradle.kts").exists() && !excludeList.contains(file.name)) {
    if (file.resolve("build.gradle.kts").exists()) {
      var path = ""
      var nowFile = file
      while (nowFile.name != topName) {
        path = ":${nowFile.name}$path"
        nowFile = nowFile.parentFile
      }
      path = "${topName}$path"
      println("include($path)")
      include(path)
    }
    // 递归寻找所有子模块
    file.listFiles()?.filter {
      it.name != "src" // 去掉 src 文件夹
          && it.name != "build"
          && it.name != "iosApp"
          && it.name != "gradle"
          && !it.name.startsWith(".")
    }?.forEach {
      includeModule(topName, it)
    }
  }
}

rootDir.listFiles()!!.filter { it.isDirectory }.forEach {
  includeModule(it.name, it)
}

/**
 * 每次新建模块会自动添加 include()，请删除掉，因为上面会自动读取
 */
