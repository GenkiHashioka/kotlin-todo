package com.example.kotlin_todo.service

import com.example.kotlin_todo.AbstractPostgresTest
import com.example.kotlin_todo.db.Categories
import com.example.kotlin_todo.db.Todos
import com.example.kotlin_todo.db.Users
import com.example.kotlin_todo.domain.Priority
import com.example.kotlin_todo.domain.TodoStatus
import com.example.kotlin_todo.exception.CategoryNotFoundException
import com.example.kotlin_todo.exception.TodoNotFoundException
import com.example.kotlin_todo.repository.CategoryRepository
import com.example.kotlin_todo.repository.TodoRepository
import com.example.kotlin_todo.repository.UserRepository
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TodoServiceTest : AbstractPostgresTest() {
    private val userRepository: UserRepository = UserRepository()
    private val categoryRepository: CategoryRepository = CategoryRepository()
    private val todoRepository: TodoRepository = TodoRepository()

    // テスト対象
    private val todoService = TodoService(categoryRepository, todoRepository)

    // 初期化
    @BeforeEach
    fun cleanUp() {
        transaction {
            Todos.deleteAll()
            Categories.deleteAll()
            Users.deleteAll()
        }
    }

    @Test
    fun `createで作成したTODOがfindByIdで取得できる`() = runBlocking {
        // 事前準備
        val user = userRepository.create(email = "test@test.com", passwordHash = "hashedPassword")
        val category = categoryRepository.create(name = "category", ownerId = user.id)

        // 実行
        val created = todoService.create(
            ownerId = user.id,
            title = "kotlinを学ぶ",
            description = "Ktorを勉強",
            dueDate = null,
            priority = Priority.HIGH,
            status = TodoStatus.NOT_STARTED,
            categoryId = category.id,
        )
        val found = todoService.findById(created.id)

        // 検証
        assertEquals(created.id, found.id)
        assertEquals("kotlinを学ぶ", found.title)
        assertEquals(Priority.HIGH, found.priority)
        assertEquals(category.id, found.categoryId)
    }

    @Test
    fun `createで存在しないcategoryIdを指定すると、CategoryNotFoundException`() = runBlocking {
        val user = userRepository.create(email = "test@test.com", passwordHash = "hashedPassword")
        // 検証
        // 例外を捕捉する想定
        val ex = assertFailsWith<CategoryNotFoundException> {
            todoService.create(
                ownerId = user.id,
                title = "test",
                description = null,
                dueDate = null,
                priority = Priority.HIGH,
                status = TodoStatus.NOT_STARTED,
                // 存在しないIDを指定
                categoryId = 9999L,
            )
        }
        assertEquals(9999L, ex.id)
    }

    @Test
    fun `findByIdで存在しないIDを指定すると、TodoNotFoundException`() = runBlocking {
        val ex = assertFailsWith<TodoNotFoundException> {
            todoService.findById(9999L)
        }
        assertEquals(9999L, ex.id)
    }

    @Test
    fun `updateで存在しないIDを指定するとTodoNotFoundException`() = runBlocking {
        val ex = assertFailsWith<TodoNotFoundException> {
            return@assertFailsWith todoService.update(
                id = 9999L,
                title = "更新後タイトル",
                description = null,
                dueDate = null,
                priority = Priority.HIGH,
                status = TodoStatus.IN_PROGRESS,
                categoryId = null,
            )
        }
        assertEquals(9999L, ex.id)
    }

    @Test
    fun `deleteは削除したTODOの内容を返却する`(): Unit = runBlocking {
        // 事前準備
        val user = userRepository.create(email = "test@test.com", passwordHash = "hashedPassword")
        val created = todoService.create(
            ownerId = user.id,
            title = "消される予定のタイトル",
            description = "TODOの説明",
            dueDate = null,
            priority = Priority.MEDIUM,
            status = TodoStatus.IN_PROGRESS,
            categoryId = null,
        )

        // 実行
        val deleted = todoService.delete(created.id)

        // 削除された内容が返却されている。
        assertEquals(created.id, deleted.id)
        assertEquals("消される予定のタイトル", deleted.title)
        assertEquals("TODOの説明", deleted.description)

        // 削除後にfindByIdでTODOを取得しようとすると例外が発生すること
        assertFailsWith<TodoNotFoundException> {
            todoService.findById(created.id)
        }
    }
}