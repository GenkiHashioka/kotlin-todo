package com.genkihashioka.kotlintodo.ui.todo

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.genkihashioka.kotlintodo.data.repository.TodoRepository

/**
 * TodoDetailViewModelを生成するためのFactory
 */
fun todoDetailViewModelFactory(todoRepository: TodoRepository): ViewModelProvider.Factory {
    return viewModelFactory {
        initializer {
            TodoDetailViewModel(
                todoRepository = todoRepository,
                savedStateHandle = createSavedStateHandle(),
            )
        }
    }
}
