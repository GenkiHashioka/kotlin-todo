package com.genkihashioka.kotlintodo.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
        composable<TodoListDestination> {
            val todoListViewModel: TodoListViewModel = viewModel(
                factory = todoListViewModelFactory,
            )

            TodoListRoute(
                viewModel = todoListViewModel,
                onTodoClick = { todoId ->
                    navController.navigate(
                        TodoDetailDestination(todoId = todoId),
                    )
                },
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
    }
}
