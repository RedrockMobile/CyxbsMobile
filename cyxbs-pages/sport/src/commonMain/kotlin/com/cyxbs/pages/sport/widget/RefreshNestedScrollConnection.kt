package com.cyxbs.pages.sport.widget

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

class RefreshNestedScrollConnection(
    private val state: RefreshState,
    private val canPull: () -> Boolean
) : NestedScrollConnection {

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

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (state.pullOffset <= 0f) {
            return Velocity.Zero
        }

        state.release()
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (
            !state.isRefreshing && available.y > 0f && canPull()
        ) {
            state.consumeFling(available.y)
            return available
        }

        return Velocity.Zero
    }
}