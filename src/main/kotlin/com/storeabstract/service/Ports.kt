package com.storeabstract.service

import com.storeabstract.domain.OrderEvent
import java.util.UUID

interface OrderEventPublisher : AutoCloseable {
    fun publish(event: OrderEvent)
    override fun close() {}
}

interface OrderCache : AutoCloseable {
    fun cache(orderId: UUID, payload: String, ttlSeconds: Long)
    fun invalidate(orderId: UUID)
    override fun close() {}
}
