package com.cyxbs.components.utils.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.cyxbs.components.account.api.IAccountService
import com.cyxbs.components.account.api.ITokenService
import com.cyxbs.components.utils.BuildConfig
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.defaultGson
import com.cyxbs.components.utils.service.allImpl
import com.cyxbs.components.utils.service.impl
import com.cyxbs.components.utils.utils.LogLocal
import com.cyxbs.components.utils.utils.LogUtils
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

/**
 * # 用法
 * ## 命名规则
 * XXXApiService 接口，命名规则，以 ApiService 结尾
 *
 * ## 接口模板
 * ```
 * interface XXXApiService {
 *
 *     @GET("/aaa/bbb")
 *     fun getXXX(): Single<ApiWrapper<Bean>>
 *     // 统一使用 ApiWrapper 或 ApiStatus 包装，注意 Bean 类要去掉 data 字段，不然会报 json 错误
 *
 *     companion object {
 *         val INSTANCE by lazy {
 *             ApiGenerator.getXXXApiService(XXXApiService::class)
 *         }
 *     }
 * }
 *
 * 或者：
 * interface XXXApiService : IApi
 *
 * 使用：
 * XXXApiService::class.impl
 *     .getXXX()
 * ```
 *
 * ## 示例代码
 * ```
 * ApiService.INSTANCE.getXXX()
 *     .subscribeOn(Schedulers.io())  // 线程切换
 *     .observeOn(AndroidSchedulers.mainThread())
 *     .mapOrInterceptException {     // 当 errorCode 的值不为成功时抛错，并拦截异常
 *         // 这里面可以使用 DSL 写法，更多详细用法请看该方法注释
 *     }
 *     .safeSubscribeBy {            // 如果是网络连接错误，则这里会默认处理
 *         // 成功的时候
 *         // 如果是仓库层，请使用 unsafeSubscribeBy()
 *     }
 * ```
 *
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/29 22:30
 */
object ApiGenerator {

    private const val DEFAULT_TIME_OUT = 10

    private var retrofit: Retrofit //统一添加了token到header
    private var commonRetrofit: Retrofit // 未添加token到header

    private val mAccountService = IAccountService::class.impl()

    val networkConfigs = INetworkConfigService::class.allImpl()
        .map { it.value.get() }

    //init对两种公共的retrofit进行配置
    init {
        //添加监听得到登录后的token和refreshToken,应用于初次登录或重新登录
        retrofit = Retrofit.Builder().apply {
            this.defaultConfig()
            configRetrofitBuilder {
                it.apply {
                    defaultConfig()
                    configureTokenOkHttp()
                    networkConfigs.forEach { config -> config.onCreateOkHttp(this) }
                }.build()
            }
        }.build()
        commonRetrofit = Retrofit.Builder().apply {
            this.defaultConfig()
            configRetrofitBuilder {
                it.apply {
                    defaultConfig()
                    configureCommonOkHttp()
                    networkConfigs.forEach { config -> config.onCreateOkHttp(this) }
                }.build()
            }
        }.build()
    }

    /**
     * 带 token 的请求
     */
    fun <T : Any> getApiService(clazz: KClass<T>): T = if (isTouristMode()) {
        getCommonApiService(clazz)
    } else {
        retrofit.create(clazz.java)
    }

    /**
     * 带 token 的请求，适配lib_common模块
     */
    fun <T> getApiService(clazz: Class<T>): T = if (isTouristMode()) {
        getCommonApiService(clazz)
    } else {
        retrofit.create(clazz)
    }

    /**
     * 不带 token 的请求
     */
    fun <T : Any> getCommonApiService(clazz: KClass<T>): T {
        return commonRetrofit.create(clazz.java)
    }

    /**
     * 不带 token 的请求，适配老模块lib_common
     */
    fun <T> getCommonApiService(clazz: Class<T>): T {
        return commonRetrofit.create(clazz)
    }

    /**
     * 通过此方法对得到单独的 Retrofit
     * @param retrofitConfig 配置Retrofit.Builder，已配置有
     * @see GsonConverterFactory
     * @see RxJava3CallAdapterFactory
     * null-> 默认BaseUrl
     * @param okHttpClientConfig 配置OkHttpClient.Builder，已配置有
     * @see HttpLoggingInterceptor
     * null-> 默认Timeout
     * @param tokenNeeded 是否需要添加token请求
     */
    fun createSelfRetrofit(
        tokenNeeded: Boolean,
        retrofitConfig: ((Retrofit.Builder) -> Retrofit.Builder)? = null,
        okHttpClientConfig: ((OkHttpClient.Builder) -> OkHttpClient.Builder)? = null
    ): Retrofit {
        return Retrofit.Builder()
            //对传入的retrofitConfig配置
            .apply {
                if (retrofitConfig == null)
                    this.defaultConfig()
                else
                    retrofitConfig.invoke(this)
            }
            //对传入的okHttpClientConfig配置
            .configRetrofitBuilder {
                it.apply {

                    if (tokenNeeded && !isTouristMode())
                        configureTokenOkHttp()
                    if (okHttpClientConfig == null)
                        this.defaultConfig()
                    else
                        okHttpClientConfig.invoke(
                            it.addInterceptor(BackupInterceptor)
                        )
                }.build()
            }.build()
    }

