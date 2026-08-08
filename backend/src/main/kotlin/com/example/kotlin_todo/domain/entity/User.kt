package com.example.kotlin_todo.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * ユーザーエンティティクラス。
 */
@Entity
@Table(name = "users")
class User(
    /** ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    /** メールアドレス */
    @Column(unique = true, nullable = false)
    var email: String,

    /** ハッシュ化したパスワード */
    @Column(nullable = false)
    var passwordHash: String,

    /** 作成日（後から変更不可） */
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)