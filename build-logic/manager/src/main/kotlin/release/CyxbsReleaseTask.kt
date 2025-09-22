package release

import Config
import okhttp3.OkHttpClient
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import release.net.TaskService
import release.step.ApkDownloadStep
import release.step.ApkInstallStep
import release.step.ApkUploadStep
import release.step.NewVersionInfoCheckStep
import release.step.UploadNewVersionInfoStep
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Scanner
import java.util.concurrent.TimeUnit

/**
 * ...
 * @author RQ527 (Ran Sixiang)
 * @email 1799796122@qq.com
 * @date 2023/11/11
 * @Description: 一键发版的 task,执行任务过程无异常处理,异常都将抛到控制台。
 *
 * 使用方式（三种）：
 * 1.右侧 gradle 栏的 Run Configurations 下找到 module_app/Tasks/cyxbs/cyxbsRelease 点击执行
 * 2.或者右侧 gradle 有个搜索 task 的选项，搜索 cyxbsRelease 点击执行
 * 3.命令行执行 ./gradlew cyxbsRelease
 *
 * 记得先修改 [Config] 中的版本信息 !!!!!!!!
 */
abstract class CyxbsReleaseTask : DefaultTask() {

    private val token by lazy {
        runCatching {
            project.rootDir
                .resolve("build-logic")
                .resolve("secret")
                .resolve("release-token.txt")
                .readText()
        }.getOrNull() ?: throw IllegalStateException(
            "secret 中缺少 release-token.txt 文件，为发版请求密钥，请寻找副站或站长添加，若已丢失，请联系上一届学长")
    }

    // 发版的 token 由运维下发，只能由每届 Android 管理人持有 (移动副站或 Android 部长)
    private val okHttpClient = OkHttpClient.Builder().addInterceptor {
        it.proceed(
            it.request()
                .newBuilder()
                .header("token", token).build()
        )
    }.connectTimeout(300, TimeUnit.MINUTES)//运维cdn宽带受限上传apk较慢
        .callTimeout(300, TimeUnit.MINUTES)
        .writeTimeout(300,TimeUnit.MINUTES)
        .readTimeout(300,TimeUnit.MINUTES)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://app.redrock.team")
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttpClient)
        .build()

    private val netService = retrofit.create(TaskService::class.java)

    @get:InputFile
    abstract val getApkFile: RegularFileProperty // 由外界任务执行者设置

    @TaskAction
    fun taskAction() {
        val apk = getApkFile.get().asFile
        // 发版信息检查
        val newVersionInfoCheck = NewVersionInfoCheckStep(project, netService)
        if (!newVersionInfoCheck.execute(apk)) {
            return
        }
        // 本地 apk 安装检查
        val apkInstallStep = ApkInstallStep(project)
        if (!apkInstallStep.execute(apk)) {
            return
        }
        var apkUrl: String
        while (true) {
            // 上传 apk
            val apkUploadStep = ApkUploadStep(netService)
            apkUrl = apkUploadStep.execute(apk) ?: return
            // 下载 apk 进行效验
            val apkDownloadStep = ApkDownloadStep(project, okHttpClient)
            val apkDownloadResult = apkDownloadStep.execute(apk, apkUrl)
            if (!apkDownloadResult) {
                println()
                val sc = Scanner(System.`in`)
                println("\napk 下载或效验失败，是否重新上传? (y/n)".red())
                val nextLine = sc.nextLine()
                if (nextLine != "y") {
                    println("❌ 取消重试".red())
                    return
                }
            } else {
                break
            }
        }
        // 发布新版本信息
        UploadNewVersionInfoStep(netService).execute(apkUrl) ?: return
        println()
        println("版本已发布，请及时更新 github 的 tag !!!".red())
        println()
        println("已改名为 .Apk 后缀".bold() + ", 请及时发布到掌邮反馈群".yellow())
        apk.renameTo(apk.parentFile.resolve(apk.name.replace(".apk", ".Apk")))
    }

    fun String.red() = "\u001B[31m$this\u001B[0m"
    fun String.green() = "\u001B[32m$this\u001B[0m"
    fun String.yellow() = "\u001B[33m$this\u001B[0m"
    fun String.blue() = "\u001B[34m$this\u001B[0m"
    fun String.purple() = "\u001B[35m$this\u001B[0m"
    fun String.cyan() = "\u001B[36m$this\u001B[0m"
    fun String.white() = "\u001B[37m$this\u001B[0m"
    fun String.bold() = "\u001B[1m$this\u001B[0m"
}