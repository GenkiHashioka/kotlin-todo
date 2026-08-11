package com.example.kotlin_todo.repository

import com.example.kotlin_todo.AbstractPostgresTest
import com.example.kotlin_todo.db.Categories
import com.example.kotlin_todo.db.Todos
import com.example.kotlin_todo.db.Users
import com.example.kotlin_todo.domain.Priority
import com.example.kotlin_todo.domain.TodoStatus
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TodoRepositoryTest : AbstractPostgresTest() {
    private val userRepository = UserRepository()
    private val categoryRepository = CategoryRepository()
    private val todoRepository = TodoRepository()

    @BeforeEach
    fun cleanUp() {
        transaction {
            Todos.deleteAll()
            Categories.deleteAll()
            Users.deleteAll()
        }
    }

    @Test
    fun `Todo, Category, Userの関連を正常に保存し取得できること`() = runBlocking {
        // データ準備
        val user = userRepository.create(email = "example@test.com", passwordHash = "hashedPassword")
        val category = categoryRepository.create(name = "Category", ownerId = user.id)
        val todo = todoRepository.create(
            title = "kotlinを学習する",
            description = "Ktorを勉強する",
            dueDate = null,
            priority = Priority.MEDIUM,
            status = TodoStatus.IN_PROGRESS,
            categoryId = category.id,
            ownerId = user.id,
        )

        // 取得
        val found = todoRepository.findById(todo.id)

        // 検証
        assertNotNull(found)
        assertEquals("kotlinを学習する", found.title)
        assertEquals("Ktorを勉強する", found.description)
        assertEquals(Priority.MEDIUM, found.priority)
        assertEquals(TodoStatus.IN_PROGRESS, found.status)
        assertEquals(category.id, found.categoryId)
        assertEquals(user.id, found.ownerId)
    }

    @Test
    fun `Todoを更新すると updatedAt が更新されること`() = runBlocking {
        // データ作成
        val user = userRepository.create(email = "test@test.com", passwordHash = "hashedPassword")
        val todo = todoRepository.create(
            "タイトル",
            description = null,
            dueDate = null,
            priority = Priority.LOW,
            status = TodoStatus.NOT_STARTED,
            categoryId = null,
            ownerId = user.id,
        )
        // 元のupdated_atを取得
        val initialUpdatedAt = todo.updatedAt
        // スレッドを100ミリ秒止める。
        Thread.sleep(100)

        // 更新処理
        val updated = todoRepository.update(
            id = todo.id,
            title = "更新後タイトル",
            description = null,
            dueDate = null,
            priority = Priority.HIGH,
            status = TodoStatus.IN_PROGRESS,
            categoryId = null,
        )

        // 検証
        assertNotNull(updated)
        assertEquals("更新後タイトル", updated.title)
        assertTrue(updated.updatedAt.isAfter(initialUpdatedAt))
    }

    @Test
    fun `Categoryを削除すると Todoの CategoryId が null　になること`() = runBlocking {
        // データ作成
        val user = userRepository.create(email = "test@test.com", passwordHash = "hashedPassword")
        val category = categoryRepository.create(name = "Category", ownerId = user.id)
        val todo = todoRepository.create(
            title = "タイトル",
            description = null,
            dueDate = null,
            priority = Priority.LOW,
            status = TodoStatus.NOT_STARTED,
            categoryId = category.id,
            ownerId = user.id,
        )

        // 削除処理
        categoryRepository.delete(category.id)

        val found = todoRepository.findById(todo.id)
        assertNotNull(found)
        assertNull(found.categoryId)
    }
}