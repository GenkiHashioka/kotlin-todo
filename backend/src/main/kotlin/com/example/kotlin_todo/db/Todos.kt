package com.example.kotlin_todo.db

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * TODOテーブル定義
 */
object Todos : Table("todos") {
    /** TODOID */
    val id = long("id").autoIncrement()

    /** タイトル */
    val title = varchar("title", 200)

    /** TODOの説明 */
    val description = text("description").nullable()

    /** 期日 */
    val dueDate = date("due_date").nullable()

    /** 優先度 */
    val priority = varchar("priority", 20)

    /** ステータス */
    val status = varchar("status", 20)

    /** カテゴリID */
    val categoryId = long("category_id")
        .references(Categories.id, onDelete = ReferenceOption.SET_NULL)
        .nullable()

    /** オーナーID */
    val ownerId = long("owner_id")
        .references(Users.id, onDelete = ReferenceOption.CASCADE)

    /** 作成日 */
    val createdAt = datetime("created_at")

    /** 更新日 */
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)

    /**
     * インデックス
     */
    init {
        index("idx_todos_owner_id", isUnique = false, ownerId)
        index("idx_todos_owner_status", isUnique = false, ownerId, status)
        index("idx_todos_due_date", isUnique = false, dueDate)
    }
}
