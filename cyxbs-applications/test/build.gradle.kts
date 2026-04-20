plugins {
  id("manager.app")
  alias(libs.plugins.vasdolly) // 腾讯打包插件 https://github.com/Tencent/VasDolly
}

useKtProvider()

// 测试使用，设置 test 暂时不依赖的模块
val excludeList = mutableListOf<String>(

)

kotlin {
  sourceSets {
    commonMain.dependencies {
      // 根 gradle 中包含的所有子模块
      project.rootProject.subprojects.filter {
        it.name !in excludeList
            && it != project
            && it.name != "debug" // lib_debug 单独依赖
            && !it.path.contains("cyxbs-applications")
            && !it.path.contains("cyxbs-compiler")
            && !it.name.startsWith("cyxbs-")
      }.forEach {
        api(it)
      }
    }
    androidMain.dependencies {
      implementation(libs.bundles.projectBase)
    }
    desktopMain.dependencies {
      implementation(libs.filekit.core)
    }
  }
}

android {
  signingConfigs {
    create("config") {
      keyAlias = "androiddebugkey"
      keyPassword = "android"
      storePassword = "android"
      storeFile = projectDir.resolve("debug.keystore") // 使用官方的默认密钥
    }
  }

  buildTypes {
    release {
      signingConfig = signingConfigs.getByName("config")
    }
    debug {
      signingConfig = signingConfigs.getByName("config")
    }
  }
}

// channel 闭包，这是腾讯的多渠道打包，输出文件在 test 模块的 build/chanel 文件夹下
// ./gradlew channelRelease
channel {
  //指定渠道文件
  channelFile = rootDir.resolve("build-logic").resolve("channel.txt")
  //多渠道包的输出目录，默认为new File(project.buildDir,"channel")
  outputDir = project.layout.buildDirectory.get().asFile.resolve("channel")
  //多渠道包的命名规则，默认为：${appName}-${versionName}-${versionCode}-${flavorName}-${buildType}-${buildTime}
  apkNameFormat = "掌上重邮-\${versionName}-\${flavorName}-\${buildType}-\${buildTime}"
  //快速模式：生成渠道包时不进行校验（速度可以提升10倍以上，默认为false）
  fastMode = false
  //buildTime的时间格式，默认格式：yyyyMMdd-HHmmss
  buildTimeDateFormat = "yyyyMMdd-HHmm"
  //低内存模式（仅针对V2签名，默认为false）：只把签名块、中央目录和EOCD读取到内存，不把最大头的内容块读取到内存，在手机上合成APK时，可以使用该模式
  lowMemory = false
}

// 一键打包并安装 release 包的任务 releaseAndInstall
tasks.register("buildReleaseAndInstall") {
  group = "com.tencent.vasdolly"
  dependsOn("channelRelease")
  doLast {
    channel.outputDir.listFiles().also {
      println("buildReleaseAndInstall: ${it?.joinToString("\n")}")
    }
    val apkFile = channel.outputDir.listFiles()!!.first {
      it.name.contains("official") // 找到第一个 official 的渠道包
    }
    val adb = project.localProperties["sdk.dir"].toString() + File.separator + "platform-tools" + File.separator + "adb"
    val installResult = providers.exec {
      // adb install 安装
      commandLine(
        adb, "install", "-r",
        apkFile
      )
      isIgnoreExitValue = true
    }.result.get()
    if (installResult.exitValue == 0) {
      println("applicationId = " + android.defaultConfig.applicationId)
      providers.exec {
        // adb shell am start 打开 app
        commandLine(
          adb, "shell", "monkey",
          "-c", "android.intent.category.LAUNCHER",
          "-p", android.defaultConfig.applicationId,
          1
        )
        isIgnoreExitValue = true
      }.result.get()
    }
    if (apkFile.name.endsWith(".apk")) {
      // 改名为 .Apk 后缀，方便发到 QQ
      apkFile.renameTo(apkFile.parentFile.resolve(apkFile.name.replace(".apk", ".Apk")))
    }
  }
}

tasks.all {
  if (name == "channelRelease" || name == "wasmJsBrowserDevelopmentRun") {
    // 抑制 channelRelease 不能缓存的报错
    notCompatibleWithConfigurationCache("suppres configuration cache")
  }
}