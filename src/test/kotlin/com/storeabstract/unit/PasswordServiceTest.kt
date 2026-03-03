package com.storeabstract.unit

import com.storeabstract.config.PasswordService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordServiceTest {
    private val passwordService = PasswordService()

    @Test
    fun `hash and verify password`() {
        val raw = "SuperSecret123"
        val hash = passwordService.hash(raw)

        assertTrue(passwordService.verify(raw, hash))
        assertFalse(passwordService.verify("wrong", hash))
    }

    @Test
    fun `same password produces different hashes because of salt`() {
        val raw = "SuperSecret123"

        val hash1 = passwordService.hash(raw)
        val hash2 = passwordService.hash(raw)

        assertNotEquals(hash1, hash2)
        assertTrue(passwordService.verify(raw, hash1))
        assertTrue(passwordService.verify(raw, hash2))
    }

    @Test
    fun `generated hash has bcrypt format`() {
        val hash = passwordService.hash("AnotherSecret456")

        assertTrue(hash.startsWith("$2"))
        assertTrue(hash.length > 30)
    }
}
