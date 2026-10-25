package com.cyxbs.pages.mine.page.security.viewmodel

import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.utils.extensions.setSchedulers
import com.cyxbs.pages.mine.network.model.SecurityQuestion
import com.cyxbs.pages.mine.util.apiService

/**
 * @date 2020-10-29
 * @author Sca RayleighZ
 */
class SetPasswordProtectViewModel : BaseViewModel() {

    //输入字符数不合理的提示
    val tipForInputNum = MutableLiveData<String>()

    //默认密保问题id为0
    var securityQuestionId: Int? = null
    lateinit var listOfSecurityQuestion: List<SecurityQuestion>

    // todo 这种写法会内存泄漏，别学习老登写法
    //请求获取密保问题的方法，建议在Activity建立之后就进行调用
    //最后传递进来的方法旨在帮助Activity刷新视图，没有使用必要时可以传空
    fun getSecurityQuestions(onQuestionLoaded: (List<SecurityQuestion>?) -> Unit) {
        apiService.getAllSecurityQuestions()
                .setSchedulers()
                .doOnError {
                    toast("获取密保问题失败")
                }
                .safeSubscribeBy { response ->
                    if (response.status == 10000) {
                        listOfSecurityQuestion = response.data
                        onQuestionLoaded(listOfSecurityQuestion)
                    } else {
                        toast("获取密保问题失败")
                    }
                }
    }

    // todo 这种写法会内存泄漏，别学习老登写法
    fun setSecurityQA(securityAnswer: String, onSucceed: () -> Unit) {
        val securityQuestionId = securityQuestionId
        if (securityQuestionId == null) {
            toast("请先选择密保问题")
            return
        }
        //如果输入的答案字数合理
        securityAnswer.length.let { length ->
            if (length in 2..17) {
                tipForInputNum.postValue("")
                apiService.setSecurityQuestionAnswer(
                        id = securityQuestionId,
                        content = securityAnswer)
                        .setSchedulers()
                        .doOnError {
                            toast(it.toString())
                        }
                        .safeSubscribeBy {
                            if (it.status == 10000) {
                                toast("恭喜您，设置成功")
                                onSucceed()
                            }
                        }
            }
        }
    }
}