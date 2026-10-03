package com.genkihashioka.kotlintodo.ui.todo

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.genkihashioka.kotlintodo.data.repository.TodoRepository

/**
 * TodoCreateViewModelを生成するためのFactory
 */
fun todoCreateViewModelFactory(todoRepository: TodoRepository): ViewModelProvider.Factory {
    return viewModelFactory {
        initializer {
            TodoCreateViewModel(todoRepository)
        }
    }
}
