package com.cyxbs.pages.affair.room

import android.content.Context
import androidx.core.content.edit
import com.google.gson.reflect.TypeToken
import com.cyxbs.pages.affair.ui.adapter.data.AffairAdapterData
import com.cyxbs.pages.affair.ui.adapter.data.AffairTimeData
import com.cyxbs.pages.affair.ui.adapter.data.AffairWeekData
import com.cyxbs.components.utils.extensions.defaultGson
import com.google.gson.annotations.SerializedName
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/2 16:14
 */
object AffairDataBase {
  fun getAffairDao(): AffairDao = AffairDaoImpl
  fun getAffairCalendarDao(): AffairCalendarDao = AffairCalendarDaoImpl
  fun getLocalAddAffairDao(): LocalAddAffairDao = LocalAddAffairDaoImpl
  fun getLocalUpdateAffairDao(): LocalUpdateAffairDao = LocalUpdateAffairDaoImpl
  fun getLocalDeleteAffairDao(): LocalDeleteAffairDao = LocalDeleteAffairDaoImpl
}



////////////////////////////
//
//     用于桌面显示的事务表
//
////////////////////////////
/**
 * 用于删库后重新添加的残缺的实体类，因为插入时需要重新获取新的 [AffairEntity.onlyId]，所以要单独插入
 */
data class AffairIncompleteEntity(
  @SerializedName("remoteId")
  val remoteId: Int, // 后端的 id，因为存在本地临时事务，所以会发生改变
  @SerializedName("time")
  val time: Int, // 提醒时间
  @SerializedName("title")
  val title: String,
  @SerializedName("content")
  val content: String,
  @SerializedName("atWhatTime")
  val atWhatTime: List<AffairEntity.AtWhatTime>
): Serializable {
  fun toEntity(stuNum: String, onlyId: Int): AffairEntity {
    return AffairEntity(
      stuNum = stuNum,
      onlyId = onlyId,
      remoteId = remoteId,
      time = time,
      title = title,
      content = content,
      atWhatTime = atWhatTime
    )
  }
}

data class AffairEntity(
  @SerializedName("stuNum")
  val stuNum: String,
  @SerializedName("onlyId")
  val onlyId: Int, // 本地的唯一 id，由我们端上给出
  @SerializedName("remoteId")
  val remoteId: Int, // 后端的 id，如果小于 0，则说明是本地临时添加的事务，并且可能会发生改变，业务侧不建议使用
  @SerializedName("time")
  val time: Int, // 提醒时间
  @SerializedName("title")
  val title: String,
  @SerializedName("content")
  val content: String,
  @SerializedName("atWhatTime")
  val atWhatTime: List<AtWhatTime>
): Serializable {
  
  companion object {
    /**
     * 表示没有上传到远端的事务的 [remoteId]
     */
    val LocalRemoteId = -114514
  }

  data class AtWhatTime(
    @SerializedName("beginLesson")
    val beginLesson: Int, // 开始节数，如：1、2 节课以 1 开始；3、4 节课以 3 开始，注意：中午是以 -1 开始，傍晚是以 -2 开始
    @SerializedName("day")
    val day: Int, // 星期数，星期一为 0
    @SerializedName("period")
    val period: Int, // 长度
    @SerializedName("week")
    val week: List<Int> // 在哪几周，特别注意：整学期的 week 为 0
  ): Serializable

  // 将数据库的类转化为要展示的类
  fun toAffairAdapterData(): List<AffairAdapterData> {
    val newList = arrayListOf<AffairAdapterData>()
    val affairList = atWhatTime
    affairList[0].week.forEach { newList.add(AffairWeekData(it)) }
    affairList.forEach { newList.add(AffairTimeData(it.day, it.beginLesson, it.period)) }
    return newList
  }
}

object AffairDaoImpl : AffairDao() {

  private val affairSp = com.cyxbs.components.init.appContext.getSharedPreferences("stu_affair", Context.MODE_PRIVATE)

  private val observerMap = ConcurrentHashMap<String, BehaviorSubject<List<AffairEntity>>>()

  private val entityListType = object : TypeToken<List<AffairEntity>>() {}.type

  private val synchronizedObject = SynchronizedObject()

  override fun getAffairByStuNum(stuNum: String): List<AffairEntity> {
    synchronized(synchronizedObject) {
      val cache = observerMap[stuNum]
      if (cache != null) {
        return cache.value ?: emptyList()
      }
      return getAffairByStuNumFromSp(stuNum)
    }
  }

