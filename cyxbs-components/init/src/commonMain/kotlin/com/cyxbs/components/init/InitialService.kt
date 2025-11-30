package com.cyxbs.components.init

/**
 * spi 依赖注入
 *
 * 在自己的模块中参考以下写法即可实现注入：
 * ```
 * 添加配置：
 * useKtProvider()
 *
 * 实现接口：
 * @ImplProvider(clazz = InitialService::class, name = "XXXInitialService")
 * object XXXInitialService : InitialService
 * // 建议使用 object 单例
 * ```
 *
 * @author ZhiQiang Tu
 * @time 2022/3/24  12:40
 * @signature 我将追寻并获取我想要的答案
 */
interface InitialService {
    // 所有进程的回调
    fun onAllProcess() {}
    //处于主进程的调用(可以进行与隐私策略无关的sdk的初始化,因为app启动就会回调)
    fun onMainProcess() {}
    //处于sdk所对应的进程的时候的回调
    fun onOtherProcess() {}
    //隐私策略同意的时候的回调，注意：只会在主线程上调用，且整个应用生命周期内只回调一次
    fun onPrivacyAgreed() {}
}