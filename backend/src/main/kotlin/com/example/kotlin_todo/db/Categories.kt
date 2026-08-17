package com.example.kotlin_todo.db

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

/**
 * カテゴリテーブル定義
 */
object Categories : Table("categories") {
    /** カテゴリID */
    val id = long("id").autoIncrement()

    /** カテゴリ名 */
    val name = varchar("name", 100)

    /** オーナーID */
    val ownerId = long("owner_id")
        .references(Users.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("categories_owner_name_unique", ownerId, name)
    }
}