  private fun getAffairByStuNumFromSp(stuNum: String): List<AffairEntity> {
    return affairSp.getString(stuNum, null)?.let {
      runCatching<List<AffairEntity>> {
        defaultGson.fromJson(it, entityListType)
      }.onFailure {
        affairSp.edit {
          remove(stuNum)
        }
      }.getOrNull()
    } ?: emptyList()
  }

  override fun findAffairByOnlyId(
    stuNum: String,
    onlyId: Int
  ): Maybe<AffairEntity> {
    val entity = getAffairByStuNum(stuNum).firstOrNull {
      it.onlyId == onlyId
    }
    return if (entity != null) {
      Maybe.just(entity)
    } else {
      Maybe.empty()
    }
  }

  override fun observeAffair(stuNum: String): Observable<List<AffairEntity>> {
    synchronized(synchronizedObject) {
      return observerMap.getOrPut(stuNum) {
        val list = getAffairByStuNumFromSp(stuNum)
        BehaviorSubject.createDefault(list)
      }
    }
  }

  override fun deleteAffair(stuNum: String, onlyId: Int): AffairEntity? {
    synchronized(synchronizedObject) {
      val list = getAffairByStuNum(stuNum).toMutableList()
      val index = list.indexOfFirst { it.onlyId == onlyId }
      val entity = list.removeAt(index)
      affairSp.edit {
        putString(stuNum, defaultGson.toJson(list, entityListType))
      }
      observerMap[stuNum]?.onNext(list)
      return entity
    }
  }

  override fun updateAffair(affair: AffairEntity) {
    synchronized(synchronizedObject) {
      val list = getAffairByStuNum(affair.stuNum).toMutableList()
      val index = list.indexOfFirst { it.onlyId == affair.onlyId }
      if (index == -1) return
      list[index] = affair
      affairSp.edit {
        putString(affair.stuNum, defaultGson.toJson(list, entityListType))
      }
      observerMap[affair.stuNum]?.onNext(list)
    }
  }

  override fun deleteAffair(affair: AffairEntity) {
    deleteAffair(stuNum = affair.stuNum, onlyId = affair.onlyId)
  }

  override fun insertAffair(affair: AffairEntity) {
    synchronized(synchronizedObject) {
      val list = getAffairByStuNum(affair.stuNum).toMutableList()
      val index = list.indexOfFirst { it.onlyId == affair.onlyId }
      if (index == -1) {
        list.add(affair)
      } else {
        list[index] = affair
      }
      affairSp.edit {
        putString(affair.stuNum, defaultGson.toJson(list, entityListType))
      }
      observerMap[affair.stuNum]?.onNext(list)
    }
  }

  override fun updateRemoteId(stuNum: String, onlyId: Int, newRemoteId: Int) {
    synchronized(synchronizedObject) {
      val list = getAffairByStuNum(stuNum).toMutableList()
      val index = list.indexOfFirst { it.onlyId == onlyId }
      if (index == -1) return
      list[index] = list[index].copy(remoteId = newRemoteId)
      affairSp.edit {
        putString(stuNum, defaultGson.toJson(list, entityListType))
      }
      observerMap[stuNum]?.onNext(list)
    }
  }

  override fun insertAffair(
    stuNum: String,
    incompleteEntity: AffairIncompleteEntity
  ): AffairEntity {
    synchronized(synchronizedObject) {
      val list = getAffairByStuNum(stuNum)
      val maxOnlyId = list.maxOfOrNull { it.onlyId } ?: 0
      val newEntity = incompleteEntity.toEntity(stuNum, maxOnlyId + 1)
      insertAffair(newEntity)
      return newEntity
    }
  }

  override fun resetData(
    stuNum: String,
    incompleteEntity: List<AffairIncompleteEntity>
  ): List<AffairEntity> {
    synchronized(synchronizedObject) {
      val list = getAffairByStuNum(stuNum)
      val maxOnlyId = list.maxOfOrNull { it.onlyId } ?: 0
      var onlyId = maxOnlyId + 1
      val newList = incompleteEntity.map { it.toEntity(stuNum, onlyId++) }
      affairSp.edit {
        putString(stuNum, defaultGson.toJson(newList, entityListType))
      }
      observerMap[stuNum]?.onNext(newList)
      return newList
    }
  }


}

abstract class AffairDao {

  abstract fun getAffairByStuNum(stuNum: String): List<AffairEntity>

