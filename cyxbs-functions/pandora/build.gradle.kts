plugins {
  id("manager.lib")
}

useKtProvider()

kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(projects.cyxbsComponents.init)
      implementation(projects.cyxbsComponents.base)
      implementation(projects.cyxbsComponents.utils)
      implementation(projects.cyxbsComponents.config)
      implementation(projects.cyxbsComponents.account.api)
    }
    androidMain.dependencies {
      /**
       * 很牛逼的 debug 检测工具，debug 模式下摇一摇手机或者按三次手机中间顶部区域触发
       *
       * 支持功能：
       * 1、网络请求监听
       * 2、View 树查看（还可以随意移动 View 的位置）
       * 3、崩溃记录
       * 4、SP 文件查看
       * 5、Room 数据查看
       * 6、更多请看：https://www.wanandroid.com/blog/show/2526
       *
       * 注意：
       * 1、摇一摇手机后会出现一个小条，那个小条是可以左右滑动的滑动后有更多功能
       * 2、pandora-plugin 插件使用了会在 gradle 8.0 移除的 transform API，我的建议是你们 fork 下仓库，
       *   然后改了发一个 jitpack 依赖（发这个依赖很简单，不需要账号）
       * 3、Pandora 已停止维护，可以使用 doKit 进行代替
       * (doKit 在之前引入过，但因为不兼容AGP版本所以后面删除了，如果想再次引入，可参考 23/7/16 时“移除 doKit” 的 commit)
       */
      implementation(libs.pandora)
    }
  }
}

