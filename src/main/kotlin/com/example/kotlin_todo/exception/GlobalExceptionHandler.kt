package com.example.kotlin_todo.exception

import com.example.kotlin_todo.dto.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * エラーハンドラ。
 * コントローラからスローされた例外をこのクラスで適切にハンドリングを行う。
 */
@RestControllerAdvice
class GlobalExceptionHandler {
    // TodoNotFoundExceptionとCategoryNotFoundExceptionを捕捉し、エラーハンドリングを行う。
    @ExceptionHandler(TodoNotFoundException::class, CategoryNotFoundException::class)
    fun handleNotFoundException(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(status = 404, message = ex.message ?: "Not Found"))
    }

    // MethodArgumentNotValidExceptionを補足し、エラーハンドリングを行う。（バリデーションエラー）
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        // エラーメッセージを生成する
        val message = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        // エラーレスポンスを返却する。
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(status = 400, message = message))
    }
}