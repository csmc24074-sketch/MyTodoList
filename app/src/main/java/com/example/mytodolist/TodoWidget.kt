package com.example.mytodolist

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

class TodoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                var todoItems by remember { mutableStateOf(listOf<TodoItem>()) }
                val coroutineScope = rememberCoroutineScope()

                // 現在の「日付」と「時刻」を取得
                val currentCalendar = Calendar.getInstance()
                val currentYear = currentCalendar.get(Calendar.YEAR)
                val currentMonth = currentCalendar.get(Calendar.MONTH) + 1 // 0から始まるため+1
                val currentDay = currentCalendar.get(Calendar.DAY_OF_MONTH)

                // 「YYYY/MM/DD」の形式で今日の文字列を作成
                val todayStr = String.format("%04d/%02d/%02d", currentYear, currentMonth, currentDay)

                val currentHour = currentCalendar.get(Calendar.HOUR_OF_DAY)
                val currentMinute = currentCalendar.get(Calendar.MINUTE)

                LaunchedEffect(Unit) {
                    context.dataStore.data.collectLatest { prefs ->
                        val stringSet = prefs[TODO_SET_KEY] ?: emptySet()

                        // ★ 全タスクの中から「今日（todayStr）」のものだけに絞り込む
                        todoItems = stringSet.mapNotNull { TodoItem.fromRawString(it) }
                            .filter { it.date == todayStr } // ← ここで今日のタスクだけに限定！
                            .sortedWith(
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
                    }
                }

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.background)
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.Horizontal.Start
                ) {
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity<MainActivity>()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "今日のタスク一覧",
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = GlanceTheme.colors.onBackground
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Text(
                            text = "アプリ起動 ↗",
                            style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.secondary)
                        )
                    }

                    Spacer(modifier = GlanceModifier.height(16.dp))

                    if (todoItems.isEmpty()) {
                        Text(
                            text = "今日の予定はありません",
                            style = TextStyle(
                                fontSize = 18.sp,
                                color = GlanceTheme.colors.onBackground
                            ),
                            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>())
                        )
                    } else {
                        todoItems.forEach { todo ->
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                // 丸型チェックボックス
                                Box(
                                    modifier = GlanceModifier
                                        .size(24.dp)
                                        .cornerRadius(12.dp)
                                        .background(
                                            if (todo.isCompleted) GlanceTheme.colors.primary
                                            else ColorProvider(Color(0xFFE0E0E0))
                                        )
                                        .clickable {
                                            coroutineScope.launch {
                                                val prefs = context.dataStore.data.first()
                                                val stringSet = prefs[TODO_SET_KEY] ?: emptySet()
                                                val currentTodoList = stringSet.mapNotNull { TodoItem.fromRawString(it) }

                                                val updatedList = currentTodoList.map {
                                                    if (it.id == todo.id) it.copy(isCompleted = !it.isCompleted) else it
                                                }

                                                val newStringSet = updatedList.map { it.toRawString() }.toSet()
                                                context.dataStore.edit { preferences ->
                                                    preferences[TODO_SET_KEY] = newStringSet
                                                }
                                                TodoWidget().updateAll(context)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (todo.isCompleted) {
                                        Text(
                                            text = "✓",
                                            style = TextStyle(
                                                color = GlanceTheme.colors.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = GlanceModifier.width(12.dp))

                                // 優先度用のカラーバッジ
                                val badgeColor = when (todo.priority) {
                                    "高" -> ColorProvider(Color(0xFFE53935))
                                    "中" -> ColorProvider(Color(0xFFFB8C00))
                                    else -> ColorProvider(Color(0xFF9E9E9E))
                                }

                                Box(
                                    modifier = GlanceModifier
                                        .size(10.dp)
                                        .cornerRadius(5.dp)
                                        .background(if (todo.isCompleted) GlanceTheme.colors.secondary else badgeColor)
                                ) {}

                                Spacer(modifier = GlanceModifier.width(10.dp))

                                // 期限切れアラートの判定
                                var isOverdue = false
                                if (!todo.isCompleted && todo.time != "--:--") {
                                    val parts = todo.time.split(":")
                                    if (parts.size == 2) {
                                        val taskHour = parts[0].toIntOrNull() ?: 0
                                        val taskMinute = parts[1].toIntOrNull() ?: 0
                                        if (taskHour < currentHour || (taskHour == currentHour && taskMinute < currentMinute)) {
                                            isOverdue = true
                                        }
                                    }
                                }

                                val textColor = when {
                                    todo.isCompleted -> GlanceTheme.colors.secondary
                                    isOverdue -> ColorProvider(Color(0xFFFF1744))
                                    else -> GlanceTheme.colors.onBackground
                                }

                                Text(
                                    text = "${todo.time}  ${todo.title}",
                                    style = TextStyle(
                                        fontSize = 18.sp,
                                        textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                        color = textColor
                                    ),
                                    modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())
                                )
                            }
                        }
                    }
                }
            }
        }
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