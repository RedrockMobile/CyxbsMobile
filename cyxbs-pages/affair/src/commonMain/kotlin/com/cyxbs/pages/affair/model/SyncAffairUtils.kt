package com.cyxbs.pages.affair.model

import androidx.compose.ui.util.fastDistinctBy
import com.cyxbs.components.config.isDebug
import com.cyxbs.components.config.time.Date
import com.cyxbs.components.utils.extensions.showExceptionDialog
import com.cyxbs.pages.affair.api.AffairIdModelEditor
import com.cyxbs.pages.affair.api.AffairWhatTimeModelEditor
import com.cyxbs.pages.affair.bean.AffairEntity
import com.cyxbs.pages.affair.bean.AffairWhatTime
import com.cyxbs.pages.affair.model.impl.AffairGroupModelImpl
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * .
 *
 * @author 985892345
 * @date 2025/8/3
 */
object SyncAffairUtils {

  suspend fun syncAffair(affairs: List<AffairEntity>, groupModel: AffairGroupModelImpl) {
    // 本地数据与远端数据都有数据，但数据存在差异，需要进行比较
    // 先通过 remoteId 整合起来比较单个事务
    val oldMap = groupModel.itemList.value.associateByTo(LinkedHashMap()) { it.remoteId.value }
    oldMap.remove(0) // 本地临时事务不参与更新
    val newMap = affairs.associateByTo(LinkedHashMap()) { it.remoteId }
    val newMapIterator = newMap.iterator()
    while (newMapIterator.hasNext()) {
      val newItem = newMapIterator.next()
      val oldItem = oldMap.remove(newItem.key)
      if (oldItem != null) {
        // newMap 和 oldMap 都包含相同 id 的事务
        newMapIterator.remove() // 从 newMap 中移除
        if (newItem.value == oldItem.entity) continue // 如果 entity 相等，则无需更新
        // 同步本地数据为远端数据
        val editor = oldItem.createEditorSuspend()
        syncAffairEntity(editor, newItem.value)
        editor.commit(needUpload = false).onFailure {
          // 在同步为远端事务时正常来说不应该会出现失败的情况，
          // 并且这里 needUpload = false 也不会是请求失败，只可能是事务本身出现了问题，我们抛弃该事务的更新
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
    supervisorScope {
      oldMap.forEach {
        launch {
          val editor = it.value.createEditorSuspend()
          editor.clear() // 清空即表示删除
          editor.commit(
            needUpload = false, // 不上传
          )
        }
      }
    }
    newMap.forEach {
      val editor = groupModel.createAddAffairEditor(
        remoteId = it.value.remoteId
      )
      syncAffairEntity(editor, it.value)
      editor.commit(needUpload = false) // 不上传
    }
  }


  private fun syncAffairEntity(editor: AffairIdModelEditor, affair: AffairEntity) {
    editor.setTitle(affair.title)
    editor.setContent(affair.content)
    editor.setRemindTime(affair.remindTime)
    syncAffairWhatTimeList(editor, affair.whatTime)
  }

  private fun syncAffairWhatTimeList(editor: AffairIdModelEditor, list: List<AffairWhatTime>) {
    if (list.isEmpty()) {
      editor.clear()
    } else if (editor.whatTimeDate.isEmpty()) {
      // 这里需要聚合一下 timePair 的数据
      list.groupBy(
        keySelector = { it.timePair },
        valueTransform = { it.date }
      ).forEach {
        editor.add(it.key)?.let { whatTimeModelEditor ->
          syncDateList(whatTimeModelEditor, it.value.flatten())
        }
      }
    } else {
      val newMapByTimePair = list.groupByTo(
        destination = LinkedHashMap(),
        keySelector = { it.timePair }, // 去重相同 timePair
        valueTransform = { it.date }
      ).mapValuesTo(LinkedHashMap()) { entry ->
        AffairWhatTime(
          timePair = entry.key,
          date = entry.value.flatten().fastDistinctBy { it }.sorted(),
        )
      }
      val oldMapByTimePair = editor.whatTimeDate.entries.associateByTo(
        destination = LinkedHashMap(),
        keySelector = { it.key.timePair },
        valueTransform = { it.key }
      )

      // 以 timePair 比较新旧数据
      val newSetIterator = newMapByTimePair.iterator()
      while (newSetIterator.hasNext()) {
        val new = newSetIterator.next()
        val old = oldMapByTimePair.remove(new.key)
        if (old != null) {
          // newSet 和 oldSet 都包含相同 timePair
          newSetIterator.remove()
          syncAffairWhatTime(old, new.value)
        }
      }
      // newMapByTimePair 剩下的为新增的数据
      // oldMapByTimePair 剩下的为被删除的数据
      // timePair 可能会发生改变，但是 dateList 却不变，但这种情况比较少见，就不考虑了
      oldMapByTimePair.forEach {
        editor.remove(it.value)
      }
      newMapByTimePair.forEach {
        editor.add(it.key)?.let { whatTimeModelEditor ->
          syncDateList(whatTimeModelEditor, it.value.date)
        }
      }
    }
  }

  private fun syncAffairWhatTime(editor: AffairWhatTimeModelEditor, affair: AffairWhatTime) {
    editor.setTimePair(affair.timePair)
    syncDateList(editor, affair.date)
  }

  private fun syncDateList(editor: AffairWhatTimeModelEditor, dateList: List<Date>) {
    if (dateList.isEmpty()) {
      editor.clear()
    } else if (editor.dateList.isEmpty()) {
      dateList.forEach {
        editor.add(it)
      }
    } else {
      // 再使用 Set 集合去重
      val newSet = dateList.toMutableSet()
      val oldSet = editor.dateList.associateByTo(LinkedHashMap()) { it.date }
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
      newSet.forEach {
        editor.add(it)
      }
      oldSet.forEach {
        editor.remove(it.value)
      }
    }
  }
}