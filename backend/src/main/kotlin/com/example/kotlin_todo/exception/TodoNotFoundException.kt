package com.example.kotlin_todo.exception

/**
 * 指定したIDのTODOが存在しない時にスローする例外。
 */
class TodoNotFoundException(val id: Long) : RuntimeException("Todo not found: id=$id")
