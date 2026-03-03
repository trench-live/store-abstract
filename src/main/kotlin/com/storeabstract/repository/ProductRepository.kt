package com.storeabstract.repository

import com.storeabstract.config.DatabaseFactory
import com.storeabstract.config.ProductsTable
import com.storeabstract.domain.Product
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

data class ProductCreateInput(
    val name: String,
    val description: String,
    val price: BigDecimal,
    val stock: Int,
)

class ProductRepository {
    fun listAll(): List<Product> = DatabaseFactory.dbQuery {
        ProductsTable.selectAll()
            .where { ProductsTable.isActive eq true }
            .map { it.toDomain() }
    }

    fun findById(id: UUID): Product? = DatabaseFactory.dbQuery {
        ProductsTable.selectAll()
            .where { ProductsTable.id eq id }
            .singleOrNull()
            ?.takeIf { it[ProductsTable.isActive] }
            ?.toDomain()
    }

    fun create(input: ProductCreateInput): Product = DatabaseFactory.dbQuery {
        val now = LocalDateTime.now()
        val id = UUID.randomUUID()

        ProductsTable.insert {
            it[ProductsTable.id] = id
            it[ProductsTable.name] = input.name
            it[ProductsTable.description] = input.description
            it[ProductsTable.price] = input.price
            it[ProductsTable.stock] = input.stock
            it[ProductsTable.isActive] = true
            it[ProductsTable.createdAt] = now
            it[ProductsTable.updatedAt] = now
        }

        Product(
            id = id,
            name = input.name,
            description = input.description,
            price = input.price,
            stock = input.stock,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun update(id: UUID, input: ProductCreateInput): Product? = DatabaseFactory.dbQuery {
        val existing = ProductsTable.selectAll()
            .where { ProductsTable.id eq id }
            .singleOrNull()
            ?: return@dbQuery null

        if (!existing[ProductsTable.isActive]) {
            return@dbQuery null
        }

        val now = LocalDateTime.now()
        ProductsTable.update({ ProductsTable.id eq id }) {
            it[ProductsTable.name] = input.name
            it[ProductsTable.description] = input.description
            it[ProductsTable.price] = input.price
            it[ProductsTable.stock] = input.stock
            it[ProductsTable.updatedAt] = now
        }

        ProductsTable.selectAll().where { ProductsTable.id eq id }.single().toDomain()
    }

    fun delete(id: UUID): Boolean = DatabaseFactory.dbQuery {
        val existing = ProductsTable.selectAll()
            .where { ProductsTable.id eq id }
            .singleOrNull()
            ?: return@dbQuery false

        if (!existing[ProductsTable.isActive]) {
            return@dbQuery false
        }

        val now = LocalDateTime.now()
        ProductsTable.update({ ProductsTable.id eq id }) {
            it[ProductsTable.isActive] = false
            it[ProductsTable.stock] = 0
            it[ProductsTable.updatedAt] = now
        } > 0
    }

    private fun ResultRow.toDomain(): Product = Product(
        id = this[ProductsTable.id].value,
        name = this[ProductsTable.name],
        description = this[ProductsTable.description],
        price = this[ProductsTable.price],
        stock = this[ProductsTable.stock],
        createdAt = this[ProductsTable.createdAt],
        updatedAt = this[ProductsTable.updatedAt],
    )
}