  abstract fun findAffairByOnlyId(stuNum: String, onlyId: Int): Maybe<AffairEntity>

  abstract fun observeAffair(stuNum: String): Observable<List<AffairEntity>>
  
  abstract fun deleteAffair(stuNum: String, onlyId: Int): AffairEntity?
  
  abstract fun updateAffair(affair: AffairEntity)
  
  // 内部使用
  protected abstract fun deleteAffair(affair: AffairEntity)
  
  // 内部使用
  protected abstract fun insertAffair(affair: AffairEntity)

  /**
   * 更新旧事务的 id
   */
  abstract fun updateRemoteId(stuNum: String, onlyId: Int, newRemoteId: Int)
  
  /**
   * 返回当前插入的 [AffairEntity]
   */
  abstract fun insertAffair(
    stuNum: String,
    incompleteEntity: AffairIncompleteEntity
  ) : AffairEntity
  
  /**
   * 重新设置数据，先删除，再插入
   */
  abstract fun resetData(
    stuNum: String,
    incompleteEntity: List<AffairIncompleteEntity>
  ) : List<AffairEntity>
}


////////////////////////////
//
//    事务与手机日历对应表
//
////////////////////////////
data class AffairCalendarEntity(
  val onlyId: Int,
  val eventIdList: List<Long> // 手机日历的 id
)

object AffairCalendarDaoImpl : AffairCalendarDao() {

  private val affairSp = com.cyxbs.components.init.appContext.getSharedPreferences("stu_affair_calendar", Context.MODE_PRIVATE)

  private val longListType = object : TypeToken<List<Long>>() {}.type

  private val cacheMap = mutableMapOf<Int, List<Long>>()

  private val synchronizedObject = SynchronizedObject()

  override fun insert(entity: AffairCalendarEntity) {
    synchronized(synchronizedObject) {
      cacheMap[entity.onlyId] = entity.eventIdList
      affairSp.edit {
        putString(entity.onlyId.toString(), defaultGson.toJson(entity, longListType))
      }
    }
  }

  override fun remove(onlyId: Int): List<Long> {
    synchronized(synchronizedObject) {
      val list = cacheMap.remove(onlyId) ?: emptyList()
      affairSp.edit {
        remove(onlyId.toString())
      }
      return list
    }
  }

}

abstract class AffairCalendarDao {
  
  abstract fun insert(entity: AffairCalendarEntity)

  abstract fun remove(onlyId: Int): List<Long>
}


////////////////////////////
//
//     临时添加事务表
//
////////////////////////////
data class LocalAddAffairEntity(
  @SerializedName("stuNum")
  val stuNum: String,
  @SerializedName("onlyId")
  val onlyId: Int,
  @SerializedName("time")
  val time: Int,
  @SerializedName("title")
  val title: String,
  @SerializedName("content")
  val content: String,
  @SerializedName("dateJson")
  val dateJson: String
) : Serializable

object LocalAddAffairDaoImpl : LocalAddAffairDao() {
  private val affairSp = com.cyxbs.components.init.appContext.getSharedPreferences("stu_affair_local_add", Context.MODE_PRIVATE)
  private val entityListType = object : TypeToken<List<LocalAddAffairEntity>>() {}.type

  private val cacheMap = mutableMapOf<String, List<LocalAddAffairEntity>>()

  private val synchronizedObject = SynchronizedObject()

  override fun insertLocalAddAffair(affair: LocalAddAffairEntity) {
    synchronized(synchronizedObject) {
      val list = getLocalAddAffair(affair.stuNum).toMutableList()
      val index = list.indexOfFirst { it.onlyId == affair.onlyId }
      if (index != -1) {
        list[index] = affair
      } else {
        list.add(affair)
      }
      cacheMap[affair.stuNum] = list
      affairSp.edit {
        putString(affair.stuNum, defaultGson.toJson(list, entityListType))
      }
    }
  }

  override fun updateLocalAddAffair(affair: LocalAddAffairEntity) {
    synchronized(synchronizedObject) {
      val list = getLocalAddAffair(affair.stuNum).toMutableList()
      val index = list.indexOfFirst { it.onlyId == affair.onlyId }
      if (index == -1) return
      list[index] = affair
      cacheMap[affair.stuNum] = list
      affairSp.edit {
        putString(affair.stuNum, defaultGson.toJson(list, entityListType))
      }
    }
  }

