package com.storeabstract.repository

import com.storeabstract.config.ApiException
import com.storeabstract.config.AuditLogsTable
import com.storeabstract.config.DatabaseFactory
import com.storeabstract.config.OrderItemsTable
import com.storeabstract.config.OrdersTable
import com.storeabstract.config.OutboxEventsTable
import com.storeabstract.config.ProductsTable
import com.storeabstract.domain.Order
import com.storeabstract.domain.OrderItem
import com.storeabstract.domain.OrderStats
import com.storeabstract.domain.OrderStatus
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

private const val ORDER_EVENT_TYPE = "ORDER_EVENT"

data class CreateOrderLine(
    val productId: UUID,
    val quantity: Int,
)

class OrderRepository {
    fun createOrder(userId: UUID, lines: List<CreateOrderLine>): Order = DatabaseFactory.dbQuery {
        if (lines.isEmpty()) {
            throw ApiException(HttpStatusCode.BadRequest, "Order must contain at least one item")
        }

        val now = LocalDateTime.now()
        val orderId = UUID.randomUUID()

        val resolvedItems = lines.map { line ->
            if (line.quantity <= 0) {
                throw ApiException(HttpStatusCode.BadRequest, "Quantity must be greater than zero")
            }

            val productRow = ProductsTable.selectAll()
                .where { ProductsTable.id eq line.productId }
                .forUpdate()
                .singleOrNull()
                ?: throw ApiException(HttpStatusCode.NotFound, "Product ${line.productId} not found")

            if (!productRow[ProductsTable.isActive]) {
                throw ApiException(HttpStatusCode.NotFound, "Product ${line.productId} not found")
            }

            val stock = productRow[ProductsTable.stock]
            if (stock < line.quantity) {
                throw ApiException(HttpStatusCode.BadRequest, "Insufficient stock for product ${line.productId}")
            }

            val updated = ProductsTable.update({ ProductsTable.id eq line.productId }) {
                with(org.jetbrains.exposed.sql.SqlExpressionBuilder) {
                    it.update(ProductsTable.stock, ProductsTable.stock - line.quantity)
                }
                it[ProductsTable.updatedAt] = now
            }

            if (updated == 0) {
                throw ApiException(HttpStatusCode.BadRequest, "Insufficient stock for product ${line.productId}")
            }

            val price = productRow[ProductsTable.price]
            val productName = productRow[ProductsTable.name]
            ResolvedOrderLine(
                productId = line.productId,
                productName = productName,
                quantity = line.quantity,
                priceAtPurchase = price,
            )
        }

        val total = resolvedItems.fold(BigDecimal.ZERO) { acc, item ->
            acc + item.priceAtPurchase.multiply(item.quantity.toBigDecimal())
        }

        OrdersTable.insert {
            it[OrdersTable.id] = orderId
            it[OrdersTable.userId] = userId
            it[OrdersTable.status] = OrderStatus.CREATED
            it[OrdersTable.total] = total
            it[OrdersTable.createdAt] = now
        }

        val orderItems = resolvedItems.map { item ->
            val itemId = UUID.randomUUID()
            OrderItemsTable.insert {
                it[OrderItemsTable.id] = itemId
                it[OrderItemsTable.orderId] = orderId
                it[OrderItemsTable.productId] = item.productId
                it[OrderItemsTable.quantity] = item.quantity
                it[OrderItemsTable.priceAtPurchase] = item.priceAtPurchase
            }

            OrderItem(
                id = itemId,
                orderId = orderId,
                productId = item.productId,
                productName = item.productName,
                quantity = item.quantity,
                priceAtPurchase = item.priceAtPurchase,
            )
        }

        insertAuditLog(
            actorUserId = userId,
            action = "ORDER_CREATED",
            payload = "{" +
                "\"orderId\":\"$orderId\"," +
                "\"userId\":\"$userId\"," +
                "\"items\":${orderItems.size}," +
                "\"total\":\"${total.toPlainString()}\"" +
                "}",
            now = now,
        )

        enqueueOrderEvent(
            orderId = orderId,
            userId = userId,
            status = OrderStatus.CREATED,
            total = total,
            occurredAt = now,
            now = now,
        )

        Order(
            id = orderId,
            userId = userId,
            status = OrderStatus.CREATED,
            total = total,
            createdAt = now,
            items = orderItems,
        )
    }

