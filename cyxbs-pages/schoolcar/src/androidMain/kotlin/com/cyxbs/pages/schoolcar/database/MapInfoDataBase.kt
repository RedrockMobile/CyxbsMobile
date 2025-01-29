package com.cyxbs.pages.schoolcar.database

import androidx.room.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.cyxbs.pages.schoolcar.bean.Line
import com.cyxbs.pages.schoolcar.bean.MapLines
import com.cyxbs.components.init.appContext

/**
 *@Author:SnowOwlet
 *@Date:2022/5/7 17:26
 *
 */
@Database(entities = [MapLines::class], version = 1, exportSchema = false)
@TypeConverters(MapLineConvert::class, LineConvert::class)
abstract class MapInfoDataBase : RoomDatabase() {
  abstract fun mapInfoDao(): MapInfoDao

  companion object {
    val INSTANCE: MapInfoDataBase by lazy {
      Room.databaseBuilder(
        com.cyxbs.components.init.appContext,
        MapInfoDataBase::class.java, "map_info_database"
      ).fallbackToDestructiveMigration().build()
    }
  }
}

class MapLineConvert{
  @TypeConverter
  fun MapLine2String(value: MapLines): String{
    return Gson().toJson(value)
  }

  @TypeConverter
  fun string2MapLine(value: String): MapLines {
    return Gson().fromJson(value, MapLines::class.java)
  }
}
class LineConvert{
  @TypeConverter
  fun line2String(value: List<Line>): String{
    return Gson().toJson(value)
  }

  @TypeConverter
  fun string2line(value: String): List<Line> {
    return Gson().fromJson(
      value,
      object : TypeToken<List<Line>>() {}.type
    )
  }
}