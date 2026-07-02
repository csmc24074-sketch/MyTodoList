package com.example.mytodolist

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.TextDecoration
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.updateAll
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class TodoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sharedPreferences = context.getSharedPreferences("todo_prefs_v5", Context.MODE_PRIVATE)
        val count = sharedPreferences.getInt("todo_count", 0)
        val list = mutableListOf<TodoItem>()

        for (i in 0 until count) {
            val title = sharedPreferences.getString("todo_title_$i", "") ?: ""
            if (title.isBlank()) continue
            val time = sharedPreferences.getString("todo_time_$i", "--:--") ?: "--:--"
            val isCompleted = sharedPreferences.getBoolean("todo_completed_$i", false)
            val priority = sharedPreferences.getString("todo_priority_$i", "中") ?: "中"

            list.add(TodoItem(title, time, isCompleted, priority))
        }

        val todoItems = list.sortedWith(
            compareBy<TodoItem> { it.isCompleted }
                .thenBy {
                    when (it.priority) {
                        "高" -> 0
                        "中" -> 1
                        else -> 2
                    }
                }
                .thenBy { it.time }
        )

        provideContent {
            WidgetContent(todoItems)
        }
    }

    @Composable
    private fun WidgetContent(todos: List<TodoItem>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "今日のタスク一覧",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = GlanceTheme.colors.onBackground
                )
            )

            Spacer(modifier = GlanceModifier.height(12.dp))

            if (todos.isEmpty()) {
                Text(
                    text = "予定はありません",
                    style = TextStyle(fontSize = 15.sp, color = GlanceTheme.colors.onBackground)
                )
            } else {
                todos.forEach { todo ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 🛠️ 修正ポイント：現在の完了状態（isCompleted）もパラメータとして渡す
                        val paramArgs = actionParametersOf(
                            ActionParameters.Key<String>("todo_title") to todo.title,
                            ActionParameters.Key<String>("todo_time") to todo.time,
                            ActionParameters.Key<Boolean>("todo_completed") to todo.isCompleted
                        )

                        Image(
                            provider = ImageProvider(
                                if (todo.isCompleted) android.R.drawable.checkbox_on_background else android.R.drawable.checkbox_off_background
                            ),
                            contentDescription = "Complete",
                            modifier = GlanceModifier.clickable(
                                actionRunCallback<CompleteTodoAction>(paramArgs)
                            )
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        val priorityColor = when (todo.priority) {
                            "高" -> ColorProvider(Color(0xFFE53935))
                            "中" -> ColorProvider(Color(0xFFFB8C00))
                            else -> ColorProvider(Color(0xFF757575))
                        }

                        Text(
                            text = "[${todo.priority}]",
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (todo.isCompleted) GlanceTheme.colors.onBackground else priorityColor
                            )
                        )

                        Spacer(modifier = GlanceModifier.width(4.dp))

                        Text(
                            text = "${todo.time} ${todo.title}",
                            style = TextStyle(
                                fontSize = 14.sp,
                                textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (todo.isCompleted) GlanceTheme.colors.secondary else GlanceTheme.colors.onBackground
                            )
                        )
                    }
                }
            }
        }
    }
}

class CompleteTodoAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val titleKey = ActionParameters.Key<String>("todo_title")
        val timeKey = ActionParameters.Key<String>("todo_time")
        val completedKey = ActionParameters.Key<Boolean>("todo_completed")

        val title = parameters[titleKey] ?: return
        val time = parameters[timeKey] ?: return
        val isCurrentlyCompleted = parameters[completedKey] ?: return

        val sharedPreferences = context.getSharedPreferences("todo_prefs_v5", Context.MODE_PRIVATE)
        val count = sharedPreferences.getInt("todo_count", 0)

        val editor = sharedPreferences.edit()
        for (i in 0 until count) {
            val savedTitle = sharedPreferences.getString("todo_title_$i", "")
            val savedTime = sharedPreferences.getString("todo_time_$i", "--:--")
            val savedCompleted = sharedPreferences.getBoolean("todo_completed_$i", false)

            // 🛠️ 修正ポイント：タイトル、時間、そして「現在の完了状態」が一致するターゲットを確実に狙い撃つ
            if (savedTitle == title && savedTime == time && savedCompleted == isCurrentlyCompleted) {
                editor.putBoolean("todo_completed_$i", !isCurrentlyCompleted) // 状態を確実に反転
                break
            }
        }
        editor.apply()

        TodoWidget().updateAll(context)
    }
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        MainScope().launch {
            TodoWidget().updateAll(context)
        }
    }
}