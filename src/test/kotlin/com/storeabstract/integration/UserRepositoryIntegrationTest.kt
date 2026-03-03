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
import kotlin.test.assertTrue

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

    @Test
    fun `create or update admin creates admin when user does not exist`() {
        val admin = repository.createOrUpdateAdmin(
            email = "admin.integration@example.com",
            passwordHash = "admin-hash-1",
        )

        assertEquals(UserRole.ADMIN, admin.role)
        assertEquals("admin.integration@example.com", admin.email)
        assertEquals("admin-hash-1", admin.passwordHash)

        val found = repository.findByEmail("admin.integration@example.com")
        assertNotNull(found)
        assertEquals(UserRole.ADMIN, found.role)
    }

    @Test
    fun `create or update admin promotes existing user and updates hash`() {
        val createdUser = repository.create(
            email = "existing.user@example.com",
            passwordHash = "old-hash",
            role = UserRole.USER,
        )

        val updatedAdmin = repository.createOrUpdateAdmin(
            email = "existing.user@example.com",
            passwordHash = "new-admin-hash",
        )

        assertEquals(createdUser.id, updatedAdmin.id)
        assertEquals(UserRole.ADMIN, updatedAdmin.role)
        assertEquals("new-admin-hash", updatedAdmin.passwordHash)

        val found = repository.findById(createdUser.id)
        assertNotNull(found)
        assertTrue(found.role == UserRole.ADMIN && found.passwordHash == "new-admin-hash")
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
