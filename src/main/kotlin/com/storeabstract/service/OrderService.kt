package com.storeabstract.service

import com.storeabstract.domain.Order
import com.storeabstract.dto.OrderResponse
import com.storeabstract.repository.CreateOrderLine
import com.storeabstract.repository.OrderRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

class OrderService(
    private val orderRepository: OrderRepository,
    private val orderCache: OrderCache,
    private val json: Json,
) {
    private val logger = LoggerFactory.getLogger(OrderService::class.java)

    fun createOrder(userId: UUID, lines: List<CreateOrderLine>): Order {
        val order = orderRepository.createOrder(userId, lines)

        runCatching {
            orderCache.cache(order.id, json.encodeToString(OrderResponse.fromDomain(order)), ttlSeconds = 900)
        }.onFailure { error ->
            logger.warn("Failed to cache order {}: {}", order.id, error.message)
        }

        return order
    }

    fun cancelOrder(userId: UUID, orderId: UUID): Order {
        val order = orderRepository.cancelOrder(orderId, userId)

        runCatching {
            orderCache.invalidate(order.id)
        }.onFailure { error ->
            logger.warn("Failed to invalidate order cache {}: {}", order.id, error.message)
        }

        return order
    }

    fun getOwnOrders(userId: UUID): List<Order> = orderRepository.listByUser(userId)
}
