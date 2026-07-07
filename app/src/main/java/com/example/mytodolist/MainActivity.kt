package com.example.mytodolist

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "todo_prefs")
val TODO_SET_KEY = stringSetPreferencesKey("todo_set_data")

data class TodoItem(
    val id: String,
    val title: String,
    val time: String,
    val isCompleted: Boolean = false,
    val priority: String = "中",
    val date: String
) {
    fun toRawString(): String = "$id|split|$title|split|$time|split|$isCompleted|split|$priority|split|$date"

    companion object {
        fun fromRawString(raw: String): TodoItem? {
            val parts = raw.split("|split|")
            if (parts.size < 5) return null
            val calendar = Calendar.getInstance()
            val defaultDate = String.format("%04d/%02d/%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))

            return TodoItem(
                id = parts[0],
                title = parts[1],
                time = parts[2],
                isCompleted = parts[3].toBoolean(),
                priority = parts[4],
                date = if (parts.size >= 6) parts[5] else defaultDate
            )
        }
    }
}

// ★ ダークモード・ライトモードのカラー定義
private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),       // メインの青（少し明るめ）
    background = Color(0xFF121212),    // 背景（深い黒・グレー）
    surface = Color(0xFF1E1E1E),       // カードなどの背景
    onBackground = Color(0xFFFFFFFF),  // 背景の上の文字（白）
    onSurface = Color(0xFFFFFFFF),      // カードの上の文字（白）
    secondary = Color(0xFF888888)      // 完了したタスクなどのグレー
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2196F3),       // メインの青
    background = Color(0xFFFFFFFF),    // 背景（白）
    surface = Color(0xFFF5F5F5),       // カードなどの背景（薄いグレー）
    onBackground = Color(0xFF000000),  // 文字（黒）
    onSurface = Color(0xFF000000),
    secondary = Color(0xFF888888)
)

