package com.example.kotlin_todo.exception

/**
 * 指定したIDのCategoryが存在しない時にスローする例外。
 */
class CategoryNotFoundException(val id: Long) : RuntimeException("Category not found: id=$id")