    //以下是retrofit基本配置
    /**
     * 现目前必须配置基本的需求，比如Log，Gson，RxJava
     */
    private fun Retrofit.Builder.configRetrofitBuilder(client: ((OkHttpClient.Builder) -> OkHttpClient)): Retrofit.Builder {
        return this.client(client.invoke(OkHttpClient().newBuilder().apply {
            val logging = HttpLoggingInterceptor { message ->
                LogUtils.d("OKHTTP", message)
                LogLocal.log("OKHTTP", "OKHTTP$message")
            }
            logging.level = HttpLoggingInterceptor.Level.BODY
            addInterceptor(Interceptor {
                it.proceed(
                    it.request().newBuilder()
                        .addHeader("APPVersion", BuildConfig.VERSION_NAME)
                        .build()
                )
            })
            dns(OkHttpDnsService.dns)
            addInterceptor(logging)
            //这里是在debug模式下方便开发人员简单确认 http 错误码 和 url(magipoke开始切的)
            if (BuildConfig.DEBUG) {
                addInterceptor(Interceptor {
                    val request = it.request()
                    Log.d("OKHTTP", "OKHTTP${request.body}")

                    val response = it.proceed(request)
                    // 因为部分请求一直 403、404，一直不修，就直接不弹了，所以注释掉，以后直接看 Pandora
//                        if (!response.isSuccessful){
//                            Handler(Looper.getMainLooper()).post {
//                                BaseApp.appContext.toast("${response.code} ${request.url} ")
//                            }
//                        }
                    response
                })
            }
        }))
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.createSynchronous())
    }

    //默认配置
    private fun Retrofit.Builder.defaultConfig() {
        this.baseUrl(getBaseUrl())
    }

    //默认配置
    private fun OkHttpClient.Builder.defaultConfig() {
        this.connectTimeout(DEFAULT_TIME_OUT.toLong(), TimeUnit.SECONDS)
        this.readTimeout(DEFAULT_TIME_OUT.toLong(), TimeUnit.SECONDS)
        dispatcher(OkHttpDispatcher)
    }


    //不带token请求的OkHttp配置
    private fun OkHttpClient.Builder.configureCommonOkHttp(): OkHttpClient {
        return this.apply {
            /**
             * 连接失败时切换备用url的Interceptor
             * 一旦切换，只有重启app才能切回来（因为如果请求得到的url不是原来的@{link getBaseUrl()}，则切换到新的url，而以后访问都用这个新的url了）
             * 放在tokenInterceptor上游的理由是：因为那里面还有token刷新机制，无法判断是否真正是因为服务器的原因请求失败
             */
            interceptors().add(BackupInterceptor)
        }.build()
    }

    //带token请求的OkHttp配置
    private fun OkHttpClient.Builder.configureTokenOkHttp(): OkHttpClient {
        return this.apply {
            /**
             * 发送版本号
             */
            interceptors().add(Interceptor {
                it.proceed(
                    it.request()
                        .newBuilder()
                        .addHeader("version", BuildConfig.VERSION_NAME)
                        .build()
                )
            })
            /**
             * 连接失败时切换备用url的Interceptor
             * 一旦切换，只有重启app才能切回来（因为如果请求得到的url不是原来的@{link getBaseUrl()}，则切换到新的url，而以后访问都用这个新的url了）
             * 放在tokenInterceptor上游的理由是：因为那里面还有token刷新机制，无法判断是否真正是因为服务器的原因请求失败
             */
            interceptors().add(BackupInterceptor)


            interceptors().add(Interceptor {

                if (!mAccountService.isLogin()) {
                    // 未登录直接请求，有些人对于不需要 token 的请求也使用了这个
                    return@Interceptor it.proceed(it.request())
                }
                // todo ApiGenerator 先暂时不检查 token 是否过期，后续慢慢迁移到 Network
                it.proceedWithToken()
            })
        }.build()
    }

    private fun Interceptor.Chain.proceedWithToken(
        block: (Request.Builder.() -> Unit)? = null
    ): Response {
        val token = ITokenService::class.impl().getToken()
        return proceed(
            request()
                .newBuilder()
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .also { block?.invoke(it) }
                .build()
        )
    }

    object BackupInterceptor : Interceptor {

        @Volatile
        private var mBackupUrl: String? = null

        private var mLastToastTime = 0L

        override fun intercept(chain: Interceptor.Chain): Response {

            // 如果切换过url，则直接用这个url请求
            val backupUrl = mBackupUrl
            if (backupUrl != null) {
                return useBackupUrl(backupUrl, chain)
            }

            // 正常请求，照理说应该进入tokenInterceptor
            // 除了登录和部分接口使用的 CommonApiService 以外，他们不会跑进 tokenInterceptor
            var response: Response? = null
            val exception: Exception
            val request = chain.request()
            try {
                response = chain.proceed(request)
                return response // 这里不能检查 code，因为部分老接口会返回 http 状态码 500
            } catch (e: Exception) {
                exception = BackupException(request, e)
            }

            // 分不同的环境触发不同的容灾请求
            when (getBaseUrl()) {
                END_POINT_REDROCK_DEV -> {
                    // dev 环境不触发容灾，不然会导致测试接口 404
                    val nowTime = System.currentTimeMillis()
                    if (nowTime - mLastToastTime > 10 * 1000) { // 保证不会一直疯狂 toast
                        mLastToastTime = nowTime
                        Handler(Looper.getMainLooper()).post {
                            // 使用原生 toast 醒目一点
                            Toast.makeText(
                                appContext,
                                "dev 请求异常, 请查看 Pandora",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                END_POINT_REDROCK_PROD -> {
                    val url = getBackupUrl()
                    mBackupUrl = url
                    response = useBackupUrl(url, chain)
                }

                else -> throw IllegalStateException("未知请求头！")
            }

            if (response == null) {
                // 这里抛出异常可以被 Pandora 捕获
                // 只有在没有触发容灾时会跑到这一步
                throw exception
            }

            return response
        }


        private val mLock = ReentrantLock()

        private fun getBackupUrl(): String {
            val backupUrl = mBackupUrl
            if (backupUrl != null) {
                return backupUrl
            }
            return mLock.withLock {
                val url = mBackupUrl
                if (url != null) url // 如果 mBackupUrl 不为 null 则说明前一个线程已经请求到了容灾地址
                else {
                    val okHttpClient = OkHttpClient()
                    val request: Request = Request.Builder()
                        .url(BASE_NORMAL_BACKUP_GET)
                        .build()
                    val call = okHttpClient.newCall(request)
                    val json = call.execute().body?.string()
                    val backupUrlStatus = defaultGson.fromJson<ApiWrapper<BackupUrlStatus>>(
                        json,
                        object : TypeToken<ApiWrapper<BackupUrlStatus>>() {}.type
                    )
                    backupUrlStatus.data.baseUrl
                }
            }
        }

        private fun useBackupUrl(backupUrl: String, chain: Interceptor.Chain): Response {
            val newUrl: HttpUrl = chain.request().url
                .newBuilder()
                .scheme("https")
                .host(backupUrl)
                .build()
            val builder: Request.Builder = chain.request().newBuilder()
            return chain.proceed(builder.url(newUrl).build())
        }

        data class BackupUrlStatus(
            @SerializedName("base_url")
            val baseUrl: String
        )

        private class BackupException(
            request: Request,
            exception: Exception,
        ) : RuntimeException("BackupInterceptor: url = ${request.url}, method = ${request.method}", exception)
    }

    //是否是游客模式
    private fun isTouristMode() = IAccountService::class.impl().isTouristMode()
}

/**
 * 实现该接口后后直接使用这种写法：
 * ```
 * ApiService::class.api
 *   .getXXX()
 * ```
 */
interface IApi {
    companion object {
        internal val MAP = HashMap<KClass<out IApi>, IApi>()
        internal val MAP_COMMON = HashMap<KClass<out IApi>, IApi>()
    }
}

/**
 * 带 token 的请求
 */
@Suppress("UNCHECKED_CAST")
val <I : IApi> KClass<I>.api: I
    get() = IApi.MAP.getOrPut(this) {
        ApiGenerator.getApiService(this)
    } as I

/**
 * 不带 token 的请求
 */
@Suppress("UNCHECKED_CAST")
val <I : IApi> KClass<I>.commonApi: I
    get() = IApi.MAP_COMMON.getOrPut(this) {
        ApiGenerator.getCommonApiService(this)
    } as I