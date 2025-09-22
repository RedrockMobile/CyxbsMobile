package com.cyxbs.pages.widget.repo.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cyxbs.components.init.appContext
import com.cyxbs.pages.widget.repo.bean.LessonEntity

/**
 * author : Watermelon02
 * email : 1446157077@qq.com
 */
@Database(entities = [LessonEntity::class], version = 1, exportSchema = false)
abstract class LessonDatabase : RoomDatabase() {

    abstract fun getLessonDao(): LessonDao

    companion object {
        const val MY_STU_NUM = "my_stu_num"
        const val OTHERS_STU_NUM = "others_stu_num"
        
        val INSTANCE by lazy {
            Room.databaseBuilder(
                appContext,
                LessonDatabase::class.java,
                "lesson_database"
            ).build()
        }
    }
}