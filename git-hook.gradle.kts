/*
* 操控 Git 钩子文件的脚本
* 使用教程：https://git-scm.com/book/zh/v2/%E8%87%AA%E5%AE%9A%E4%B9%89-Git-Git-%E9%92%A9%E5%AD%90
*
* 由于钩子文件应该放到 .git/hooks 文件下，并且要去掉 .sh 后缀，
* 但为了以后好修改，所以将钩子文件以 sh 文件的形式放在了根项目的 hooks 文件夹下，
* 通过该脚本将文件移动到 .git/hooks 文件夹下
*
* 注意：
* 1、该脚本应该是在根目录下的 build.gradle.kts 中使用
* 2、部分系统可能存在权限问题，无法删除文件
* */

/*
* 我也不知道为什么这个文件会提示黄条：This script caused build configguration to fail, run ......
* 但不影响正常运行，所以就不要管它
* */

val hooksFile = rootDir.absoluteFile.resolve("hooks")
val gitFilr = rootDir.absoluteFile.resolve(".git")
val gitHookFile = gitFilr.resolve("hooks")

fun moveHookFile(action: ((File) -> Unit)? = null) {
  hooksFile.listFiles()?.forEach { file ->
    val newFile = gitHookFile.resolve(file.name.substringBeforeLast(".sh"))
    if (newFile.exists()) {
      if (!newFile.delete()) {
        println("${newFile.absolutePath} 删除失败\n大概率是 gradle 脚本没有权限删除旧的 hook 文件\n请在 git-hook.gradle.kts 中添加权限设置！")
        return
      }
    }
    copy {
      from(file)
      into(newFile.parentFile)
      rename { it.substringBeforeLast(".sh") }
    }
    action?.invoke(file)
  }
}

if (gitFilr.isDirectory) { // 如果使用了 worktree 时则 .git 为普通文件而非文件夹
  // 将 根目录下的 hooks 移动到 .git/hooks 的 task
  // 已生成了 group 为 hook，名字叫 git-hook-move 的任务
  val task = tasks.register("git-hook-move") {
    group = "cyxbs"
    inputs.dir(hooksFile) // gradle 任务缓存设置
    outputs.dir(gitHookFile) // gradle 任务缓存设置
    doFirst {
      println("正在移动 git 钩子文件：")
      moveHookFile {
        println("${it.name} 已复制到 .git/hooks 文件下")
      }
      println("git 钩子文件移动完毕")
    }
  }
  // 依赖于刷新 gradle 的 task
  tasks.getByName("prepareKotlinBuildScriptModel").dependsOn(task)
}
