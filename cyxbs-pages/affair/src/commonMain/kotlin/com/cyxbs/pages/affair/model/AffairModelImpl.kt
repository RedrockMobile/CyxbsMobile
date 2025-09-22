package com.cyxbs.pages.affair.model

import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachReversed
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapTo
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.config.time.MinuteTimePair
import com.cyxbs.components.utils.extensions.runCatchingCoroutine
import com.cyxbs.components.utils.extensions.showExceptionDialog
import com.cyxbs.pages.affair.api.AffairDateModel
import com.cyxbs.pages.affair.api.AffairDateModelEditor
import com.cyxbs.pages.affair.api.AffairItemModel
import com.cyxbs.pages.affair.api.AffairItemModelEditor
import com.cyxbs.pages.affair.api.AffairModel
import com.cyxbs.pages.affair.api.AffairWhatTimeModel
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.bean.AffairWhatTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * .
 *
 * @author 985892345
 * @date 2025/6/22
 */

class AffairModelImpl(
  override val stuNum: String,
  var affairList: List<AffairEntity>,
  val addAction: suspend (entity: AffairEntity) -> Result<AffairEntity>,
  val deleteAction: suspend (id: Int) -> Result<Any>,
  val updateAction: suspend (entity: AffairEntity) -> Result<Any>
) : AffairModel {

  override val items = MutableStateFlow(affairList.fastMap {
    createAffairItemModelImpl(it)
  })

  override suspend fun addAffair(
    remindTime: Int,
    title: String,
    content: String,
    action: suspend (AffairItemModelEditor) -> Unit
  ): Result<AffairItemModelImpl> {
    return runCatching {
      val itemModelImpl = createAffairItemModelImpl(
        AffairEntity(
          id = 0,
          remindTime = remindTime,
          title = title,
          content = content,
          whatTime = emptyList(),
        )
      )
      val editor = AffairItemModelEditorImpl(itemModelImpl)
      action.invoke(editor)
      editor.checkEnableUpdate()?.let {
        throw IllegalArgumentException(it)
      }
      val affair = editor.createAffair(0)
      if (affair == null) {
        throw IllegalArgumentException("不存在时间段可以展示, $affair")
      } else {
        addAction.invoke(affair).map {
          items.updateAndGet {
            it.toMutableList().apply {
              add(createAffairItemModelImpl(affair))
            }
          }.last()
        }.getOrThrow()
      }
    }
  }

  fun createAffairItemModelImpl(affair: AffairEntity): AffairItemModelImpl {
    return AffairItemModelImpl(affair, this, deleteAction, updateAction)
  }

  suspend fun syncAffair(affairs: List<AffairEntity>) {
    if (affairList == affairs) return // 本地与远端数据完全一致
    items.update { old ->
      if (old.isEmpty()) {
        // 本地无数据，使用远端数据
        affairs.map {
          createAffairItemModelImpl(it)
        }
      } else if (affairs.isEmpty()) {
        // 远端无数据，直接清空
        emptyList()
      } else {
        // 本地数据与远端数据都有数据，但数据存在差异，需要进行比较
        // 先通过 id 整合起来比较单个事务
        val oldMap = old.associateByTo(LinkedHashMap()) { it.id }
        val newMap = affairs.associateByTo(LinkedHashMap()) { it.id }
        val newMapIterator = newMap.iterator()
        while (newMapIterator.hasNext()) {
          val newItem = newMapIterator.next()
          val oldItem = oldMap.remove(newItem.key)
          if (oldItem != null) {
            // newMap 和 oldMap 都包含相同 id 的事务
            newMapIterator.remove() // 从 newMap 中移除
            // 同步本地数据为远端数据
            oldItem.editInternal(needUpload = false) { editor ->
              editor.syncAffair(newItem.value)
            }.onFailure {
              // 在同步为远端事务时正常来说不应该会出现失败的情况，
              // 并且这里 needUpload = false 也不会是请求失败，只可能是事务本身的 checkEnableUpdate() 不通过
              // 所以可能是远端事务数据存在问题，我们抛弃该事务的更新
              if (isDebug()) {
                showExceptionDialog(
                  RuntimeException(
                    "同步远端事务失败, remote = ${newItem.value}, local = $oldItem",
                    it
                  )
                )
              }
            }
          }
        }
        // 剩余的 newMap 为新增的数据，oldMap 为被删除的数据
        newMap.values.map {
          createAffairItemModelImpl(it)
        } + old.filter { !oldMap.containsKey(it.id) }
      }
    }
  }
}

