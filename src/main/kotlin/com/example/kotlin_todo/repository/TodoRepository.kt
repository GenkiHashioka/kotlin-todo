package com.example.kotlin_todo.repository

import com.example.kotlin_todo.domain.entity.Todo
import org.springframework.data.jpa.repository.JpaRepository

interface TodoRepository : JpaRepository<Todo, Long>