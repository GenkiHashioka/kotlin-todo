package com.example.kotlin_todo.exception

class CategoryNotFoundException(id: Long) : RuntimeException("Category not found: $id")