package com.storeabstract.domain

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

enum class UserRole {
    USER,
    ADMIN,
}

data class User(
    val id: UUID,
    val email: String,
    val passwordHash: String,
    val role: UserRole,
    val createdAt: LocalDateTime,
)

data class Product(
    val id: UUID,
    val name: String,
    val description: String,
    val price: BigDecimal,
    val stock: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

enum class OrderStatus {
    CREATED,
    CANCELLED,
}

data class OrderItem(
    val id: UUID,
    val orderId: UUID,
    val productId: UUID,
    val productName: String,
    val quantity: Int,
    val priceAtPurchase: BigDecimal,
)

data class Order(
    val id: UUID,
    val userId: UUID,
    val status: OrderStatus,
    val total: BigDecimal,
    val createdAt: LocalDateTime,
    val items: List<OrderItem>,
)

data class OrderStats(
    val totalOrders: Long,
    val createdOrders: Long,
    val cancelledOrders: Long,
    val totalRevenue: BigDecimal,
)

data class OrderEvent(
    val orderId: UUID,
    val userId: UUID,
    val status: OrderStatus,
    val total: BigDecimal,
    val occurredAt: LocalDateTime,
)
