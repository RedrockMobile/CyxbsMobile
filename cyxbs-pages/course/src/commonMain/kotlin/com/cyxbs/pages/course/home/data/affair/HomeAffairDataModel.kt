package com.cyxbs.pages.course.home.data.affair

import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.init.appCoroutineScope
import com.cyxbs.pages.affair.api.AffairIdModel
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.AffairWhatTimeModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * .
 *
 * @author 985892345
 * @date 2025/7/4
 */

interface HomeAffairIdModel {
  val enable: StateFlow<Boolean>
  val id: Int
  val remindTime: MutableStateFlow<Int>
  val title: MutableStateFlow<String>
  val content: MutableStateFlow<String>
  val whatTimeList: StateFlow<ImmutableList<HomeAffairWhatTimeModel>>

  fun add(timePair: MinuteTimePair): HomeAffairWhatTimeModel?
  fun remove(whatTime: HomeAffairWhatTimeModel): Boolean

  fun delete()

  // 提交当前修改到数据层
  fun commit()
}

interface HomeAffairWhatTimeModel {
  val enable: StateFlow<Boolean>
  val idModel: HomeAffairIdModel
  val timePair: MutableStateFlow<MinuteTimePair>
  val dateList: StateFlow<ImmutableList<HomeAffairLeafModel>>

  fun add(date: Date): HomeAffairLeafModel?
  fun delete()
}

interface HomeAffairLeafModel {
  val enable: StateFlow<Boolean>
  val idModel: HomeAffairIdModel
  val whatTime: StateFlow<HomeAffairWhatTimeModel>
  val date: MutableStateFlow<Date>

  fun replace(whatTime: HomeAffairWhatTimeModel)

  fun delete()
}

internal class HomeAffairIdModelImpl(val idModel: AffairIdModel) : HomeAffairIdModel {
  override val enable = MutableStateFlow(true)
  override val id: Int
    get() = idModel.id
  override val remindTime = ObservableMutableStateFlow(idModel.remindTime.value)
  override val title = ObservableMutableStateFlow(idModel.title.value)
  override val content = ObservableMutableStateFlow(idModel.content.value)
  override val whatTimeList = ObservableMutableStateFlow(idModel.whatTimeList.value.map {
    HomeAffairWhatTimeModelImpl(this, it.value, it.key)
  }.toPersistentList())

  val whatTimeAddedList = mutableListOf<HomeAffairWhatTimeModelImpl>()
  val whatTimeRemovedList = mutableListOf<HomeAffairWhatTimeModelImpl>()

  override fun add(timePair: MinuteTimePair): HomeAffairWhatTimeModel? {
    if (!enable.value) return null
    if (whatTimeList.value.any { it.timePair.value == timePair }) return null
    val new = HomeAffairWhatTimeModelImpl(this, null, timePair)
    whatTimeList.value = whatTimeList.value.add(new)
    whatTimeAddedList.add(new)
    return new
  }

  override fun delete() {
    if (!enable.value) return
    whatTimeList.value.forEach { it.delete() }
    enable.value = false
    appCoroutineScope.launch {
      idModel.delete()
    }
  }

  override fun commit() {
    if (!enable.value) return
    appCoroutineScope.launch {
      idModel.edit {
        HomeAffairCommitHelper(it, this@HomeAffairIdModelImpl).commit()
      }
    }
  }
}

internal class HomeAffairWhatTimeModelImpl(
  override val idModel: HomeAffairIdModelImpl,
  var whatTimeModel: AffairWhatTimeModel?,
  initTimePair: MinuteTimePair,
) : HomeAffairWhatTimeModel {
  override val enable = MutableStateFlow(true)
  override val timePair = MutableStateFlow(initTimePair)
  override val dateList = MutableStateFlow(whatTimeModel?.dateList?.value?.map {
    HomeAffairLeafModelImpl(idModel, MutableStateFlow(this), it.value, it.key)
  }?.toPersistentList() ?: persistentListOf())

  val whatTimeAddedList = mutableListOf<HomeAffairLeafModelImpl>()
  val whatTimeRemovedList = mutableListOf<HomeAffairLeafModelImpl>()

  override fun add(date: Date): HomeAffairLeafModel? {
    if (!enable.value) return null
    if (dateList.value.any { it.date.value == date }) return null
    val new = HomeAffairLeafModelImpl(idModel, MutableStateFlow(this), null, date)
    dateList.value = dateList.value.add(new)
    whatTimeAddedList.add(new)
    return new
  }

  override fun delete() {
    if (!enable.value) return
    dateList.value.forEach { it.delete() }
    enable.value = false
  }
}

