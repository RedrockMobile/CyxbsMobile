package release

import Config
import com.android.build.gradle.options.Option
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import release.net.ReleaseData
import release.net.TaskService
import java.io.File
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

    // 发版的 token 由运维下发，只能由每届 Android 管理人持有 (移动副站或 Android 部长)
    private val okHttpClient = OkHttpClient.Builder().addInterceptor {
        it.proceed(
            it.request()
                .newBuilder()
                .header(
                    "token",
                    project.rootDir
                        .resolve("build-logic")
                        .resolve("secret")
                        .resolve("release-token.txt")
                        .readText()
                ).build()
        )
    }.connectTimeout(60, TimeUnit.SECONDS)//运维cdn宽带受限上传apk较慢，设置timeout时间在60s
        .callTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60,TimeUnit.SECONDS)
        .readTimeout(60,TimeUnit.SECONDS)
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
        val lastVersion = getUpdateContent()
        //忘记改updateContent和versionName的情况
        if (lastVersion.versionCode > Config.versionCode) {
            throw IllegalArgumentException("没改版本号 versionCode")
        } else if (lastVersion.versionCode == Config.versionCode) {
            if (project.findProperty("force") == "TRUE") {
                releaseApp(apk)
            } else {
                throw IllegalArgumentException("当前版本已发布，如果是因为发布错误或者需要重新覆盖安装包，" +
                    "请在终端中手动执行: ./gradlew releaseApp -P force=\"TRUE\"")
            }
        } else {
            if (lastVersion.versionName == Config.versionName) {
                throw IllegalArgumentException("改了 versionCode 却没改 versionName")
            } else if (lastVersion.updateContent == Config.updateContent) {
                throw IllegalArgumentException("没改更新的文案，去找产品要一个，然后写在 Config#updateContent 中")
            } else if (!lastVersion.versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
                throw IllegalArgumentException("versionName 不符合规则，只能为 x.y.z")
            } else if (compareVersion(lastVersion.versionName, Config.versionName) >= 0) {
                throw IllegalArgumentException("versionName 版本号低于或等于线上版本号")
            } else {
                releaseApp(apk)
            }
        }
    }

    private fun releaseApp(apk: File) {
        val apkUrl = uploadApk(apk)
        println("发版成功: ${postUpdateContent(apkUrl)}\n" +
            "请及时更新 github 的 tag!!!")
    }

    /**
     * 上传apk
     */
    private fun uploadApk(apk: File): String {
        val filePart = MultipartBody.Part.createFormData(
            "file", apk.name,
            apk.asRequestBody("application/octet-stream".toMediaType())
        )
        val response = netService.uploadApk(filePart).execute()
        if (!response.isSuccessful) throw RuntimeException("apk 上传失败: " +
            "message: ${response.message()}, code: ${response.code()}")
        val apkUploadData = response.body()!!
        if (apkUploadData.ok) {
            println("上传 apk 成功: $apkUploadData")
            return apkUploadData.data
        } else {
            throw RuntimeException("上传 apk 失败: $apkUploadData")
        }
    }

    /**
     * 上传更新信息
     */
    private fun postUpdateContent(apkUrl: String): ReleaseData {
        val data = ReleaseData(apkUrl)
        val response = netService.postUpdateContent(data).execute()
        if (!response.isSuccessful) throw RuntimeException("发版信息上传失败: " +
            "message: ${response.message()}, code: ${response.code()}")
        return response.body()!!
    }

    /**
     * 获取线上版本信息
     */
    private fun getUpdateContent(): ReleaseData {
        val response = netService.getUpdateContent().execute()
        if (!response.isSuccessful) throw RuntimeException("获取更新信息失败: " +
            "message: ${response.message()}, code: ${response.code()}")
        return response.body()!!
    }

    /**
     * https://leetcode.cn/problems/compare-version-numbers/description/
     * 时间复杂度: O(n+m)
     * 空间复杂度: O(1)
     */
    private fun compareVersion(version1: String, version2: String): Int {
        val n = version1.length
        val m = version2.length
        var i = 0
        var j = 0
        while (i < n || j < m) {
            var x = 0
            while (i < n && version1[i] != '.') {
                x = x * 10 + (version1[i] - '0')
                i++
            }
            i++ // 跳过点号
            var y = 0
            while (j < m && version2[j] != '.') {
                y = y * 10 + (version2[j] - '0')
                j++
            }
            j++ // 跳过点号
            if (x != y) {
                return if (x > y) 1 else -1
            }
        }
        return 0
    }
}