package com.example.kotlin_todo.repository

import com.example.kotlin_todo.db.Todos
import com.example.kotlin_todo.domain.Priority
import com.example.kotlin_todo.domain.Todo
import com.example.kotlin_todo.domain.TodoStatus
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.LocalDateTime

class TodoRepository {
    /**
     * TODO作成
     */
    suspend fun create(
        title: String,
        description: String?,
        dueDate: LocalDate?,
        priority: Priority,
        status: TodoStatus,
        categoryId: Long?,
        ownerId: Long,
    ): Todo = newSuspendedTransaction(Dispatchers.IO) {
        // 現在日時の取得
        val now = LocalDateTime.now()

        // TODO作成処理
        val id = Todos.insert {
            it[Todos.title] = title
            it[Todos.description] = description
            it[Todos.dueDate] = dueDate
            it[Todos.priority] = priority.name
            it[Todos.status] = status.name
            it[Todos.categoryId] = categoryId
            it[Todos.ownerId] = ownerId
            it[Todos.createdAt] = now
            it[Todos.updatedAt] = now
        } get Todos.id

        // TODOドメインを返却する。
        Todo(
            id = id,
            title = title,
            description = description,
            dueDate = dueDate,
            priority = priority,
            status = status,
            categoryId = categoryId,
            ownerId = ownerId,
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * TODOIDで検索
     */
    suspend fun findById(id: Long): Todo? =
        newSuspendedTransaction(Dispatchers.IO) {
            Todos.selectAll().where {
                Todos.id eq id
            }.singleOrNull()?.toTodo()
        }

    /**
     * 全件取得
     */
    suspend fun findAll(): List<Todo> =
        newSuspendedTransaction(Dispatchers.IO) {
            Todos.selectAll().map {
                it.toTodo()
            }
        }

    /**
     * 更新
     */
    suspend fun update(
        id: Long,
        title: String,
        description: String?,
        dueDate: LocalDate?,
        priority: Priority,
        status: TodoStatus,
        categoryId: Long?,
    ): Todo? = newSuspendedTransaction(Dispatchers.IO) {
        // 現在日時の取得
        val now = LocalDateTime.now()
        // 更新処理
        val updated = Todos.update({ Todos.id eq id }) {
            it[Todos.title] = title
            it[Todos.description] = description
            it[Todos.dueDate] = dueDate
            it[Todos.priority] = priority.name
            it[Todos.status] = status.name
            it[Todos.categoryId] = categoryId
            it[Todos.updatedAt] = now
        }
        // 更新されたTodoエンティティを返却する。
        if (updated > 0) {
            Todos.selectAll().where { Todos.id eq id }.singleOrNull()?.toTodo()
        } else {
            null
        }
    }

    /**
     * TODO削除
     */
    suspend fun delete(id: Long): Boolean =
        newSuspendedTransaction(Dispatchers.IO) {
            // 削除件数が1件以上であればtrueを返却する
            Todos.deleteWhere { Todos.id eq id } > 0
        }

    /**
     * DBからの取得結果をTODOドメインに変換する
     */
    private fun ResultRow.toTodo(): Todo = Todo(
        id = this[Todos.id],
        title = this[Todos.title],
        description = this[Todos.description],
        dueDate = this[Todos.dueDate],
        priority = Priority.valueOf(this[Todos.priority]),
        status = TodoStatus.valueOf(this[Todos.status]),
        categoryId = this[Todos.categoryId],
        ownerId = this[Todos.ownerId],
        createdAt = this[Todos.createdAt],
        updatedAt = this[Todos.updatedAt],
    )
}
