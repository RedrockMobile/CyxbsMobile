package com.cyxbs.pages.affair.room

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.cyxbs.pages.affair.ui.adapter.data.AffairAdapterData
import com.cyxbs.pages.affair.ui.adapter.data.AffairTimeData
import com.cyxbs.pages.affair.ui.adapter.data.AffairWeekData
import com.cyxbs.components.init.appContext
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/5/2 16:14
 */
@Database(
  entities = [
    AffairEntity::class,
    AffairCalendarEntity::class,
    LocalAddAffairEntity::class,
    LocalUpdateAffairEntity::class,
    LocalDeleteAffairEntity::class,
             ],
  version = 2
)
abstract class AffairDataBase : RoomDatabase() {
  abstract fun getAffairDao(): AffairDao
  abstract fun getAffairCalendarDao(): AffairCalendarDao
  abstract fun getLocalAddAffairDao(): LocalAddAffairDao
  abstract fun getLocalUpdateAffairDao(): LocalUpdateAffairDao
  abstract fun getLocalDeleteAffairDao(): LocalDeleteAffairDao

  companion object {
    val INSTANCE by lazy {
      Room.databaseBuilder(
        com.cyxbs.components.init.appContext,
        AffairDataBase::class.java,
        "course_affair_db"
      ).fallbackToDestructiveMigration().build()
    }
  }
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
  val remoteId: Int, // 后端的 id，因为存在本地临时事务，所以会发生改变
  val time: Int, // 提醒时间
  val title: String,
  val content: String,
  val atWhatTime: List<AffairEntity.AtWhatTime>
)

@TypeConverters(AffairEntity.AtWhatTimeConverter::class)
@Entity(tableName = "affair", primaryKeys = ["stuNum", "onlyId"])
data class AffairEntity(
  val stuNum: String,
  val onlyId: Int, // 本地的唯一 id，由我们端上给出
  val remoteId: Int, // 后端的 id，如果小于 0，则说明是本地临时添加的事务，并且可能会发生改变，业务侧不建议使用
  val time: Int, // 提醒时间
  val title: String,
  val content: String,
  val atWhatTime: List<AtWhatTime>
) {
  
  companion object {
    /**
     * 表示没有上传到远端的事务的 [remoteId]
     */
    val LocalRemoteId = -114514
  }

  data class AtWhatTime(
    val beginLesson: Int, // 开始节数，如：1、2 节课以 1 开始；3、4 节课以 3 开始，注意：中午是以 -1 开始，傍晚是以 -2 开始
    val day: Int, // 星期数，星期一为 0
    val period: Int, // 长度
    val week: List<Int> // 在哪几周，特别注意：整学期的 week 为 0
  )

  class AtWhatTimeConverter {
    companion object {
      private val GSON = Gson()
    }

    @TypeConverter
    fun listToString(list: List<AtWhatTime>): String {
      return GSON.toJson(list)
    }

    @TypeConverter
    fun stringToList(string: String): List<AtWhatTime> {
      return GSON.fromJson(string, object : TypeToken<List<AtWhatTime>>() {}.type)
    }
  }
  
  // 将数据库的类转化为要展示的类
  fun toAffairAdapterData(): List<AffairAdapterData> {
    val newList = arrayListOf<AffairAdapterData>()
    val affairList = atWhatTime
    affairList[0].week.forEach { newList.add(AffairWeekData(it)) }
    affairList.forEach { newList.add(AffairTimeData(it.day, it.beginLesson, it.period)) }
    return newList
  }
}


@Dao
abstract class AffairDao {

  @Query("SELECT * FROM affair WHERE stuNum = :stuNum")
  abstract fun getAffairByStuNum(stuNum: String): List<AffairEntity>

  @Query("SELECT * FROM affair WHERE stuNum = :stuNum AND onlyId = :onlyId")
  abstract fun findAffairByOnlyId(stuNum: String, onlyId: Int): Maybe<AffairEntity>

  @Query("SELECT * FROM affair WHERE stuNum = :stuNum")
  abstract fun observeAffair(stuNum: String): Observable<List<AffairEntity>>
  
  @Query("DELETE FROM affair WHERE stuNum = :stuNum AND onlyId = :onlyId")
  abstract fun deleteAffair(stuNum: String, onlyId: Int)
  
  @Update
  abstract fun updateAffair(affair: AffairEntity)
  
  // 内部使用
  @Delete
  protected abstract fun deleteAffair(affair: AffairEntity)
  
  // 内部使用
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  protected abstract fun insertAffair(affair: AffairEntity)

  @Transaction
  open fun deleteAffairReturn(stuNum: String, onlyId: Int): AffairEntity? {
    return findAffairByOnlyId(stuNum, onlyId)
      .doOnSuccess {
        deleteAffair(it)
      }.blockingGet()
  }
  
  /**
   * 更新旧事务的 id
   */
  @Transaction
  open fun updateRemoteId(stuNum: String, onlyId: Int, newRemoteId: Int) {
    findAffairByOnlyId(stuNum, onlyId)
      .doOnSuccess {
        updateAffair(it.copy(remoteId = newRemoteId))
      }.blockingGet()
  }
  
  @Query("SELECT MAX(onlyId) FROM affair WHERE stuNum = :stuNum")
  protected abstract fun getMaxOnlyId(stuNum: String): Int
  
  /**
   * 提供一个唯一的 [AffairEntity.onlyId]
   */
  private fun getNewOnlyId(stuNum: String): Int {
    return getMaxOnlyId(stuNum) + 1
  }
  
