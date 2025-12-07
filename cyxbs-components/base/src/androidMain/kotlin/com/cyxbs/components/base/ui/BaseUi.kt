package com.cyxbs.components.base.ui

import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.withStarted
import com.cyxbs.components.base.utils.RxjavaLifecycle
import com.cyxbs.components.base.utils.ToastUtils
import com.cyxbs.components.utils.extensions.launchByLifecycleScope
import com.cyxbs.components.utils.utils.BindView
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow

/**
 * 从 BaseActivity、BaseFragment、BaseDialog 中抽离的共用函数
 *
 * 这里面不要跟业务挂钩！！！
 * 比如：使用 api 模块
 * 这种操作请放在 OperationUi 中，以扩展的方式向外提供
 *
 * ## 一、属性代理获取 View
 * ```
 * val mTvNum: TextView by R.id.xxx.view() // 可以在简单页面使用，就不用写 findViewById() 了
 * ```
 * 详细用法请查看 [Int.view]
 *
 * ## 二、Flow 相关 collect 封装
 * ### collectLaunch()
 * ```
 * mViewModel.flow
 *     .collectLaunch {
 *         // 配合生命周期的收集方法，可以少写一个 lifecycleScope.launch {} 包在外面
 *     }
 * ```
 * ### collectSuspend()
 * ```
 * mViewModel.flow
 *     .collectSuspend {
 *         // launchWhenStarted() 的封装，作用：在进入后台时会自动挂起
 *     }
 * ```
 * ### collectRestart()
 * ```
 * mViewModel.flow
 *     .collectRestart {
 *         // flowWithLifecycle() 的封装，作用：在进入后台时摧毁，再次进入时重启 Flow
 *     }
 * // 注意：该方法适用场景很少，有数据倒灌的缺点，请注意使用场景！！！
 * ```
 *
 *
 *
 *
 *
 *
 * # 更多封装请往父类和接口查看
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/7/20 19:44
 */
interface BaseUi : ToastUtils, RxjavaLifecycle {

  /**
   * 根布局
   */
  val rootView: View

  /**
   * View 的 LifecycleOwner
   *
   * 注意：如果是 Fragment，则可能因为没有初始化 View 而报错，
   * 使用 [doOnCreateContentView] 回调后再去拿 LifecycleOwner
   */
  fun getViewLifecycleOwner(): LifecycleOwner

  /**
   * 创建 View 时的回调，该方法可用于外界获取 View 对象
   *
   * 请留意以下情况；
   * - 如果添加时已经创建了 View，则会立马回调
   * - Fragment 因为与 View 生命周期不一致所以存在多次回调的情况
   *
   * @return 返回非 null 值则继续观察下一次回调，用于 Fragment 中
   */
  fun doOnCreateContentView(action: (rootView: View) -> Any?)

  /**
   * 在简单界面，使用这种方式来得到 View，kae 插件 和 DataBinding/ViewBinding 已不被允许使用
   * ```
   * 使用方法：
   *    val mTvNum: TextView by R.id.xxx.view()
   *        .addInitialize {
   *           // 进行初始化的设置
   *        }
   *
   * 代替 findViewById 的方法有：
   *    kt 插件(被废弃)、属性代理、ButterKnife(被废弃)、DataBinding、ViewBinding
   *
   * 如果使用 DataBinding 和 ViewBinding 会因为 id 太长而劝退
   * ViewBinding 是给所有布局都默认开启的，大项目会严重拖垮编译速度
   * ```
   */
  fun <T : View> Int.view(): BindView<T>

  /**
   * 尤其注意这个 viewLifecycleOwner
   *
   * Fragment 与 View 的生命周期是不同的，而且一般情况下不会使用到 Fragment 的生命周期
   */
  fun <T> LiveData<T>.observe(observer: (T) -> Unit) {
    observe(getViewLifecycleOwner(), observer)
  }

  /**
   * 只观察一次 LiveData
   */
  fun <T> LiveData<T>.observeOnce(
    owner: LifecycleOwner = getViewLifecycleOwner(),
    observer: (T) -> Unit
  ) {
    observe(
      owner,
      object : Observer<T> {
        override fun onChanged(value: T) {
          removeObserver(this)
          observer.invoke(value)
        }
      }
    )
  }

  /**
   * 观察 LiveData 直到返回 true
   * @param observer 返回 true，则停止观察；返回 false，则继续观察
   */
  fun <T> LiveData<T>.observeUntil(
    owner: LifecycleOwner = getViewLifecycleOwner(),
    observer: (T) -> Boolean
  ) {
    observe(
      owner,
      object : Observer<T> {
        override fun onChanged(value: T) {
          if (observer.invoke(value)) {
            removeObserver(this)
          }
        }
      }
    )
  }

  fun <T> Flow<T>.collectLaunch(
    owner: LifecycleOwner = getViewLifecycleOwner(),
    action: suspend (value: T) -> Unit
  ): Job = owner.launchByLifecycleScope {
    collect { action.invoke(it) }
  }

  /**
   * 结合生命周期收集 Flow 方法，在进入后台的时候会自动挂起
   *
   * 该方法会在界面进入后台后自动挂起下游，即下游不处理数据，但上游仍会发送数据
   *
   * [launchWhenStarted() 内部使用的 whenStarted()，点击跳转去掘金学习](https://juejin.cn/post/6992746840605065229)
   */
  fun <T> Flow<T>.collectSuspend(
    owner: LifecycleOwner = getViewLifecycleOwner(),
    action: suspend (value: T) -> Unit
  ): Job = owner.launchByLifecycleScope {
    owner.withStarted {
      owner.launchByLifecycleScope {
        collect { action.invoke(it) }
      }
    }
  }


  /**
   * 结合生命周期收集 Flow 方法，在进入后台的时候会自动取消
   *
   * 该方法会在界面进入后台后取消上游，再回到前台后重新触发上游发送数据
   *
   * [flowWithLifecycle() 内部就是使用的 repeatOnLifecycle()，点击跳转去掘金学习](https://juejin.cn/post/6992746840605065229)
   *
   * **注意:** 该方法请在合适的需求下使用，因为会有数据倒灌（粘性事件）的问题，即每次进入前台都会重新发送数据
   * （适用于一直观察的情况，比如我一直观察学号是否改变、观察位置是否变化等，这些并不是只收集一次数据，而是会一直收集数据）
   */
  fun <T> Flow<T>.collectRestart(
    owner: LifecycleOwner = getViewLifecycleOwner(),
    action: suspend (value: T) -> Unit
  ): Job = flowWithLifecycle(owner.lifecycle).collectLaunch(owner, action)


  // Rxjava 自动关流
  @Deprecated("内部方法，禁止调用", level = DeprecationLevel.HIDDEN)
  override fun onAddRxjava(disposable: Disposable) {
    bindLifecycle(disposable, getViewLifecycleOwner().lifecycle)
  }
}