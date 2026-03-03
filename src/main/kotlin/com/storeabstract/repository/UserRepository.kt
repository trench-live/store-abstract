package com.storeabstract.repository

import com.storeabstract.config.DatabaseFactory
import com.storeabstract.config.UsersTable
import com.storeabstract.domain.User
import com.storeabstract.domain.UserRole
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.util.UUID

class UserRepository {
    fun create(email: String, passwordHash: String, role: UserRole = UserRole.USER): User = DatabaseFactory.dbQuery {
        val now = LocalDateTime.now()
        val id = UUID.randomUUID()
        UsersTable.insert {
            it[UsersTable.id] = id
            it[UsersTable.email] = email
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.role] = role
            it[UsersTable.createdAt] = now
        }
        User(id, email, passwordHash, role, now)
    }

    fun findByEmail(email: String): User? = DatabaseFactory.dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.email eq email }
            .singleOrNull()
            ?.toDomain()
    }

    fun findById(id: UUID): User? = DatabaseFactory.dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.id eq id }
            .singleOrNull()
            ?.toDomain()
    }

    fun createOrUpdateAdmin(email: String, passwordHash: String): User = DatabaseFactory.dbQuery {
        val existing = UsersTable.selectAll()
            .where { UsersTable.email eq email }
            .singleOrNull()

        if (existing == null) {
            val now = LocalDateTime.now()
            val id = UUID.randomUUID()
            UsersTable.insert {
                it[UsersTable.id] = id
                it[UsersTable.email] = email
                it[UsersTable.passwordHash] = passwordHash
                it[UsersTable.role] = UserRole.ADMIN
                it[UsersTable.createdAt] = now
            }
            User(id, email, passwordHash, UserRole.ADMIN, now)
        } else {
            UsersTable.update({ UsersTable.email eq email }) {
                it[UsersTable.passwordHash] = passwordHash
                it[UsersTable.role] = UserRole.ADMIN
            }
            UsersTable.selectAll()
                .where { UsersTable.email eq email }
                .single()
                .toDomain()
        }
    }

    private fun ResultRow.toDomain(): User = User(
        id = this[UsersTable.id].value,
        email = this[UsersTable.email],
        passwordHash = this[UsersTable.passwordHash],
        role = this[UsersTable.role],
        createdAt = this[UsersTable.createdAt],
    )
}
