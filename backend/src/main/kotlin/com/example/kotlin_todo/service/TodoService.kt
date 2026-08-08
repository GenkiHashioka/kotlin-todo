package com.example.kotlin_todo.service

import com.example.kotlin_todo.config.DevDataInitializer
import com.example.kotlin_todo.dto.TodoCreateRequest
import com.example.kotlin_todo.dto.TodoResponse
import com.example.kotlin_todo.dto.TodoUpdateRequest
import com.example.kotlin_todo.dto.applyUpdate
import com.example.kotlin_todo.dto.toEntity
import com.example.kotlin_todo.dto.toResponse
import com.example.kotlin_todo.exception.CategoryNotFoundException
import com.example.kotlin_todo.exception.TodoNotFoundException
import com.example.kotlin_todo.repository.CategoryRepository
import com.example.kotlin_todo.repository.TodoRepository
import com.example.kotlin_todo.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Todo関係のビジネスロジックを扱うクラス
 */
@Service
class TodoService(
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val todoRepository: TodoRepository
) {
    /**
     * 作成
     */
    @Transactional
    fun create(request: TodoCreateRequest): TodoResponse {
        // 固定ユーザーを取得（User?を解決するためエルビス演算子を使用。nullの場合例外をスロー）
        val fixedUser = userRepository.findByEmail(DevDataInitializer.FIXED_USER_EMAIL)
            ?: throw IllegalStateException("fixed user is not exists")

        // リクエストにCategoryIdが存在する場合、Categoryを取得
        val category = request.categoryId?.let {
            // カテゴリが取れてこない場合、例外をスロー
            categoryRepository.findById(it).orElseThrow { CategoryNotFoundException(it) }
        }
        // エンティティを組み立てる。
        val todoEntity = request.toEntity(fixedUser, category)

        // DB保存
        val savedTodo = todoRepository.save(todoEntity)

        // レスポンス用のDTOを返却
        return savedTodo.toResponse()
    }

    /**
     * 1件取得
     * TODOのIDを検索条件として、該当するTODOを取得する
     */
    @Transactional(readOnly = true)
    fun findById(id: Long): TodoResponse {
        // 該当TODOが存在する場合はそのまま返却。無ければ例外をスロー
        return todoRepository.findById(id).orElseThrow { TodoNotFoundException(id) }.toResponse()
    }

    /**
     * 全件取得
     */
    @Transactional(readOnly = true)
    fun findAll(): List<TodoResponse> {
        return todoRepository.findAll().map { it.toResponse() }
    }

    /**
     * 更新
     */
    @Transactional
    fun update(id: Long, request: TodoUpdateRequest): TodoResponse {
        // 更新対象のTODOを取得。
        val todo = todoRepository.findById(id).orElseThrow { TodoNotFoundException(id) }

        // リクエストにCategoryIdが存在する場合、Categoryを取得
        val category = request.categoryId?.let {
            // カテゴリが取れてこない場合、例外をスロー
            categoryRepository.findById(it).orElseThrow { CategoryNotFoundException(it) }
        }
        // エンティティの内容を更新する。
        todo.applyUpdate(request, category)

        // 内容を確定させる。（@LastModifiedDateはflushのタイミングで発火するため、そのまま返却するとDB登録内容とAPIレスポンスにズレが生じる可能性がある。
        // そのため、明示的にreturn前にsaveAndFlushを行う）
        val updatedTodo = todoRepository.saveAndFlush(todo)
        // 更新後の内容を返却する。
        return updatedTodo.toResponse()
    }

    /**
     * 削除
     * TODOIDを検索条件に、該当TODOを削除する。
     */
    @Transactional
    fun delete(id: Long): TodoResponse {
        // 対象Todoの取得
        val todo = todoRepository.findById(id).orElseThrow { TodoNotFoundException(id) }
        // レスポンス用のDTOを作成
        val response = todo.toResponse()

        // 削除処理
        todoRepository.delete(todo)

        return response
    }
}