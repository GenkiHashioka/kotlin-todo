package com.genkihashioka.kotlintodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.genkihashioka.kotlintodo.data.remote.ApiClient
import com.genkihashioka.kotlintodo.data.repository.TodoRepository
import com.genkihashioka.kotlintodo.ui.navigation.KotlinTodoNavHost
import com.genkihashioka.kotlintodo.ui.theme.KotlinTodoTheme
import com.genkihashioka.kotlintodo.ui.todo.create.todoCreateViewModelFactory
import com.genkihashioka.kotlintodo.ui.todo.detail.todoDetailViewModelFactory
import com.genkihashioka.kotlintodo.ui.todo.edit.todoEditViewModelFactory
import com.genkihashioka.kotlintodo.ui.todo.list.todoListViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val todoRepository = TodoRepository(ApiClient.todoApi)
    private val todoListFactory = todoListViewModelFactory(todoRepository)
    private val todoDetailFactory = todoDetailViewModelFactory(todoRepository)
    private val todoCreateFactory = todoCreateViewModelFactory(todoRepository)
    private val todoEditFactory = todoEditViewModelFactory(todoRepository)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KotlinTodoTheme {
                // snackbarの状態を管理する
                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                val todoCreatedMessage = stringResource(R.string.todo_created)
                val todoUpdatedMessage = stringResource(R.string.todo_updated)
                val todoDeletedMessage = stringResource(R.string.todo_deleted)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState)
                    },
                ) { innerPadding ->
                    KotlinTodoNavHost(
                        todoListViewModelFactory = todoListFactory,
                        todoDetailViewModelFactory = todoDetailFactory,
                        todoCreateViewModelFactory = todoCreateFactory,
                        todoEditViewModelFactory = todoEditFactory,
                        onTodoCreated = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(todoCreatedMessage)
                            }
                        },
                        onTodoUpdated = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(todoUpdatedMessage)
                            }
                        },
                        onTodoDeleted = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(todoDeletedMessage)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    )
                }
            }
        }
    }
}
