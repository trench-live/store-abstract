package com.storeabstract.config

import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

private const val ORDER_EVENTS_QUEUE = "order_events"
private const val ORDER_EVENT_TYPE = "ORDER_EVENT"
private const val OUTBOX_BATCH_SIZE = 50
private const val OUTBOX_POLL_INTERVAL_MS = 2000L
private const val OUTBOX_MAX_BACKOFF_SECONDS = 300L

class OrderEventsWorker(
    private val dbConfig: DatabaseConfig,
    private val rabbitConfig: RabbitMqConfig,
) {
    private val logger = LoggerFactory.getLogger(OrderEventsWorker::class.java)

    fun start() {
        DatabaseFactory.init(dbConfig, migrate = true)

        val factory = ConnectionFactory().apply {
            host = rabbitConfig.host
            port = rabbitConfig.port
            username = rabbitConfig.user
            password = rabbitConfig.password
            isAutomaticRecoveryEnabled = true
        }

        val connection = factory.newConnection("store-abstract-worker")
        val publishChannel = connection.createChannel()
        val consumeChannel = connection.createChannel()

        publishChannel.queueDeclare(ORDER_EVENTS_QUEUE, true, false, false, null)
        consumeChannel.queueDeclare(ORDER_EVENTS_QUEUE, true, false, false, null)

        val running = AtomicBoolean(true)

        startOutboxRelayThread(running, publishChannel)

        logger.info("Worker started: outbox relay + queue consumer")

        val deliverCallback = DeliverCallback { _, message ->
            val body = String(message.body, StandardCharsets.UTF_8)
            logger.info("Received order event: {}", body)
            logger.info("Simulating email notification for event")
        }
        val cancelCallback = CancelCallback { consumerTag ->
            logger.warn("Consumer {} has been cancelled", consumerTag)
        }

        consumeChannel.basicConsume(ORDER_EVENTS_QUEUE, true, deliverCallback, cancelCallback)

        Runtime.getRuntime().addShutdownHook(Thread {
            running.set(false)
            runCatching { publishChannel.close() }
            runCatching { consumeChannel.close() }
            runCatching { connection.close() }
            runCatching { DatabaseFactory.close() }
        })

        CountDownLatch(1).await()
    }

    private fun startOutboxRelayThread(running: AtomicBoolean, publishChannel: Channel) {
        val thread = Thread {
            while (running.get()) {
                runCatching {
                    relayPendingOutboxEvents(publishChannel)
                }.onFailure { error ->
                    logger.error("Outbox relay failed: {}", error.message)
                }

                runCatching { Thread.sleep(OUTBOX_POLL_INTERVAL_MS) }
            }
        }
        thread.isDaemon = true
        thread.name = "outbox-relay"
        thread.start()
    }

    private fun relayPendingOutboxEvents(publishChannel: Channel) {
        val now = LocalDateTime.now()
        val pendingEvents = DatabaseFactory.dbQuery {
            OutboxEventsTable.selectAll()
                .where { OutboxEventsTable.eventType eq ORDER_EVENT_TYPE }
                .andWhere { OutboxEventsTable.publishedAt.isNull() }
                .andWhere { OutboxEventsTable.nextAttemptAt lessEq now }
                .orderBy(OutboxEventsTable.createdAt, SortOrder.ASC)
                .limit(OUTBOX_BATCH_SIZE)
                .map { row -> row.toOutboxEvent() }
        }

        if (pendingEvents.isEmpty()) {
            return
        }

        pendingEvents.forEach { event ->
            runCatching {
                publishChannel.basicPublish(
                    "",
                    ORDER_EVENTS_QUEUE,
                    null,
                    event.payload.toByteArray(StandardCharsets.UTF_8),
                )
                markPublished(event)
            }.onFailure { error ->
                scheduleRetry(event, error)
            }
        }
    }

    private fun markPublished(event: PendingOutboxEvent) {
        DatabaseFactory.dbQuery {
            OutboxEventsTable.update({ OutboxEventsTable.id eq event.id }) {
                it[attempts] = event.attempts + 1
                it[publishedAt] = LocalDateTime.now()
                it[lastError] = null
            }
        }
    }

    private fun scheduleRetry(event: PendingOutboxEvent, error: Throwable) {
        val nextAttempts = event.attempts + 1
        val backoffSeconds = minOf(OUTBOX_MAX_BACKOFF_SECONDS, nextAttempts.toLong() * 5L)
        val nextAttemptAt = LocalDateTime.now().plusSeconds(backoffSeconds)

        DatabaseFactory.dbQuery {
            OutboxEventsTable.update({ OutboxEventsTable.id eq event.id }) {
                it[attempts] = nextAttempts
                it[lastError] = error.message?.take(1024)
                it[OutboxEventsTable.nextAttemptAt] = nextAttemptAt
            }
        }

        logger.warn(
            "Outbox publish failed for event {} (attempt {}), next retry at {}",
            event.id,
            nextAttempts,
            nextAttemptAt,
        )
    }

    private fun ResultRow.toOutboxEvent(): PendingOutboxEvent = PendingOutboxEvent(
        id = this[OutboxEventsTable.id].value,
        payload = this[OutboxEventsTable.payload],
        attempts = this[OutboxEventsTable.attempts],
    )

    private data class PendingOutboxEvent(
        val id: UUID,
        val payload: String,
        val attempts: Int,
    )
}
