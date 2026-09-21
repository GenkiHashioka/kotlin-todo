package com.genkihashioka.kotlintodo.ui.todo

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.genkihashioka.kotlintodo.data.repository.TodoRepository

/**
 * TodoListViewModelを生成するためのFactory
 */
fun todoListViewModelFactory(todoRepository: TodoRepository): ViewModelProvider.Factory {
    return viewModelFactory {
        initializer {
            TodoListViewModel(todoRepository)
        }
    }
}
