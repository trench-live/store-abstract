package com.storeabstract.service

import com.storeabstract.config.ApiException
import com.storeabstract.config.PasswordService
import com.storeabstract.config.JwtService
import com.storeabstract.domain.User
import com.storeabstract.domain.UserRole
import com.storeabstract.repository.UserRepository
import io.ktor.http.HttpStatusCode

class AuthService(
    private val userRepository: UserRepository,
    private val passwordService: PasswordService,
    private val jwtService: JwtService,
) {
    fun register(email: String, password: String): String {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank() || password.length < 6) {
            throw ApiException(HttpStatusCode.BadRequest, "Invalid email or password")
        }

        if (userRepository.findByEmail(normalizedEmail) != null) {
            throw ApiException(HttpStatusCode.Conflict, "User with this email already exists")
        }

        val user = userRepository.create(
            email = normalizedEmail,
            passwordHash = passwordService.hash(password),
            role = UserRole.USER,
        )
        return jwtService.generate(user)
    }

    fun login(email: String, password: String): String {
        val normalizedEmail = email.trim().lowercase()
        val user = userRepository.findByEmail(normalizedEmail)
            ?: throw ApiException(HttpStatusCode.Unauthorized, "Invalid credentials")

        if (!passwordService.verify(password, user.passwordHash)) {
            throw ApiException(HttpStatusCode.Unauthorized, "Invalid credentials")
        }

        return jwtService.generate(user)
    }

    fun getUserById(userId: String): User {
        val id = runCatching { java.util.UUID.fromString(userId) }.getOrNull()
            ?: throw ApiException(HttpStatusCode.Unauthorized, "Invalid token subject")

        return userRepository.findById(id)
            ?: throw ApiException(HttpStatusCode.Unauthorized, "User not found")
    }
}