  override fun deleteLocalAddAffair(stuNum: String, onlyId: Int) {
    synchronized(synchronizedObject) {
      val list = getLocalAddAffair(stuNum).toMutableList()
      val index = list.indexOfFirst { it.onlyId == onlyId }
      if (index == -1) return
      list.removeAt(index)
      cacheMap[stuNum] = list
      affairSp.edit {
        putString(stuNum, defaultGson.toJson(list, entityListType))
      }
    }
  }

  override fun findLocalAddAffair(
    stuNum: String,
    onlyId: Int
  ): LocalAddAffairEntity? {
    return getLocalAddAffair(stuNum).firstOrNull { it.onlyId == onlyId }
  }

  override fun getLocalAddAffair(stuNum: String): List<LocalAddAffairEntity> {
    synchronized(synchronizedObject) {
      val cache = cacheMap[stuNum]
      if (cache != null) {
        return cache
      }
      return getLocalAffAffairFromSp(stuNum)
    }
  }

  private fun getLocalAffAffairFromSp(stuNum: String): List<LocalAddAffairEntity> {
    return affairSp.getString(stuNum, null)?.let {
      runCatching {
        defaultGson.fromJson<List<LocalAddAffairEntity>>(it, entityListType)
      }.onFailure {
        affairSp.edit {
          remove(stuNum)
        }
      }.getOrNull()
    } ?: emptyList()
  }

  override fun deleteLocalAddAffair(affair: LocalAddAffairEntity) {
    deleteLocalAddAffair(affair.stuNum, affair.onlyId)
  }

}

abstract class LocalAddAffairDao {
  
  abstract fun insertLocalAddAffair(affair: LocalAddAffairEntity)
  
  abstract fun updateLocalAddAffair(affair: LocalAddAffairEntity)
  
  abstract fun deleteLocalAddAffair(stuNum: String, onlyId: Int)
  
  abstract fun findLocalAddAffair(stuNum: String, onlyId: Int): LocalAddAffairEntity?
  
  abstract fun getLocalAddAffair(stuNum: String): List<LocalAddAffairEntity>
  
  abstract fun deleteLocalAddAffair(affair: LocalAddAffairEntity)
}


////////////////////////////
//
//     临时更新事务表
//
////////////////////////////
data class LocalUpdateAffairEntity(
  @SerializedName("stuNum")
  val stuNum: String,
  @SerializedName("onlyId")
  val onlyId: Int,
  @SerializedName("remoteId")
  val remoteId: Int,
  @SerializedName("time")
  val time: Int,
  @SerializedName("title")
  val title: String,
  @SerializedName("content")
  val content: String,
  @SerializedName("dateJson")
  val dateJson: String
): Serializable

object LocalUpdateAffairDaoImpl : LocalUpdateAffairDao() {
  private val affairSp = com.cyxbs.components.init.appContext.getSharedPreferences("stu_affair_local_update", Context.MODE_PRIVATE)
  private val entityListType = object : TypeToken<List<LocalUpdateAffairEntity>>() {}.type
  private val cacheMap = mutableMapOf<String, List<LocalUpdateAffairEntity>>()

  private val synchronizedObject = SynchronizedObject()

  override fun insertLocalUpdateAffair(affair: LocalUpdateAffairEntity) {
    synchronized(synchronizedObject) {
      val list = getLocalUpdateAffair(affair.stuNum).toMutableList()
      val index = list.indexOfFirst { it.onlyId == affair.onlyId }
      if (index != -1) {
        list[index] = affair
      } else {
        list.add(affair)
      }
      cacheMap[affair.stuNum] = list
      affairSp.edit {
        putString(affair.stuNum, defaultGson.toJson(list, entityListType))
      }
    }
  }

  override fun updateLocalUpdateAffair(affair: LocalUpdateAffairEntity) {
    synchronized(synchronizedObject) {
      val list = getLocalUpdateAffair(affair.stuNum).toMutableList()
      val index = list.indexOfFirst { it.onlyId == affair.onlyId }
      if (index == -1) return
      list[index] = affair
      cacheMap[affair.stuNum] = list
      affairSp.edit {
        putString(affair.stuNum, defaultGson.toJson(list, entityListType))
      }
    }
  }

  override fun deleteLocalUpdateAffair(stuNum: String, onlyId: Int) {
    synchronized(synchronizedObject) {
      val list = getLocalUpdateAffair(stuNum).toMutableList()
      val index = list.indexOfFirst { it.onlyId == onlyId }
      if (index == -1) return
      list.removeAt(index)
      cacheMap[stuNum] = list
      affairSp.edit {
        putString(stuNum, defaultGson.toJson(list, entityListType))
      }
    }
  }

