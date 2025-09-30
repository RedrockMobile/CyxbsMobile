package com.mredrock.cyxbs.common.viewmodel

import androidx.annotation.CallSuper
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mredrock.cyxbs.common.viewmodel.event.ProgressDialogEvent
import com.mredrock.cyxbs.common.viewmodel.event.SingleLiveEvent
import io.reactivex.rxjava3.disposables.Disposable

/**
 * Created By jay68 on 2018/8/23.
 */
open class BaseViewModel : ViewModel() {
    val toastEvent: MutableLiveData<Int> by lazy { SingleLiveEvent() }
    val longToastEvent: MutableLiveData<Int> by lazy { SingleLiveEvent() }
    val progressDialogEvent: MutableLiveData<ProgressDialogEvent> by lazy { SingleLiveEvent() }

    private val disposables: MutableList<Disposable> = mutableListOf()

    fun Disposable.lifeCycle(): Disposable {
        disposables.add(this)
        return this@lifeCycle
    }

    @CallSuper
    open fun onProgressDialogDismissed() {
        disposeAll()
    }

    @CallSuper
    override fun onCleared() {
        super.onCleared()
        disposeAll()
    }

    private fun disposeAll() {
        disposables.asSequence()
            .filterNot { it.isDisposed }
            .forEach { it.dispose() }
        disposables.clear()
    }

    protected fun toast(s: CharSequence) {
        com.cyxbs.components.utils.extensions.toast(s)
    }

    protected fun toastLong(s: CharSequence) {
        com.cyxbs.components.utils.extensions.toastLong(s)
    }
}