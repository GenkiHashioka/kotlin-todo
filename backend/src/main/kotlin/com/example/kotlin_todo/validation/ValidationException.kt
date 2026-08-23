package com.example.kotlin_todo.validation

import com.example.kotlin_todo.dto.error.FieldError

/**
 * リクエストボディの検証に失敗したときにスローする例外
 */
class ValidationException(val fieldErrors: List<FieldError>) :
    RuntimeException("Validation failed: ${fieldErrors.size} error(s)")
