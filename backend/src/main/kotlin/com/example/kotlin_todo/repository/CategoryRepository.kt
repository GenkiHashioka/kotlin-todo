package com.example.kotlin_todo.repository

import com.example.kotlin_todo.domain.entity.Category
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<Category, Long>