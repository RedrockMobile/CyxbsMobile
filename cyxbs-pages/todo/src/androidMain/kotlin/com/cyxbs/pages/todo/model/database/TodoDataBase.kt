package com.cyxbs.pages.todo.model.database

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cyxbs.pages.todo.model.bean.RemindMode
import com.cyxbs.pages.todo.model.bean.RemindMode.Companion.generateDefaultRemindMode
import com.cyxbs.pages.todo.model.bean.Todo
import com.google.gson.Gson
import com.cyxbs.components.init.appContext
import com.cyxbs.components.utils.extensions.getSp
import com.cyxbs.components.init.appCoroutineScope
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.launch

/**
 * description:
 * author: sanhuzhen
 * date: 2024/8/20 14:21
 */
@Database(entities = [Todo::class], version = 2)
@TypeConverters(Convert::class)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: TodoDatabase? = null

        val instance: TodoDatabase
            get() = INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    com.cyxbs.components.init.appContext,
                    TodoDatabase::class.java,
                    "todo_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(getDatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }

        private fun getDatabaseCallback(): Callback {
            return object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    insertDefaultTodosIfNeeded()
                }
            }
        }

        private fun insertDefaultTodosIfNeeded() {
            if (com.cyxbs.components.init.appContext.getSp("todo").getLong("TODO_LAST_MODIFY_TIME", 0L) == 0L) {
                val database = INSTANCE ?: return
                val defaultTodos = listOf(
                    Todo(1, "长按可以拖动我哟", "", 0, generateDefaultRemindMode(), System.currentTimeMillis(), "", "", 0, 0),
                    Todo(2, "左滑可置顶或者删除", "", 0, generateDefaultRemindMode(), System.currentTimeMillis(), "", "", 0, 0),
                    Todo(3, "点击查看代办详情", "", 0, generateDefaultRemindMode(), System.currentTimeMillis(), "", "", 0, 0)
                )
                Completable.fromAction { appCoroutineScope.launch {
                    database.todoDao().insertAll(defaultTodos) }
                }.subscribeOn(Schedulers.io()).subscribe()
            }
        }
    }
}

class Convert {
    @TypeConverter
    fun remindMode2String(value: RemindMode): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun string2RemindMode(value: String): RemindMode {
        return Gson().fromJson(value, RemindMode::class.java)
    }
}