class AffairItemModelImpl(
  affair: AffairEntity,
  val affairModelImpl: AffairModelImpl,
  val deleteAction: suspend (id: Int) -> Result<Any>,
  val updateAction: suspend (entity: AffairEntity) -> Result<Any>
) : AffairItemModel {
  override val id: Int = affair.id
  override val remindTime = MutableStateFlow(affair.remindTime)
  override val title = MutableStateFlow(affair.title)
  override val content = MutableStateFlow(affair.content)
  override val whatTimeList = MutableStateFlow(
    affair.whatTime.groupBy(
      keySelector = { it.timePair }, // 需要先聚合 timePair，防止 server 下发数据存在重复的 timePair
      valueTransform = { it.date },
    ).map { 
      AffairWhatTimeModelImpl(
        this, 
        AffairWhatTime(it.key, it.value.flatten())
      )
    }.associateWith { it.timePair.value }
  )

  // mutex 是不支持可重入的，重入时会变成死锁，需要添加 owner 来进行检测
  private val editMutex = Mutex() // 编辑行为锁
  private val deleteMutex = Mutex() // 删除行为锁


  // 已经触发了删除的标记
  private var hasDelete: Boolean = false

  override suspend fun delete(): Result<Any> {
    return deleteInternal(fromEdit = false)
  }

  private suspend fun deleteInternal(fromEdit: Boolean): Result<Any> {
    return deleteMutex.withLock(this) {
      if (hasDelete) return Result.success(Unit)
      if (!fromEdit && editMutex.isLocked) {
        throw IllegalStateException("编辑中不允许调用 delete，如果需要 delete 请将 whatTimeList 置为空")
      }
      deleteAction.invoke(id).onSuccess {
        hasDelete = true
        // 从 affairModelImpl 中删除
        affairModelImpl.items.update {
          it.toMutableList().apply { remove(this@AffairItemModelImpl) }
        }
        // 删除所有 whatTimeModel
        whatTimeList.getAndUpdate { emptyMap() }.forEach {
          it.key.delete()
        }
      }
    }
  }

  override suspend fun edit(
    action: suspend (AffairItemModelEditor) -> Unit,
  ): Result<Any?> {
    return editInternal(needUpload = true, action = action)
  }

  suspend fun editInternal(
    needUpload: Boolean, // 是否需要上传到后端，在同步远端数据到本地时是不需要上传的
    action: suspend (AffairItemModelEditorImpl) -> Unit,
  ): Result<AffairEntity?> {
    return editMutex.withLock(this) { // 重入 withLock 时会抛出异常
      runCatchingCoroutine {
        val editor = AffairItemModelEditorImpl(this)
        action.invoke(editor)
        editor.checkEnableUpdate()?.let {
          throw IllegalArgumentException(it)
        }
        val affair = editor.createAffair(id)
        if (needUpload) {
          if (affair == null) {
            deleteInternal(fromEdit = true).getOrThrow()
          } else {
            updateAction.invoke(affair).getOrThrow()
          }
        }
        editor.update()
        affair
      }
    }
  }
}

class AffairWhatTimeModelImpl(
  affairModel: AffairItemModelImpl,
  whatTime: AffairWhatTime
) : AffairWhatTimeModel {
  override val enable = MutableStateFlow(true)
  override val affair = affairModel
  override val timePair = MutableStateFlow(whatTime.timePair)
  override val dateList = MutableStateFlow(
    whatTime.date.fastDistinctBy { it }.sorted().associateBy(
      keySelector = { AffairDateModelImpl(affairModel, this, it) },
      valueTransform = { it }
    )
  )

  fun delete() {
    enable.value = false
    dateList.getAndUpdate { emptyMap() }.forEach {
      it.key.delete()
    }
  }
}

class AffairDateModelImpl(
  affairModel: AffairItemModelImpl,
  whatTimeModel: AffairWhatTimeModelImpl,
  date: Date,
) : AffairDateModel {
  override val enable = MutableStateFlow(true)
  override val affair = affairModel
  override val whatTime = MutableStateFlow<AffairWhatTimeModelImpl>(whatTimeModel)
  override val date = MutableStateFlow(date)

  fun delete() {
    enable.value = false
  }
}

//////////////////////////////////////// Editor ////////////////////////////////////////

