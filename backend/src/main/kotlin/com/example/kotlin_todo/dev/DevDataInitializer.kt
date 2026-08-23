package com.example.kotlin_todo.dev

import com.example.kotlin_todo.domain.User
import com.example.kotlin_todo.repository.UserRepository
import org.slf4j.LoggerFactory

/**
 * 開発用の固定ユーザーを用意するための暫定コード。
 * 認証が未実装の間、Todo.ownerIdに入れる値を確保するためだけに使用する。
 *
 * TODO(#23): 認証機能の実装時にdevパッケージごと削除する。
 */
object DevDataInitializer {
    private val logger = LoggerFactory.getLogger(DevDataInitializer::class.java)

    private const val DEV_USER_EMAIL = "dev@example.com"
    private const val DEV_USER_PASSWORD_HASH = "NOT_A_REAL_HASH_DEV_ONLY"

    /**
     * 開発用ユーザーを取得する。存在しなければ作成する。（冪等）
     */
    suspend fun ensureDevUser(userRepository: UserRepository): User {
        val existing = userRepository.findByEmail(DEV_USER_EMAIL)
        if (existing != null) {
            logger.info("開発用ユーザーを再利用します。: id={}, email={}", existing.id, existing.email)
            return existing
        }

        val created = userRepository.create(
            email = DEV_USER_EMAIL,
            passwordHash = DEV_USER_PASSWORD_HASH,
        )
        logger.info("開発用ユーザーを作成しました。: id={}, email={}", created.id, created.email)
        return created
    }
}
