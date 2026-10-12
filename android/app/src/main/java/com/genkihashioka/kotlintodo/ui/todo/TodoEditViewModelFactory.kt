package com.genkihashioka.kotlintodo.ui.todo

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.genkihashioka.kotlintodo.data.repository.TodoRepository

/**
 * TodoEditViewModelを生成するためのFactory
 */
fun todoEditViewModelFactory(todoRepository: TodoRepository): ViewModelProvider.Factory {
    return viewModelFactory {
        initializer {
            TodoEditViewModel(
                todoRepository = todoRepository,
                savedStateHandle = createSavedStateHandle(),
            )
        }
    }
}
