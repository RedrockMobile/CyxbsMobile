package com.cyxbs.pages.course.find.model

import com.cyxbs.components.config.serializable.defaultJson
import com.cyxbs.components.config.sp.PreferencesSettings
import com.cyxbs.pages.course.find.bean.FindStuHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer

/**
 * 查找学生历史记录仓库（全局 PreferencesSettings 存储）
 *
 * 与老 Room HistoryDataBase 行为对齐：
 * - 最新点击放在最前
 * - 同学号会去重并提前
 * - 容量上限 20，超出截尾
 *
 * @author 985892345
 * @date 2026/5/27
 */
object FindHistoryRepository {

  private const val SETTINGS_KEY = "course_find_history"
  private const val DATA_KEY = "history"
  private const val MAX_SIZE = 20

  private val settings = PreferencesSettings.get(SETTINGS_KEY)
  private val serializer = ListSerializer(FindStuHistoryEntity.serializer())

  val state: StateFlow<List<FindStuHistoryEntity>> get() = _state
  private val _state = MutableStateFlow(load())

  fun add(entity: FindStuHistoryEntity) {
    val current = _state.value
    val next = buildList(current.size + 1) {
      add(entity)
      current.forEach { if (it.stuNum != entity.stuNum) add(it) }
    }.take(MAX_SIZE)
    persistAndEmit(next)
  }

  fun delete(stuNum: String) {
    val next = _state.value.filterNot { it.stuNum == stuNum }
    if (next.size == _state.value.size) return
    persistAndEmit(next)
  }

  fun clear() {
    persistAndEmit(emptyList())
  }

  private fun load(): List<FindStuHistoryEntity> {
    val raw = settings.getStringOrNull(DATA_KEY) ?: return emptyList()
    return runCatching { defaultJson.decodeFromString(serializer, raw) }
      .onFailure { settings.remove(DATA_KEY) }
      .getOrDefault(emptyList())
  }

  private fun persistAndEmit(list: List<FindStuHistoryEntity>) {
    settings.putString(DATA_KEY, defaultJson.encodeToString(serializer, list))
    _state.value = list
  }
}
