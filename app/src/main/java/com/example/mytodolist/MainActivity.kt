package com.example.mytodolist // プロジェクトのパッケージ名に合わせて変更してください

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TodoSimpleScreen()
                }
            }
        }
    }
}

@Composable
fun TodoSimpleScreen() {
    // ユーザ入力を保持する状態（バリデーション対象）
    var taskTitleInput by remember { mutableStateOf("") }

    // エラーメッセージを保持する状態（方針4: エラー表示用）
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // メモリ上でタスク名を管理するリスト（今回は最小限のためStringのリスト）
    // ※シーケンス図にある「状態を更新してタスク表示」のローカル擬似版です
    val taskList = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 方針4: ユーザ入力の検証とエラー表示 ---
        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // タスク名 入力欄
        OutlinedTextField(
            value = taskTitleInput,
            onValueChange = {
                taskTitleInput = it
                // 文字が入力されたら、自動的にエラー表示を消す親切設計
                if (it.isNotBlank()) errorMessage = null
            },
            label = { Text("タスク名を入力") },
            isError = errorMessage != null, // エラー時は枠線を赤くする
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 「追加」ボタン (シーケンス図のイベント10番に相当)
        Button(
            onClick = {
                // --- 方針4: バリデーションチェック ---
                // シーケンス図 alt [入力値が不正な場合（タスク名が空など）] の再現
                if (taskTitleInput.trim().isEmpty()) {
                    errorMessage = "タスク名を入力してください（空欄は不可です）"
                } else {
                    // 入力値が正常な場合
                    taskList.add(taskTitleInput.trim()) // リストに追加
                    taskTitleInput = "" // 入力欄をクリア
                    errorMessage = null // エラーをクリア
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("追加")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "今日のタスク一覧",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 即座に追加・表示されるリスト部分
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(taskList) { taskName ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = taskName,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}