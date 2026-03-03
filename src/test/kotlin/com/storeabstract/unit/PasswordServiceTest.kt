package com.storeabstract.unit

import com.storeabstract.config.PasswordService
import kotlin.test.Test
import kotlin.test.assertFalse
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
}