    fun cancelOrder(orderId: UUID, userId: UUID): Order = DatabaseFactory.dbQuery {
        val existing = OrdersTable.selectAll()
            .where { OrdersTable.id eq orderId }
            .singleOrNull()
            ?: throw ApiException(HttpStatusCode.NotFound, "Order not found")

        if (existing[OrdersTable.userId].value != userId) {
            throw ApiException(HttpStatusCode.NotFound, "Order not found")
        }

        if (existing[OrdersTable.status] == OrderStatus.CANCELLED) {
            throw ApiException(HttpStatusCode.BadRequest, "Order already cancelled")
        }

        OrdersTable.update({ OrdersTable.id eq orderId }) {
            it[OrdersTable.status] = OrderStatus.CANCELLED
        }

        val items = loadOrderItems(orderId)
        val now = LocalDateTime.now()

        insertAuditLog(
            actorUserId = userId,
            action = "ORDER_CANCELLED",
            payload = "{" +
                "\"orderId\":\"$orderId\"," +
                "\"userId\":\"$userId\"" +
                "}",
            now = now,
        )

        enqueueOrderEvent(
            orderId = orderId,
            userId = userId,
            status = OrderStatus.CANCELLED,
            total = existing[OrdersTable.total],
            occurredAt = now,
            now = now,
        )

        Order(
            id = existing[OrdersTable.id].value,
            userId = existing[OrdersTable.userId].value,
            status = OrderStatus.CANCELLED,
            total = existing[OrdersTable.total],
            createdAt = existing[OrdersTable.createdAt],
            items = items,
        )
    }

    fun listByUser(userId: UUID): List<Order> = DatabaseFactory.dbQuery {
        OrdersTable.selectAll()
            .where { OrdersTable.userId eq userId }
            .orderBy(OrdersTable.createdAt, SortOrder.DESC)
            .map { row ->
                val orderId = row[OrdersTable.id].value
                row.toDomain(loadOrderItems(orderId))
            }
    }

    fun getStats(): OrderStats = DatabaseFactory.dbQuery {
        val allOrders = OrdersTable.selectAll().toList()
        val totalOrders = allOrders.size.toLong()
        val createdOrders = allOrders.count { it[OrdersTable.status] == OrderStatus.CREATED }.toLong()
        val cancelledOrders = allOrders.count { it[OrdersTable.status] == OrderStatus.CANCELLED }.toLong()
        val totalRevenue = allOrders
            .filter { it[OrdersTable.status] == OrderStatus.CREATED }
            .fold(BigDecimal.ZERO) { acc, row -> acc + row[OrdersTable.total] }

        OrderStats(
            totalOrders = totalOrders,
            createdOrders = createdOrders,
            cancelledOrders = cancelledOrders,
            totalRevenue = totalRevenue,
        )
    }

    private fun loadOrderItems(orderId: UUID): List<OrderItem> {
        return OrderItemsTable
            .innerJoin(ProductsTable)
            .selectAll()
            .where { OrderItemsTable.orderId eq orderId }
            .map {
                OrderItem(
                    id = it[OrderItemsTable.id].value,
                    orderId = it[OrderItemsTable.orderId].value,
                    productId = it[OrderItemsTable.productId].value,
                    productName = it[ProductsTable.name],
                    quantity = it[OrderItemsTable.quantity],
                    priceAtPurchase = it[OrderItemsTable.priceAtPurchase],
                )
            }
    }

    private fun insertAuditLog(actorUserId: UUID?, action: String, payload: String, now: LocalDateTime) {
        AuditLogsTable.insert {
            it[AuditLogsTable.id] = UUID.randomUUID()
            it[AuditLogsTable.actorUserId] = actorUserId
            it[AuditLogsTable.action] = action
            it[AuditLogsTable.payload] = payload
            it[AuditLogsTable.createdAt] = now
        }
    }

    private fun enqueueOrderEvent(
        orderId: UUID,
        userId: UUID,
        status: OrderStatus,
        total: BigDecimal,
        occurredAt: LocalDateTime,
        now: LocalDateTime,
    ) {
        val payload = "{" +
            "\"orderId\":\"$orderId\"," +
            "\"userId\":\"$userId\"," +
            "\"status\":\"${status.name}\"," +
            "\"total\":\"${total.toPlainString()}\"," +
            "\"occurredAt\":\"$occurredAt\"" +
            "}"

        OutboxEventsTable.insert {
            it[OutboxEventsTable.id] = UUID.randomUUID()
            it[OutboxEventsTable.eventType] = ORDER_EVENT_TYPE
            it[OutboxEventsTable.payload] = payload
            it[OutboxEventsTable.attempts] = 0
            it[OutboxEventsTable.nextAttemptAt] = now
            it[OutboxEventsTable.publishedAt] = null
            it[OutboxEventsTable.lastError] = null
            it[OutboxEventsTable.createdAt] = now
        }
    }

    private fun ResultRow.toDomain(items: List<OrderItem>): Order = Order(
        id = this[OrdersTable.id].value,
        userId = this[OrdersTable.userId].value,
        status = this[OrdersTable.status],
        total = this[OrdersTable.total],
        createdAt = this[OrdersTable.createdAt],
        items = items,
    )

    private data class ResolvedOrderLine(
        val productId: UUID,
        val productName: String,
        val quantity: Int,
        val priceAtPurchase: BigDecimal,
    )
}
