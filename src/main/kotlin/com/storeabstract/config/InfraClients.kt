package com.storeabstract.config

import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.storeabstract.domain.OrderEvent
import com.storeabstract.service.OrderCache
import com.storeabstract.service.OrderEventPublisher
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

private const val ORDER_EVENTS_QUEUE = "order_events"

@Serializable
private data class OrderEventMessage(
    val orderId: String,
    val userId: String,
    val status: String,
    val total: String,
    val occurredAt: String,
)

class RabbitOrderEventPublisher(
    config: RabbitMqConfig,
    private val json: Json,
) : OrderEventPublisher {
    private val connection: Connection
    private val channel: Channel

    init {
        val factory = ConnectionFactory().apply {
            host = config.host
            port = config.port
            username = config.user
            password = config.password
            isAutomaticRecoveryEnabled = true
        }
        connection = factory.newConnection("store-abstract-api-publisher")
        channel = connection.createChannel()
        channel.queueDeclare(ORDER_EVENTS_QUEUE, true, false, false, null)
    }

    override fun publish(event: OrderEvent) {
        val payload = json.encodeToString(
            OrderEventMessage(
                orderId = event.orderId.toString(),
                userId = event.userId.toString(),
                status = event.status.name,
                total = event.total.toPlainString(),
                occurredAt = event.occurredAt.toString(),
            ),
        )

        channel.basicPublish("", ORDER_EVENTS_QUEUE, null, payload.toByteArray())
    }

    override fun close() {
        runCatching { channel.close() }
        runCatching { connection.close() }
    }
}

class RedisOrderCache(
    config: RedisConfig,
) : OrderCache {
    private val redisClient: RedisClient = RedisClient.create("redis://${config.host}:${config.port}")
    private val connection: StatefulRedisConnection<String, String> = redisClient.connect()

    override fun cache(orderId: UUID, payload: String, ttlSeconds: Long) {
        val key = "order:$orderId"
        val sync = connection.sync()
        sync.setex(key, ttlSeconds, payload)
    }

    override fun invalidate(orderId: UUID) {
        connection.sync().del("order:$orderId")
    }

    override fun close() {
        runCatching { connection.close() }
        redisClient.shutdown()
    }
}

class InMemoryOrderCache : OrderCache {
    private val store: MutableMap<UUID, String> = mutableMapOf()

    override fun cache(orderId: UUID, payload: String, ttlSeconds: Long) {
        store[orderId] = payload
    }

    override fun invalidate(orderId: UUID) {
        store.remove(orderId)
    }
}

class LoggingOrderEventPublisher : OrderEventPublisher {
    private val logger = LoggerFactory.getLogger(LoggingOrderEventPublisher::class.java)

    override fun publish(event: OrderEvent) {
        logger.info(
            "order-event orderId={} userId={} status={} total={} at={}",
            event.orderId,
            event.userId,
            event.status,
            event.total,
            LocalDateTime.now(),
        )
    }
}
