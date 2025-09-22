package com.cyxbs.pages.widget.repo.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cyxbs.components.init.appContext
import com.cyxbs.pages.widget.repo.bean.AffairEntity

/**
 * author : Watermelon02
 * email : 1446157077@qq.com
 * date : 2022/8/6 20:09
 */
@Database(entities = [AffairEntity::class], version = 1, exportSchema = false)
abstract class AffairDatabase : RoomDatabase() {

    abstract fun getAffairDao(): AffairDao

    companion object {
        val INSTANCE by lazy {
            Room.databaseBuilder(
                appContext,
                AffairDatabase::class.java,
                "affair_database"
            ).build()
        }
    }
}