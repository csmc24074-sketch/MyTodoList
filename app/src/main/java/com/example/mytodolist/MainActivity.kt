package com.example.mytodolist

import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class TodoItem(
    val title: String,
    val time: String,
    val isCompleted: Boolean,
    val priority: String = "中"
)

class MainActivity : ComponentActivity() {
    private val refreshTrigger = mutableStateOf(0)

    override fun onResume() {
        super.onResume()
        refreshTrigger.value += 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("todo_prefs_v5", Context.MODE_PRIVATE)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val savedTodos = remember { mutableStateListOf<TodoItem>() }

                    val todoComparator = compareBy<TodoItem> { it.isCompleted }
                        .thenBy {
                            when (it.priority) {
                                "高" -> 0
                                "中" -> 1
                                else -> 2
                            }
                        }
                        .thenBy { it.time }

                    LaunchedEffect(refreshTrigger.value) {
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

                        savedTodos.clear()
                        savedTodos.addAll(list.sortedWith(todoComparator))
                    }

                    var text by remember { mutableStateOf("") }
                    var selectedTime by remember { mutableStateOf("時間選択") }
                    var selectedPriority by remember { mutableStateOf("中") }

                    val saveToPrefs = { currentList: List<TodoItem> ->
                        val editor = sharedPreferences.edit()
                        editor.clear().commit()

                        editor.putInt("todo_count", currentList.size)
                        currentList.forEachIndexed { index, item ->
                            editor.putString("todo_title_$index", item.title)
                            editor.putString("todo_time_$index", item.time)
                            editor.putBoolean("todo_completed_$index", item.isCompleted)
                            editor.putString("todo_priority_$index", item.priority)
                        }
                        editor.commit()

                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                TodoWidget().updateAll(context)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }

                        try {
                            val intent = Intent(context, TodoWidgetReceiver::class.java).apply {
                                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            }
                            val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
                                ComponentName(context, TodoWidgetReceiver::class.java)
                            )
                            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                            context.sendBroadcast(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "My Todo List",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("新しいタスクを入力") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("優先度:", style = MaterialTheme.typography.bodyMedium)
                            listOf("高", "中", "低").forEach { priority ->
                                val isSelected = selectedPriority == priority
                                ElevatedFilterChip(
                                    selected = isSelected,
                                    onClick = { selectedPriority = priority },
                                    label = {
                                        Text(
                                            text = priority,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    colors = FilterChipDefaults.elevatedFilterChipColors(
                                        selectedContainerColor = when (priority) {
                                            "高" -> Color(0xFFE53935)
                                            "中" -> Color(0xFFFB8C00)
                                            else -> Color(0xFF757575)
                                        }
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            selectedTime = String.format("%02d:%02d", hour, minute)
                                        },
                                        calendar.get(Calendar.HOUR_OF_DAY),
                                        calendar.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text(text = if (selectedTime == "時間選択") "時間を設定" else selectedTime)
                            }

                            Button(
                                onClick = {
                                    if (text.isNotBlank()) {
                                        val timeToSave = if (selectedTime == "時間選択") "--:--" else selectedTime
                                        val fixedPriority = selectedPriority

                                        val newList = savedTodos.toMutableList()
                                        newList.add(
                                            TodoItem(
                                                title = text,
                                                time = timeToSave,
                                                isCompleted = false,
                                                priority = fixedPriority
                                            )
                                        )
                                        newList.sortWith(todoComparator)

                                        savedTodos.clear()
                                        savedTodos.addAll(newList)

                                        saveToPrefs(newList)

                                        text = ""
                                        selectedTime = "時間選択"
                                        selectedPriority = "中"
                                    }
                                }
                            ) {
                                Text("追加")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            itemsIndexed(savedTodos, key = { index, todo -> "$index-${todo.title}-${todo.time}" }) { index, todo ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Checkbox(
                                                checked = todo.isCompleted,
                                                onCheckedChange = { isChecked ->
                                                    val itemIndex = savedTodos.indexOf(todo)
                                                    if (itemIndex != -1) {
                                                        val newList = savedTodos.toMutableList()
                                                        newList[itemIndex] = todo.copy(isCompleted = isChecked)
                                                        newList.sortWith(todoComparator)

                                                        savedTodos.clear()
                                                        savedTodos.addAll(newList)

                                                        saveToPrefs(newList)
                                                    }
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))

                                            val badgeColor = when (todo.priority) {
                                                "高" -> Color(0xFFE53935)
                                                "中" -> Color(0xFFFB8C00)
                                                else -> Color(0xFF757575)
                                            }
                                            Surface(
                                                color = if (todo.isCompleted) Color.Gray.copy(alpha = 0.3f) else badgeColor,
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier.padding(end = 4.dp)
                                            ) {
                                                Text(
                                                    text = todo.priority,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = Color.White
                                                )
                                            }

                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                Text(
                                                    text = todo.time,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }

                                            Text(
                                                text = " ${todo.title}",
                                                style = LocalTextStyle.current.copy(
                                                    textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                                ),
                                                color = if (todo.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        TextButton(
                                            onClick = {
                                                savedTodos.remove(todo)
                                                saveToPrefs(savedTodos)
                                            }
                                        ) {
                                            // 🛠️ エラー修正箇所：MaterialTheme の指定方法を変更、または Color.Red を用いることで安全に
                                            Text("削除", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}