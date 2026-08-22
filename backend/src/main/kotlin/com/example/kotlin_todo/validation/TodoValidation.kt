package com.example.kotlin_todo.validation

import com.example.kotlin_todo.dto.TodoCreateRequest
import com.example.kotlin_todo.dto.TodoUpdateRequest
import com.example.kotlin_todo.dto.error.FieldError
import io.konform.validation.Invalid
import io.konform.validation.Validation
import io.konform.validation.ValidationError
import io.konform.validation.ValidationResult
import io.konform.validation.constraints.maxLength
import io.konform.validation.constraints.minimum
import io.konform.validation.constraints.notBlank

/**
 * TodoCreateRequestのバリデーション
 */
private val validateTodoCreateRequest = Validation<TodoCreateRequest> {
    // タイトル
    TodoCreateRequest::title {
        notBlank()
        maxLength(200)
    }
    // Todoの説明
    TodoCreateRequest::description ifPresent {
        maxLength(2000)
    }
    // カテゴリID
    TodoCreateRequest::categoryId ifPresent {
        minimum(1L)
    }
}

/**
 * TodoUpdateRequestのバリデーション
 */
private val validateTodoUpdateRequest = Validation<TodoUpdateRequest> {
    // タイトル
    TodoUpdateRequest::title {
        notBlank()
        maxLength(200)
    }
    // Todoの説明
    TodoUpdateRequest::description ifPresent {
        maxLength(2000)
    }
    // カテゴリID
    TodoUpdateRequest::categoryId ifPresent {
        minimum(1L)
    }
}

/**
 * 検証に失敗した場合ValidationExceptionをスロー
 */
fun TodoCreateRequest.validateOrThrow() {
    validateTodoCreateRequest(this).throwIfInvalid()
}

fun TodoUpdateRequest.validateOrThrow() {
    validateTodoUpdateRequest(this).throwIfInvalid()
}

private fun ValidationResult<*>.throwIfInvalid() {
    if (this is Invalid) {
        throw ValidationException(errors.map { it.toFieldError() })
    }
}

private fun ValidationError.toFieldError(): FieldError =
    FieldError(
        // dataPathはJSONPath風のなので、頭文字に.が付く。
        // クライアントから見えるフィールド名が整合するように頭文字の.を取り除く
        field = dataPath.removePrefix("."),
        message = message,
    )