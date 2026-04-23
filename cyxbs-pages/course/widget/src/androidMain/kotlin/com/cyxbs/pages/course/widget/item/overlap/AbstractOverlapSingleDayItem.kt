package com.cyxbs.pages.course.widget.item.overlap

import android.content.Context
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.Transition
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.util.forEach
import androidx.core.util.isNotEmpty
import com.cyxbs.components.utils.extensions.dimen
import com.cyxbs.pages.course.widget.R
import com.cyxbs.pages.course.widget.fragment.course.base.FoldImpl
import com.cyxbs.pages.course.widget.fragment.course.base.OverlapImpl
import com.cyxbs.pages.course.widget.internal.item.forEachRow
import com.cyxbs.pages.course.widget.item.single.ISingleDayItem
import com.ndhzs.netlayout.attrs.NetLayoutParams
import com.ndhzs.netlayout.callback.OnRowWeightChangeListener
import com.ndhzs.netlayout.view.NetLayout
import java.util.Collections

/**
 * 用于实现单列的可重叠 item
 *
 * ## 重叠是什么？
 * 比如我有一个 1、2、3、4 节的课，但是另一节课是在 2、3 节，此时就会导致一节课被分开展示
 *
 * ## 怎么解决被分开展示的问题？
 * 按照产品给的需求：这分开的区域每个都要单独显示，且显示的形式与 1、2、3、4 节相同，只不过大小不同。
 *
 * 根据上述需求，一个 item 可以由多行组成（当前未考虑多列），我将 item 对应的 view 设为了 NetLayout，
 * 在 NetLayout 里面填充多个相同的子 View 来实现分开展示
 *
 * ## 怎么计算重叠的？
 * 重叠的计算具体逻辑请看 [OverlapImpl]
 *
 * 这里讲一下大致思路：课表是网格布局，每个格子都有一条引用链，串联起了这个格子上的所有 item。
 * 正常情况下只会显示最顶上的 item，如果你将顶上的 item removed 或者 gone，引用链会重新计算并刷新显示
 *
 * ## 注意
 * - 如果你遇到了重新布局时只有 Item 出现卡顿的情况，可以看看 [IOverlap.lockRefreshAnim]
 *
 * @author 985892345 (Guo Xiangrui)
 * @email guo985892345@foxmail.com
 * @date 2022/8/26 17:00
 */
