package com.example.kotlin_todo.repository

import com.example.kotlin_todo.AbstractPostgresTest
import com.example.kotlin_todo.db.Users
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserRepositoryTest : AbstractPostgresTest() {
    private val userRepository = UserRepository()

    @BeforeEach
    fun cleanUp() {
        transaction {
            Users.deleteAll()
        }
    }

    @Test
    fun `findByEmail で存在するユーザーを返す`() = runBlocking {
        // ユーザー作成
        userRepository.create(email = "test@test.com", passwordHash = "passwordHash")
        // 取得
        val found = userRepository.findByEmail("test@test.com")

        // 検証
        assertNotNull(found)
        assertEquals("test@test.com", found.email)
    }

    @Test
    fun `findByEmail は存在しないメールアドレスで検索するとnullを返却する`() = runBlocking {
        val found = userRepository.findByEmail("nullnull@null.com")
        // 検証
        assertNull(found)
    }
}