internal class HomeAffairLeafModelImpl(
  override val idModel: HomeAffairIdModelImpl,
  override val whatTime: MutableStateFlow<HomeAffairWhatTimeModel>,
  var leafModel: AffairDateModel?,
  initDate: Date,
) : HomeAffairLeafModel {
  override val enable = MutableStateFlow(true)
  override val date = MutableStateFlow(initDate)

  override fun replace(whatTime: HomeAffairWhatTimeModel) {
    val oldWhatTime = this.whatTime.value as HomeAffairWhatTimeModelImpl
    if (whatTime === oldWhatTime) return
    whatTime as HomeAffairWhatTimeModelImpl
    oldWhatTime.whatTimeRemovedList.add(this)
    whatTime.whatTimeAddedList.add(this)
    this.whatTime.value = whatTime

  }

  override fun delete() {
    enable.value = false
    if (leafModel != null) {
      val whatTime = whatTime.value as HomeAffairWhatTimeModelImpl
    }
  }
}

internal class HomeAffairCommitHelper(
  val idMoelEditor: AffairIdModelEditor,
  val homeIdModel: HomeAffairIdModelImpl,
) {
  fun commit() {
    commitIdModel()
  }

  fun commitIdModel() {
    if (homeIdModel.remindTime.hasEdited) idMoelEditor.remindTime = homeIdModel.remindTime.value
    if (homeIdModel.title.hasEdited) idMoelEditor.title = homeIdModel.title.value
    if (homeIdModel.content.hasEdited) idMoelEditor.content = homeIdModel.content.value
    commitWhatTimeModelList()
  }

  fun commitWhatTimeModelList() {
    if (homeIdModel.whatTimeList.hasEdited) {
      // 发生过编辑，说明存在新增或者移除操作
    }
  }
}

// 两个集合差
// old 最终结果为被移除的
// new 最终结果为新添加的
fun <T> diff(old: MutableCollection<T>, new: MutableSet<T>) {
  val iterator = old.iterator()
  while (iterator.hasNext()) {
    val next = iterator.next()
    if (new.contains(next)) {
      iterator.remove()
      new.remove(next)
    }
  }
}


internal class HomeAffairSyncHelper(
  val affairIdModel: AffairIdModel,
  val homeIdModel: HomeAffairIdModelImpl,
) {
  fun sync() {
    if (affairIdModel.id != homeIdModel.id) return
    syncIdModel()
  }

  private fun syncIdModel() {
    homeIdModel.remindTime.value = affairIdModel.remindTime.value
    homeIdModel.title.value = affairIdModel.title.value
    homeIdModel.content.value = affairIdModel.content.value
    syncWhatTimeModelList()
  }

  private fun syncWhatTimeModelList() {
    val newWhatTimeModels = affairIdModel.whatTimeList.value
    val map = homeIdModel.whatTimeList.value.associateBy { it.timePair }
  }

  private fun syncLeafModel() {

  }
}


@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class ObservableMutableStateFlow<T>(
  private var initValue: T,
  private val stateFlow: MutableStateFlow<T> = MutableStateFlow(initValue),
) : MutableStateFlow<T> by stateFlow {

  // 判断是否发生了修改
  var hasEdited = false
    private set

  override var value: T
    get() = stateFlow.value
    set(value) {
      hasEdited = value != initValue
      stateFlow.value = value
    }

  fun syncNewInitValue(newInitValue: T) {
    hasEdited = false
    initValue = newInitValue
    stateFlow.value = newInitValue
  }
}