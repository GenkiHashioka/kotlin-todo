package com.example.kotlin_todo.repository

import com.example.kotlin_todo.domain.entity.Category
import com.example.kotlin_todo.domain.entity.Todo
import com.example.kotlin_todo.domain.entity.User
import com.example.kotlin_todo.domain.enums.Priority
import com.example.kotlin_todo.domain.enums.TodoStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import java.time.LocalDate

@DataJpaTest
class TodoRepositoryTest(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val categoryRepository: CategoryRepository,
    @Autowired private val todoRepository: TodoRepository,
    @Autowired private val entityManager: TestEntityManager
) {
    @Test
    fun `Todo,Category,Userの関連を正常に保存し取得できること`() {
        // Userのインスタンスを作成し、userRepository.save(user)の返り値を変数で取得
        val user = User(
            email = "test@test.com",
            passwordHash = "passwordHash"
        )
        val savedUser = userRepository.save(user)

        // 保存済みのユーザーをオーナーに指定してCategoryを作成し、save(category)の返り値を変数で取得
        val category = Category(
            name = "Category",
            owner = savedUser
        )
        val savedCategory = categoryRepository.save(category)

        // Todoを作成して、保存処理
        val todo = Todo(
            title = "テストテストテスト",
            description = "説明です。テストです。",
            dueDate = LocalDate.now(),
            priority = Priority.MEDIUM,
            status = TodoStatus.IN_PROGRESS,
            category = savedCategory,
            owner = savedUser,
        )

        val savedTodo = todoRepository.save(todo)

        // todoRepository.findById(savedTodoのid)で取得し直す。
        // TODOIDがnullの場合、例外を発生させる。
        val todoId = checkNotNull(savedTodo.id) {"保存後のTODOIDはnullを許容していません。"}
        val foundTodoOptional = todoRepository.findById(todoId)

        // 取得できたかをチェック。
        assertTrue(foundTodoOptional.isPresent)

        // 期待値通りの値が取得できているかを確認
        val foundTodo = foundTodoOptional.get()
        assertEquals("テストテストテスト", foundTodo.title)
        assertEquals("test@test.com", foundTodo.owner.email)
        assertEquals("Category", foundTodo.category?.name)
        assertEquals(Priority.MEDIUM, foundTodo.priority)
        assertEquals(TodoStatus.IN_PROGRESS, foundTodo.status)
    }

    @Test
    fun `Todoを更新するとupdatedAtが自動更新されること`() {
        // Userのインスタンスを作成し、userRepository.save(user)の返り値を変数で取得
        val user = User(
            email = "test@test.com",
            passwordHash = "passwordHash"
        )
        val savedUser = userRepository.save(user)

        // 保存済みのユーザーをオーナーに指定してCategoryを作成し、save(category)の返り値を変数で取得
        val category = Category(
            name = "Category",
            owner = savedUser
        )
        val savedCategory = categoryRepository.save(category)

        // 更新前のTodoを作成して、保存処理
        val initialTodo = Todo(
            title = "テストテストテスト",
            description = "説明です。テストです。",
            dueDate = LocalDate.now(),
            priority = Priority.MEDIUM,
            status = TodoStatus.IN_PROGRESS,
            category = savedCategory,
            owner = savedUser,
        )

        val savedTodo = todoRepository.save(initialTodo)

        // 保存直後のupdatedAtを変数に控える
        val initialUpdatedAt = savedTodo.updatedAt

        // 処理速度が早すぎて「同一ミリ秒」になるとテストが失敗するため、数ミリ秒だけ次の処理を待つ
        Thread.sleep(10)

        // savedTodoのtitleを書き換える
        savedTodo.title = "変更したタイトル"

        // saveAndFlush()で即座にDBへUPDATEを発行・反映させる。
        val updatedTodo = todoRepository.saveAndFlush(savedTodo)

        // 更新後のupdatedAtがinitialUpdatedAtより後の時刻になっているか検証
        assertTrue(updatedTodo.updatedAt.isAfter(initialUpdatedAt))
    }

    @Test
    fun `Categoryを削除したら、それに紐づいていたTodoのcategoryが自動的にnullになる`() {
        // Userのインスタンスを作成し、userRepository.save(user)の返り値を変数で取得
        val user = User(
            email = "test@test.com",
            passwordHash = "passwordHash"
        )
        val savedUser = userRepository.save(user)

        // 保存済みのユーザーをオーナーに指定してCategoryを作成し、save(category)の返り値を変数で取得
        val category = Category(
            name = "Category",
            owner = savedUser
        )
        val savedCategory = categoryRepository.save(category)

        // 更新前のTodoを作成して、保存処理
        val todo = Todo(
            title = "テストテストテスト",
            description = "説明です。テストです。",
            dueDate = LocalDate.now(),
            priority = Priority.MEDIUM,
            status = TodoStatus.IN_PROGRESS,
            category = savedCategory,
            owner = savedUser,
        )

        val savedTodo = todoRepository.save(todo)
        // 再検索用のTODOIDを取得する
        val todoId = checkNotNull(savedTodo.id) {"保存後のTODOIDはnullを許容していません。"}
        // 1次キャッシュをクリアして、DBの最新状態を読み込ませる
        entityManager.flush()
        entityManager.clear()
        // Categoryを削除して即座にDBへ反映
        categoryRepository.delete(savedCategory)
        categoryRepository.flush()

        // todoを取得し直す
        val foundTodoOptional = todoRepository.findById(todoId)
        assertTrue(foundTodoOptional.isPresent)

        // categoryがnullになっていることを検証
        val foundTodo = foundTodoOptional.get()
        assertNull(foundTodo.category)
    }
}