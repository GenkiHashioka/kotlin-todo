package com.example.kotlin_todo.repository

import com.example.kotlin_todo.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository

/**
 * ユーザーリポジトリインタフェース
 */
interface UserRepository : JpaRepository<User, Long> {
    /**
     * ユーザー検索（email）
     * メールアドレスを条件にユーザーを検索する
     */
    fun findByEmail(email: String): User?
}