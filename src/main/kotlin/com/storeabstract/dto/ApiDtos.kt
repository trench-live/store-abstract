package com.storeabstract.dto

import com.storeabstract.domain.Order
import com.storeabstract.domain.OrderStats
import com.storeabstract.domain.Product
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.util.UUID

@Serializable
data class ErrorResponse(
    val message: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val token: String,
)

@Serializable
data class ProductRequest(
    val name: String,
    val description: String,
    @Serializable(with = BigDecimalAsStringSerializer::class)
    val price: BigDecimal,
    val stock: Int,
)

@Serializable
data class ProductResponse(
    val id: String,
    val name: String,
    val description: String,
    @Serializable(with = BigDecimalAsStringSerializer::class)
    val price: BigDecimal,
    val stock: Int,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: java.time.LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val updatedAt: java.time.LocalDateTime,
) {
    companion object {
        fun fromDomain(product: Product): ProductResponse = ProductResponse(
            id = product.id.toString(),
            name = product.name,
            description = product.description,
            price = product.price,
            stock = product.stock,
            createdAt = product.createdAt,
            updatedAt = product.updatedAt,
        )
    }
}

@Serializable
data class OrderItemRequest(
    val productId: String,
    val quantity: Int,
)

@Serializable
data class CreateOrderRequest(
    val items: List<OrderItemRequest>,
)

@Serializable
data class OrderItemResponse(
    val id: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    @Serializable(with = BigDecimalAsStringSerializer::class)
    val priceAtPurchase: BigDecimal,
)

@Serializable
data class OrderResponse(
    val id: String,
    val userId: String,
    val status: String,
    @Serializable(with = BigDecimalAsStringSerializer::class)
    val total: BigDecimal,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: java.time.LocalDateTime,
    val items: List<OrderItemResponse>,
) {
    companion object {
        fun fromDomain(order: Order): OrderResponse = OrderResponse(
            id = order.id.toString(),
            userId = order.userId.toString(),
            status = order.status.name,
            total = order.total,
            createdAt = order.createdAt,
            items = order.items.map {
                OrderItemResponse(
                    id = it.id.toString(),
                    orderId = it.orderId.toString(),
                    productId = it.productId.toString(),
                    productName = it.productName,
                    quantity = it.quantity,
                    priceAtPurchase = it.priceAtPurchase,
                )
            },
        )
    }
}

@Serializable
data class OrderStatsResponse(
    val totalOrders: Long,
    val createdOrders: Long,
    val cancelledOrders: Long,
    @Serializable(with = BigDecimalAsStringSerializer::class)
    val totalRevenue: BigDecimal,
) {
    companion object {
        fun fromDomain(stats: OrderStats): OrderStatsResponse = OrderStatsResponse(
            totalOrders = stats.totalOrders,
            createdOrders = stats.createdOrders,
            cancelledOrders = stats.cancelledOrders,
            totalRevenue = stats.totalRevenue,
        )
    }
}

fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
