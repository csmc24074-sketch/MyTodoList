package com.example.mytodolist

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todo_table")
data class Todo(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,        // 自動で1, 2, 3...と増える背番号
    val title: String,     // ToDoのタイトル（例：「牛乳を買う」）
    val isDone: Boolean = false // 完了したかどうかのフラグ
)