package com.cyxbs.pages.sport.widget

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * @Desc : 体育页面下拉刷新的嵌套滚动连接器
 * @Author : xt
 *
 * @param state 下拉刷新状态
 * @param canPull 当前列表是否允许继续下拉
 * @param onRelease 松手后的刷新处理回调
 */
class RefreshNestedScrollConnection(
    private val state: RefreshState,
    private val canPull: () -> Boolean,
    private val onRelease: suspend () -> Unit
) : NestedScrollConnection {

    /** 在列表上滑时优先收起已展开的刷新头部。 */
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (state.isRefreshing) return Offset.Zero

        //上滑时若header已经展开，优先回收header
        if (state.pullOffset > 0f && available.y < 0f) {
            return Offset(x = 0f, y = state.consumeDrag(available.y))
        }

        if (source != NestedScrollSource.UserInput) {
            return Offset.Zero
        }

        return Offset.Zero
    }

    // 消费用户下拉距离并更新刷新头部偏移量
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        if (source != NestedScrollSource.UserInput) {
            return Offset.Zero
        }

        if (available.y > 0f && canPull()) {
            return Offset(x = 0f, y = state.consumeDrag(available.y))
        }

        return Offset.Zero
    }

    // 松手时触发刷新或回弹动画
    override suspend fun onPreFling(available: Velocity): Velocity {
        if (state.pullOffset <= 0f) {
            return Velocity.Zero
        }

        onRelease()
        return available
    }

    // 处理惯性滚动结束后的刷新阈值判断
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (
            !state.isRefreshing && available.y > 0f && canPull()
        ) {
            if (state.consumeFling(available.y)) {
                onRelease()
            }
            return available
        }

        return Velocity.Zero
    }
}
