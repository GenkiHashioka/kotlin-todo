package com.example.kotlin_todo.repository

import com.example.kotlin_todo.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository

/**
 * ユーザーリポジトリインタフェース
 */
interface UserRepository : JpaRepository<User, Long>