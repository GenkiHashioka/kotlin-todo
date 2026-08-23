package com.example.kotlin_todo.repository

import com.example.kotlin_todo.db.Categories
import com.example.kotlin_todo.domain.Category
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

/**
 * カテゴリリポジトリ
 */
class CategoryRepository {
    /**
     * カテゴリ作成。
     */
    suspend fun create(name: String, ownerId: Long): Category =
        newSuspendedTransaction(Dispatchers.IO) {
            // カテゴリテーブルにカテゴリをINSERT。IDは自動生成の為、最後に取得する。
            val id = Categories.insert {
                it[Categories.name] = name
                it[Categories.ownerId] = ownerId
            } get Categories.id
            // カテゴリドメインを返却。
            Category(id = id, name = name, ownerId = ownerId)
        }

    /**
     * カテゴリIDで検索
     */
    suspend fun findById(id: Long): Category? =
        newSuspendedTransaction(Dispatchers.IO) {
            Categories.selectAll().where {
                Categories.id eq id
            }.singleOrNull()?.toCategory()
        }

    /**
     * カテゴリ削除
     */
    suspend fun delete(id: Long): Boolean =
        newSuspendedTransaction(Dispatchers.IO) {
            // 削除処理。削除件数が1件以上ならtrueを返却
            Categories.deleteWhere {
                Categories.id eq id
            } > 0
        }

    /**
     * DBからの検索結果をカテゴリドメインに変換する。
     */
    private fun ResultRow.toCategory(): Category = Category(
        id = this[Categories.id],
        name = this[Categories.name],
        ownerId = this[Categories.ownerId],
    )
}