abstract class AbstractOverlapSingleDayItem : IOverlapItem, OverlapHelper.IOverlapLogic,
  ISingleDayItem {
  
  /**
   * 创建子 View，这个 View 才是真正用于显示的
   *
   * 因为存在重叠分开显示的情况，所以需要单独用子 View 来实现（比如：1 2 3 4 节课，中间的 2 3 节课被遮挡了）
   *
   * 该方法返回的 View 将被缓存，如果需要修改属性可以使用 [getChildInFree]、 [getChildInParent]、[getChildIterable]
   */
  protected abstract fun createView(context: Context): View
  
  /**
   * 即将被添加进父布局时的回调
   *
   * ## 注意
   * - 此时并没有被添加进父布局，并且不能保证一定就能添加成功
   */
  protected open fun onAddIntoCourse() {}
  
  /**
   * 刷新重叠区域时的回调
   *
   * ## 注意
   * - 只有添加进父布局的才会收到回调
   *
   * @param isAllowAnim 是否允许动画
   * @param hasChanged 显示的区域是否发生了改变
   */
  protected open fun onRefreshOverlap(isAllowAnim: Boolean, hasChanged: Boolean) {}
  
  /**
   * 需要清除重叠区域时的回调
   */
  override fun onClearOverlap() {}
  
  /**
   * 得到使用 [createView] 生成的所有 view 集合的迭代器
   */
  fun getChildIterable(): Iterable<View> {
    return object : Iterable<View> {
      override fun iterator(): Iterator<View> {
        return object : Iterator<View> {
          
          val iterator1 = mChildInFree.iterator()
          val iterator2 = mChildInParent.iterator()
          
          override fun hasNext(): Boolean {
            return iterator1.hasNext() || iterator2.hasNext()
          }
          
          override fun next(): View {
            return if (iterator1.hasNext()) iterator1.next() else iterator2.next()
          }
        }
      }
    }
  }
  
  /**
   * 得到使用 [createView] 生成但目前不在 [mNetLayout] 中显示的所有 view 集合的迭代器
   */
  fun getChildInFree(): List<View> {
    return Collections.unmodifiableList(mChildInFree)
  }
  
  /**
   * 得到使用 [createView] 生成并在 [mNetLayout] 中显示的所有 view 集合的迭代器
   */
  fun getChildInParent(): List<View> {
    return Collections.unmodifiableList(mChildInParent)
  }
  
  /**
   * 遍历空闲的区域
   */
  fun forEachFreeAreaMap(block: (startRow: Int, endRow: Int) -> Unit) {
    mFreeAreaMap.forEach { startRow, endRow ->
      block.invoke(startRow, endRow)
    }
  }
  
  /**
   * 初始化 View 的回调
   */
  protected open fun onInitializeView(view: NetLayout) {}
  
  /**
   * 得到包裹 [createView] 的 ViewGroup
   *
   * ## 注意
   * 这个 View 并不是 [initializeView] 返回的 View
   */
  fun getNetView(): NetLayout? {
    return if (this::mNetLayout.isInitialized) mNetLayout else null
  }
  
  /**
   * 得到 Item 对应的 View
   */
  fun getRootView(): ViewGroup? {
    return if (this::mRootLayout.isInitialized) mRootLayout else null
  }
  
  /**
   * 用于实现折叠的 View
   *
   * [createView] 中得到的 View 都会添加进这个 view 中
   */
  private lateinit var mNetLayout: NetLayout
  private lateinit var mRootLayout: RootLayout
  
  @Suppress("LeakingThis")
  final override val overlap: IOverlap = OverlapHelper(this)
  
  final override fun initializeView(context: Context): View {
    if (!this::mNetLayout.isInitialized) {
      mNetLayout = NetLayout(context).apply {
        setRowColumnCount(lp.rowCount, lp.columnCount)
      }
      mRootLayout = RootLayout(mNetLayout)
    } else {
      mNetLayout.setRowColumnCount(lp.rowCount, lp.columnCount)
    }
    onInitializeView(mNetLayout)
    return mRootLayout
  }
  
  final override fun isAddIntoParent(): Boolean {
    refreshFreeArea()
    if (mFreeAreaMap.isNotEmpty()) {
      onAddIntoCourse()
      return true
    }
    return false
  }
  
  private val mChildInFree = arrayListOf<View>()
  private val mChildInParent = arrayListOf<View>()
  
  final override fun refreshOverlap(isAllowAnim: Boolean) {
    if (lp.rowCount != mNetLayout.rowCount || lp.columnCount != mNetLayout.columnCount) {
      // 这里需要重新设置一遍总行数，因为外面重新设置了 lp，但 mView 的属性与 lp 并没有相互绑定
      mNetLayout.setRowColumnCount(lp.rowCount, lp.columnCount)
    }
    // 刷新空闲区域
    refreshFreeArea()
    // 没有发生改变就直接退出
    if (!mHasChangedFreeArea) {
      onRefreshOverlap(isAllowAnim, false)
      return
    }
    mHasChangedFreeArea = false
  
    // 设置动画
    if (isAllowAnim) {
      TransitionManager.beginDelayedTransition(mNetLayout, createTransition())
    }
    val diffSize = mChildInParent.size - mFreeAreaMap.size()
    // 移除掉多的子 View
    repeat(diffSize) {
      val view = mChildInParent.removeAt(mChildInParent.lastIndex)
      mNetLayout.removeView(view)
      mChildInFree.add(view)
    }
    // 添加新的子 View
    repeat(-diffSize) {
      val view = mChildInFree.removeLastOrNull() ?: createView(mNetLayout.context)
      val params = view.layoutParams as? NetLayoutParams ?: createNetLayoutParams()
      mNetLayout.addNetChild(view, params)
      mChildInParent.add(view)
    }
    // 重新设置 View 的位置
    repeat(mFreeAreaMap.size()) {
      val startRow = mFreeAreaMap.keyAt(it)
      val endRow = mFreeAreaMap.valueAt(it)
      val view = mChildInParent[it]
      val params = view.layoutParams as NetLayoutParams
      params.startColumn = 0
      params.endColumn = 0
      params.startRow = startRow - lp.startRow
      params.endRow = endRow - lp.startRow
      view.layoutParams = params
    }
    onRefreshOverlap(isAllowAnim, true)
  }
  
  /**
   * 创建子 View 的 [NetLayoutParams]
   */
  private fun createNetLayoutParams(): NetLayoutParams {
    return NetLayoutParams(0, 0, 0, 0).apply {
      leftMargin = R.dimen.course_widget_item_margin.dimen.toInt()
      rightMargin = leftMargin
      topMargin = leftMargin
      bottomMargin = leftMargin
    }
  }
  
  private val mFreeAreaMap = SparseIntArray()
  private var mHasChangedFreeArea = false
  
  /**
   * 刷新空闲区域，重新计算自己可以显示的节点
   */
  private fun refreshFreeArea() {
    val old = if (!mHasChangedFreeArea) mFreeAreaMap.toString() else null
    mFreeAreaMap.clear()
    val column = lp.weekNum
    var s = lp.startRow
    var e = s - 1
    lp.forEachRow { row ->
      if (overlap.isDisplayable(row, column)) {
        e = row
      } else {
        if (s <= e) {
          mFreeAreaMap.put(s, e)
        }
        s = row + 1
      }
    }
    // 判断 e 是不是最后一格，如果是的话，就要单独加上
    if (e == lp.endRow) {
      mFreeAreaMap.put(s, e)
    }
    val new = if (!mHasChangedFreeArea) mFreeAreaMap.toString() else null
    if (old != new) {
      // 直接用字符串进行比较，这样更方便
      mHasChangedFreeArea = true
    }
  }
  
  private fun createTransition(): Transition {
    return TransitionSet().apply {
      addTransition(ChangeBounds())
      addTransition(Fade())
      duration = 160
    }
  }
  
  private class RootLayout(val netLayout: NetLayout) : FrameLayout(netLayout.context) {
    
    init {
      /**
       * 这里将 [netLayout] 套进两层 FrameLayout 是为了解决 ChangeBounds 的 Transition 动画调用的 suppressLayout(true)
       * 导致中午或傍晚展开动画卡顿的问题，原因可以看 [FoldImpl] 的头注释
       *
       * 为了好看的动画，多层嵌套这点性能不值一提 :)
       */
      val lp = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      val layout = FrameLayout(context).apply { setWillNotDraw(true); layoutParams = lp }
      layout.addView(netLayout)
      addView(layout)
      setWillNotDraw(true)
    }
    
    override fun setTranslationZ(translationZ: Float) {
      super.setTranslationZ(translationZ)
      // 设置 translationZ 可以在部分 View 中绘制阴影（FrameLayout 不会绘制）
      // 但外布局不知道子 View 的圆角大小，所以交给子 View 绘制阴影
      val childCount = netLayout.childCount
      for (i in 0 until childCount) {
        val child = netLayout.getChildAt(i)
        child.translationZ = translationZ
      }
    }
  
    // 关联课表与 netLayout 的行比重，主要用于中午和傍晚时间段的折叠，但这里不想过于耦合，所以采取这种写法
    private val mOnWeightChangeListener =
      OnRowWeightChangeListener { _, newWeight, row ->
        val lp = layoutParams as NetLayoutParams
        if (row in lp.startRow .. lp.endRow) {
          if (lp.rowCount != netLayout.rowCount) {
            // refreshOverlap() 方法中虽然会同步 lp 跟 netLayout，但有点靠后了
            // 这里会因为不同步在设置比重时出现问题，所以这里先进行同步
            netLayout.setRowColumnCount(lp.rowCount, lp.columnCount)
          }
          netLayout.setRowShowWeight(row - lp.startRow, newWeight)
        }
      }
  
    // 课表父布局，基于 NetLayout
    private var mParent: NetLayout? = null
  
    override fun onAttachedToWindow() {
      super.onAttachedToWindow()
      val parent = parent as NetLayout
      parent.addOnRowWeightChangeListener(mOnWeightChangeListener)
      setNetLayoutRow(parent)
      mParent = parent
    }
  
    override fun onDetachedFromWindow() {
      super.onDetachedFromWindow()
      mParent?.removeOnRowWeightChangeListener(mOnWeightChangeListener)
      mParent = null
    }
  
    // 同步行比重
    private fun setNetLayoutRow(parent: NetLayout) {
      val lp = layoutParams as NetLayoutParams
      for (row in lp.startRow .. lp.endRow) {
        netLayout.setRowShowWeight(row - lp.startRow, parent.getRowsShowWeight(row, row))
      }
    }
  }
}