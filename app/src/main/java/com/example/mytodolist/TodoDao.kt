package com.example.mytodolist

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    // 🗄️ データをすべて取得する命令（データが変わると自動で画面に通知してくれます）
    @Query("SELECT * FROM todo_table ORDER BY id DESC")
    fun getAllTodos(): Flow<List<Todo>>

    // 📥 データを保存する命令
    @Insert
    suspend fun insert(todo: Todo)

    // 🗑️ データを削除する命令
    @Delete
    suspend fun delete(todo: Todo)
}