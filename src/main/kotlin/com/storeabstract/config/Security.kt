package com.storeabstract.config

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.storeabstract.domain.User
import com.storeabstract.domain.UserRole
import io.ktor.server.auth.Principal
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.mindrot.jbcrypt.BCrypt

data class UserPrincipal(
    val userId: String,
    val role: UserRole,
) : Principal

class JwtService(secret: String) {
    private val issuer = "store-abstract"
    private val audience = "store-abstract-users"
    private val algorithm = Algorithm.HMAC256(secret)

    fun verifier() = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun generate(user: User): String {
        val expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(user.id.toString())
            .withClaim("role", user.role.name)
            .withExpiresAt(java.util.Date.from(expiresAt))
            .sign(algorithm)
    }
}

class PasswordService {
    fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt())

    fun verify(password: String, hash: String): Boolean = BCrypt.checkpw(password, hash)
}
