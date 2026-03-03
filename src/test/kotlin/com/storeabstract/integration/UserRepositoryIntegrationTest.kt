package com.storeabstract.integration

import com.storeabstract.TestDbCleaner
import com.storeabstract.TestPostgres
import com.storeabstract.domain.UserRole
import com.storeabstract.repository.UserRepository
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UserRepositoryIntegrationTest {
    private val repository = UserRepository()

    @BeforeEach
    fun clean() {
        TestDbCleaner.cleanAll()
    }

    @Test
    fun `create and fetch user by email`() {
        val created = repository.create(
            email = "integration@example.com",
            passwordHash = "hash",
            role = UserRole.USER,
        )

        val found = repository.findByEmail("integration@example.com")

        assertNotNull(found)
        assertEquals(created.id, found.id)
        assertEquals(created.email, found.email)
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            TestPostgres.initDatabase()
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            TestPostgres.stopDatabase()
        }
    }
}