class AffairItemModelEditorImpl(
  val affairModel: AffairItemModelImpl
) : AffairItemModelEditor {
  override var remindTime: Int = affairModel.remindTime.value
  override var title: String = affairModel.title.value
  override var content: String = affairModel.content.value
  override var whatTimeList: AffairWhatTimeEditorListImpl =
    AffairWhatTimeEditorListImpl(
      affairModel,
      affairModel.whatTimeList.value.mapTo(mutableListOf()) {
        AffairWhatTimeModelEditorImpl(affairModel, it.key)
      }
    )

  fun checkEnableUpdate(): String? {
    if (remindTime < 0) return "remindTime 不能小于 0"
    if (title.isBlank()) return "title 不能为空"
    return whatTimeList.checkEnableUpdate()
  }

  // 调用前需调用 checkEnableUpdate()
  fun createAffair(id: Int): AffairEntity? {
    val affairWhatTimeList = whatTimeList.createAffairWhatTimeList()
    return if (affairWhatTimeList.isEmpty()) null else {
      AffairEntity(
        id = id,
        remindTime = remindTime,
        title = title,
        content = content,
        whatTime = affairWhatTimeList,
      )
    }
  }

  // 调用前需调用 checkEnableUpdate()
  fun update() {
    affairModel.remindTime.value = remindTime
    affairModel.title.value = title
    affairModel.content.value = content
    whatTimeList.update() // update 时内部需要进行去重
    affairModel.whatTimeList.value = whatTimeList.origin.associateBy(
      keySelector = { it.whatTimeModel },
      valueTransform = { it.timePair },
    )
  }

  fun syncAffair(affair: AffairEntity) {
    remindTime = affair.remindTime
    title = affair.title
    content = affair.content
    whatTimeList.syncAffair(affair.whatTime)
  }

  class AffairWhatTimeEditorListImpl(
    val affairModel: AffairItemModelImpl,
    val origin: MutableList<AffairWhatTimeModelEditorImpl>
  ) : AffairItemModelEditor.AffairWhatTimeModelEditorList,
    List<AffairWhatTimeModelEditor> by origin {

    private val addList = mutableListOf<AffairWhatTimeModelEditorImpl>()
    private val removeList = mutableListOf<AffairWhatTimeModelEditorImpl>()

    override fun add(timePair: MinuteTimePair): AffairWhatTimeModelEditorImpl? {
      origin.fastFirstOrNull { it.timePair == timePair }?.let { return null } // 已有的话则直接返回 null
      val whatTimeModel =
        AffairWhatTimeModelImpl(affairModel, AffairWhatTime(timePair, emptyList()))
      val whatTimeModelEditor = AffairWhatTimeModelEditorImpl(affairModel, whatTimeModel)
      origin.add(whatTimeModelEditor)
      addList.add(whatTimeModelEditor)
      removeList.remove(whatTimeModelEditor)
      return whatTimeModelEditor
    }

    override fun remove(whatTime: AffairWhatTimeModelEditor): Boolean {
      whatTime as AffairWhatTimeModelEditorImpl
      if (origin.remove(whatTime)) {
        removeList.add(whatTime)
        addList.remove(whatTime)
        return true
      }
      return false
    }

    fun checkEnableUpdate(): String? {
      origin.fastForEach { whatTimeModelEditorImpl ->
        whatTimeModelEditorImpl.checkEnableUpdate()?.let { return it }
      }
      return null
    }

    fun update() {
      val map = HashMap<MinuteTimePair, AffairWhatTimeModelEditorImpl>()
      val iterator = origin.iterator()
      while (iterator.hasNext()) {
        val whatTimeModelEditor = iterator.next()
        val timePair = whatTimeModelEditor.timePair
        val oldWhatTimeModelEditor = map[timePair]
        if (oldWhatTimeModelEditor == null) {
          map[timePair] = whatTimeModelEditor
          whatTimeModelEditor.update()
        } else {
          // 去重相同 timePair，再跟之前的合并 List<Date>
          iterator.remove()
          removeList.add(whatTimeModelEditor)
          addList.remove(whatTimeModelEditor)
          whatTimeModelEditor.dateList.fastForEachReversed {
            // 转移 Date 到 old 中
            whatTimeModelEditor.dateList.remove(it)
            oldWhatTimeModelEditor.dateList.add(it)
          }
          oldWhatTimeModelEditor.update()
        }
      }
      // 通知所有被移除的 whatTimeModel
      removeList.forEach {
        it.update()
        it.whatTimeModel.delete()
      }
    }

    fun syncAffair(list: List<AffairWhatTime>) {
      if (list.isEmpty()) {
        origin.fastForEach {
          removeList.add(it)
          addList.remove(it)
        }
        origin.clear()
      } else if (origin.isEmpty()) {
        // 这里需要聚合一下 timePair 的数据
        list.groupBy(
          keySelector = { it.timePair },
          valueTransform = { it.date }
        ).forEach {
          add(it.key)?.syncAffair(
            AffairWhatTime(it.key, it.value.flatten())
          )
        }
      } else {
        val newSet = list.groupByTo(
          destination = LinkedHashMap(),
          keySelector = { it.timePair }, // 去重相同 timePair
          valueTransform = { it.date }
        ).mapValuesTo(LinkedHashMap()) { entry ->
          AffairWhatTime(
            timePair = entry.key,
            date = entry.value.flatten().fastDistinctBy { it }.sorted(),
          )
        }
        val oldSet = origin.associateByTo(LinkedHashMap()) { it.timePair }
        // 先以 timePair 比较新旧数据
        val newSetIterator = newSet.iterator()
        while (newSetIterator.hasNext()) {
          val new = newSetIterator.next()
          val old = oldSet.remove(new.key)
          if (old != null) {
            // newSet 和 oldSet 都包含相同 timePair
            newSetIterator.remove()
            old.syncAffair(new.value)
          }
        }
        if (newSet.isEmpty()) {
          // newSet 已经为空，则比较提前结束，删除 oldSet 中的元素即可
          oldSet.forEach { remove(it.value) }
          return
        } else if (oldSet.isEmpty()) {
          // newSet 已经为空，则比较提前结束，新增 newSet 中的元素即可
          newSet.forEach { add(it.key)?.syncAffair(it.value) }
          return
        }
        // 再以 List<Date> 比较新旧数据
        val newSet2 = newSet.values.associateByTo(LinkedHashMap()) { it.date }
        val oldSet2 = oldSet.values.associateByTo(LinkedHashMap()) { impl ->
          impl.dateList.fastMapTo(mutableListOf()) { it.date }.apply { sort() }
        }
        val newSet2Iterator = newSet2.iterator()
        while (newSet2Iterator.hasNext()) {
          val new = newSet2Iterator.next()
          val old = oldSet2.remove(new.key)
          if (old != null) {
            // newSet 和 oldSet 都包含相同 List<Date>
            newSet2Iterator.remove()
            old.syncAffair(new.value)
          }
        }
        // 剩下的就是 timePair 与 List<Date> 都匹配不上的数据
        // 剩余的 newSet2 为新增的数据，oldSet2 为被删除的数据
        newSet2.forEach { add(it.value.timePair)?.syncAffair(it.value) }
        oldSet2.forEach { remove(it.value) }
      }
    }

    fun createAffairWhatTimeList(): List<AffairWhatTime> {
      return origin.groupBy( // 先聚合相同的 timePair
        keySelector = { it.timePair },
        valueTransform = { impl -> impl.dateList.fastMap { it.date } }
      ).mapNotNull { entry ->
        val date = entry.value.flatten()
        if (date.isEmpty()) null else {
          AffairWhatTime(
            timePair = entry.key,
            date = date.fastDistinctBy { it }.sorted(), // 去重 Date 再进行排序
          )
        }
      }
    }
  }
}