  /**
   * 返回当前插入的 [AffairEntity]
   */
  @Transaction
  open fun insertAffair(
    stuNum: String,
    incompleteEntity: AffairIncompleteEntity
  ) : AffairEntity {
    val onlyId = getNewOnlyId(stuNum)
    val entity = AffairEntity(
      stuNum,
      onlyId,
      incompleteEntity.remoteId,
      incompleteEntity.time,
      incompleteEntity.title,
      incompleteEntity.content,
      incompleteEntity.atWhatTime
    )
    insertAffair(entity)
    return entity
  }
  
  // 内部使用
  @Query("DELETE FROM affair WHERE stuNum = :stuNum")
  protected abstract fun deleteAffairByStuNum(stuNum: String)
  
  /**
   * 重新设置数据，先删除，再插入
   */
  @Transaction
  open fun resetData(
    stuNum: String,
    incompleteEntity: List<AffairIncompleteEntity>
  ) : List<AffairEntity> {
    deleteAffairByStuNum(stuNum)
    return incompleteEntity.map {
      insertAffair(stuNum, it)
    }
  }
}


////////////////////////////
//
//    事务与手机日历对应表
//
////////////////////////////
@Entity(tableName = "affair_calendar")
@TypeConverters(AffairCalendarEntity.CalendarIdConverter::class)
data class AffairCalendarEntity(
  @PrimaryKey
  val onlyId: Int,
  val eventIdList: List<Long> // 手机日历的 id
) {
  class CalendarIdConverter {
    @TypeConverter
    fun listToString(list: List<Long>): String {
      return list.joinToString("*&*")
    }
    @TypeConverter
    fun stringToList(string: String): List<Long> {
      return string.split("*&*").map { it.toLong() }
    }
  }
}

@Dao
abstract class AffairCalendarDao {
  
  @Insert
  abstract fun insert(entity: AffairCalendarEntity)
  
  // 内部使用
  @Query("SELECT * FROM affair_calendar WHERE onlyId = :onlyId")
  protected abstract fun get(onlyId: Int): AffairCalendarEntity?
  
  // 内部使用
  @Query("DELETE FROM affair_calendar WHERE onlyId = :onlyId")
  protected abstract fun delete(onlyId: Int)
  
  @Transaction
  open fun remove(onlyId: Int): List<Long> {
    val list = get(onlyId)
    delete(onlyId)
    return list?.eventIdList ?: emptyList()
  }
}


////////////////////////////
//
//     临时添加事务表
//
////////////////////////////
@Entity(tableName = "affair_local_add", primaryKeys = ["stuNum", "onlyId"])
data class LocalAddAffairEntity(
  val stuNum: String,
  val onlyId: Int,
  val time: Int,
  val title: String,
  val content: String,
  val dateJson: String
)

@Dao
abstract class LocalAddAffairDao {
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract fun insertLocalAddAffair(affair: LocalAddAffairEntity)
  
  @Update
  abstract fun updateLocalAddAffair(affair: LocalAddAffairEntity)
  
  @Query("DELETE FROM affair_local_add WHERE stuNum = :stuNum AND onlyId = :onlyId")
  abstract fun deleteLocalAddAffair(stuNum: String, onlyId: Int)
  
  @Query("SELECT * FROM affair_local_add WHERE stuNum = :stuNum AND onlyId = :onlyId")
  abstract fun findLocalAddAffair(stuNum: String, onlyId: Int): LocalAddAffairEntity?
  
  @Query("SELECT * FROM affair_local_add WHERE stuNum = :stuNum")
  abstract fun getLocalAddAffair(stuNum: String): List<LocalAddAffairEntity>
  
  @Delete
  abstract fun deleteLocalAddAffair(affair: LocalAddAffairEntity)
}


////////////////////////////
//
//     临时更新事务表
//
////////////////////////////
@Entity(tableName = "affair_local_update", primaryKeys = ["stuNum", "onlyId"])
data class LocalUpdateAffairEntity(
  val stuNum: String,
  val onlyId: Int,
  val remoteId: Int,
  val time: Int,
  val title: String,
  val content: String,
  val dateJson: String
)

@Dao
abstract class LocalUpdateAffairDao {
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract fun insertLocalUpdateAffair(affair: LocalUpdateAffairEntity)
  
  @Update
  abstract fun updateLocalUpdateAffair(affair: LocalUpdateAffairEntity)
  
  @Query("DELETE FROM affair_local_update WHERE stuNum = :stuNum AND onlyId = :onlyId")
  abstract fun deleteLocalUpdateAffair(stuNum: String, onlyId: Int)
  
  @Query("SELECT * FROM affair_local_update WHERE stuNum = :stuNum")
  abstract fun getLocalUpdateAffair(stuNum: String): List<LocalUpdateAffairEntity>
  
  @Delete
  abstract fun deleteLocalUpdateAffair(affair: LocalUpdateAffairEntity)
}


////////////////////////////
//
//     临时删除事务表
//
////////////////////////////
@Entity(tableName = "affair_local_delete", primaryKeys = ["stuNum", "onlyId"])
data class LocalDeleteAffairEntity(
  val stuNum: String,
  val onlyId: Int,
  val remoteId: Int
)

@Dao
abstract class LocalDeleteAffairDao {
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract fun insertLocalDeleteAffair(affair: LocalDeleteAffairEntity)
  
  @Query("SELECT * FROM affair_local_delete WHERE stuNum = :stuNum")
  abstract fun getLocalDeleteAffair(stuNum: String): List<LocalDeleteAffairEntity>
  
  @Delete
  abstract fun deleteLocalDeleteAffair(affair: LocalDeleteAffairEntity)
}