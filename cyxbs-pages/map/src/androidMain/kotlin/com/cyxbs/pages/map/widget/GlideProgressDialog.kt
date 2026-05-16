package com.cyxbs.pages.map.widget

import android.content.Context

object GlideProgressDialog {
    private var mDialog: CustomProgressDialog? = null

    fun show(context: Context, title: String, message: String, cancelable: Boolean) {
        mDialog = CustomProgressDialog.createDialog(context)
        val dialog = mDialog!!
        dialog.setMessage(message)
        dialog.setTitle(title)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(cancelable)
        dialog.show()
    }

    fun hide() {
        val dialog = mDialog
        if (dialog != null && dialog.isShowing) {
            dialog.dismiss()
        }
        mDialog = null
    }

    fun setProcess(process: Int) {
        mDialog?.setProgress(process)
    }
}