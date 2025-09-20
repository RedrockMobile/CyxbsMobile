package com.cyxbs.pages.course.page.find.room

import androidx.room.*
import com.cyxbs.pages.course.page.find.bean.FindStuBean
import com.cyxbs.pages.course.page.find.bean.FindTeaBean
import com.cyxbs.components.init.appContext
import io.reactivex.rxjava3.core.Observable
import kotlinx.coroutines.flow.Flow

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/4/20 17:12
 */
@Database(entities = [FindStuEntity::class, FindTeaEntity::class], version = 1)
abstract class HistoryDataBase : RoomDatabase() {
  abstract fun getStuDao(): FindStuDao
  abstract fun getTeaDao(): FindTeaDao
  
  companion object {
    val INSTANCE by lazy {
      Room.databaseBuilder(
        com.cyxbs.components.init.appContext,
        HistoryDataBase::class.java,
        "course_find_course_history_db"
      ).fallbackToDestructiveMigration().build()
    }
  }
}

sealed interface FindPersonEntity {
  val name: String
  val num: String
}

@Entity(tableName = "student")
data class FindStuEntity(
  override val name: String,
  @PrimaryKey
  override val num: String
) : FindPersonEntity {
  constructor(stu: FindStuBean) : this(stu.name, stu.stuNum)
}

@Dao
interface FindStuDao {
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertStu(stu: FindStuEntity)
  
  @Query("DELETE FROM student WHERE num in (:stuNum)")
  fun deleteStuFromNum(vararg stuNum: String)
  
  @Query("SELECT * FROM student")
  fun observeAllStu(): Observable<List<FindStuEntity>>
}

@Entity(tableName = "teacher")
data class FindTeaEntity(
  override val name: String,
  @PrimaryKey
  override val num: String
) : FindPersonEntity {
  constructor(tea: FindTeaBean) : this(tea.name, tea.teaNum)
}

@Dao
interface FindTeaDao {
  
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertTea(stu: FindTeaEntity)
  
  @Query("DELETE FROM teacher WHERE num in (:teaNum)")
  fun deleteTeaFromNum(vararg teaNum: String)
  
  @Query("SELECT * FROM teacher")
  fun observeAllTea(): Flow<List<FindTeaEntity>>
}