@Composable
fun TodoAppTheme(content: @Composable () -> Unit) {
    // システムがダークモード設定かどうかを自動判定
    val darkTheme = isSystemInDarkTheme()
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // ★ 作成した自動着せ替えテーマで全体を包み込む
            TodoAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background // テーマに連動して黒か白に
                ) {
                    TodoApp(this)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp(activity: MainActivity) {
    val context = activity.applicationContext
    val coroutineScope = rememberCoroutineScope()

    var todoList by remember { mutableStateOf(listOf<TodoItem>()) }
    var taskTitle by remember { mutableStateOf("") }
    var taskTime by remember { mutableStateOf("") }
    var taskPriority by remember { mutableStateOf("中") }

    val realCalendar = Calendar.getInstance()
    val todayStr = String.format("%04d/%02d/%02d", realCalendar.get(Calendar.YEAR), realCalendar.get(Calendar.MONTH) + 1, realCalendar.get(Calendar.DAY_OF_MONTH))

    var taskDate by remember { mutableStateOf(todayStr) }
    var selectedViewDate by remember { mutableStateOf(todayStr) }

    val currentCalendar = Calendar.getInstance()
    val currentHour = currentCalendar.get(Calendar.HOUR_OF_DAY)
    val currentMinute = currentCalendar.get(Calendar.MINUTE)

    val timePickerDialog = TimePickerDialog(
        LocalContext.current,
        { _, selectedHour, selectedMinute ->
            taskTime = String.format("%02d:%02d", selectedHour, selectedMinute)
        }, currentHour, currentMinute, true
    )

    val datePickerDialog = DatePickerDialog(
        LocalContext.current,
        { _, year, month, dayOfMonth ->
            taskDate = String.format("%04d/%02d/%02d", year, month + 1, dayOfMonth)
        },
        currentCalendar.get(Calendar.YEAR),
        currentCalendar.get(Calendar.MONTH),
        currentCalendar.get(Calendar.DAY_OF_MONTH)
    )

    val viewDatePickerDialog = DatePickerDialog(
        LocalContext.current,
        { _, year, month, dayOfMonth ->
            selectedViewDate = String.format("%04d/%02d/%02d", year, month + 1, dayOfMonth)
        },
        currentCalendar.get(Calendar.YEAR),
        currentCalendar.get(Calendar.MONTH),
        currentCalendar.get(Calendar.DAY_OF_MONTH)
    )

    LaunchedEffect(Unit) {
        context.dataStore.data.collectLatest { prefs ->
            val stringSet = prefs[TODO_SET_KEY] ?: emptySet()
            todoList = stringSet.mapNotNull { TodoItem.fromRawString(it) }
        }
    }

    fun updateAndSave(newList: List<TodoItem>) {
        todoList = newList
        coroutineScope.launch(Dispatchers.IO) {
            val stringSet = newList.map { it.toRawString() }.toSet()
            context.dataStore.edit { preferences ->
                preferences[TODO_SET_KEY] = stringSet
            }
        }
    }

    val filteredSortedTodoList = todoList
        .filter { it.date == selectedViewDate }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // テキスト色をテーマ連動（MaterialTheme.colorScheme.onBackground）に
        Text(text = "My Todo List", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(bottom = 12.dp))

        OutlinedTextField(
            value = taskTitle,
            onValueChange = { taskTitle = it },
            label = { Text("新しいタスクを入力") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f).clickable { datePickerDialog.show() }) {
                OutlinedTextField(
                    value = taskDate, onValueChange = {}, label = { Text("日付") }, readOnly = true,
                    modifier = Modifier.fillMaxWidth(), enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onBackground,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
            Box(modifier = Modifier.weight(1f).clickable { timePickerDialog.show() }) {
                OutlinedTextField(
                    value = taskTime, onValueChange = {}, label = { Text("時間") }, readOnly = true, placeholder = { Text("--:--") },
                    modifier = Modifier.fillMaxWidth(), enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onBackground,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1.5f), horizontalArrangement = Arrangement.Start) {
                listOf("高", "中", "低").forEach { p ->
                    Button(
                        onClick = { taskPriority = p },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (taskPriority == p) MaterialTheme.colorScheme.primary else Color.LightGray,
                            contentColor = if (taskPriority == p) MaterialTheme.colorScheme.onPrimary else Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.padding(horizontal = 2.dp)
                    ) { Text(p, fontSize = 12.sp) }
                }
            }
            Button(
                onClick = {
                    if (taskTitle.isNotBlank()) {
                        val timeStr = if (taskTime.isNotBlank()) taskTime else "--:--"
                        val uniqueId = System.currentTimeMillis().toString() + "_" + taskTitle
                        val updated = todoList + TodoItem(uniqueId, taskTitle, timeStr, false, taskPriority, taskDate)
                        updateAndSave(updated)
                        taskTitle = ""
                        taskTime = ""
                        taskPriority = "中"
                        taskDate = todayStr
                    }
                }
            ) { Text("追加") }
        }

        Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color.Gray)

        Text(text = "表示中の日付: $selectedViewDate", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = { selectedViewDate = todayStr },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedViewDate == todayStr) MaterialTheme.colorScheme.primary else Color.LightGray,
                    contentColor = if (selectedViewDate == todayStr) MaterialTheme.colorScheme.onPrimary else Color.Black
                ),
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)
            ) { Text("今日", fontSize = 12.sp) }

            val tomorrowCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
            val tomorrowStr = String.format("%04d/%02d/%02d", tomorrowCalendar.get(Calendar.YEAR), tomorrowCalendar.get(Calendar.MONTH) + 1, tomorrowCalendar.get(Calendar.DAY_OF_MONTH))
            Button(
                onClick = { selectedViewDate = tomorrowStr },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedViewDate == tomorrowStr) MaterialTheme.colorScheme.primary else Color.LightGray,
                    contentColor = if (selectedViewDate == tomorrowStr) MaterialTheme.colorScheme.onPrimary else Color.Black
                ),
                modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)
            ) { Text("明日", fontSize = 12.sp) }

            Button(
                onClick = { viewDatePickerDialog.show() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedViewDate != todayStr && selectedViewDate != tomorrowStr) MaterialTheme.colorScheme.primary else Color.LightGray,
                    contentColor = if (selectedViewDate != todayStr && selectedViewDate != tomorrowStr) MaterialTheme.colorScheme.onPrimary else Color.Black
                ),
                modifier = Modifier.weight(1.2f), contentPadding = PaddingValues(4.dp)
            ) { Text("日付を選択", fontSize = 11.sp) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (filteredSortedTodoList.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("この日の予定はありません", color = MaterialTheme.colorScheme.secondary, fontSize = 16.sp)
                    }
                }
            }

            items(filteredSortedTodoList, key = { it.id }) { todo ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    // ダークモード時は少し明るめの枠線にして見やすく
                    border = BorderStroke(1.dp, if (isSystemInDarkTheme()) Color(0xFF333333) else Color(0xFFE0E0E0)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = todo.isCompleted,
                            onCheckedChange = { isChecked ->
                                val updated = todoList.map {
                                    if (it.id == todo.id) it.copy(isCompleted = isChecked) else it
                                }
                                updateAndSave(updated)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.secondary
                            )
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        val priorityColor = when (todo.priority) {
                            "高" -> Color(0xFFE53935)
                            "中" -> Color(0xFFFF9800)
                            else -> Color.Gray
                        }
                        Box(
                            modifier = Modifier.size(12.dp).clip(CircleShape).background(if (todo.isCompleted) Color.LightGray else priorityColor)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        var isOverdue = false
                        if (todo.date == todayStr && !todo.isCompleted && todo.time != "--:--") {
                            val parts = todo.time.split(":")
                            if (parts.size == 2) {
                                val taskHour = parts[0].toIntOrNull() ?: 0
                                val taskMinute = parts[1].toIntOrNull() ?: 0
                                if (taskHour < currentHour || (taskHour == currentHour && taskMinute < currentMinute)) {
                                    isOverdue = true
                                }
                            }
                        }

                        val timeTextColor = when {
                            todo.isCompleted -> MaterialTheme.colorScheme.secondary
                            isOverdue -> Color(0xFFFF1744) // 期限切れの赤
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Row(modifier = Modifier.weight(1.5f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = todo.time,
                                color = timeTextColor,
                                fontSize = 16.sp,
                                fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
                                textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = todo.title,
                                color = if (todo.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                textDecoration = if (todo.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text(
                            text = "削除", color = Color(0xFFE53935),
                            modifier = Modifier.clickable {
                                val updated = todoList.filterNot { it.id == todo.id }
                                updateAndSave(updated)
                            }.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}