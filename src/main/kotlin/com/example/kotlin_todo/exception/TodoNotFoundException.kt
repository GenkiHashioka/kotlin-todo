package com.example.kotlin_todo.exception

class TodoNotFoundException(id: Long) : RuntimeException("Todo not found: $id")