package com.example.kotlin_todo.repository

import com.example.kotlin_todo.db.Users
import com.example.kotlin_todo.domain.User
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

/**
 * ユーザーリポジトリ
 */
class UserRepository {
    /**
     * ユーザー作成。
     */
    suspend fun create(email: String, passwordHash: String): User =
        newSuspendedTransaction(Dispatchers.IO) {
            // 現在日時を取得。
            val now = LocalDateTime.now()
            // ユーザーテーブルにユーザー情報をINSERT。IDは自動生成の為、最後に取得する。
            val id = Users.insert {
                it[Users.email] = email
                it[Users.passwordHash] = passwordHash
                it[Users.createdAt] = now
            } get Users.id
            // ドメインクラスを返却。
            User(id = id, email = email, passwordHash = passwordHash, createdAt = now)
        }

    /**
     * IDで検索
     */
    suspend fun findById(id: Long): User? =
        newSuspendedTransaction(Dispatchers.IO) {
            // IDを検索条件にユーザー情報を取得。
            Users.selectAll().where {
                Users.id eq id
            }.singleOrNull()?.toUser()
        }

    /**
     * メールアドレスで検索
     */
    suspend fun findByEmail(email: String): User? =
        newSuspendedTransaction(Dispatchers.IO) {
            // メールアドレスを検索条件にユーザー情報を取得。
            Users.selectAll().where {
                Users.email eq email
            }.singleOrNull()?.toUser()
        }

    /**
     * DBからの検索結果をユーザードメインに変換
     */
    private fun ResultRow.toUser(): User = User(
        id = this[Users.id],
        email = this[Users.email],
        passwordHash = this[Users.passwordHash],
        createdAt = this[Users.createdAt],
    )
}