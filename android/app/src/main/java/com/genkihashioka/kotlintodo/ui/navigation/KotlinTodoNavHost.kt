package com.genkihashioka.kotlintodo.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.genkihashioka.kotlintodo.ui.todo.TodoCreateRoute
import com.genkihashioka.kotlintodo.ui.todo.TodoCreateViewModel
import com.genkihashioka.kotlintodo.ui.todo.TodoDetailRoute
import com.genkihashioka.kotlintodo.ui.todo.TodoDetailViewModel
import com.genkihashioka.kotlintodo.ui.todo.TodoListRoute
import com.genkihashioka.kotlintodo.ui.todo.TodoListViewModel

/**
 * KotlinTodoNavHost。
 * NavControllerを保持し、
 * 現在のDestinationに対応する画面を表示する。
 */
@Composable
fun KotlinTodoNavHost(
    todoListViewModelFactory: ViewModelProvider.Factory,
    todoDetailViewModelFactory: ViewModelProvider.Factory,
    todoCreateViewModelFactory: ViewModelProvider.Factory,
    modifier: Modifier = Modifier,
) {
    // 現在のDestinationを管理するNavController
    val navController = rememberNavController()

    // Destinationごとの画面を表示するNavHost。初期表示はTodoListDestination。(Todo一覧表示)
    NavHost(
        navController = navController,
        startDestination = TodoListDestination,
        modifier = modifier,
    ) {
        // Todo一覧表示
        composable<TodoListDestination> { backStackEntry ->
            // Todo再取得通知を受け取る。通知ありの場合true。
            val refreshRequired by backStackEntry.savedStateHandle
                .getStateFlow("todo_list_refresh_required", false)
                .collectAsStateWithLifecycle()

            val todoListViewModel: TodoListViewModel = viewModel(
                factory = todoListViewModelFactory,
            )

            // Todo再取得通知がtrueなら再取得を行い、通知を処理済みにする。　
            LaunchedEffect(refreshRequired) {
                if (refreshRequired) {
                    todoListViewModel.refresh()
                    backStackEntry.savedStateHandle["todo_list_refresh_required"] = false
                }
            }

            TodoListRoute(
                viewModel = todoListViewModel,
                onTodoClick = { todoId ->
                    navController.navigate(
                        TodoDetailDestination(todoId = todoId),
                    )
                },
                onCreateClick = { navController.navigate(TodoCreateDestination) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Todo詳細表示
        composable<TodoDetailDestination> {
            val todoDetailViewModel: TodoDetailViewModel = viewModel(
                factory = todoDetailViewModelFactory,
            )

            TodoDetailRoute(
                viewModel = todoDetailViewModel,
                onBack = {
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Todo作成
        composable<TodoCreateDestination> {
            val todoCreateViewModel: TodoCreateViewModel = viewModel(
                factory = todoCreateViewModelFactory,
            )

            TodoCreateRoute(
                viewModel = todoCreateViewModel,
                onCreated = {
                    // Todo一覧画面に「再取得が必要」と記録。
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("todo_list_refresh_required", true)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
