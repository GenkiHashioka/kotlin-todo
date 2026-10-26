package com.genkihashioka.kotlintodo.ui.todo.list

import androidx.lifecycle.viewModelScope
import com.genkihashioka.kotlintodo.data.remote.api.FakeTodoApi
import com.genkihashioka.kotlintodo.data.remote.model.Priority
import com.genkihashioka.kotlintodo.data.remote.model.TodoDto
import com.genkihashioka.kotlintodo.data.remote.model.TodoStatus
import com.genkihashioka.kotlintodo.data.repository.TodoRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * TodoListViewModelのテストクラス
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodoListViewModelTest {
    // Coroutineを進めるタイミングをテスト側から制御できるようにするためのDispatcher
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun getTodos_whenEmpty_updatesStateToEmpty() = runTest {
        val todoApi = FakeTodoApi()
        val todoRepository = TodoRepository(todoApi)
        val viewModel = TodoListViewModel(todoRepository)

        try {
            // 実行
            advanceUntilIdle()

            // 検証
            assertEquals(TodoListUiState.Empty, viewModel.uiState.value)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun getTodos_whenNotEmpty_updatesStateToSuccess() = runTest {
        val todoApi = FakeTodoApi()
        // Todoのテストデータ
        val expectedTodos = listOf(
            TodoDto(
                id = 1L,
                title = "単体テストを学ぶ",
                description = null,
                dueDate = null,
                priority = Priority.LOW,
                status = TodoStatus.NOT_STARTED,
                category = null,
                createdAt = "2026-10-25T10:00:00",
                updatedAt = "2026-10-25T10:00:00",
            ),
        )
        // Fakeへ一覧を設定。
        todoApi.todosToReturn = expectedTodos
        val todoRepository = TodoRepository(todoApi)
        val viewModel = TodoListViewModel(todoRepository)

        try {
            // 実行
            advanceUntilIdle()

            // 検証
            assertEquals(
                TodoListUiState.Success(expectedTodos),
                viewModel.uiState.value,
            )
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun getTodos_whenFails_updatesStateToError() = runTest {
        val todoApi = FakeTodoApi()
        todoApi.exceptionToThrow = IllegalStateException("一覧取得失敗のテスト")

        val todoRepository = TodoRepository(todoApi)
        val viewModel = TodoListViewModel(todoRepository)

        try {
            // 実行
            advanceUntilIdle()

            // 検証
            assertEquals(
                TodoListUiState.Error,
                viewModel.uiState.value,
            )
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun getTodos_whileWaiting_keepsStateLoading() = runTest {
        val pendingResponse = CompletableDeferred<List<TodoDto>>()
        val todoApi = FakeTodoApi()
        todoApi.pendingTodosResponse = pendingResponse

        val todoRepository = TodoRepository(todoApi)
        val viewModel = TodoListViewModel(todoRepository)
        try {
            // viewModel生成直後はLoading状態であることを検証
            assertEquals(
                TodoListUiState.Loading,
                viewModel.uiState.value,
            )

            // 応答待ちになるところまで処理を進める
            runCurrent()

            // getTodosが呼ばれていることと、まだLoading状態であることを検証
            assertEquals(1, todoApi.getTodosCallCount)
            assertEquals(
                TodoListUiState.Loading,
                viewModel.uiState.value
            )

            // APIに応答を許可する。
            pendingResponse.complete(emptyList())

            // 続きの処理を実行
            advanceUntilIdle()

            // uiStateの状態がEmptyであることを検証
            assertEquals(
                TodoListUiState.Empty,
                viewModel.uiState.value,
            )
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun retry_afterFailure_updatesStateToSuccess() = runTest {
        val todoApi = FakeTodoApi()
        todoApi.exceptionToThrow = IllegalStateException("一覧取得失敗のテスト")

        val todoRepository = TodoRepository(todoApi)
        val viewModel = TodoListViewModel(todoRepository)

        try {
            advanceUntilIdle()

            // uiStateがErrorであることとgetTodosが１度実行されていることを検証
            assertEquals(1, todoApi.getTodosCallCount)
            assertEquals(
                TodoListUiState.Error,
                viewModel.uiState.value,
            )

            // exceptionToThrowのリセット。
            todoApi.exceptionToThrow = null

            val pendingResponse = CompletableDeferred<List<TodoDto>>()
            todoApi.pendingTodosResponse = pendingResponse

            // viewModel.retry()を実行
            viewModel.retry()

            // 応答待ちになるところまで処理を進める
            runCurrent()

            // uiStateがLoadingであることとgetTodosが2度実行されていることを検証
            assertEquals(2, todoApi.getTodosCallCount)
            assertEquals(
                TodoListUiState.Loading,
                viewModel.uiState.value,
            )

            // Todoのテストデータ
            val expectedTodos = listOf(
                TodoDto(
                    id = 1L,
                    title = "単体テストを学ぶ",
                    description = null,
                    dueDate = null,
                    priority = Priority.LOW,
                    status = TodoStatus.NOT_STARTED,
                    category = null,
                    createdAt = "2026-10-25T10:00:00",
                    updatedAt = "2026-10-25T10:00:00",
                ),
            )
            pendingResponse.complete(expectedTodos)
            advanceUntilIdle()

            // Success(expectedTodos)と一致することを検証。
            assertEquals(
                TodoListUiState.Success(expectedTodos),
                viewModel.uiState.value,
            )
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun refresh_afterSuccess_updatesStateWithLatestTodos() = runTest {
        // Todoのテストデータを準備
        val todoA = TodoDto(
            id = 1L,
            title = "単体テストを学ぶ",
            description = null,
            dueDate = null,
            priority = Priority.LOW,
            status = TodoStatus.NOT_STARTED,
            category = null,
            createdAt = "2026-10-25T10:00:00",
            updatedAt = "2026-10-25T10:00:00",
        )

        val todoB = TodoDto(
            id = 2L,
            title = "Coroutineを学ぶ",
            description = null,
            dueDate = null,
            priority = Priority.LOW,
            status = TodoStatus.NOT_STARTED,
            category = null,
            createdAt = "2026-10-25T10:00:00",
            updatedAt = "2026-10-25T10:00:00",
        )

        val todoC = TodoDto(
            id = 3L,
            title = "Composeを学ぶ",
            description = null,
            dueDate = null,
            priority = Priority.LOW,
            status = TodoStatus.NOT_STARTED,
            category = null,
            createdAt = "2026-10-25T10:00:00",
            updatedAt = "2026-10-25T10:00:00",
        )

        val updatedTodoB = todoB.copy(
            title = "Coroutineのテストを学ぶ",
        )

        // 更新前後のTodo
        val initialTodos = listOf(todoA, todoB)
        val refreshedTodos = listOf(updatedTodoB, todoC)

        val todoApi = FakeTodoApi()
        val pendingResponse = CompletableDeferred<List<TodoDto>>()
        todoApi.pendingTodosResponse = pendingResponse
        val todoRepository = TodoRepository(todoApi)
        val viewModel = TodoListViewModel(todoRepository)

        try {
            runCurrent()
            // 取得開始後はLoading
            assertEquals(
                TodoListUiState.Loading,
                viewModel.uiState.value,
            )
            // getTodosの呼び出し回数は1回
            assertEquals(1, todoApi.getTodosCallCount)

            // 1回目の応答を完了させる
            pendingResponse.complete(initialTodos)
            advanceUntilIdle()

            // Success(initialTodos)で処理が完了していること
            assertEquals(
                TodoListUiState.Success(initialTodos),
                viewModel.uiState.value,
            )
            // getTodosの呼び出し回数は1回のまま
            assertEquals(1, todoApi.getTodosCallCount)

            // 2回目の取得用に、未完了の応答を新しく用意する
            val refreshResponse = CompletableDeferred<List<TodoDto>>()
            todoApi.pendingTodosResponse = refreshResponse

            // refresh()を実行
            viewModel.refresh()

            // 応答待ちになるところまで処理を進める
            runCurrent()
            // 取得開始直後はLoading
            assertEquals(
                TodoListUiState.Loading,
                viewModel.uiState.value,
            )
            // getTodosの呼び出し回数は2回
            assertEquals(2, todoApi.getTodosCallCount)

            // 2回目の応答を完了させる。
            refreshResponse.complete(refreshedTodos)
            advanceUntilIdle()

            // Success(refreshedTodos)で処理が完了していること
            assertEquals(
                TodoListUiState.Success(refreshedTodos),
                viewModel.uiState.value,
            )

            // 最終的なgetTodosの呼び出し回数は2回
            assertEquals(2, todoApi.getTodosCallCount)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }
}