class AffairWhatTimeModelEditorImpl(
  affairModel: AffairItemModelImpl,
  val whatTimeModel: AffairWhatTimeModelImpl,
) : AffairWhatTimeModelEditor {
  override var timePair: MinuteTimePair = whatTimeModel.timePair.value
  override val dateList: AffairDateEditorListImpl =
    AffairDateEditorListImpl(
      affairModel,
      whatTimeModel,
      whatTimeModel.dateList.value.mapTo(mutableListOf()) {
        AffairDateModelEditorImpl(whatTimeModel, it.key)
      }
    )

  fun checkEnableUpdate(): String? {
    if (timePair.second < timePair.first) return "结束时间不能小于开始时间"
    return dateList.checkEnableUpdate()
  }

  fun update() {
    whatTimeModel.timePair.value = timePair
    dateList.update() // update 时内部需要进行去重
    whatTimeModel.dateList.value = dateList.origin.sortedBy { it.date }.associateBy(
      keySelector = { it.dateModel },
      valueTransform = { it.date },
    )
  }

  fun syncAffair(whatTime: AffairWhatTime) {
    timePair = whatTime.timePair
    dateList.syncAffair(whatTime.date)
  }

  fun createAffairWhatTime(): AffairWhatTime? {
    val dateList = dateList.createDateList()
    return if (dateList.isEmpty()) null else {
      AffairWhatTime(
        timePair = timePair,
        date = dateList
      )
    }
  }

  class AffairDateEditorListImpl(
    val affairModel: AffairItemModelImpl,
    val whatTimeModel: AffairWhatTimeModelImpl,
    val origin: MutableList<AffairDateModelEditorImpl>
  ) : AffairWhatTimeModelEditor.AffairDateModelEditorList, List<AffairDateModelEditor> by origin {

    private val addList = mutableListOf<AffairDateModelEditorImpl>()
    private val removeList = mutableListOf<AffairDateModelEditorImpl>()

    override fun add(date: Date): AffairDateModelEditor? {
      origin.fastFirstOrNull { it.date == date }?.let { return null } // 已有的话则直接返回 null
      val dateModel = AffairDateModelImpl(affairModel, whatTimeModel, date)
      val editor = AffairDateModelEditorImpl(whatTimeModel, dateModel)
      addList.add(editor)
      origin.add(editor)
      return editor
    }

    override fun add(date: AffairDateModelEditor): Boolean {
      date as AffairDateModelEditorImpl
      if (date.whatTimeModel === whatTimeModel) return true
      if (date.whatTimeModel != null) return false // 需要先移除才能添加
      if (origin.fastAny { it.date == date.date }) return false // 有相同日期则添加失败
      addList.add(date)
      removeList.remove(date)
      origin.add(date)
      date.whatTimeModel = whatTimeModel
      return true
    }

    override fun remove(date: AffairDateModelEditor): Boolean {
      date as AffairDateModelEditorImpl
      if (date.whatTimeModel !== whatTimeModel) return false
      if (origin.remove(date)) {
        removeList.add(date)
        addList.remove(date)
        date.whatTimeModel = null
        return true
      }
      return false
    }

    fun checkEnableUpdate(): String? {
      origin.fastForEach { dateModelEditorImpl ->
        dateModelEditorImpl.checkEnableUpdate()?.let { return it }
      }
      return null
    }

    fun update() {
      val set = HashSet<Date>()
      val iterator = origin.iterator()
      while (iterator.hasNext()) {
        val item = iterator.next()
        if (set.add(item.date)) {
          item.update()
          item.dateModel.whatTime.value = whatTimeModel
        } else {
          // 去重相同的日期
          iterator.remove()
          removeList.add(item)
          addList.remove(item)
          item.whatTimeModel = null
        }
      }
      // 通知已经被移除的 dateModelEditor
      removeList.forEach {
        if (it.whatTimeModel == null) {
          // it.whatTimeModel 为 null 表明没有被添加进其他 AffairDateEditorListImpl
          it.update()
          it.dateModel.delete()
        }
      }
    }

    fun syncAffair(list: List<Date>) {
      if (list.isEmpty()) {
        origin.fastForEach {
          removeList.add(it)
          addList.remove(it)
          it.whatTimeModel = null
        }
        origin.clear()
      } else if (origin.isEmpty()) {
        list.fastForEach { add(it) } // add 的时候会进行去重处理
      } else {
        // 先快速比较是否完全等价
        if (list.size == origin.size) {
          var same = true
          for (i in list.indices) {
            if (list[i] != origin[i].date) {
              same = false
              break
            }
          }
          if (same) return
        }
        // 再使用 Set 集合去重
        val newSet = list.toMutableSet()
        val oldSet = origin.associateByTo(LinkedHashMap()) { it.date }
        val newSetIterator = newSet.iterator()
        while (newSetIterator.hasNext()) {
          val new = newSetIterator.next()
          val old = oldSet.remove(new)
          if (old != null) {
            // newSet 和 oldSet 都包含相同 date
            newSetIterator.remove()
          }
        }
        // 剩余的 newSet 为新增的数据，oldSet 为被删除的数据
        newSet.forEach { add(it) }
        oldSet.forEach { remove(it.value) }
      }
    }

    fun createDateList(): List<Date> {
      return fastDistinctBy { it.date }.fastMapTo(mutableListOf()) { it.date }.apply { sort() }
    }
  }
}

class AffairDateModelEditorImpl(
  var whatTimeModel: AffairWhatTimeModelImpl?, // 仅用来标识在哪个 whatTimeModel 下
  val dateModel: AffairDateModelImpl,
) : AffairDateModelEditor {
  override var date: Date = dateModel.date.value

  fun checkEnableUpdate(): String? {
    return null
  }

  fun update() {
    dateModel.date.value = date
  }
}