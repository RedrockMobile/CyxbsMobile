package com.cyxbs.pages.noclass.page.viewmodel.other

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cyxbs.components.base.ui.BaseViewModel
import com.cyxbs.components.config.service.impl
import com.cyxbs.pages.course.api.ILessonService
import com.cyxbs.pages.noclass.bean.NoClassSpareTime
import com.cyxbs.pages.noclass.bean.Student
import com.cyxbs.pages.noclass.bean.toSpareTime
import com.cyxbs.pages.store.api.IStoreService
import io.reactivex.rxjava3.core.Observable

/**
 * 课表的viewModel，任何一个需要查询的activity界面都在使用
 */

typealias StuLessons = List<ILessonService.Lesson>

class CourseViewModel : BaseViewModel(){
    /**
     * 没课时段
     */
    val noclassData : LiveData<HashMap<Int, NoClassSpareTime>> get() = _noclassData
    private val _noclassData : MutableLiveData<HashMap<Int, NoClassSpareTime>> = MutableLiveData()

    fun getLessons(stuNumList: List<String>, students: List<Student>){
        val stuNameById = students.associateTo(hashMapOf()) { it.id to it.name }
        getLessons(stuNumList, stuNameById)
    }
    fun getLessonsFromNum2Name(stuNumList: List<String>, num2namePair: List<Pair<String,String>>){
        val stuNameById = num2namePair.associateTo(hashMapOf()) { it }
        getLessons(stuNumList, stuNameById)
    }

    private fun getLessons(stuNumList: List<String>, stuNameById: Map<String, String>) {
        if (stuNumList.isEmpty() || stuNameById.isEmpty() || stuNumList.size != stuNameById.size) return
        val lessonService = ILessonService::class.impl()
        val observables = stuNumList.map { stuNum ->
            lessonService.getStuLesson(stuNum)
                .map { stuNum to it as StuLessons }
                .toObservable()
        }
        Observable.zip(observables) {
            it.associate { pair ->
                @Suppress("UNCHECKED_CAST")
                pair as Pair<String, StuLessons> // 因为 zip 合并后用的 Array，所以这里只能强转
                pair
            }
        }.doOnError {
            toast("网络似乎开小差了~")
        }.safeSubscribeBy { map ->
            //将new的studentsLessons变成空闲时间对象
            _noclassData.postValue(map.toSpareTime().onEach {
                it.value.mIdToNameMap = HashMap(stuNameById)
            })
            IStoreService::class.impl().postTask(IStoreService.Task.JOIN_NOCLASS,"","今日已使用没课约一次，获得10邮票")
        }
    }
}