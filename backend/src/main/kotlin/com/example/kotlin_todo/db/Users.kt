package com.example.kotlin_todo.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * ユーザーテーブル定義
 */
object Users : Table("users") {
    /** ID */
    val id = long("id").autoIncrement()

    /** メールアドレス */
    val email = varchar("email", 255).uniqueIndex()

    /** パスワード */
    val passwordHash = varchar("password_hash", 255)

    /** 作成日時 */
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)
}