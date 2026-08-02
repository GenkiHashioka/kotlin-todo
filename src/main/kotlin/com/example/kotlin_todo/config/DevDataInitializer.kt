package com.example.kotlin_todo.config

import com.example.kotlin_todo.domain.entity.User
import com.example.kotlin_todo.repository.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

/**
 * 開発用のデータを用意するためのCommandLineRunner
 */
@Component
class DevDataInitializer(
    private val userRepository: UserRepository
) : CommandLineRunner {
    companion object {
        const val FIXED_USER_EMAIL = "fixed-user@example.com"
    }

    // アプリケーションの起動が完了した直後に、自動的に１度だけ呼ばれる。
    override fun run(vararg args: String) {
        // ダミーメールアドレスのユーザーデータがDBに存在していない場合、ダミーのユーザーデータを登録する。
        if (userRepository.findByEmail(FIXED_USER_EMAIL) == null) {
            userRepository.save(
                User(email = FIXED_USER_EMAIL, passwordHash = "dummy-hash")
            )
        }
    }
}