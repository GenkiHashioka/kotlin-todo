package com.example.kotlin_todo.controller

import com.example.kotlin_todo.dto.TodoCreateRequest
import com.example.kotlin_todo.dto.TodoResponse
import com.example.kotlin_todo.dto.TodoUpdateRequest
import com.example.kotlin_todo.service.TodoService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

/**
 * TODOコントローラークラス。
 * リクエストに対して適切なアプローチをコントロールする。
 */
@RestController
@RequestMapping("/todos")
class TodoController(
    private val todoService: TodoService
) {
    /**
     * 1件取得用
     */
    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<TodoResponse> {
        // 検索処理をサービス層へ依頼
        val response = todoService.findById(id)
        // ステータスコードとレスポンスを返却する。
        return ResponseEntity.ok(response)
    }

    /**
     * TODO作成
     */
    @PostMapping
    fun create(
        @Valid
        @RequestBody
        request: TodoCreateRequest
    ): ResponseEntity<TodoResponse> {
        // 作成処理をサービス層へ依頼
        val response = todoService.create(request)
        // 作成されたリソースの場所を示す、Locationヘッダーを作成。
        val location =
            ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id).toUri()
        // ステータスコードとレスポンスを返却する
        return ResponseEntity.created(location).body(response)
    }

    /**
     * 全件取得
     */
    @GetMapping
    fun findAll(): ResponseEntity<List<TodoResponse>> {
        // 全件取得をサービス層へ依頼
        val response = todoService.findAll()
        // ステータスコードとレスポンスを返却する
        return ResponseEntity.ok(response)
    }

    /**
     * 更新
     */
    @PutMapping("/{id}")
    fun update(
        @PathVariable
        id: Long,
        @Valid
        @RequestBody
        request: TodoUpdateRequest
    ): ResponseEntity<TodoResponse> {
        // 更新をサービス層へ依頼
        val response = todoService.update(id, request)
        // ステータスコードとレスポンスを返却する
        return ResponseEntity.ok(response)
    }

    /**
     * 削除用
     */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<TodoResponse> {
        // 削除をサービス層へ依頼
        val response = todoService.delete(id)
        // ステータスコードとレスポンスを返却する
        return ResponseEntity.ok(response)
    }
}