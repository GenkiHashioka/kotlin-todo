package com.genkihashioka.kotlintodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.genkihashioka.kotlintodo.data.remote.ApiClient
import com.genkihashioka.kotlintodo.data.repository.TodoRepository
import com.genkihashioka.kotlintodo.ui.theme.KotlinTodoTheme
import com.genkihashioka.kotlintodo.ui.todo.TodoListRoute
import com.genkihashioka.kotlintodo.ui.todo.TodoListViewModel
import com.genkihashioka.kotlintodo.ui.todo.todoListViewModelFactory

class MainActivity : ComponentActivity() {
    private val todoRepository = TodoRepository(ApiClient.todoApi)
    private val viewModelFactory = todoListViewModelFactory(todoRepository)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val todoListViewModel: TodoListViewModel = viewModel(
                factory = viewModelFactory,
            )
            KotlinTodoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TodoListRoute(
                        viewModel = todoListViewModel,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }
}
