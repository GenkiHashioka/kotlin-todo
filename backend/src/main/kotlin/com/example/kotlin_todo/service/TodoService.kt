package com.example.kotlin_todo.service

import com.example.kotlin_todo.domain.Category
import com.example.kotlin_todo.domain.Priority
import com.example.kotlin_todo.domain.Todo
import com.example.kotlin_todo.domain.TodoStatus
import com.example.kotlin_todo.exception.CategoryNotFoundException
import com.example.kotlin_todo.exception.TodoNotFoundException
import com.example.kotlin_todo.repository.CategoryRepository
import com.example.kotlin_todo.repository.TodoRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDate

/**
 * Todoの業務ロジック層。
 * Repositoryを組み合わせて、業務ルール（Categoryの存在確認など）を担う。
 */
class TodoService(
    private val categoryRepository: CategoryRepository,
    private val todoRepository: TodoRepository,
) {
    /**
     * TODOを作成する。
     * categoryIdが指定された場合、そのCategoryが存在しなければ例外をスローする。
     */
    suspend fun create(
        ownerId: Long,
        title: String,
        description: String?,
        dueDate: LocalDate?,
        priority: Priority,
        status: TodoStatus,
        categoryId: Long?,
    ): Todo = newSuspendedTransaction(Dispatchers.IO) {
        // Categoryが存在するかどうかを確認。(存在しなければ、例外をスロー)
        if (categoryId != null) {
            categoryRepository.findById(categoryId)
                ?: throw CategoryNotFoundException(categoryId)
        }

        // Todo作成
        todoRepository.create(
            title = title,
            description = description,
            dueDate = dueDate,
            priority = priority,
            status = status,
            categoryId = categoryId,
            ownerId = ownerId,
        )
    }

    /**
     * IDでTODOを検索する。
     * 見つからなければ例外をスロー。
     */
    suspend fun findById(id: Long): Todo {
        return todoRepository.findById(id)
            ?: throw TodoNotFoundException(id)
    }

    /**
     * 全TODOを取得する。
     */
    suspend fun findAll(): List<Todo> {
        return todoRepository.findAll()
    }

    /**
     * TODOを更新する。
     * TODOまたはCategoryが存在しない場合は例外をスロー。
     */
    suspend fun update(
        id: Long,
        title: String,
        description: String?,
        dueDate: LocalDate?,
        priority: Priority,
        status: TodoStatus,
        categoryId: Long?,
    ): Todo = newSuspendedTransaction(Dispatchers.IO) {
        // TODO取得。見つからなければ、例外をスロー。
        todoRepository.findById(id)
            ?: throw TodoNotFoundException(id)

        // 存在しないカテゴリが登録されている場合、例外をスロー。
        if (categoryId != null) {
            categoryRepository.findById(categoryId)
                ?: throw CategoryNotFoundException(categoryId)
        }
        // TODO更新
        todoRepository.update(
            id = id,
            title = title,
            description = description,
            dueDate = dueDate,
            priority = priority,
            status = status,
            categoryId = categoryId,
        ) ?: throw TodoNotFoundException(id)
    }

    /**
     * TODOを削除する。
     */
    suspend fun delete(id: Long): Todo = newSuspendedTransaction(Dispatchers.IO) {
        // 削除前のTODOを取得。（存在しなければ例外）
        val todo = todoRepository.findById(id)
            ?: throw TodoNotFoundException(id)

        // 削除実行
        todoRepository.delete(id)

        // 削除内容を返却する
        todo
    }

    /**
     * カテゴリIDを検索条件として、カテゴリを検索する。
     */
    suspend fun findCategoryById(id: Long): Category? = categoryRepository.findById(id)
}