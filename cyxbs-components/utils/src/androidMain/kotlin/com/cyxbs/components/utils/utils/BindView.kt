package com.cyxbs.components.utils.utils

import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.cyxbs.components.init.appContext
import java.lang.ref.WeakReference
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * 一种使用属性代理的方式来查找 View
 *
 * 用法参考了一篇郭霖分享的文章：
 * - [https://mp.weixin.qq.com/s?search_click_id=8523453218399021385-1645947629304-395847&__biz=MzA5MzI3NjE2MA==&mid=2650254267&idx=1&sn=15eee34e20406c34f12d51c4391891bd&chksm=88635ad4bf14d3c2783172ff3875bc1ab72a6dd04763884f8405aa9bf10b589cfaaa70b252c4&scene=19&subscene=10000&clicktime=1645947629&enterid=1645947629#rd]
 *
 * 但经过时间的考验后，进行了很多改进，可以看后面的改进思考
 *
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
 * **NOTE:** kt 直接通过 id 获取 View 的插件已经被废弃，禁止再使用！
 * **NOTE:** DataBinding 因 Kapt 与多平台问题，禁止再使用！
 *
 *
 *
 *
 * # 一些历史改进的思考
 * ## 第一代
 * 构造函数三个参数
 * ```
 * class BindView<T : View>(
 *   @IdRes
 *   val resId: Int,
 *   private val rootView: () -> View,
 *   private val lifecycle: () -> Lifecycle
 * ) : ReadOnlyProperty<Any, T> {
 * ```
 * 然后 getValue() 正常判断全局变量 mView: View 是否为空，为空就 findViewById()
 *
 * 主要的重点在于: getValue() 中使用 Lifecycle 注册一个回调，
 * 当状态为 DESTROYED 时将 mView 置为空
 *
 * ### 缺点
 * - 收到的 DESTROYED 回调比 Fragment 的 onDestroyView() 早一步，导致在 onDestroyView() 中使用时会重新 findViewById()
 * - 因为上面重新 findViewById()，导致 mInitializeList 又会再次初始化
 * - Activity 和 Fragment 一起管理过于麻烦
 *
 *
 * ## 第二代
 * 主要改进如下：
 * - 分离 BindView 为 ActivityBindView 和 FragmentBindView
 * - 每次调用 getValue() 时提供 checkLifecycleValid() 方法用于检测生命周期
 * - mView 改为弱引用，省去置为 null 带来的麻烦
 *
 *
 * ## 第三代
 * 又回归了第一代的想法，第一代最主要的问题在于 Lifecycle 的 DESTROYED 比 Activity 的 onDestroy() 快一步，
 * 但是他们是在同一条方法调用链上的，
 * 所以第三代在 Lifecycle 回调 DESTROYED 时用 Handler.post() 将 mView 的清空移到后面执行，就可以避开上述问题
 *
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2021/9/8
 * @time 17:34
 */
class BindView<V : View>(
  @IdRes val resId: Int,
  val findView: (Int) -> V,
  val getLifecycle: () -> Lifecycle,
) : ReadOnlyProperty<Any, V> {

  constructor(@IdRes resId: Int, root: () -> View) : this(
    resId,
    { root.invoke().findViewById(it) },
    { root.invoke().findViewTreeLifecycleOwner()!!.lifecycle }
  )

  constructor(@IdRes resId: Int, activity: ComponentActivity) : this(
    resId,
    { activity.findViewById(it) },
    { activity.lifecycle },
  )

  constructor(@IdRes resId: Int, fragment: Fragment) : this(
    resId,
    { fragment.requireView().findViewById(it) },
    { fragment.viewLifecycleOwner.lifecycle },
  )

  constructor(@IdRes resId: Int, dialog: ComponentDialog) : this(
    resId,
    { dialog.findViewById(it) },
    { dialog.lifecycle },
  )


  /**
   * 增加初始化的设置
   */
  fun addInitialize(initialize: V.() -> Unit): BindView<V> {
    mInitializeList.add(initialize)
    return this
  }



  private var mView: V? = null

  private val mInitializeList = mutableListOf<V.() -> Unit>()

  // 这里 thisRef 写成非空就不允许在局部变量中使用
  override fun getValue(thisRef: Any, property: KProperty<*>): V {
    if (mView == null) {
      val lifecycle: Lifecycle
      try {
        lifecycle = getLifecycle.invoke()
      } catch (e: Exception) {
        // 目前只有 Fragment 使用 viewLifecycleOwner 会出现这个问题，因为 Fragment 与 View 的生命周期不一致
        throw RuntimeException("获取 lifecycle 失败", e)
      }

      // 检查当前生命周期
      if (lifecycle.currentState == Lifecycle.State.DESTROYED) {
        // 虽然 lifecycle 的 DESTROYED 在 onDestroy() 前回调，但是在 onDestroy() 时 mView 并不会为 null，所以不会在这里报错
        throw RuntimeException("此时 ${thisRef::class.simpleName} 已经经历了 destroy，非法获取 ${getIdName()}")
      }

      // 查找 View
      val view: View
      try {
        view = findView(resId)
        mView = view
      } catch (e: Exception) {
        throw RuntimeException("获取 View 失败，id 为 ${getIdName()}", e)
      }

      // 回调 initialize
      mInitializeList.forEach {
        it.invoke(view)
      }

      // 观察 lifecycle 的 DESTROYED 回调
      lifecycle.addObserver(
        object : DefaultLifecycleObserver {
          override fun onDestroy(owner: LifecycleOwner) {
            lifecycle.removeObserver(this)
            // 通过 Handler 发送一个 Runnable 用于随后清除 mView 的引用
            // 原因在于 lifecycle 的 DESTROYED 回调在 Activity 的 onDestroy() 回调前，所以需要移到下一个消息队列中删除
            postClearView(this@BindView)
          }
        }
      )
    }
    return mView!!
  }

  // 用于清除 mView 的引用
  private fun clearView() {
    mView = null
  }

  // 获取 id 对应的名称
  private fun getIdName(): String {
    return "R.id.${appContext.resources.getResourceEntryName(resId)}"
  }

  companion object {
    private val Handler = Handler(Looper.getMainLooper())

    /**
     * 这里使用弱引用，防止全局 Handler 持有 [BindView] 导致 Activity/Fragment 内存泄漏
     *
     * 写到静态方法的原因在于内部类会持有外部类对象
     *
     * 由于 [BindView] 是由 Activity/Fragment 强持有的，所以这里的弱引用不会被提前 gc 掉，
     * 如果被 gc 掉了，说明 Activity/Fragment 已经被回收了
     */
    private fun postClearView(bind: BindView<*>) {
      val weakBind = WeakReference(bind)
      Handler.post {
        weakBind.get()?.clearView()
      }
    }
  }
}

fun <T : View> Int.view(root: () -> View): BindView<T> = BindView(this, root)
