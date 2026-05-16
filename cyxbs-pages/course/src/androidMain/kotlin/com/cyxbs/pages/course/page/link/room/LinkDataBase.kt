package com.cyxbs.pages.course.page.link.room

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.cyxbs.pages.course.page.link.bean.LinkStudent
import io.reactivex.rxjava3.core.Observable
import java.io.Serializable

/**
 * ...
 * @author 985892345 (Guo Xiangrui)
 * @email 2767465918@qq.com
 * @date 2022/4/23 14:40
 */
@Database(entities = [LinkStuEntity::class], version = 2)
abstract class LinkDataBase : RoomDatabase() {
  abstract fun getLinkStuDao(): LinkStuDao

  companion object {
    val INSTANCE by lazy {
      Room.databaseBuilder(
        com.cyxbs.components.init.appContext,
        LinkDataBase::class.java,
        "course_link_student_db"
      ).fallbackToDestructiveMigration(false).build()
    }
  }
}

@Entity(tableName = "link_stu")
data class LinkStuEntity(
  @PrimaryKey
  val selfNum: String, // 自己的学号
  val linkNum: String, // 关联人的学号
  val linkMajor: String, // 关联人的专业
  val linkName: String, // 关联人的姓名
  val isShowLink: Boolean, // 是否显示
) : Serializable {
  constructor(
    linkStu: LinkStudent,
    isShowLink: Boolean,
  ) : this(linkStu.selfNum, linkStu.linkNum, linkStu.major, linkStu.name, isShowLink)
  
  fun isNull(): Boolean = linkNum.isBlank() || selfNum.isBlank()
  fun isNotNull(): Boolean = !isNull()
  companion object {
    val NULL = LinkStuEntity("", "", "", "", isShowLink = false)
  }
}

@Dao
interface LinkStuDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  fun insertLinkStu(link: LinkStuEntity)
  
  @Update
  fun updateLinkStu(link: LinkStuEntity)

  @Query("SELECT * FROM link_stu WHERE selfNum = :selfNum")
  fun getLinkStu(selfNum: String): LinkStuEntity?
  
  @Query("SELECT * FROM link_stu WHERE selfNum = :selfNum")
  fun observeLinkStu(selfNum: String): Observable<LinkStuEntity>
}