  override fun getLocalUpdateAffair(stuNum: String): List<LocalUpdateAffairEntity> {
    synchronized(synchronizedObject) {
      val cache = cacheMap[stuNum]
      if (cache != null) {
        return cache
      }
      return getLocalAffAffairFromSp(stuNum)
    }
  }

  override fun deleteLocalUpdateAffair(affair: LocalUpdateAffairEntity) {
    deleteLocalUpdateAffair(affair.stuNum, affair.onlyId)
  }

  private fun getLocalAffAffairFromSp(stuNum: String): List<LocalUpdateAffairEntity> {
    return affairSp.getString(stuNum, null)?.let {
      runCatching {
        defaultGson.fromJson<List<LocalUpdateAffairEntity>>(it, entityListType)
      }.onFailure {
        affairSp.edit {
          remove(stuNum)
        }
      }.getOrNull()
    } ?: emptyList()
  }
}

abstract class LocalUpdateAffairDao {
  
  abstract fun insertLocalUpdateAffair(affair: LocalUpdateAffairEntity)
  
  abstract fun updateLocalUpdateAffair(affair: LocalUpdateAffairEntity)
  
  abstract fun deleteLocalUpdateAffair(stuNum: String, onlyId: Int)
  
  abstract fun getLocalUpdateAffair(stuNum: String): List<LocalUpdateAffairEntity>
  
  abstract fun deleteLocalUpdateAffair(affair: LocalUpdateAffairEntity)
}


////////////////////////////
//
//     临时删除事务表
//
////////////////////////////
data class LocalDeleteAffairEntity(
  val stuNum: String,
  val onlyId: Int,
  val remoteId: Int
) : Serializable

object LocalDeleteAffairDaoImpl : LocalDeleteAffairDao() {
  private val affairSp = com.cyxbs.components.init.appContext.getSharedPreferences("stu_affair_local_delete", Context.MODE_PRIVATE)
  private val entityListType = object : TypeToken<List<LocalDeleteAffairEntity>>() {}.type
  private val cacheMap = mutableMapOf<String, List<LocalDeleteAffairEntity>>()

  private val synchronizedObject = SynchronizedObject()

  override fun insertLocalDeleteAffair(affair: LocalDeleteAffairEntity) {
    synchronized(synchronizedObject) {
      val list = getLocalDeleteAffair(affair.stuNum).toMutableList()
      val index = list.indexOfFirst { it.onlyId == affair.onlyId }
      if (index != -1) {
        list[index] = affair
      } else {
        list.add(affair)
      }
      cacheMap[affair.stuNum] = list
      affairSp.edit {
        putString(affair.stuNum, defaultGson.toJson(list, entityListType))
      }
    }
  }

  override fun getLocalDeleteAffair(stuNum: String): List<LocalDeleteAffairEntity> {
    synchronized(synchronizedObject) {
      val cache = cacheMap[stuNum]
      if (cache != null) {
        return cache
      }
      return getLocalAffAffairFromSp(stuNum)
    }
  }

  override fun deleteLocalDeleteAffair(affair: LocalDeleteAffairEntity) {
    synchronized(synchronizedObject) {
      val list = getLocalDeleteAffair(affair.stuNum).toMutableList()
      val index = list.indexOfFirst { it.onlyId == affair.onlyId }
      list.removeAt(index)
      cacheMap[affair.stuNum] = list
      affairSp.edit {
        putString(affair.stuNum, defaultGson.toJson(list, entityListType))
      }
    }
  }

  private fun getLocalAffAffairFromSp(stuNum: String): List<LocalDeleteAffairEntity> {
    return affairSp.getString(stuNum, null)?.let {
      runCatching {
        defaultGson.fromJson<List<LocalDeleteAffairEntity>>(it, entityListType)
      }.onFailure {
        affairSp.edit {
          remove(stuNum)
        }
      }.getOrNull()
    } ?: emptyList()
  }

}

abstract class LocalDeleteAffairDao {
  
  abstract fun insertLocalDeleteAffair(affair: LocalDeleteAffairEntity)
  
  abstract fun getLocalDeleteAffair(stuNum: String): List<LocalDeleteAffairEntity>
  
  abstract fun deleteLocalDeleteAffair(affair: LocalDeleteAffairEntity)
}